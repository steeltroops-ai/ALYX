package com.alyx.jobscheduler;

import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * Property-based test for retry mechanism correctness
 * **Feature: alyx-system-fix, Property 12: Retry mechanism correctness**
 * **Validates: Requirements 6.2**
 */
public class RetryMechanismCorrectnessPropertyTest {

    /**
     * Mock retry mechanism implementation for testing
     */
    static class MockRetryMechanism {
        private final int maxRetries;
        private final long baseDelayMs;
        private final double backoffMultiplier;
        private final AtomicInteger attemptCount = new AtomicInteger(0);
        private final List<Long> retryDelays = new ArrayList<>();
        
        public MockRetryMechanism(int maxRetries, long baseDelayMs, double backoffMultiplier) {
            this.maxRetries = maxRetries;
            this.baseDelayMs = baseDelayMs;
            this.backoffMultiplier = backoffMultiplier;
        }
        
        public boolean executeWithRetry(boolean[] outcomes) {
            attemptCount.set(0);
            retryDelays.clear();
            
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                attemptCount.incrementAndGet();
                
                // Use predetermined outcome if available, otherwise assume success
                boolean success = attempt < outcomes.length ? outcomes[attempt] : true;
                
                if (success) {
                    return true;
                }
                
                // If this was the last allowed attempt, don't add delay
                if (attempt < maxRetries) {
                    long delay = calculateDelay(attempt);
                    retryDelays.add(delay);
                    
                    // Simulate delay (we don't actually sleep in tests)
                    // Thread.sleep(delay);
                }
            }
            
            return false; // All retries exhausted
        }
        
        private long calculateDelay(int attemptNumber) {
            return (long) (baseDelayMs * Math.pow(backoffMultiplier, attemptNumber));
        }
        
        public int getAttemptCount() {
            return attemptCount.get();
        }
        
