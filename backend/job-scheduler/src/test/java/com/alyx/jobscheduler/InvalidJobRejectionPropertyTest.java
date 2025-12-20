package com.alyx.jobscheduler;

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

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * **Feature: alyx-distributed-orchestrator, Property 2: Invalid job rejection**
 * Property-based test to validate that invalid job parameters are properly rejected.
 * This test validates Requirements 1.3 by ensuring that invalid job parameters result in 
 * rejection with specific error messages without queuing the job.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
public class InvalidJobRejectionPropertyTest {

    @Autowired
    private JobSchedulerService jobSchedulerService;

    /**
     * Generator for invalid job parameters with empty/null job names
     */
    private static final Generator<JobParameters> invalidJobNameGenerator() {
        return new Generator<JobParameters>() {
            @Override
            public JobParameters next() {
                String invalidJobName = booleans().next() ? "" : null; // Empty or null
                return new JobParameters(
                    invalidJobName,
                    strings(10, 200).next(), // description
                    integers(1, 1000).next(), // expectedEvents
                    doubles(0.1, 100.0).next(), // energyThreshold
                    booleans().next() // highPriority
                );
            }
        };
    }

    /**
     * Generator for invalid job parameters with non-positive expected events
     */
    private static final Generator<JobParameters> invalidExpectedEventsGenerator() {
        return new Generator<JobParameters>() {
            @Override
            public JobParameters next() {
                int invalidEvents = integers(-1000, 0).next(); // Negative or zero
                return new JobParameters(
                    strings(5, 50).next(), // jobName
                    strings(10, 200).next(), // description
                    invalidEvents,
                    doubles(0.1, 100.0).next(), // energyThreshold
                    booleans().next() // highPriority
                );
            }
        };
    }

    /**
     * Generator for invalid job parameters with non-positive energy threshold
     */
    private static final Generator<JobParameters> invalidEnergyThresholdGenerator() {
        return new Generator<JobParameters>() {
            @Override
            public JobParameters next() {
                double invalidThreshold = doubles(-100.0, 0.0).next(); // Negative or zero
                return new JobParameters(
                    strings(5, 50).next(), // jobName
                    strings(10, 200).next(), // description
                    integers(1, 1000).next(), // expectedEvents
                    invalidThreshold,
                    booleans().next() // highPriority
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

    @Test
    public void testInvalidJobNameRejection() {
        // **Feature: alyx-distributed-orchestrator, Property 2: Invalid job rejection**
        QuickCheck.forAll(invalidJobNameGenerator(), 
            new AbstractCharacteristic<JobParameters>() {
                @Override
                protected void doSpecify(JobParameters jobParams) throws Throwable {
                    String userId = "test-user-" + System.currentTimeMillis();
                    // Create job submission request with invalid job name
                    JobSubmissionRequest request = new JobSubmissionRequest(userId, jobParams);
                    
                    // Submit the job
                    JobSubmissionResponse response = jobSchedulerService.submitJob(request);
                    
                    // Property: For any job parameters with invalid job name, submission should result in:
                    // 1. A failure response
                    // 2. No job ID assigned
                    // 3. Specific error message about job name
                    
                    assert !response.isSuccess() : "Job submission should fail for invalid job name";
                    assert response.getJobId() == null : "Job ID should be null for invalid parameters";
                    assert response.getEstimatedCompletion() == null : "Estimated completion should be null for invalid parameters";
                    assert response.getMessage() != null : "Error message should not be null";
                    assert response.getMessage().toLowerCase().contains("name") : "Error message should mention job name issue";
                }
            });
    }

    @Test
    public void testInvalidExpectedEventsRejection() {
        // **Feature: alyx-distributed-orchestrator, Property 2: Invalid job rejection**
        QuickCheck.forAll(invalidExpectedEventsGenerator(), 
            new AbstractCharacteristic<JobParameters>() {
                @Override
                protected void doSpecify(JobParameters jobParams) throws Throwable {
                    String userId = "test-user-" + System.currentTimeMillis();
                    // Create job submission request with invalid expected events
                    JobSubmissionRequest request = new JobSubmissionRequest(userId, jobParams);
                    
                    // Submit the job
                    JobSubmissionResponse response = jobSchedulerService.submitJob(request);
                    
                    // Property: For any job parameters with invalid expected events, submission should result in:
                    // 1. A failure response
                    // 2. No job ID assigned
                    // 3. Specific error message about expected events
                    
                    assert !response.isSuccess() : "Job submission should fail for invalid expected events";
                    assert response.getJobId() == null : "Job ID should be null for invalid parameters";
                    assert response.getEstimatedCompletion() == null : "Estimated completion should be null for invalid parameters";
                    assert response.getMessage() != null : "Error message should not be null";
                    assert response.getMessage().toLowerCase().contains("events") || 
                           response.getMessage().toLowerCase().contains("positive") : 
                           "Error message should mention events or positive requirement";
                }
            });
    }

    @Test
    public void testInvalidEnergyThresholdRejection() {
        // **Feature: alyx-distributed-orchestrator, Property 2: Invalid job rejection**
        QuickCheck.forAll(invalidEnergyThresholdGenerator(), 
            new AbstractCharacteristic<JobParameters>() {
                @Override
                protected void doSpecify(JobParameters jobParams) throws Throwable {
                    String userId = "test-user-" + System.currentTimeMillis();
                    // Create job submission request with invalid energy threshold
                    JobSubmissionRequest request = new JobSubmissionRequest(userId, jobParams);
                    
                    // Submit the job
                    JobSubmissionResponse response = jobSchedulerService.submitJob(request);
                    
                    // Property: For any job parameters with invalid energy threshold, submission should result in:
                    // 1. A failure response
                    // 2. No job ID assigned
                    // 3. Specific error message about energy threshold
                    
                    assert !response.isSuccess() : "Job submission should fail for invalid energy threshold";
                    assert response.getJobId() == null : "Job ID should be null for invalid parameters";
                    assert response.getEstimatedCompletion() == null : "Estimated completion should be null for invalid parameters";
                    assert response.getMessage() != null : "Error message should not be null";
                    assert response.getMessage().toLowerCase().contains("energy") || 
                           response.getMessage().toLowerCase().contains("threshold") || 
                           response.getMessage().toLowerCase().contains("positive") : 
                           "Error message should mention energy threshold or positive requirement";
                }
            });
    }
}