# ALYX API Gateway Security Implementation

## Overview

This document describes the comprehensive security implementation for the ALYX Distributed Analysis Orchestrator API Gateway. The security framework implements multiple layers of protection including authentication, authorization, input validation, encryption, and comprehensive audit logging.

## Security Features Implemented

### 1. JWT-based Authentication

**Implementation**: Enhanced JWT service with role-based claims
- **Algorithm**: HS256 with 256-bit secret key
- **Token Expiration**: 24 hours (configurable)
- **Claims**: User ID, role, organization, permissions, issued/expiry timestamps
- **Validation**: Signature verification, expiration check, role validation

**Key Components**:
- `JwtService`: Token generation, validation, and claims extraction
- `AuthenticationFilter`: Request authentication and user context injection
- `AuthController`: Login/logout endpoints with security audit logging

### 2. Role-Based Access Control (RBAC)

**Roles Hierarchy** (highest to lowest privilege):
1. **ADMIN**: Full system access including user management
2. **PHYSICIST**: Full analysis access, job submission, data manipulation
3. **ANALYST**: Read-only access to analysis results and basic visualization
4. **GUEST**: Limited read-only access to public datasets

**Permission System**:
- Fine-grained permissions for specific operations
- Role-based permission inheritance
- Endpoint-specific permission requirements
- Method-based access control (GET/POST/PUT/DELETE)

### 3. Input Validation and Sanitization

**Protection Against**:
- SQL Injection attacks
- Cross-Site Scripting (XSS)
- Command Injection
- LDAP Injection
- Path Traversal attacks

**Validation Rules**:
- Request size limits (10MB max)
- Header size limits (8KB max)
- URL length limits (2048 chars max)
- Content-type validation
- Pattern-based malicious content detection

### 4. Data Encryption

**At Rest**:
- AES-256-GCM encryption for sensitive data
- Secure key management with external key support
- BCrypt password hashing (strength 12)
- Salt-based hashing for additional security

**In Transit**:
- HTTPS enforcement with HSTS headers
- TLS 1.2+ requirement
- Secure cookie configuration
- CORS policy enforcement

### 5. Rate Limiting

**Implementation**: Redis-based distributed rate limiting
- **Default Users**: 100 requests/minute
- **Premium Users**: 500 requests/minute
- **Admin Users**: 1000 requests/minute
- **Sliding Window**: 1-minute windows with automatic cleanup

### 6. Security Headers

**Implemented Headers**:
- `Strict-Transport-Security`: HTTPS enforcement
- `X-Content-Type-Options`: MIME type sniffing prevention
- `X-Frame-Options`: Clickjacking protection
- `X-XSS-Protection`: XSS filtering
- `Content-Security-Policy`: Resource loading restrictions
- `Referrer-Policy`: Referrer information control

### 7. Comprehensive Audit Logging

**Security Events Logged**:
- Authentication attempts (success/failure)
- Authorization failures
- Privilege escalation attempts
- Data access events
- Suspicious activity detection
- System configuration changes

**Log Formats**:
- Structured JSON logging with correlation IDs
- Separate log files for different event types
- Long-term retention for security events (90+ days)
- Real-time alerting for critical events

## Configuration

### Environment Variables

```yaml
# JWT Configuration
JWT_SECRET: "your-256-bit-secret-key"
JWT_EXPIRATION: 86400000  # 24 hours
JWT_REFRESH_EXPIRATION: 604800000  # 7 days

# Encryption Configuration
ENCRYPTION_KEY: "base64-encoded-aes-256-key"

# Redis Configuration (for rate limiting)
REDIS_HOST: localhost
REDIS_PORT: 6379
REDIS_PASSWORD: "your-redis-password"

# Security Settings
SECURITY_ENABLE_STRICT_TRANSPORT_SECURITY: true
SECURITY_ENABLE_CONTENT_SECURITY_POLICY: true
SECURITY_MAX_LOGIN_ATTEMPTS: 5
SECURITY_LOCKOUT_DURATION_MINUTES: 15
```

### Application Properties

```yaml
security:
  rate-limiting:
    default-requests-per-minute: 100
    premium-requests-per-minute: 500
    admin-requests-per-minute: 1000
  
  input-validation:
    max-request-size: 10485760  # 10MB
    max-header-size: 8192       # 8KB
    max-url-length: 2048
```

## API Endpoints

### Authentication Endpoints

#### POST /api/auth/login
**Purpose**: User authentication
**Security**: Rate limited, input validation, audit logging
**Request**:
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```
**Response**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": "user-123",
    "email": "user@example.com",
    "role": "PHYSICIST",
    "organization": "CERN"
  }
}
```

#### POST /api/auth/register
**Purpose**: User registration (admin-controlled in production)
**Security**: Input validation, password strength requirements
**Request**:
```json
{
  "email": "newuser@example.com",
  "password": "securePassword123",
  "firstName": "John",
  "lastName": "Doe",
  "role": "ANALYST",
  "organization": "FERMILAB"
}
```

