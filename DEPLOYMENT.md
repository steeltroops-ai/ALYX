# ALYX Deployment Guide

This guide provides comprehensive instructions for deploying the ALYX Distributed Analysis Orchestrator in different environments.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Development Deployment](#local-development-deployment)
3. [Kubernetes Deployment](#kubernetes-deployment)
4. [Database Management](#database-management)
5. [Monitoring and Observability](#monitoring-and-observability)
6. [Security Configuration](#security-configuration)
7. [Troubleshooting](#troubleshooting)
8. [Maintenance](#maintenance)

## Prerequisites

### System Requirements

- **CPU**: Minimum 8 cores (16 cores recommended for production)
- **Memory**: Minimum 16GB RAM (32GB recommended for production)
- **Storage**: Minimum 100GB SSD (1TB+ recommended for production)
- **Network**: Gigabit Ethernet (10Gb recommended for production)

### Software Requirements

- Docker 20.10+
- Docker Compose 2.0+
- Kubernetes 1.25+ (for K8s deployment)
- kubectl (for K8s deployment)
- Maven 3.8+
- Node.js 18+
- PostgreSQL Client 15+

### Development Tools

- Git
- Java 17+
- npm/yarn

## Local Development Deployment

### Quick Start

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd alyx-distributed-orchestrator
   ```

2. **Run the deployment script**:
   ```bash
   chmod +x scripts/deploy-local.sh
   ./scripts/deploy-local.sh
   ```

3. **Access the application**:
   - Frontend: http://localhost:3001
   - API Gateway: http://localhost:8080
   - Grafana: http://localhost:3000 (admin/admin)

### Manual Deployment Steps

If you prefer manual deployment or need to troubleshoot:

1. **Build backend services**:
   ```bash
   mvn clean compile -DskipTests
   
   # Build each service
   cd backend/api-gateway && mvn spring-boot:build-image -DskipTests && cd ../..
   cd backend/job-scheduler && mvn spring-boot:build-image -DskipTests && cd ../..
   # ... repeat for other services
   ```

2. **Build frontend**:
   ```bash
   cd frontend
   npm ci
   npm run build
   docker build -t alyx/frontend:latest .
   cd ..
   ```

3. **Start infrastructure**:
   ```bash
   cd infrastructure
   docker-compose up -d postgres redis-node-1 kafka zookeeper eureka
   ```

4. **Run database migrations**:
   ```bash
   ./scripts/db-migrate.sh local migrate
   ```

5. **Start application services**:
   ```bash
   cd infrastructure
   docker-compose up -d
   ```

### Stopping Local Deployment

```bash
./scripts/stop-local.sh
```

## Kubernetes Deployment

### Prerequisites

1. **Kubernetes cluster** with:
   - Ingress controller (nginx recommended)
   - Cert-manager (for TLS certificates)
   - Storage class for persistent volumes

2. **Container registry** access:
   ```bash
   docker login ghcr.io
   ```

### Deployment Steps

1. **Build and push images** (if not using CI/CD):
   ```bash
   # Build all images
   ./scripts/build-images.sh
   
   # Push to registry
   ./scripts/push-images.sh
   ```

2. **Deploy to staging**:
   ```bash
   ./scripts/deploy-k8s.sh staging
   ```

3. **Deploy to production**:
   ```bash
   ./scripts/deploy-k8s.sh production
   ```

### Configuration Management

#### Environment-Specific Configurations

- **Staging**: Uses `application-kubernetes.yml` with staging-specific overrides
- **Production**: Uses `application-kubernetes.yml` with production-specific overrides

#### Secrets Management

Create Kubernetes secrets for sensitive data:

```bash
# Database password
kubectl create secret generic alyx-secrets \
  --from-literal=POSTGRES_PASSWORD=<password> \
  --from-literal=MINIO_SECRET_KEY=<secret> \
  --from-literal=JWT_SECRET=<jwt-secret> \
  -n alyx

# TLS certificates (if not using cert-manager)
kubectl create secret tls api-gateway-tls \
  --cert=path/to/tls.crt \
  --key=path/to/tls.key \
  -n alyx
```

#### ConfigMap Updates

Update configuration without rebuilding images:

```bash
kubectl apply -f infrastructure/k8s/configmap.yaml
kubectl rollout restart deployment/api-gateway -n alyx
```

### Scaling

#### Horizontal Pod Autoscaling

```bash
# Enable HPA for API Gateway
kubectl autoscale deployment api-gateway \
  --cpu-percent=70 \
  --min=3 \
  --max=10 \
  -n alyx

# Enable HPA for data processing
kubectl autoscale deployment data-processing \
  --cpu-percent=80 \
  --min=3 \
  --max=20 \
  -n alyx
```

#### Manual Scaling

```bash
# Scale specific service
kubectl scale deployment job-scheduler --replicas=5 -n alyx

# Scale all services
kubectl scale deployment --all --replicas=3 -n alyx
```

## Database Management

### Migrations

#### Run Migrations

```bash
# Local environment
./scripts/db-migrate.sh local migrate

# Staging environment
STAGING_DB_PASSWORD=<password> ./scripts/db-migrate.sh staging migrate

# Production environment
PROD_DB_PASSWORD=<password> ./scripts/db-migrate.sh production migrate
```

#### Migration Status

```bash
./scripts/db-migrate.sh <environment> info
```

#### Validate Migrations

```bash
./scripts/db-migrate.sh <environment> validate
```

### Backups

#### Create Backup

```bash
# Full backup
./scripts/db-backup.sh production full

# Incremental backup
./scripts/db-backup.sh production incremental

# Schema only
./scripts/db-backup.sh production schema
```

#### Automated Backups

Set up cron jobs for automated backups:

```bash
# Daily full backup at 2 AM
0 2 * * * /path/to/scripts/db-backup.sh production full 90

# Hourly incremental backup during business hours
0 9-17 * * 1-5 /path/to/scripts/db-backup.sh production incremental 7
```

#### Restore from Backup

```bash
# List available backups
ls -la backups/production/

# Restore (staging/development only)
./scripts/db-migrate.sh staging rollback
```

### Performance Optimization

#### Database Tuning

Apply performance optimizations:

```bash
# Run optimization script
psql -h <host> -U <user> -d alyx -f backend/performance-optimization/database-optimization.sql
```

#### Connection Pooling

Monitor connection pool metrics:

```bash
# Check active connections
kubectl exec -it deployment/api-gateway -n alyx -- \
  curl localhost:8080/actuator/metrics/hikaricp.connections.active
```

## Monitoring and Observability

### Prometheus Metrics

Access Prometheus at:
- Local: http://localhost:9090
- Kubernetes: Port-forward or through ingress

Key metrics to monitor:
- `http_server_requests_seconds`
- `jvm_memory_used_bytes`
- `hikaricp_connections_active`
- `kafka_consumer_lag_sum`

### Grafana Dashboards

Access Grafana at:
- Local: http://localhost:3000 (admin/admin)
- Kubernetes: Port-forward or through ingress

Pre-configured dashboards:
- System Overview
- Job Scheduler Metrics
- Data Processing Performance
- Collaboration Service Metrics

### Distributed Tracing

Access Jaeger at:
- Local: http://localhost:16686
- Kubernetes: Port-forward or through ingress

### Log Aggregation

#### Local Development

View logs using Docker Compose:

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f api-gateway
```

#### Kubernetes

View logs using kubectl:

```bash
# All pods in namespace
kubectl logs -f -l app.kubernetes.io/name=alyx -n alyx

# Specific service
kubectl logs -f deployment/api-gateway -n alyx

# Previous container logs
kubectl logs deployment/api-gateway -n alyx --previous
```

### Alerting

#### Prometheus Alerts

Key alerts configured:
- Service down
- High memory usage (>80%)
- High CPU usage (>80%)
- Database connection failures
- Kafka consumer lag

#### Alert Routing

Configure Alertmanager for notifications:

```yaml
# alertmanager.yml
route:
  group_by: ['alertname']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'web.hook'

receivers:
- name: 'web.hook'
  slack_configs:
  - api_url: '<slack-webhook-url>'
    channel: '#alyx-alerts'
```

## Security Configuration

### TLS/SSL

#### Local Development

TLS is disabled for local development. Use HTTP endpoints.

#### Production

TLS is enforced in production:

1. **Cert-manager** automatically provisions certificates
2. **Ingress** terminates TLS
3. **Internal communication** uses service mesh (optional)

### Authentication & Authorization

#### JWT Configuration

Update JWT secrets:

```bash
# Generate new JWT secret
JWT_SECRET=$(openssl rand -base64 64)

# Update Kubernetes secret
kubectl patch secret alyx-secrets -n alyx \
  -p='{"data":{"JWT_SECRET":"'$(echo -n $JWT_SECRET | base64)'"}}'
```

#### RBAC

Role-based access control is configured in the application:

- **Admin**: Full system access
- **Physicist**: Analysis and visualization access
- **Viewer**: Read-only access

### Network Security

#### Kubernetes Network Policies

Apply network policies to restrict traffic:

```bash
kubectl apply -f infrastructure/k8s/network-policies.yaml
```

#### Firewall Rules

Configure firewall rules for external access:

- **Frontend**: Port 80/443 (public)
- **API Gateway**: Port 8080 (public)
- **Databases**: Internal only
- **Monitoring**: Internal + admin access

## Troubleshooting

### Common Issues

#### Service Won't Start

1. **Check logs**:
   ```bash
   kubectl logs deployment/<service-name> -n alyx
   ```

2. **Check resource limits**:
   ```bash
   kubectl describe pod <pod-name> -n alyx
   ```

3. **Check configuration**:
   ```bash
   kubectl get configmap alyx-config -n alyx -o yaml
   ```

#### Database Connection Issues

1. **Test connectivity**:
   ```bash
   kubectl run -it --rm debug --image=postgres:15 --restart=Never -- \
     psql -h postgres-service.alyx.svc.cluster.local -U alyx_user -d alyx
   ```

2. **Check connection pool**:
   ```bash
   curl localhost:8080/actuator/health/db
   ```

#### Performance Issues

1. **Check resource usage**:
   ```bash
   kubectl top pods -n alyx
   kubectl top nodes
   ```

2. **Check metrics**:
   ```bash
   curl localhost:9090/api/v1/query?query=rate(http_server_requests_seconds_count[5m])
   ```

3. **Profile application**:
   ```bash
   kubectl port-forward deployment/api-gateway 8080:8080 -n alyx
   curl localhost:8080/actuator/metrics
   ```

### Health Checks

#### Application Health

```bash
# Check all services
kubectl get pods -n alyx

# Detailed health check
for service in api-gateway job-scheduler resource-optimizer; do
  kubectl exec deployment/$service -n alyx -- \
    curl -f localhost:8080/actuator/health
done
```

#### Infrastructure Health

```bash
# Database
kubectl exec deployment/postgres -n alyx -- pg_isready

# Redis
kubectl exec statefulset/redis -n alyx -- redis-cli ping

# Kafka
kubectl exec deployment/kafka -n alyx -- \
  kafka-broker-api-versions --bootstrap-server localhost:9092
```

### Performance Tuning

#### JVM Tuning

Update JVM parameters in deployment:

```yaml
env:
- name: JAVA_OPTS
  value: "-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

#### Database Tuning

Apply database optimizations:

```sql
-- Connection settings
ALTER SYSTEM SET max_connections = 200;
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '1GB';

-- Performance settings
ALTER SYSTEM SET random_page_cost = 1.1;
ALTER SYSTEM SET effective_io_concurrency = 200;
```

## Maintenance

### Regular Maintenance Tasks

#### Daily

- [ ] Check service health
- [ ] Review error logs
- [ ] Monitor resource usage
- [ ] Verify backup completion

#### Weekly

- [ ] Update security patches
- [ ] Review performance metrics
- [ ] Clean up old logs
- [ ] Test disaster recovery procedures

#### Monthly

- [ ] Update dependencies
- [ ] Review and update documentation
- [ ] Capacity planning review
- [ ] Security audit

### Upgrade Procedures

#### Application Updates

1. **Staging deployment**:
   ```bash
   ./scripts/deploy-k8s.sh staging
   ```

2. **Run tests**:
   ```bash
   kubectl run smoke-test --image=curlimages/curl --rm -i --restart=Never -- \
     curl -f http://api-gateway-service.alyx.svc.cluster.local:8080/actuator/health
   ```

3. **Production deployment**:
   ```bash
   ./scripts/deploy-k8s.sh production
   ```

#### Database Schema Updates

1. **Test migrations in staging**:
   ```bash
   ./scripts/db-migrate.sh staging migrate
   ```

2. **Create production backup**:
   ```bash
   ./scripts/db-backup.sh production full
   ```

3. **Apply production migrations**:
   ```bash
   ./scripts/db-migrate.sh production migrate
   ```

#### Infrastructure Updates

1. **Update Kubernetes cluster**
2. **Update node pools**
3. **Update ingress controller**
4. **Update monitoring stack**

### Disaster Recovery

#### Backup Strategy

- **Database**: Daily full backups, hourly incremental
- **Configuration**: Version controlled in Git
- **Persistent volumes**: Snapshot-based backups
- **Secrets**: Encrypted backup in secure storage

#### Recovery Procedures

1. **Service failure**:
   ```bash
   kubectl rollout restart deployment/<service-name> -n alyx
   ```

2. **Database corruption**:
   ```bash
   ./scripts/db-migrate.sh production rollback
   # Restore from latest backup
   ```

3. **Complete cluster failure**:
   ```bash
   # Deploy to new cluster
   ./scripts/deploy-k8s.sh production
   # Restore data from backups
   ```

### Monitoring and Alerting

#### Key Metrics

- **Availability**: >99.9% uptime
- **Response time**: <2s for 95th percentile
- **Throughput**: 50,000+ events/second
- **Error rate**: <0.1%

#### Alert Thresholds

- **CPU usage**: >80% for 5 minutes
- **Memory usage**: >85% for 5 minutes
- **Disk usage**: >90%
- **Response time**: >2s for 95th percentile
- **Error rate**: >1% for 5 minutes

For additional support and troubleshooting, refer to the [TROUBLESHOOTING.md](TROUBLESHOOTING.md) guide or contact the development team.