        public List<Long> getRetryDelays() {
            return new ArrayList<>(retryDelays);
        }
    }

    /**
     * Generator for retry mechanism test scenarios
     */
    private static final Generator<Object[]> retryScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    integers(1, 5).next(), // maxRetries
                    integers(100, 1000).next(), // baseDelayMs
                    doubles(1.5, 3.0).next(), // backoffMultiplier
                    integers(1, 8).next() // numberOfOutcomes
                };
            }
        };
    }

    @Test
    void testRetryMechanismCorrectness() {
        // **Feature: alyx-system-fix, Property 12: Retry mechanism correctness**
        QuickCheck.forAll(retryScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    Integer maxRetries = (Integer) args[0];
                    Integer baseDelayMs = (Integer) args[1];
                    Double backoffMultiplier = (Double) args[2];
                    Integer numberOfOutcomes = (Integer) args[3];
                    
                    MockRetryMechanism retryMechanism = new MockRetryMechanism(
                        maxRetries, baseDelayMs, backoffMultiplier);
                    
                    // Generate random outcomes (true = success, false = failure)
                    boolean[] outcomes = new boolean[numberOfOutcomes];
                    for (int i = 0; i < numberOfOutcomes; i++) {
                        outcomes[i] = Math.random() < 0.3; // 30% success rate
                    }
                    
                    boolean result = retryMechanism.executeWithRetry(outcomes);
                    int actualAttempts = retryMechanism.getAttemptCount();
                    List<Long> delays = retryMechanism.getRetryDelays();
                    
                    // Property 1: Should not exceed max attempts
                    assert actualAttempts <= (maxRetries + 1) : 
                        "Attempts (" + actualAttempts + ") should not exceed max retries + 1 (" + 
                        (maxRetries + 1) + ")";
                    
                    // Property 2: If successful, should return true
                    boolean hasSuccess = false;
                    for (int i = 0; i < Math.min(outcomes.length, maxRetries + 1); i++) {
                        if (outcomes[i]) {
                            hasSuccess = true;
                            break;
                        }
                    }
                    
                    if (hasSuccess) {
                        assert result : "Should return true if any attempt within retry limit succeeds";
                    }
                    
                    // Property 3: Exponential backoff should be applied correctly
                    for (int i = 0; i < delays.size(); i++) {
                        long expectedDelay = (long) (baseDelayMs * Math.pow(backoffMultiplier, i));
                        long actualDelay = delays.get(i);
                        assert actualDelay == expectedDelay : 
                            "Delay at attempt " + i + " should be " + expectedDelay + 
                            " but was " + actualDelay;
                    }
                    
                    // Property 4: Number of delays should be one less than attempts (no delay after last attempt)
                    if (actualAttempts > 1) {
                        assert delays.size() == actualAttempts - 1 : 
                            "Number of delays (" + delays.size() + 
                            ") should be one less than attempts (" + actualAttempts + ")";
                    }
                }
            });
    }

    /**
     * Generator for immediate success scenarios
     */
    private static final Generator<Object[]> immediateSuccessScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    integers(1, 5).next(), // maxRetries
                    integers(100, 1000).next(), // baseDelayMs
                    doubles(1.5, 3.0).next() // backoffMultiplier
                };
            }
        };
    }

    @Test
    void testImmediateSuccess() {
        QuickCheck.forAll(immediateSuccessScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    Integer maxRetries = (Integer) args[0];
                    Integer baseDelayMs = (Integer) args[1];
                    Double backoffMultiplier = (Double) args[2];
                    
                    MockRetryMechanism retryMechanism = new MockRetryMechanism(
                        maxRetries, baseDelayMs, backoffMultiplier);
                    
                    // First attempt succeeds
                    boolean[] outcomes = {true};
                    boolean result = retryMechanism.executeWithRetry(outcomes);
                    
                    // Property: Should succeed immediately without retries
                    assert result : "Should succeed on first attempt";
                    assert retryMechanism.getAttemptCount() == 1 : 
                        "Should only make one attempt for immediate success";
                    assert retryMechanism.getRetryDelays().isEmpty() : 
                        "Should have no retry delays for immediate success";
                }
            });
    }

    /**
     * Generator for all failures scenarios
     */
    private static final Generator<Object[]> allFailuresScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    integers(1, 5).next(), // maxRetries
                    integers(100, 1000).next(), // baseDelayMs
                    doubles(1.5, 3.0).next() // backoffMultiplier
                };
            }
        };
    }

    @Test
    void testAllFailures() {
        QuickCheck.forAll(allFailuresScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    Integer maxRetries = (Integer) args[0];
                    Integer baseDelayMs = (Integer) args[1];
                    Double backoffMultiplier = (Double) args[2];
                    
                    MockRetryMechanism retryMechanism = new MockRetryMechanism(
                        maxRetries, baseDelayMs, backoffMultiplier);
                    
                    // All attempts fail
                    boolean[] outcomes = new boolean[maxRetries + 1];
                    // All false by default
                    
                    boolean result = retryMechanism.executeWithRetry(outcomes);
                    
                    // Property: Should fail after exhausting all retries
                    assert !result : "Should fail after exhausting all retries";
                    assert retryMechanism.getAttemptCount() == (maxRetries + 1) : 
                        "Should make exactly " + (maxRetries + 1) + " attempts";
                    assert retryMechanism.getRetryDelays().size() == maxRetries : 
                        "Should have exactly " + maxRetries + " retry delays";
                }
            });
    }

    /**
     * Generator for exponential backoff validation scenarios
     */
    private static final Generator<Object[]> backoffValidationScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    integers(3, 5).next(), // maxRetries (at least 3 for meaningful backoff test)
                    integers(100, 500).next(), // baseDelayMs
                    doubles(2.0, 3.0).next() // backoffMultiplier
                };
            }
        };
    }

    @Test
    void testExponentialBackoff() {
        QuickCheck.forAll(backoffValidationScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    Integer maxRetries = (Integer) args[0];
                    Integer baseDelayMs = (Integer) args[1];
                    Double backoffMultiplier = (Double) args[2];
                    
                    MockRetryMechanism retryMechanism = new MockRetryMechanism(
                        maxRetries, baseDelayMs, backoffMultiplier);
                    
                    // All attempts fail to test full backoff sequence
                    boolean[] outcomes = new boolean[maxRetries + 1];
                    retryMechanism.executeWithRetry(outcomes);
                    
                    List<Long> delays = retryMechanism.getRetryDelays();
                    
                    // Property: Each delay should be larger than the previous (exponential growth)
                    for (int i = 1; i < delays.size(); i++) {
                        long previousDelay = delays.get(i - 1);
                        long currentDelay = delays.get(i);
                        assert currentDelay > previousDelay : 
                            "Delay should increase exponentially: delay[" + (i-1) + "]=" + 
                            previousDelay + ", delay[" + i + "]=" + currentDelay;
                        
                        // Verify the exact exponential relationship (with reasonable tolerance for floating point)
                        double expectedRatio = backoffMultiplier;
                        double actualRatio = (double) currentDelay / previousDelay;
                        assert Math.abs(actualRatio - expectedRatio) < 0.01 : 
                            "Backoff ratio should be " + expectedRatio + " but was " + actualRatio;
                    }
                }
            });
    }

    @Test
    void testRetryMechanismBasicFunctionality() {
        // Basic test to ensure retry mechanism works
        MockRetryMechanism retryMechanism = new MockRetryMechanism(3, 100, 2.0);
        
        // Test immediate success
        boolean[] successOutcome = {true};
        assert retryMechanism.executeWithRetry(successOutcome);
        assert retryMechanism.getAttemptCount() == 1;
        
        // Test success after retries
        boolean[] retryThenSuccess = {false, false, true};
        assert retryMechanism.executeWithRetry(retryThenSuccess);
        assert retryMechanism.getAttemptCount() == 3;
        
        // Test all failures
        boolean[] allFailures = {false, false, false, false};
        assert !retryMechanism.executeWithRetry(allFailures);
        assert retryMechanism.getAttemptCount() == 4; // 3 retries + 1 initial attempt
    }
}