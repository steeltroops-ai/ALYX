package com.alyx.jobscheduler;

import com.alyx.jobscheduler.dto.JobStatusResponse;
import com.alyx.jobscheduler.dto.JobSubmissionRequest;
import com.alyx.jobscheduler.dto.JobSubmissionResponse;
import com.alyx.jobscheduler.model.JobParameters;
import com.alyx.jobscheduler.service.JobSchedulerService;
import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * **Feature: alyx-distributed-orchestrator, Property 3: Job status consistency**
 * Property-based test to validate that job status queries return accurate information.
 * This test validates Requirements 1.4 by ensuring that status queries return current 
 * progress and resource allocation information that accurately reflects the job's actual state.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
public class JobStatusConsistencyPropertyTest {

    @Autowired
    private JobSchedulerService jobSchedulerService;

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

    /**
     * Generator for job status consistency scenario
     */
    private static final Generator<Object[]> jobStatusScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    validJobParametersGenerator().next(),
                    validUserIdGenerator().next()
                };
            }
        };
    }

    @Test
    public void testJobStatusConsistency() {
        // **Feature: alyx-distributed-orchestrator, Property 3: Job status consistency**
        QuickCheck.forAll(jobStatusScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    JobParameters jobParams = (JobParameters) args[0];
                    String userId = (String) args[1];
                    // Submit a job
                    JobSubmissionRequest request = new JobSubmissionRequest(userId, jobParams);
                    JobSubmissionResponse submitResponse = jobSchedulerService.submitJob(request);
                    
                    // Skip if submission failed (not the focus of this test)
                    if (!submitResponse.isSuccess()) {
                        return;
                    }
                    
                    // Query job status
                    Optional<JobStatusResponse> statusOpt = jobSchedulerService.getJobStatus(
                        submitResponse.getJobId(), userId);
                    
                    // Property: For any queued analysis job, status queries should return:
                    // 1. Current progress and resource allocation information
                    // 2. Information that accurately reflects the job's actual state
                    // 3. Consistent data with the original submission
                    
                    assert statusOpt.isPresent() : "Job status should be available for submitted job";
                    
                    JobStatusResponse status = statusOpt.get();
                    
                    // Verify job ID consistency
                    assert status.getJobId().equals(submitResponse.getJobId()) : 
                        "Status job ID should match submitted job ID";
                    
                    // Verify status is valid
                    assert status.getStatus() != null : "Job status should not be null";
                    
                    // Verify progress information is present and valid
                    assert status.getProgressPercentage() != null : "Progress percentage should not be null";
                    assert status.getProgressPercentage() >= 0.0 : "Progress should be non-negative";
                    assert status.getProgressPercentage() <= 100.0 : "Progress should not exceed 100%";
                    
                    // Verify resource allocation information is present
                    assert status.getAllocatedCores() != null : "Allocated cores should not be null";
                    assert status.getAllocatedCores() > 0 : "Allocated cores should be positive";
                    
                    assert status.getMemoryAllocationMB() != null : "Memory allocation should not be null";
                    assert status.getMemoryAllocationMB() > 0 : "Memory allocation should be positive";
                    
                    // Verify timestamps are consistent
                    assert status.getSubmittedAt() != null : "Submitted timestamp should not be null";
                    assert status.getEstimatedCompletion() != null : "Estimated completion should not be null";
                    assert status.getEstimatedCompletion().isAfter(status.getSubmittedAt()) : 
                        "Estimated completion should be after submission time";
                    
                    // Verify estimated completion matches original response
                    assert status.getEstimatedCompletion().equals(submitResponse.getEstimatedCompletion()) : 
                        "Estimated completion should match original submission response";
                }
            });
    }

    @Test
    public void testJobStatusForNonExistentJob() {
        // **Feature: alyx-distributed-orchestrator, Property 3: Job status consistency**
        QuickCheck.forAll(validUserIdGenerator(), 
            new AbstractCharacteristic<String>() {
                @Override
                protected void doSpecify(String userId) throws Throwable {
                    // Generate a random UUID that doesn't exist
                    java.util.UUID nonExistentJobId = java.util.UUID.randomUUID();
                    
                    // Query status for non-existent job
                    Optional<JobStatusResponse> statusOpt = jobSchedulerService.getJobStatus(
                        nonExistentJobId, userId);
                    
                    // Property: For any non-existent job ID, status query should return empty
                    assert !statusOpt.isPresent() : "Status should not be available for non-existent job";
                }
            });
    }
}