# ALYX Monitoring and Observability

This directory contains the complete monitoring and observability stack for the ALYX Distributed Analysis Orchestrator.

## 🏗️ Architecture Overview

The monitoring stack consists of:

- **Prometheus**: Metrics collection and alerting
- **Grafana**: Visualization and dashboards
- **Jaeger**: Distributed tracing
- **AlertManager**: Alert routing and notifications
- **Exporters**: Infrastructure metrics collection

## 📊 Components

### Core Monitoring Services

| Service | Port | Purpose |
|---------|------|---------|
| Prometheus | 9090 | Metrics collection and storage |
| Grafana | 3000 | Visualization dashboards |
| Jaeger | 16686 | Distributed tracing UI |
| AlertManager | 9093 | Alert management |

### Metric Exporters

| Exporter | Port | Metrics |
|----------|------|---------|
| Node Exporter | 9100 | System metrics (CPU, memory, disk) |
| Postgres Exporter | 9187 | Database metrics |
| Redis Exporter | 9121 | Cache metrics |
| Kafka Exporter | 9308 | Message queue metrics |

### Application Metrics

Each ALYX service exposes metrics at `/actuator/prometheus`:

- **Job Scheduler** (8081): Job processing, queue management, resource allocation
- **Collaboration Service** (8082): WebSocket connections, operational transforms
- **Data Processing** (8083): Query performance, cache efficiency, spatial operations
- **Resource Optimizer** (8084): Resource utilization, ML predictions
- **Notebook Service** (8085): Notebook execution, kernel management

## 🚀 Quick Start

### 1. Start Monitoring Stack

```bash
# Start all monitoring services
./infrastructure/start-monitoring.sh

# Or manually with Docker Compose
docker-compose -f infrastructure/docker-compose.yml up -d prometheus grafana jaeger alertmanager
```

### 2. Validate Setup

```bash
# Run validation script
./infrastructure/validate-monitoring.sh
```

### 3. Access Dashboards

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **Jaeger**: http://localhost:16686
- **AlertManager**: http://localhost:9093

## 📈 Dashboards

Pre-configured Grafana dashboards are available in `grafana/dashboards/`:

1. **ALYX System Overview** - High-level system metrics
2. **ALYX Job Scheduler** - Job processing and resource allocation
3. **ALYX Data Processing** - Database and cache performance
4. **ALYX Collaboration** - Real-time collaboration metrics

### Importing Dashboards

Dashboards are automatically provisioned when Grafana starts. To manually import:

1. Open Grafana (http://localhost:3000)
2. Go to "+" → Import
3. Upload JSON files from `grafana/dashboards/`

## 🚨 Alerting

### Alert Rules

Comprehensive alert rules are defined in `alert-rules.yml`:

- **System Alerts**: CPU, memory, disk usage
- **Service Alerts**: Service availability, response times
- **Application Alerts**: Job failures, cache performance, connection issues
- **Performance Alerts**: High latency, resource exhaustion

### Alert Configuration

Edit `alertmanager.yml` to configure notification channels:

```yaml
receivers:
  - name: 'email-alerts'
    email_configs:
      - to: 'admin@example.com'
        subject: 'ALYX Alert: {{ .GroupLabels.alertname }}'
```

## 🔍 Distributed Tracing

### Configuration

Services are configured to send traces to Jaeger:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:14268/api/traces
```

### Viewing Traces

1. Open Jaeger UI (http://localhost:16686)
2. Select service from dropdown
3. Search for traces by operation, tags, or time range

## 📝 Structured Logging

All services use structured JSON logging with:

- **Service identification**: service name, version, environment
- **Correlation IDs**: for tracing requests across services
- **Performance metrics**: execution times, resource usage
- **Error context**: stack traces, error codes

### Log Locations

- **Console**: Structured JSON output
- **Files**: `logs/[service-name].log` with rotation
- **Metrics**: Separate performance log files

## 🔧 Configuration

### Prometheus Configuration

Key configuration in `prometheus.yml`:

- **Scrape intervals**: 5-15 seconds for different services
- **Retention**: 200 hours of metrics data
- **Alert rules**: Loaded from `alert-rules.yml`

### Service Discovery

For dynamic environments, configure service discovery:

```yaml
scrape_configs:
  - job_name: 'kubernetes-pods'
    kubernetes_sd_configs:
      - role: pod
```

## 📊 Key Metrics

### System Metrics

- `up`: Service availability
- `http_requests_total`: Request rate and status codes
- `http_request_duration_seconds`: Response time percentiles

### Application Metrics

- `alyx_jobscheduler_jobs_active`: Active job count
- `alyx_collaboration_sessions_active`: Active collaboration sessions
- `alyx_dataprocessing_cache_hits_total`: Cache performance
- `alyx_resourceoptimizer_cores_allocated`: Resource utilization

### Infrastructure Metrics

- `node_cpu_seconds_total`: CPU usage
- `node_memory_MemAvailable_bytes`: Available memory
- `postgres_up`: Database availability
- `redis_connected_clients`: Cache connections

## 🛠️ Troubleshooting

### Common Issues

1. **Metrics not appearing**
   - Check service `/actuator/prometheus` endpoints
   - Verify Prometheus target configuration
   - Check network connectivity

2. **Dashboards not loading**
   - Verify Grafana datasource configuration
   - Check dashboard JSON syntax
   - Ensure Prometheus is accessible

3. **Traces not appearing**
   - Verify Jaeger endpoint configuration
   - Check service tracing configuration
   - Ensure sampling probability > 0

### Debug Commands

```bash
# Check Prometheus targets
curl http://localhost:9090/api/v1/targets

# Test Prometheus query
curl "http://localhost:9090/api/v1/query?query=up"

# Check Grafana health
curl http://localhost:3000/api/health

# View service logs
docker-compose logs -f prometheus
```

## 🔒 Security Considerations

### Production Deployment

1. **Authentication**: Enable Grafana authentication
2. **TLS**: Use HTTPS for all web interfaces
3. **Network**: Restrict access to monitoring ports
4. **Secrets**: Use secret management for credentials

### Example Security Configuration

```yaml
# Grafana security
environment:
  GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD}
  GF_AUTH_ANONYMOUS_ENABLED: false
  GF_SERVER_PROTOCOL: https
```

## 📚 Additional Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Metrics](https://micrometer.io/docs/)

## 🤝 Contributing

When adding new metrics or dashboards:

1. Follow naming conventions: `alyx_[service]_[metric]_[unit]`
2. Add appropriate labels for filtering
3. Update dashboard configurations
4. Add relevant alert rules
5. Document new metrics in this README