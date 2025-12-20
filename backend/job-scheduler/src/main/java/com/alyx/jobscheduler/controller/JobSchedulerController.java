package com.alyx.jobscheduler.controller;

import com.alyx.jobscheduler.dto.JobSubmissionRequest;
import com.alyx.jobscheduler.dto.JobSubmissionResponse;
import com.alyx.jobscheduler.dto.JobStatusResponse;
import com.alyx.jobscheduler.model.JobParameters;
import com.alyx.jobscheduler.service.JobQueueService;
import com.alyx.jobscheduler.service.JobSchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for job scheduling operations
 */
@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*") // Configure appropriately for production
public class JobSchedulerController {
    
    private final JobSchedulerService jobSchedulerService;

    @Autowired
    public JobSchedulerController(JobSchedulerService jobSchedulerService) {
        this.jobSchedulerService = jobSchedulerService;
    }

    /**
     * Submit a new analysis job
     */
    @PostMapping("/submit")
    public ResponseEntity<JobSubmissionResponse> submitJob(@Valid @RequestBody JobSubmissionRequest request) {
        JobSubmissionResponse response = jobSchedulerService.submitJob(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get status of a specific job
     */
    @GetMapping("/{jobId}/status")
    public ResponseEntity<JobStatusResponse> getJobStatus(
            @PathVariable UUID jobId,
            @RequestParam String userId) {
        
        Optional<JobStatusResponse> status = jobSchedulerService.getJobStatus(jobId, userId);
        
        if (status.isPresent()) {
            return ResponseEntity.ok(status.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all jobs for a user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<JobStatusResponse>> getUserJobs(@PathVariable String userId) {
        List<JobStatusResponse> jobs = jobSchedulerService.getUserJobs(userId);
        return ResponseEntity.ok(jobs);
    }

    /**
     * Cancel a job
     */
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> cancelJob(
            @PathVariable UUID jobId,
            @RequestParam String userId) {
        
        boolean cancelled = jobSchedulerService.cancelJob(jobId, userId);
        
        if (cancelled) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Modify a job (only if not yet running)
     */
    @PutMapping("/{jobId}")
    public ResponseEntity<JobSubmissionResponse> modifyJob(
            @PathVariable UUID jobId,
            @RequestParam String userId,
            @Valid @RequestBody JobParameters newParameters) {
        
        JobSubmissionResponse response = jobSchedulerService.modifyJob(jobId, userId, newParameters);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get queue status information
     */
    @GetMapping("/queue/status")
    public ResponseEntity<JobQueueService.QueueStatus> getQueueStatus() {
        JobQueueService.QueueStatus status = jobSchedulerService.getQueueStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Job Scheduler Service is running");
    }

    /**
     * Exception handler for validation errors
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<JobSubmissionResponse> handleException(Exception e) {
        JobSubmissionResponse errorResponse = JobSubmissionResponse.failure("Internal server error: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}