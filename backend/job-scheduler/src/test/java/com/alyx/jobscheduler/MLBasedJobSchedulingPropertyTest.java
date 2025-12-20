package com.alyx.jobscheduler;

import com.alyx.jobscheduler.model.JobParameters;
import com.alyx.jobscheduler.service.ExecutionTimePredictionService;
import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * **Feature: alyx-distributed-orchestrator, Property 21: ML-based job scheduling**
 * Property-based test to validate that ML-based scheduling works correctly.
 * This test validates Requirements 7.1, 7.2 by ensuring that the system predicts execution 
 * time using machine learning models and schedules based on data locality and resource capacity.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
public class MLBasedJobSchedulingPropertyTest {

    @Autowired
    private ExecutionTimePredictionService predictionService;

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
                    integers(1, 10000).next(), // expectedEvents (wider range for ML testing)
                    doubles(0.1, 1000.0).next(), // energyThreshold (wider range)
                    booleans().next(), // highPriority
                    generateAdditionalParameters() // additionalParameters
                );
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
    public void testExecutionTimePrediction() {
        // **Feature: alyx-distributed-orchestrator, Property 21: ML-based job scheduling**
        QuickCheck.forAll(validJobParametersGenerator(), 
            new AbstractCharacteristic<JobParameters>() {
                @Override
                protected void doSpecify(JobParameters jobParams) throws Throwable {
                    // Get execution time prediction
                    Instant currentTime = Instant.now();
                    Instant predictedCompletion = predictionService.predictExecutionTime(jobParams);
                    
                    // Property: For any submitted analysis job, the system should predict execution time using ML models
                    // 1. Predicted completion time should be in the future
                    // 2. Prediction should be reasonable based on job parameters
                    // 3. High priority jobs should have shorter predicted times (better resource allocation)
                    
                    assert predictedCompletion != null : "Predicted completion time should not be null";
                    assert predictedCompletion.isAfter(currentTime) : 
                        "Predicted completion should be in the future";
                    
                    // Test that prediction is reasonable (not too far in the future)
                    long predictionHours = (predictedCompletion.toEpochMilli() - currentTime.toEpochMilli()) / (1000 * 60 * 60);
                    assert predictionHours < 24 : "Prediction should be within 24 hours for reasonable job sizes";
                    
                    // Test that larger jobs have longer predicted times
                    if (jobParams.getExpectedEvents() > 5000) {
                        JobParameters smallerJob = new JobParameters(
                            jobParams.getJobName(),
                            jobParams.getDescription(),
                            1000, // Much smaller
                            jobParams.getEnergyThreshold(),
                            jobParams.isHighPriority()
                        );
                        
                        Instant smallerJobPrediction = predictionService.predictExecutionTime(smallerJob);
                        assert predictedCompletion.isAfter(smallerJobPrediction) : 
                            "Larger jobs should have longer predicted execution times";
                    }
                }
            });
    }

    @Test
    public void testResourceRequirementEstimation() {
        // **Feature: alyx-distributed-orchestrator, Property 21: ML-based job scheduling**
        QuickCheck.forAll(validJobParametersGenerator(), 
            new AbstractCharacteristic<JobParameters>() {
                @Override
                protected void doSpecify(JobParameters jobParams) throws Throwable {
                    // Get resource requirement estimates
                    int estimatedCores = predictionService.estimateRequiredCores(jobParams);
                    long estimatedMemoryMB = predictionService.estimateRequiredMemoryMB(jobParams);
                    
                    // Property: For any job parameters, resource estimation should be reasonable
                    // 1. Core count should be positive and reasonable
                    // 2. Memory should be positive and reasonable
                    // 3. High priority jobs should get more resources
                    
                    assert estimatedCores > 0 : "Estimated cores should be positive";
                    assert estimatedCores <= 16 : "Estimated cores should not exceed reasonable limits";
                    
                    assert estimatedMemoryMB > 0 : "Estimated memory should be positive";
                    assert estimatedMemoryMB >= 512 : "Estimated memory should meet minimum requirements";
                    assert estimatedMemoryMB <= 64000 : "Estimated memory should not exceed reasonable limits (64GB)";
                    
                    // Test that larger jobs require more resources
                    if (jobParams.getExpectedEvents() > 5000) {
                        JobParameters smallerJob = new JobParameters(
                            jobParams.getJobName(),
                            jobParams.getDescription(),
                            1000, // Much smaller
                            jobParams.getEnergyThreshold(),
                            jobParams.isHighPriority()
                        );
                        
                        int smallerJobCores = predictionService.estimateRequiredCores(smallerJob);
                        long smallerJobMemory = predictionService.estimateRequiredMemoryMB(smallerJob);
                        
                        assert estimatedCores >= smallerJobCores : 
                            "Larger jobs should require at least as many cores";
                        assert estimatedMemoryMB >= smallerJobMemory : 
                            "Larger jobs should require at least as much memory";
                    }
                }
            });
    }

    @Test
    public void testHighPriorityJobOptimization() {
        // **Feature: alyx-distributed-orchestrator, Property 21: ML-based job scheduling**
        QuickCheck.forAll(validJobParametersGenerator(), 
            new AbstractCharacteristic<JobParameters>() {
                @Override
                protected void doSpecify(JobParameters jobParams) throws Throwable {
                    // Skip if already high priority
                    if (jobParams.isHighPriority()) {
                        return;
                    }
                    
                    // Create equivalent high-priority job
                    JobParameters highPriorityJob = new JobParameters(
                        jobParams.getJobName(),
                        jobParams.getDescription(),
                        jobParams.getExpectedEvents(),
                        jobParams.getEnergyThreshold(),
                        true // High priority
                    );
                    
                    // Get predictions for both jobs
                    Instant normalPrediction = predictionService.predictExecutionTime(jobParams);
                    Instant highPriorityPrediction = predictionService.predictExecutionTime(highPriorityJob);
                    
                    int normalCores = predictionService.estimateRequiredCores(jobParams);
                    int highPriorityCores = predictionService.estimateRequiredCores(highPriorityJob);
                    
                    // Property: High priority jobs should be scheduled for faster completion
                    // 1. High priority jobs should complete sooner (or at least not later)
                    // 2. High priority jobs may get more cores allocated
                    
                    assert !highPriorityPrediction.isAfter(normalPrediction) : 
                        "High priority jobs should complete no later than normal priority jobs";
                    
                    assert highPriorityCores >= normalCores : 
                        "High priority jobs should get at least as many cores as normal jobs";
                }
            });
    }

    @Test
    public void testEnergyThresholdComplexityScaling() {
        // **Feature: alyx-distributed-orchestrator, Property 21: ML-based job scheduling**
        QuickCheck.forAll(validJobParametersGenerator(), 
            new AbstractCharacteristic<JobParameters>() {
                @Override
                protected void doSpecify(JobParameters jobParams) throws Throwable {
                    // Skip if energy threshold is already high
                    if (jobParams.getEnergyThreshold() > 500.0) {
                        return;
                    }
                    
                    // Create job with higher energy threshold (more complex)
                    JobParameters complexJob = new JobParameters(
                        jobParams.getJobName(),
                        jobParams.getDescription(),
                        jobParams.getExpectedEvents(),
                        jobParams.getEnergyThreshold() * 2.0, // Double the energy threshold
                        jobParams.isHighPriority()
                    );
                    
                    // Get predictions for both jobs
                    Instant normalPrediction = predictionService.predictExecutionTime(jobParams);
                    Instant complexPrediction = predictionService.predictExecutionTime(complexJob);
                    
                    long normalMemory = predictionService.estimateRequiredMemoryMB(jobParams);
                    long complexMemory = predictionService.estimateRequiredMemoryMB(complexJob);
                    
                    // Property: Higher energy thresholds should result in more complex processing
                    // 1. Complex jobs should take longer or at least not be faster
                    // 2. Complex jobs should require more memory
                    
                    assert !complexPrediction.isBefore(normalPrediction) : 
                        "Higher energy threshold jobs should take at least as long to complete";
                    
                    assert complexMemory >= normalMemory : 
                        "Higher energy threshold jobs should require at least as much memory";
                }
            });
    }
}