package com.alyx.jobscheduler.service;

import com.alyx.jobscheduler.model.AnalysisJob;
import com.alyx.jobscheduler.model.JobStatus;
import com.alyx.jobscheduler.repository.AnalysisJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.PriorityQueue;

/**
 * Service for managing job queue with priority handling
 */
@Service
@Transactional
public class JobQueueService {
    
    private final AnalysisJobRepository jobRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ExecutionTimePredictionService predictionService;
    
    // In-memory queue for fast access (would be replaced with Redis in production)
    private final PriorityQueue<AnalysisJob> jobQueue;
    private final ConcurrentHashMap<UUID, AnalysisJob> activeJobs;

    @Autowired
    public JobQueueService(AnalysisJobRepository jobRepository, 
                          KafkaTemplate<String, Object> kafkaTemplate,
                          ExecutionTimePredictionService predictionService) {
        this.jobRepository = jobRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.predictionService = predictionService;
        
        // Priority queue ordered by priority (lower number = higher priority) then submission time
        this.jobQueue = new PriorityQueue<>((j1, j2) -> {
            int priorityCompare = Integer.compare(j1.getPriority(), j2.getPriority());
            if (priorityCompare != 0) return priorityCompare;
            return j1.getSubmittedAt().compareTo(j2.getSubmittedAt());
        });
        
        this.activeJobs = new ConcurrentHashMap<>();
        
        // Initialize queue from database on startup
        initializeQueueFromDatabase();
    }

    /**
     * Adds a job to the queue
     */
    public AnalysisJob queueJob(AnalysisJob job) {
        // Set estimated completion time and resource requirements
        job.setEstimatedCompletion(predictionService.predictExecutionTime(job.getParameters()));
        job.setAllocatedCores(predictionService.estimateRequiredCores(job.getParameters()));
        job.setMemoryAllocationMB(predictionService.estimateRequiredMemoryMB(job.getParameters()));
        job.setStatus(JobStatus.QUEUED);
        
        // Save to database
        AnalysisJob savedJob = jobRepository.save(job);
        
        // Add to in-memory queue
        synchronized (jobQueue) {
            jobQueue.offer(savedJob);
        }
        
        // Publish job queued event
        kafkaTemplate.send("job-events", "job.queued", savedJob);
        
        return savedJob;
    }

    /**
     * Gets the next job from the queue for processing
     */
    public Optional<AnalysisJob> getNextJob() {
        synchronized (jobQueue) {
            AnalysisJob nextJob = jobQueue.poll();
            if (nextJob != null) {
                nextJob.setStatus(JobStatus.RUNNING);
                nextJob = jobRepository.save(nextJob);
                activeJobs.put(nextJob.getJobId(), nextJob);
                
                // Publish job started event
                kafkaTemplate.send("job-events", "job.started", nextJob);
                
                return Optional.of(nextJob);
            }
            return Optional.empty();
        }
    }

    /**
     * Updates job progress
     */
    public void updateJobProgress(UUID jobId, double progressPercentage) {
        AnalysisJob job = activeJobs.get(jobId);
        if (job != null) {
            job.setProgressPercentage(progressPercentage);
            jobRepository.save(job);
            
            // Publish progress update event
            kafkaTemplate.send("job-events", "job.progress", job);
        }
    }

    /**
     * Marks a job as completed
     */
    public void completeJob(UUID jobId, boolean success, String errorMessage) {
        AnalysisJob job = activeJobs.remove(jobId);
        if (job != null) {
            job.setStatus(success ? JobStatus.COMPLETED : JobStatus.FAILED);
            job.setActualCompletion(Instant.now());
            job.setProgressPercentage(success ? 100.0 : job.getProgressPercentage());
            if (errorMessage != null) {
                job.setErrorMessage(errorMessage);
            }
            
            jobRepository.save(job);
            
            // Update ML model with actual execution data
            if (success && job.getActualCompletion() != null) {
                long executionTimeMs = job.getActualCompletion().toEpochMilli() - 
                                     job.getSubmittedAt().toEpochMilli();
                predictionService.updateModelWithActualData(job.getParameters(), executionTimeMs);
            }
            
            // Publish job completed event
            kafkaTemplate.send("job-events", success ? "job.completed" : "job.failed", job);
        }
    }

    /**
     * Cancels a job
     */
    public boolean cancelJob(UUID jobId, String userId) {
        // Check if job is in queue
        synchronized (jobQueue) {
            Optional<AnalysisJob> queuedJob = jobQueue.stream()
                .filter(job -> job.getJobId().equals(jobId) && job.getUserId().equals(userId))
                .findFirst();
            
            if (queuedJob.isPresent()) {
                jobQueue.remove(queuedJob.get());
                AnalysisJob job = queuedJob.get();
                job.setStatus(JobStatus.CANCELLED);
                job.setActualCompletion(Instant.now());
                jobRepository.save(job);
                
                kafkaTemplate.send("job-events", "job.cancelled", job);
                return true;
            }
        }
        
        // Check if job is running
        AnalysisJob runningJob = activeJobs.get(jobId);
        if (runningJob != null && runningJob.getUserId().equals(userId)) {
            runningJob.setStatus(JobStatus.CANCELLED);
            runningJob.setActualCompletion(Instant.now());
            jobRepository.save(runningJob);
            activeJobs.remove(jobId);
            
            kafkaTemplate.send("job-events", "job.cancelled", runningJob);
            return true;
        }
        
        return false;
    }

    /**
     * Gets queue status information
     */
    public QueueStatus getQueueStatus() {
        synchronized (jobQueue) {
            long queuedCount = jobQueue.size();
            long runningCount = activeJobs.size();
            long totalCompleted = jobRepository.countByStatus(JobStatus.COMPLETED);
            long totalFailed = jobRepository.countByStatus(JobStatus.FAILED);
            
            return new QueueStatus(queuedCount, runningCount, totalCompleted, totalFailed);
        }
    }

    /**
     * Initializes the queue from database on startup
     */
    private void initializeQueueFromDatabase() {
        List<AnalysisJob> queuedJobs = jobRepository.findQueuedJobsByPriority();
        synchronized (jobQueue) {
            jobQueue.addAll(queuedJobs);
        }
        
        List<AnalysisJob> runningJobs = jobRepository.findByStatusOrderByPriorityAscSubmittedAtAsc(JobStatus.RUNNING);
        for (AnalysisJob job : runningJobs) {
            activeJobs.put(job.getJobId(), job);
        }
    }

    /**
     * Queue status information
     */
    public static class QueueStatus {
        private final long queuedJobs;
        private final long runningJobs;
        private final long completedJobs;
        private final long failedJobs;

        public QueueStatus(long queuedJobs, long runningJobs, long completedJobs, long failedJobs) {
            this.queuedJobs = queuedJobs;
            this.runningJobs = runningJobs;
            this.completedJobs = completedJobs;
            this.failedJobs = failedJobs;
        }

        public long getQueuedJobs() { return queuedJobs; }
        public long getRunningJobs() { return runningJobs; }
        public long getCompletedJobs() { return completedJobs; }
        public long getFailedJobs() { return failedJobs; }
    }
}