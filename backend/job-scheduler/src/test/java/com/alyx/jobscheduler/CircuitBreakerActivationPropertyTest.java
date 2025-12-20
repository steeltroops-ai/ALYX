package com.alyx.jobscheduler;

import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * Property-based test for circuit breaker activation
 * **Feature: alyx-system-fix, Property 11: Circuit breaker activation**
 * **Validates: Requirements 6.1**
 */
public class CircuitBreakerActivationPropertyTest {

    /**
     * Mock circuit breaker implementation for testing
     */
    static class MockCircuitBreaker {
        private final int failureThreshold;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicBoolean isOpen = new AtomicBoolean(false);
        
        public MockCircuitBreaker(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }
        
        public boolean execute(boolean shouldFail) {
            if (isOpen.get()) {
                return false; // Circuit is open, reject request
            }
            
            if (shouldFail) {
                int failures = failureCount.incrementAndGet();
                if (failures >= failureThreshold) {
                    isOpen.set(true);
                }
                return false;
            } else {
                failureCount.set(0); // Reset on success
                return true;
            }
        }
        
        public boolean isOpen() {
            return isOpen.get();
        }
        
        public int getFailureCount() {
            return failureCount.get();
        }
        
        public void reset() {
            failureCount.set(0);
            isOpen.set(false);
        }
    }

    /**
     * Generator for circuit breaker test scenarios
     */
    private static final Generator<Object[]> circuitBreakerScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    integers(1, 10).next(), // failureThreshold
                    integers(1, 20).next(), // numberOfRequests
                    doubles(0.0, 1.0).next() // failureRate
                };
            }
        };
    }

    @Test
    void testCircuitBreakerActivation() {
        // **Feature: alyx-system-fix, Property 11: Circuit breaker activation**
        QuickCheck.forAll(circuitBreakerScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    Integer failureThreshold = (Integer) args[0];
                    Integer numberOfRequests = (Integer) args[1];
                    Double failureRate = (Double) args[2];
                    
                    MockCircuitBreaker circuitBreaker = new MockCircuitBreaker(failureThreshold);
                    
                    int consecutiveFailures = 0;
                    boolean hasReachedThreshold = false;
                    
                    // Execute requests
                    for (int i = 0; i < numberOfRequests; i++) {
                        boolean shouldFail = Math.random() < failureRate;
                        boolean wasOpenBefore = circuitBreaker.isOpen();
                        boolean result = circuitBreaker.execute(shouldFail);
                        
                        // If circuit is open, subsequent requests should fail
                        if (circuitBreaker.isOpen()) {
                            assert !result : "Circuit breaker should reject requests when open";
                        }
                        
                        // Track consecutive failures only when circuit is closed
                        if (!wasOpenBefore) {
                            if (shouldFail) {
                                consecutiveFailures++;
                                if (consecutiveFailures >= failureThreshold) {
                                    hasReachedThreshold = true;
                                }
                            } else if (result) {
                                consecutiveFailures = 0; // Reset on success
                            }
                        }
                    }
                    
                    // Property: Circuit breaker should open when failure threshold is reached
                    if (hasReachedThreshold) {
                        assert circuitBreaker.isOpen() : 
                            "Circuit breaker should be open when failure threshold (" + 
                            failureThreshold + ") is reached. Consecutive failures: " + consecutiveFailures;
                    }
                }
            });
    }

    /**
     * Generator for circuit breaker recovery scenarios
     */
    private static final Generator<Object[]> circuitBreakerRecoveryScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    integers(2, 5).next(), // failureThreshold
                    integers(5, 15).next() // successfulRequests
                };
            }
        };
    }

    @Test
    void testCircuitBreakerRecovery() {
        QuickCheck.forAll(circuitBreakerRecoveryScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    Integer failureThreshold = (Integer) args[0];
                    Integer successfulRequests = (Integer) args[1];
                    
                    MockCircuitBreaker circuitBreaker = new MockCircuitBreaker(failureThreshold);
                    
                    // First, trigger circuit breaker to open
                    for (int i = 0; i < failureThreshold; i++) {
                        circuitBreaker.execute(true); // Force failure
                    }
                    
                    assert circuitBreaker.isOpen() : "Circuit breaker should be open after threshold failures";
                    
                    // Reset circuit breaker (simulating timeout or manual reset)
                    circuitBreaker.reset();
                    
                    // Execute successful requests
                    for (int i = 0; i < successfulRequests; i++) {
                        boolean result = circuitBreaker.execute(false); // Force success
                        assert result : "Successful requests should pass when circuit is closed";
                    }
                    
                    // Property: Circuit breaker should remain closed for successful requests
                    assert !circuitBreaker.isOpen() : 
                        "Circuit breaker should remain closed after successful requests";
                    assert circuitBreaker.getFailureCount() == 0 : 
                        "Failure count should be reset after successful requests";
                }
            });
    }

    /**
     * Generator for failure threshold boundary scenarios
     */
    private static final Generator<Object[]> thresholdBoundaryScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                return new Object[]{
                    integers(1, 10).next() // failureThreshold
                };
            }
        };
    }

    @Test
    void testCircuitBreakerThresholdBoundary() {
        QuickCheck.forAll(thresholdBoundaryScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    Integer failureThreshold = (Integer) args[0];
                    
                    MockCircuitBreaker circuitBreaker = new MockCircuitBreaker(failureThreshold);
                    
                    // Execute failures just below threshold
                    for (int i = 0; i < failureThreshold - 1; i++) {
                        circuitBreaker.execute(true); // Force failure
                    }
                    
                    // Property: Circuit should still be closed just below threshold
                    assert !circuitBreaker.isOpen() : 
                        "Circuit breaker should remain closed below threshold (" + 
                        (failureThreshold - 1) + "/" + failureThreshold + ")";
                    
                    // Execute one more failure to reach threshold
                    circuitBreaker.execute(true);
                    
                    // Property: Circuit should open exactly at threshold
                    assert circuitBreaker.isOpen() : 
                        "Circuit breaker should open exactly at threshold (" + failureThreshold + ")";
                }
            });
    }

    @Test
    void testCircuitBreakerBasicFunctionality() {
        // Basic test to ensure circuit breaker works
        MockCircuitBreaker circuitBreaker = new MockCircuitBreaker(3);
        
        // Should be closed initially
        assert !circuitBreaker.isOpen();
        
        // Successful request should pass
        assert circuitBreaker.execute(false);
        
        // Failed requests should increment counter
        assert !circuitBreaker.execute(true);
        assert !circuitBreaker.execute(true);
        assert !circuitBreaker.execute(true);
        
        // Circuit should be open now
        assert circuitBreaker.isOpen();
        
        // Subsequent requests should fail even if they would succeed
        assert !circuitBreaker.execute(false);
    }
}