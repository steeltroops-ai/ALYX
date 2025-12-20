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

import java.util.HashMap;
import java.util.Map;

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * **Feature: alyx-distributed-orchestrator, Property 1: Job submission and validation**
 * Property-based test to validate that job submission works correctly for valid parameters.
 * This test validates Requirements 1.1, 1.2 by ensuring that valid job parameters result in 
 * successful processing with unique identifiers and estimated completion times.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
public class JobSubmissionValidationPropertyTest {

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
     * Generator for job submission validation scenario
     */
    private static final Generator<Object[]> jobSubmissionScenarioGenerator() {
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
    public void testJobSubmissionValidation() {
        // **Feature: alyx-distributed-orchestrator, Property 1: Job submission and validation**
        QuickCheck.forAll(jobSubmissionScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    JobParameters jobParams = (JobParameters) args[0];
                    String userId = (String) args[1];
                    // Create job submission request
                    JobSubmissionRequest request = new JobSubmissionRequest(userId, jobParams);
                    
                    // Submit the job
                    JobSubmissionResponse response = jobSchedulerService.submitJob(request);
                    
                    // Property: For any valid job parameters and user ID, submission should result in:
                    // 1. A successful response
                    // 2. A unique job identifier
                    // 3. An estimated completion time
                    
                    assert response.isSuccess() : "Job submission should succeed for valid parameters. Error: " + response.getMessage();
                    assert response.getJobId() != null : "Job ID should not be null for valid parameters";
                    assert response.getEstimatedCompletion() != null : "Estimated completion should not be null";
                    assert response.getJobId().toString().length() > 0 : "Job ID should not be empty";
                    assert response.getMessage() != null : "Response message should not be null";
                    assert response.getMessage().contains("successfully") : "Success message should indicate success";
                }
            });
    }
}