#### POST /api/auth/validate
**Purpose**: Token validation
**Security**: JWT signature verification
**Headers**: `Authorization: Bearer <token>`

#### POST /api/auth/logout
**Purpose**: User logout (audit logging)
**Security**: Token validation, session cleanup

## Security Testing

### Unit Tests Implemented

1. **JwtServiceTest**: Token generation, validation, role extraction
2. **SecurityAuditServiceTest**: Audit logging functionality
3. **EncryptionConfigTest**: AES encryption, password hashing
4. **UserRoleTest**: Role hierarchy, permission validation

### Security Test Scenarios

```java
// Example: Test JWT token validation
@Test
void testTokenValidation() {
    String token = jwtService.generateToken("user123", "PHYSICIST", "CERN", permissions);
    assertTrue(jwtService.isTokenValid(token));
    assertEquals("user123", jwtService.extractUserId(token));
}

// Example: Test role-based permissions
@Test
void testRolePermissions() {
    assertTrue(UserRole.PHYSICIST.hasPermission(Permission.SUBMIT_JOBS));
    assertFalse(UserRole.ANALYST.hasPermission(Permission.SUBMIT_JOBS));
}
```

## Threat Detection and Response

### Automated Threat Detection

1. **Brute Force Detection**: 5+ failed logins within 15 minutes
2. **Suspicious Request Patterns**: 50+ denied requests within 15 minutes
3. **Privilege Escalation**: Attempts to access higher-privilege resources
4. **Malicious Input**: Pattern-based detection of injection attempts

### Response Actions

1. **Account Lockout**: Temporary lockout after failed attempts
2. **Rate Limiting**: Automatic throttling of suspicious IPs
3. **Alert Generation**: Real-time alerts for critical events
4. **Audit Trail**: Comprehensive logging for forensic analysis

## Compliance and Standards

### Security Standards Compliance

- **OWASP Top 10**: Protection against common web vulnerabilities
- **ISO 27001**: Information security management practices
- **SOC 2**: Security and availability controls
- **GDPR**: Data protection and privacy requirements

### Security Headers Compliance

- **HSTS**: HTTP Strict Transport Security enforcement
- **CSP**: Content Security Policy implementation
- **CSRF**: Cross-Site Request Forgery protection
- **XSS**: Cross-Site Scripting prevention

## Monitoring and Alerting

### Security Metrics

- Authentication success/failure rates
- Authorization denial rates
- Rate limiting trigger frequency
- Suspicious activity detection counts
- System security health indicators

### Log Analysis

- Structured JSON logs with correlation IDs
- Centralized logging with ELK stack integration
- Real-time log analysis and alerting
- Long-term log retention for compliance

## Deployment Security

### Production Recommendations

1. **Key Management**: Use external key management service (AWS KMS, HashiCorp Vault)
2. **Certificate Management**: Automated certificate rotation
3. **Network Security**: VPC isolation, security groups, WAF
4. **Monitoring**: 24/7 security monitoring and incident response
5. **Backup Security**: Encrypted backups with access controls

### Security Checklist

- [ ] JWT secret key properly configured (256+ bits)
- [ ] HTTPS enforced with valid certificates
- [ ] Rate limiting configured and tested
- [ ] Input validation rules verified
- [ ] Audit logging enabled and monitored
- [ ] Security headers properly configured
- [ ] Role-based access control tested
- [ ] Encryption keys securely managed
- [ ] Security tests passing
- [ ] Monitoring and alerting configured

## Incident Response

### Security Incident Types

1. **Authentication Bypass**: Unauthorized access attempts
2. **Data Breach**: Unauthorized data access or exfiltration
3. **Privilege Escalation**: Unauthorized permission elevation
4. **Injection Attacks**: SQL, XSS, or command injection attempts
5. **DDoS Attacks**: Distributed denial of service attempts

### Response Procedures

1. **Detection**: Automated monitoring and alerting
2. **Assessment**: Severity evaluation and impact analysis
3. **Containment**: Immediate threat isolation and mitigation
4. **Investigation**: Forensic analysis and root cause identification
5. **Recovery**: System restoration and security enhancement
6. **Documentation**: Incident reporting and lessons learned

## Security Maintenance

### Regular Security Tasks

- **Weekly**: Security log review and analysis
- **Monthly**: Security configuration audit
- **Quarterly**: Penetration testing and vulnerability assessment
- **Annually**: Security policy review and update

### Security Updates

- **Dependencies**: Regular security patch application
- **Configurations**: Security setting reviews and updates
- **Policies**: Access control and permission reviews
- **Training**: Security awareness and best practices

---

**Document Version**: 1.0  
**Last Updated**: December 2024  
**Next Review**: March 2025  
**Owner**: ALYX Security Team