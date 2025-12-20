# JVM Performance Tuning for ALYX Microservices

## Overview
This document provides JVM tuning recommendations for optimal performance of ALYX microservices under high load conditions (400+ concurrent users, 50,000+ events/second).

## General JVM Parameters

### Memory Configuration
```bash
# Heap sizing for microservices
-Xms2g -Xmx4g                    # API Gateway
-Xms4g -Xmx8g                    # Job Scheduler
-Xms6g -Xmx12g                   # Data Processing Service
-Xms2g -Xmx4g                    # Collaboration Service
-Xms1g -Xmx2g                    # Other services

# Metaspace configuration
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m

# Direct memory for NIO operations
-XX:MaxDirectMemorySize=1g
```

### Garbage Collection Optimization
```bash
# Use G1GC for low-latency requirements
-XX:+UseG1GC
-XX:MaxGCPauseMillis=100         # Target 100ms pause times
-XX:G1HeapRegionSize=16m
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1MixedGCCountTarget=8
-XX:InitiatingHeapOccupancyPercent=45

# GC logging for monitoring
-Xlog:gc*:gc.log:time,tags
-XX:+UseStringDeduplication      # Reduce memory usage
```

### Performance Optimizations
```bash
# JIT Compiler optimizations
-XX:+TieredCompilation
-XX:TieredStopAtLevel=4
-XX:CompileThreshold=1000

# Memory allocation optimizations
-XX:+UseTLAB                     # Thread Local Allocation Buffers
-XX:TLABSize=1m
-XX:+ResizeTLAB

# CPU optimizations
-XX:+UseCompressedOops           # Reduce memory overhead
-XX:+UseCompressedClassPointers
-XX:+OptimizeStringConcat
```

## Service-Specific Tuning

### API Gateway
```bash
# Optimized for high throughput, low latency
JAVA_OPTS="-Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=50 \
  -XX:+UseStringDeduplication \
  -Dspring.profiles.active=production \
  -Dserver.tomcat.max-threads=400 \
  -Dserver.tomcat.min-spare-threads=50"
```

### Job Scheduler Service
```bash
# Optimized for concurrent job processing
JAVA_OPTS="-Xms4g -Xmx8g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:G1HeapRegionSize=32m \
  -Dalyx.job-scheduler.max-concurrent-jobs=500 \
  -Dalyx.job-scheduler.thread-pool-size=100"
```

### Data Processing Service
```bash
# Optimized for high-throughput data processing
JAVA_OPTS="-Xms6g -Xmx12g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseLargePages \
  -XX:LargePageSizeInBytes=2m \
  -Dalyx.data-processing.batch-size=5000 \
  -Dalyx.data-processing.max-processing-threads=16"
```

### Collaboration Service
```bash
# Optimized for real-time WebSocket connections
JAVA_OPTS="-Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=50 \
  -Dspring.websocket.max-sessions=1000 \
  -Dalyx.collaboration.max-session-participants=20"
```

## Container-Specific Configuration

### Docker Memory Limits
```yaml
# docker-compose.yml memory configuration
services:
  api-gateway:
    mem_limit: 5g
    mem_reservation: 2g
    
  job-scheduler:
    mem_limit: 10g
    mem_reservation: 4g
    
  data-processing:
    mem_limit: 16g
    mem_reservation: 6g
    
  collaboration-service:
    mem_limit: 5g
    mem_reservation: 2g
```

### Kubernetes Resource Requests/Limits
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: alyx-job-scheduler
spec:
  template:
    spec:
      containers:
      - name: job-scheduler
        resources:
          requests:
            memory: "4Gi"
            cpu: "2"
          limits:
            memory: "8Gi"
            cpu: "4"
        env:
        - name: JAVA_OPTS
          value: "-Xms4g -Xmx7g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
```

## Monitoring and Profiling

### JVM Metrics Collection
```bash
# Enable JFR for production profiling
-XX:+FlightRecorder
-XX:StartFlightRecording=duration=60s,filename=alyx-profile.jfr

# Enable JMX for monitoring
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9999
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
```

### Application Performance Monitoring
```bash
# Micrometer metrics
-Dmanagement.endpoints.web.exposure.include=health,metrics,prometheus
-Dmanagement.metrics.export.prometheus.enabled=true

# Custom ALYX metrics
-Dalyx.metrics.enable-detailed-timing=true
-Dalyx.metrics.enable-memory-tracking=true
```

## Performance Testing Configuration

### Load Test JVM Settings
```bash
# For Gatling load tests
JAVA_OPTS="-Xms1g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -Dgatling.core.directory.results=target/gatling \
  -Dgatling.core.outputDirectoryBaseName=alyx-load-test"
```

### Benchmark Environment
```bash
# System-level optimizations for benchmarking
echo 'vm.swappiness=1' >> /etc/sysctl.conf
echo 'net.core.somaxconn=65535' >> /etc/sysctl.conf
echo 'net.ipv4.tcp_max_syn_backlog=65535' >> /etc/sysctl.conf

# CPU governor for consistent performance
echo performance > /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor
```

## Environment-Specific Profiles

### Development Environment
```properties
# application-dev.properties
alyx.performance.enable-debug-logging=true
alyx.performance.gc-logging=true
spring.jpa.show-sql=true
```

### Production Environment
```properties
# application-prod.properties
alyx.performance.enable-debug-logging=false
alyx.performance.gc-logging=false
alyx.performance.enable-jfr=true
spring.jpa.show-sql=false
```

### Load Testing Environment
```properties
# application-load-test.properties
alyx.performance.enable-metrics=true
alyx.performance.metrics-interval=5s
alyx.performance.enable-detailed-timing=true
```

## Troubleshooting Common Issues

### High GC Overhead
```bash
# If GC is taking >5% of CPU time
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200  # Increase pause time target
-XX:G1MixedGCCountTarget=16  # Reduce mixed GC frequency
```

### Memory Leaks
```bash
# Enable heap dump on OOM
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/opt/alyx/dumps/

# Monitor direct memory usage
-XX:NativeMemoryTracking=summary
```

### High CPU Usage
```bash
# Profile CPU usage
-XX:+FlightRecorder
-XX:StartFlightRecording=duration=300s,filename=cpu-profile.jfr,settings=profile

# Reduce JIT compilation overhead
-XX:CompileThreshold=10000  # Increase threshold
```

## Validation Commands

### Check JVM Configuration
```bash
# Verify JVM flags
java -XX:+PrintFlagsFinal -version | grep -E "(UseG1GC|MaxGCPauseMillis|Xmx)"

# Check GC performance
jstat -gc -t <pid> 5s

# Monitor heap usage
jmap -histo <pid> | head -20
```

### Performance Metrics
```bash
# Check application metrics
curl http://localhost:8080/actuator/metrics/jvm.gc.pause
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Monitor thread usage
jstack <pid> | grep -c "java.lang.Thread.State"
```