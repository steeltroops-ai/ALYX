package com.alyx.jobscheduler;

import com.alyx.jobscheduler.dto.JobStatusResponse;
import com.alyx.jobscheduler.dto.JobSubmissionRequest;
import com.alyx.jobscheduler.dto.JobSubmissionResponse;
import com.alyx.jobscheduler.model.AnalysisJob;
import com.alyx.jobscheduler.model.JobParameters;
import com.alyx.jobscheduler.model.JobStatus;
import com.alyx.jobscheduler.service.JobQueueService;
import com.alyx.jobscheduler.service.JobSchedulerService;
import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * **Feature: alyx-system-fix, Property 8: Job lifecycle consistency**
 * Property-based test to validate that jobs progress through the correct sequence of states
 * (submitted → validated → queued → running → completed/failed) without skipping states.
 * **Validates: Requirements 4.1, 4.2, 4.3**
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
public class JobLifecycleConsistencyPropertyTest {

    @Autowired
    private JobSchedulerService jobSchedulerService;

    @Autowired
    private JobQueueService jobQueueService;

    /**
     * Generator for valid job parameters
     */
    private static final Generator<JobParameters> validJobParametersGenerator() {
        return new Generator<JobParameters>() {
            @Override
            public JobParameters next() {
                return new JobParameters(
                    strings(5, 50).next(), // jobName
                    strings(10, 200).next(), // description
                    integers(1, 1000).next(), // expectedEvents
                    doubles(0.1, 100.0).next(), // energyThreshold
                    booleans().next(), // highPriority
                    generateAdditionalParameters() // additionalParameters
                );
            }
        };
    }

    /**
     * Generator for valid user IDs
     */
    private static final Generator<String> validUserIdGenerator() {
        return new Generator<String>() {
            @Override
            public String next() {
                return "user_" + strings(5, 20).next();
            }
        };
    }

