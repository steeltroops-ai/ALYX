package com.alyx.jobscheduler.service;

import com.alyx.jobscheduler.dto.JobSubmissionRequest;
import com.alyx.jobscheduler.dto.JobSubmissionResponse;
import com.alyx.jobscheduler.dto.JobStatusResponse;
import com.alyx.jobscheduler.model.AnalysisJob;
import com.alyx.jobscheduler.model.JobParameters;
import com.alyx.jobscheduler.repository.AnalysisJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main service for job scheduling operations
 */
@Service
@Transactional
public class JobSchedulerService {
    
    private final AnalysisJobRepository jobRepository;
    private final JobQueueService queueService;
    private final Validator validator;
    private final CacheService cacheService;

    @Autowired
    public JobSchedulerService(AnalysisJobRepository jobRepository, 
                              JobQueueService queueService,
                              Validator validator,
                              CacheService cacheService) {
        this.jobRepository = jobRepository;
        this.queueService = queueService;
        this.validator = validator;
        this.cacheService = cacheService;
    }

    /**
     * Submits a new analysis job
     */
    public JobSubmissionResponse submitJob(JobSubmissionRequest request) {
        try {
            // Validate job parameters
            ValidationResult validation = validateJobParameters(request.getParameters());
            if (!validation.isValid()) {
                return JobSubmissionResponse.failure(validation.getErrorMessage());
            }
            
            // Check user permissions and limits
            if (!checkUserPermissions(request.getUserId(), request.getParameters())) {
                return JobSubmissionResponse.failure("User does not have permission to submit this type of job");
            }
            
            // Create and queue the job
            AnalysisJob job = new AnalysisJob(request.getUserId(), request.getParameters());
            AnalysisJob queuedJob = queueService.queueJob(job);
            
            // Invalidate user's job cache since new job was added
            cacheService.invalidateJobCaches(queuedJob.getJobId(), request.getUserId());
            
            return JobSubmissionResponse.success(queuedJob.getJobId(), queuedJob.getEstimatedCompletion());
            
        } catch (Exception e) {
            return JobSubmissionResponse.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * Gets the status of a specific job (cached for performance)
     */
    @Cacheable(value = "jobStatus", key = "#jobId.toString() + ':' + #userId")
    public Optional<JobStatusResponse> getJobStatus(UUID jobId, String userId) {
        Optional<AnalysisJob> job = jobRepository.findByJobIdAndUserId(jobId, userId);
        return job.map(JobStatusResponse::fromAnalysisJob);
    }

    /**
     * Gets all jobs for a user (cached for performance)
     */
    @Cacheable(value = "userJobs", key = "#userId")
    public List<JobStatusResponse> getUserJobs(String userId) {
        List<AnalysisJob> jobs = jobRepository.findByUserIdOrderBySubmittedAtDesc(userId);
        return jobs.stream()
                   .map(JobStatusResponse::fromAnalysisJob)
                   .collect(Collectors.toList());
    }

    /**
     * Cancels a job and invalidates related caches
     */
    @Caching(evict = {
        @CacheEvict(value = "jobStatus", key = "#jobId.toString() + ':' + #userId"),
        @CacheEvict(value = "userJobs", key = "#userId"),
        @CacheEvict(value = "jobStatistics", allEntries = true)
    })
    public boolean cancelJob(UUID jobId, String userId) {
        Optional<AnalysisJob> jobOpt = jobRepository.findByJobIdAndUserId(jobId, userId);
        if (jobOpt.isPresent()) {
            AnalysisJob job = jobOpt.get();
            if (job.canBeCancelled()) {
                return queueService.cancelJob(jobId, userId);
            }
        }
        return false;
    }

    /**
     * Modifies a job (only if not yet running)
     */
    public JobSubmissionResponse modifyJob(UUID jobId, String userId, JobParameters newParameters) {
        Optional<AnalysisJob> jobOpt = jobRepository.findByJobIdAndUserId(jobId, userId);
        if (jobOpt.isEmpty()) {
            return JobSubmissionResponse.failure("Job not found");
        }
        
        AnalysisJob job = jobOpt.get();
        if (!job.canBeModified()) {
            return JobSubmissionResponse.failure("Job cannot be modified in current state: " + job.getStatus());
        }
        
        // Validate new parameters
        ValidationResult validation = validateJobParameters(newParameters);
        if (!validation.isValid()) {
            return JobSubmissionResponse.failure(validation.getErrorMessage());
        }
        
        // Update job parameters
        job.setParameters(newParameters);
        job.setPriority(newParameters.isHighPriority() ? 1 : 5);
        
        // Re-queue the job with new parameters
        AnalysisJob updatedJob = queueService.queueJob(job);
        
        return JobSubmissionResponse.success(updatedJob.getJobId(), updatedJob.getEstimatedCompletion());
    }

    /**
     * Gets queue status
     */
    public JobQueueService.QueueStatus getQueueStatus() {
        return queueService.getQueueStatus();
    }

    /**
     * Validates job parameters
     */
    private ValidationResult validateJobParameters(JobParameters parameters) {
        // Use Bean Validation
        Set<ConstraintViolation<JobParameters>> violations = validator.validate(parameters);
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            return ValidationResult.invalid(errorMessage);
        }
        
        // Additional business logic validation
        if (parameters.getJobName() == null || parameters.getJobName().trim().isEmpty()) {
            return ValidationResult.invalid("Job name cannot be empty");
        }
        
        if (parameters.getExpectedEvents() <= 0) {
            return ValidationResult.invalid("Expected events must be positive");
        }
        
        if (parameters.getEnergyThreshold() <= 0) {
            return ValidationResult.invalid("Energy threshold must be positive");
        }
        
        // Check reasonable limits
        if (parameters.getExpectedEvents() > 10_000_000) {
            return ValidationResult.invalid("Expected events cannot exceed 10 million");
        }
        
        if (parameters.getEnergyThreshold() > 1000.0) {
            return ValidationResult.invalid("Energy threshold cannot exceed 1000 GeV");
        }
        
        return ValidationResult.valid();
    }

    /**
     * Checks if user has permission to submit this type of job
     */
    private boolean checkUserPermissions(String userId, JobParameters parameters) {
        // In a real system, this would check user roles and quotas
        // For now, we implement basic checks
        
        // Check if user has too many active jobs
        List<AnalysisJob> activeJobs = jobRepository.findActiveJobsByUser(userId);
        if (activeJobs.size() >= 10) { // Max 10 active jobs per user
            return false;
        }
        
        // High priority jobs require special permission (simplified check)
        if (parameters.isHighPriority() && !userId.startsWith("admin_")) {
            return false;
        }
        
        return true;
    }

    /**
     * Validation result helper class
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}