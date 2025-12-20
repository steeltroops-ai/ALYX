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
 * **Feature: alyx-distributed-orchestrator, Property 4: Permission-based job control**
 * Property-based test to validate that job control operations respect user permissions.
 * This test validates Requirements 1.5 by ensuring that users with appropriate permissions 
 * can perform job operations, while users without permissions are denied access.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
public class PermissionBasedJobControlPropertyTest {

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
     * Generator for high-priority job parameters
     */
    private static final Generator<JobParameters> highPriorityJobParametersGenerator() {
        return new Generator<JobParameters>() {
            @Override
            public JobParameters next() {
                return new JobParameters(
                    strings(5, 50).next(), // jobName
                    strings(10, 200).next(), // description
                    integers(1, 1000).next(), // expectedEvents
                    doubles(0.1, 100.0).next(), // energyThreshold
                    true, // highPriority = true
                    generateAdditionalParameters() // additionalParameters
                );
            }
        };
    }

    /**
     * Generator for admin user IDs (have special permissions)
     */
    private static final Generator<String> adminUserIdGenerator() {
        return new Generator<String>() {
            @Override
            public String next() {
                return "admin_" + strings(5, 20).next();
            }
        };
    }

    /**
     * Generator for regular user IDs (limited permissions)
     */
    private static final Generator<String> regularUserIdGenerator() {
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
     * Generator for admin user with high priority job scenario
     */
    private static final Generator<Object[]> adminHighPriorityScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    highPriorityJobParametersGenerator().next(),
                    adminUserIdGenerator().next()
                };
            }
        };
    }

    @Test
    public void testAdminUserHighPriorityJobPermission() {
        // **Feature: alyx-distributed-orchestrator, Property 4: Permission-based job control**
        QuickCheck.forAll(adminHighPriorityScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    JobParameters jobParams = (JobParameters) args[0];
                    String adminUserId = (String) args[1];
                    // Admin users should be able to submit high-priority jobs
                    JobSubmissionRequest request = new JobSubmissionRequest(adminUserId, jobParams);
                    JobSubmissionResponse response = jobSchedulerService.submitJob(request);
                    
                    // Property: For any admin user with high-priority job parameters, 
                    // job control operations should succeed
                    assert response.isSuccess() : 
                        "Admin users should be able to submit high-priority jobs. Error: " + response.getMessage();
                    assert response.getJobId() != null : "Job ID should be assigned for admin high-priority jobs";
                    assert response.getEstimatedCompletion() != null : "Estimated completion should be provided";
                }
            });
    }

    /**
     * Generator for regular user with high priority job scenario
     */
    private static final Generator<Object[]> regularHighPriorityScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    highPriorityJobParametersGenerator().next(),
                    regularUserIdGenerator().next()
                };
            }
        };
    }

    @Test
    public void testRegularUserHighPriorityJobDenial() {
        // **Feature: alyx-distributed-orchestrator, Property 4: Permission-based job control**
        QuickCheck.forAll(regularHighPriorityScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    JobParameters jobParams = (JobParameters) args[0];
                    String regularUserId = (String) args[1];
                    // Regular users should NOT be able to submit high-priority jobs
                    JobSubmissionRequest request = new JobSubmissionRequest(regularUserId, jobParams);
                    JobSubmissionResponse response = jobSchedulerService.submitJob(request);
                    
                    // Property: For any regular user with high-priority job parameters, 
                    // job control operations should be denied
                    assert !response.isSuccess() : 
                        "Regular users should not be able to submit high-priority jobs";
                    assert response.getJobId() == null : "Job ID should not be assigned for denied jobs";
                    assert response.getMessage() != null : "Error message should be provided";
                    assert response.getMessage().toLowerCase().contains("permission") : 
                        "Error message should mention permission issue";
                }
            });
    }

    /**
     * Generator for job cancellation scenario with two different users
     */
    private static final Generator<Object[]> jobCancellationScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    validJobParametersGenerator().next(),
                    regularUserIdGenerator().next(),
                    regularUserIdGenerator().next()
                };
            }
        };
    }

    @Test
    public void testJobCancellationPermissions() {
        // **Feature: alyx-distributed-orchestrator, Property 4: Permission-based job control**
        QuickCheck.forAll(jobCancellationScenarioGenerator(),
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    JobParameters jobParams = (JobParameters) args[0];
                    String ownerUserId = (String) args[1];
                    String otherUserId = (String) args[2];
                    // Skip if users are the same (not testing same-user scenario)
                    if (ownerUserId.equals(otherUserId)) {
                        return;
                    }
                    
                    // Submit a job as the owner
                    JobSubmissionRequest request = new JobSubmissionRequest(ownerUserId, jobParams);
                    JobSubmissionResponse submitResponse = jobSchedulerService.submitJob(request);
                    
                    // Skip if submission failed
                    if (!submitResponse.isSuccess()) {
                        return;
                    }
                    
                    // Try to cancel the job as the owner (should succeed)
                    boolean ownerCanCancel = jobSchedulerService.cancelJob(
                        submitResponse.getJobId(), ownerUserId);
                    
                    // Try to cancel the job as another user (should fail)
                    boolean otherCanCancel = jobSchedulerService.cancelJob(
                        submitResponse.getJobId(), otherUserId);
                    
                    // Property: Users should only be able to cancel their own jobs
                    assert ownerCanCancel : "Job owner should be able to cancel their own job";
                    assert !otherCanCancel : "Other users should not be able to cancel jobs they don't own";
                }
            });
    }

    /**
     * Generator for job status query scenario with two different users
     */
    private static final Generator<Object[]> jobStatusQueryScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    validJobParametersGenerator().next(),
                    regularUserIdGenerator().next(),
                    regularUserIdGenerator().next()
                };
            }
        };
    }

    @Test
    public void testJobStatusQueryPermissions() {
        // **Feature: alyx-distributed-orchestrator, Property 4: Permission-based job control**
        QuickCheck.forAll(jobStatusQueryScenarioGenerator(),
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    JobParameters jobParams = (JobParameters) args[0];
                    String ownerUserId = (String) args[1];
                    String otherUserId = (String) args[2];
                    // Skip if users are the same
                    if (ownerUserId.equals(otherUserId)) {
                        return;
                    }
                    
                    // Submit a job as the owner
                    JobSubmissionRequest request = new JobSubmissionRequest(ownerUserId, jobParams);
                    JobSubmissionResponse submitResponse = jobSchedulerService.submitJob(request);
                    
                    // Skip if submission failed
                    if (!submitResponse.isSuccess()) {
                        return;
                    }
                    
                    // Query job status as the owner (should succeed)
                    var ownerStatusQuery = jobSchedulerService.getJobStatus(
                        submitResponse.getJobId(), ownerUserId);
                    
                    // Query job status as another user (should fail)
                    var otherStatusQuery = jobSchedulerService.getJobStatus(
                        submitResponse.getJobId(), otherUserId);
                    
                    // Property: Users should only be able to query status of their own jobs
                    assert ownerStatusQuery.isPresent() : "Job owner should be able to query their own job status";
                    assert !otherStatusQuery.isPresent() : "Other users should not be able to query jobs they don't own";
                }
            });
    }
}