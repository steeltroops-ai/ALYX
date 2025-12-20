# ALYX API Gateway

The API Gateway serves as the single entry point for all client requests to the ALYX distributed analysis orchestrator. It provides centralized routing, authentication, rate limiting, and monitoring for all microservices.

## Features

### 🔀 Request Routing
- **Service Discovery**: Automatic service discovery via Eureka
- **Load Balancing**: Client-side load balancing across service instances
- **Path-based Routing**: Routes requests to appropriate microservices based on URL patterns

### 🔐 Security & Authentication
- **JWT Authentication**: Validates JWT tokens for protected endpoints
- **Role-based Access**: Extracts user roles for authorization
- **CORS Configuration**: Configurable CORS policies for frontend integration

### 🛡️ Resilience Patterns
- **Circuit Breaker**: Prevents cascade failures with configurable circuit breakers
- **Retry Logic**: Automatic retry with exponential backoff
- **Fallback Responses**: Graceful degradation when services are unavailable

### 🚦 Rate Limiting
- **Distributed Rate Limiting**: Redis-based rate limiting across gateway instances
- **User-based Limits**: Different limits for different user roles
- **IP-based Fallback**: Rate limiting by IP for unauthenticated requests

### 📊 Monitoring & Observability
- **Request Logging**: Comprehensive request/response logging with correlation IDs
- **Metrics Collection**: Prometheus metrics for monitoring
- **Health Checks**: Detailed health information for the gateway and dependencies
- **Distributed Tracing**: Correlation ID propagation for request tracing

## Service Routes

| Path Pattern | Target Service | Description |
|--------------|----------------|-------------|
| `/api/jobs/**` | job-scheduler | Job submission and management |
| `/api/data/**` | data-router | Data routing and distribution |
| `/api/resources/**` | resource-optimizer | Resource allocation and optimization |
| `/api/collaboration/**` | collaboration-service | Real-time collaboration features |
| `/api/notebooks/**` | notebook-service | Analysis notebook management |
| `/api/results/**` | result-aggregator | Result aggregation and retrieval |
| `/api/quality/**` | quality-monitor | Quality monitoring and validation |
| `/ws/**` | collaboration-service | WebSocket connections |

## Configuration

### Environment Variables

- `EUREKA_SERVER_URL`: Eureka server URL (default: http://localhost:8761/eureka/)
- `REDIS_HOST`: Redis host for rate limiting (default: localhost)
- `REDIS_PORT`: Redis port (default: 6379)
- `JWT_SECRET`: JWT signing secret (must be at least 256 bits)
- `JWT_EXPIRATION`: JWT token expiration time in milliseconds (default: 24 hours)

### Rate Limiting

- **Default Users**: 100 requests per minute
- **Premium Users**: 500 requests per minute
- **Window Size**: 1 minute sliding window

### Circuit Breaker Configuration

- **Sliding Window Size**: 10 requests
- **Failure Rate Threshold**: 50%
- **Wait Duration in Open State**: 5 seconds (2 seconds for collaboration service)
- **Half-Open State Calls**: 3 requests

## Running the Gateway

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Using Docker
docker build -t alyx-api-gateway .
docker run -p 8080:8080 alyx-api-gateway
```

## Health Checks

- **Gateway Health**: `GET /api/health/gateway`
- **Actuator Health**: `GET /actuator/health`
- **Metrics**: `GET /actuator/prometheus`

## Testing

```bash
# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify
```

## Security Considerations

1. **JWT Secret**: Use a strong, randomly generated secret key in production
2. **CORS Origins**: Configure specific allowed origins instead of wildcards
3. **Rate Limiting**: Adjust rate limits based on expected traffic patterns
4. **Redis Security**: Secure Redis instance with authentication and encryption
5. **HTTPS**: Always use HTTPS in production environments