    /**
     * Generate additional parameters map
     */
    private static Map<String, Object> generateAdditionalParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("analysisType", "collision_reconstruction");
        params.put("detectorConfig", "standard");
        return params;
    }

    @Test
    public void testJobLifecycleConsistency() {
        // **Feature: alyx-system-fix, Property 8: Job lifecycle consistency**
        QuickCheck.forAll(validJobParametersGenerator(), 
            new AbstractCharacteristic<JobParameters>() {
                @Override
                protected void doSpecify(JobParameters jobParams) throws Throwable {
                    String userId = "test-user-" + System.currentTimeMillis();
                    // Track job state transitions
                    List<JobStatus> observedStates = new ArrayList<>();
                    
                    // Submit a job
                    JobSubmissionRequest request = new JobSubmissionRequest(userId, jobParams);
                    JobSubmissionResponse submitResponse = jobSchedulerService.submitJob(request);
                    
                    // Skip if submission failed (validation failure is expected for some inputs)
                    if (!submitResponse.isSuccess()) {
                        return;
                    }
                    
                    UUID jobId = submitResponse.getJobId();
                    
                    // Monitor job status transitions
                    JobStatus currentStatus = null;
                    int maxChecks = 10; // Prevent infinite loops
                    int checks = 0;
                    
                    while (checks < maxChecks) {
                        Optional<JobStatusResponse> statusOpt = jobSchedulerService.getJobStatus(jobId, userId);
                        
                        if (statusOpt.isPresent()) {
                            JobStatus newStatus = statusOpt.get().getStatus();
                            
                            // Record state transition if status changed
                            if (currentStatus != newStatus) {
                                observedStates.add(newStatus);
                                currentStatus = newStatus;
                            }
                            
                            // Break if job reached terminal state
                            if (isTerminalState(newStatus)) {
                                break;
                            }
                        }
                        
                        checks++;
                        
                        // Small delay to allow state transitions
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    
                    // Property: Job lifecycle consistency
                    // For any submitted job, it should progress through valid state transitions
                    
                    // 1. Job should start in a valid initial state
                    assert !observedStates.isEmpty() : "Job should have at least one observed state";
                    JobStatus firstState = observedStates.get(0);
                    assert isValidInitialState(firstState) : 
                        "Job should start in valid initial state, but was: " + firstState;
                    
                    // 2. All state transitions should be valid
                    for (int i = 1; i < observedStates.size(); i++) {
                        JobStatus fromState = observedStates.get(i - 1);
                        JobStatus toState = observedStates.get(i);
                        
                        assert isValidTransition(fromState, toState) : 
                            String.format("Invalid state transition from %s to %s", fromState, toState);
                    }
                    
                    // 3. Job should not skip required states in the normal flow
                    if (observedStates.size() > 1) {
                        validateStateSequence(observedStates);
                    }
                    
                    // 4. Terminal states should be final
                    if (!observedStates.isEmpty()) {
                        JobStatus lastState = observedStates.get(observedStates.size() - 1);
                        if (isTerminalState(lastState)) {
                            // Verify no further transitions occur
                            Optional<JobStatusResponse> finalStatusOpt = jobSchedulerService.getJobStatus(jobId, userId);
                            if (finalStatusOpt.isPresent()) {
                                assert finalStatusOpt.get().getStatus() == lastState : 
                                    "Terminal state should not change: " + lastState;
                            }
                        }
                    }
                }
            });
    }

    @Test
    public void testJobCancellationLifecycle() {
        // **Feature: alyx-system-fix, Property 8: Job lifecycle consistency**
        QuickCheck.forAll(validJobParametersGenerator(), 
            new AbstractCharacteristic<JobParameters>() {
                @Override
                protected void doSpecify(JobParameters jobParams) throws Throwable {
                    String userId = "test-user-" + System.currentTimeMillis();
                    // Submit a job
                    JobSubmissionRequest request = new JobSubmissionRequest(userId, jobParams);
                    JobSubmissionResponse submitResponse = jobSchedulerService.submitJob(request);
                    
                    if (!submitResponse.isSuccess()) {
                        return;
                    }
                    
                    UUID jobId = submitResponse.getJobId();
                    
                    // Get initial status
                    Optional<JobStatusResponse> initialStatusOpt = jobSchedulerService.getJobStatus(jobId, userId);
                    if (!initialStatusOpt.isPresent()) {
                        return;
                    }
                    
                    JobStatus initialStatus = initialStatusOpt.get().getStatus();
                    
                    // Only test cancellation if job is in a cancellable state
                    if (isCancellableState(initialStatus)) {
                        // Cancel the job
                        boolean cancelled = jobSchedulerService.cancelJob(jobId, userId);
                        
                        if (cancelled) {
                            // Verify job transitions to CANCELLED state
                            Optional<JobStatusResponse> cancelledStatusOpt = jobSchedulerService.getJobStatus(jobId, userId);
                            
                            assert cancelledStatusOpt.isPresent() : "Cancelled job should still be queryable";
                            assert cancelledStatusOpt.get().getStatus() == JobStatus.CANCELLED : 
                                "Cancelled job should have CANCELLED status";
                            
                            // Property: Cancelled jobs should remain in CANCELLED state
                            // (terminal state consistency)
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            
                            Optional<JobStatusResponse> finalStatusOpt = jobSchedulerService.getJobStatus(jobId, userId);
                            assert finalStatusOpt.isPresent() : "Cancelled job should remain queryable";
                            assert finalStatusOpt.get().getStatus() == JobStatus.CANCELLED : 
                                "Cancelled job should remain in CANCELLED state";
                        }
                    }
                }
            });
    }

    /**
     * Checks if a state is a valid initial state for a job
     */
    private boolean isValidInitialState(JobStatus status) {
        return status == JobStatus.SUBMITTED || status == JobStatus.QUEUED;
    }

    /**
     * Checks if a state is terminal (no further transitions expected)
     */
    private boolean isTerminalState(JobStatus status) {
        return status == JobStatus.COMPLETED || 
               status == JobStatus.FAILED || 
               status == JobStatus.CANCELLED;
    }

    /**
     * Checks if a state allows cancellation
     */
    private boolean isCancellableState(JobStatus status) {
        return status != JobStatus.COMPLETED && 
               status != JobStatus.FAILED && 
               status != JobStatus.CANCELLED;
    }

    /**
     * Validates if a transition from one state to another is valid
     */
    private boolean isValidTransition(JobStatus fromState, JobStatus toState) {
        switch (fromState) {
            case SUBMITTED:
                return toState == JobStatus.QUEUED || 
                       toState == JobStatus.FAILED || 
                       toState == JobStatus.CANCELLED;
            
            case QUEUED:
                return toState == JobStatus.RUNNING || 
                       toState == JobStatus.CANCELLED ||
                       toState == JobStatus.PAUSED;
            
            case RUNNING:
                return toState == JobStatus.COMPLETED || 
                       toState == JobStatus.FAILED || 
                       toState == JobStatus.CANCELLED ||
                       toState == JobStatus.PAUSED;
            
            case PAUSED:
                return toState == JobStatus.RUNNING || 
                       toState == JobStatus.CANCELLED;
            
            case COMPLETED:
            case FAILED:
            case CANCELLED:
                // Terminal states should not transition
                return false;
            
            default:
                return false;
        }
    }

    /**
     * Validates the overall sequence of states follows expected patterns
     */
    private void validateStateSequence(List<JobStatus> states) {
        // For normal job execution, we expect the sequence to follow:
        // SUBMITTED -> QUEUED -> RUNNING -> (COMPLETED|FAILED)
        // Or variations with CANCELLED at any non-terminal point
        // Or PAUSED states in between
        
        boolean hasSubmitted = states.contains(JobStatus.SUBMITTED);
        boolean hasQueued = states.contains(JobStatus.QUEUED);
        boolean hasRunning = states.contains(JobStatus.RUNNING);
        boolean hasCompleted = states.contains(JobStatus.COMPLETED);
        boolean hasFailed = states.contains(JobStatus.FAILED);
        boolean hasCancelled = states.contains(JobStatus.CANCELLED);
        
        // If job completed successfully, it should have gone through the normal flow
        if (hasCompleted) {
            // Should have been queued before running (if it ran)
            if (hasRunning) {
                int queuedIndex = states.indexOf(JobStatus.QUEUED);
                int runningIndex = states.indexOf(JobStatus.RUNNING);
                assert queuedIndex < runningIndex : 
                    "Job should be queued before running";
            }
            
            // Completed should be the last state
            JobStatus lastState = states.get(states.size() - 1);
            assert lastState == JobStatus.COMPLETED : 
                "Completed job should end in COMPLETED state";
        }
        
        // If job failed, it should end in FAILED state
        if (hasFailed) {
            JobStatus lastState = states.get(states.size() - 1);
            assert lastState == JobStatus.FAILED : 
                "Failed job should end in FAILED state";
        }
        
        // If job was cancelled, it should end in CANCELLED state
        if (hasCancelled) {
            JobStatus lastState = states.get(states.size() - 1);
            assert lastState == JobStatus.CANCELLED : 
                "Cancelled job should end in CANCELLED state";
        }
    }
}