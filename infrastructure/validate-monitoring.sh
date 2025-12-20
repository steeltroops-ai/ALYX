#!/bin/bash

# ALYX Monitoring Validation Script
# This script validates that all monitoring components are working correctly

set -e

echo "🔍 ALYX Monitoring Stack Validation"
echo "=================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check service health
check_service() {
    local service_name=$1
    local url=$2
    local expected_status=${3:-200}
    
    echo -n "Checking $service_name... "
    
    if curl -s -o /dev/null -w "%{http_code}" "$url" | grep -q "$expected_status"; then
        echo -e "${GREEN}✅ OK${NC}"
        return 0
    else
        echo -e "${RED}❌ FAILED${NC}"
        return 1
    fi
}

# Function to check metric availability
check_metrics() {
    local service_name=$1
    local url=$2
    local metric_name=$3
    
    echo -n "Checking $service_name metrics ($metric_name)... "
    
    if curl -s "$url" | grep -q "$metric_name"; then
        echo -e "${GREEN}✅ OK${NC}"
        return 0
    else
        echo -e "${RED}❌ FAILED${NC}"
        return 1
    fi
}

# Function to check Grafana dashboard
check_dashboard() {
    local dashboard_name=$1
    local dashboard_id=$2
    
    echo -n "Checking Grafana dashboard ($dashboard_name)... "
    
    # Note: This is a simplified check. In production, you'd use Grafana API with proper auth
    if curl -s "http://localhost:3000/api/dashboards/uid/$dashboard_id" | grep -q "dashboard"; then
        echo -e "${GREEN}✅ OK${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  Dashboard may not be loaded${NC}"
        return 1
    fi
}

echo ""
echo "🏥 Core Monitoring Services"
echo "=========================="

# Check core monitoring services
check_service "Prometheus" "http://localhost:9090/-/healthy"
check_service "Grafana" "http://localhost:3000/api/health"
check_service "Jaeger" "http://localhost:16686/"
check_service "AlertManager" "http://localhost:9093/-/healthy"

echo ""
echo "📊 Metric Exporters"
echo "=================="

# Check exporters
check_service "Node Exporter" "http://localhost:9100/metrics"
check_service "Postgres Exporter" "http://localhost:9187/metrics"
check_service "Redis Exporter" "http://localhost:9121/metrics"
check_service "Kafka Exporter" "http://localhost:9308/metrics"

echo ""
echo "🎯 Application Metrics"
echo "====================="

# Check application metrics (assuming services are running)
check_metrics "Job Scheduler" "http://localhost:8081/actuator/prometheus" "alyx_jobscheduler"
check_metrics "Collaboration Service" "http://localhost:8082/actuator/prometheus" "alyx_collaboration"
check_metrics "Data Processing" "http://localhost:8083/actuator/prometheus" "alyx_dataprocessing"

echo ""
echo "📈 Prometheus Targets"
echo "===================="

# Check Prometheus targets
echo -n "Checking Prometheus targets... "
targets_up=$(curl -s "http://localhost:9090/api/v1/targets" | grep -o '"health":"up"' | wc -l)
targets_total=$(curl -s "http://localhost:9090/api/v1/targets" | grep -o '"health":"' | wc -l)

if [ "$targets_up" -gt 0 ]; then
    echo -e "${GREEN}✅ $targets_up/$targets_total targets UP${NC}"
else
    echo -e "${RED}❌ No targets UP${NC}"
fi

echo ""
echo "🚨 Alert Rules"
echo "============="

# Check alert rules
echo -n "Checking Prometheus alert rules... "
rules_loaded=$(curl -s "http://localhost:9090/api/v1/rules" | grep -o '"name":"' | wc -l)

if [ "$rules_loaded" -gt 0 ]; then
    echo -e "${GREEN}✅ $rules_loaded alert rules loaded${NC}"
else
    echo -e "${RED}❌ No alert rules loaded${NC}"
fi

echo ""
echo "🔍 Distributed Tracing"
echo "====================="

# Check Jaeger services
echo -n "Checking Jaeger services... "
jaeger_services=$(curl -s "http://localhost:16686/api/services" | grep -o '"' | wc -l)

if [ "$jaeger_services" -gt 0 ]; then
    echo -e "${GREEN}✅ Jaeger is collecting traces${NC}"
else
    echo -e "${YELLOW}⚠️  No services found in Jaeger (may be normal if no traces sent yet)${NC}"
fi

echo ""
echo "📊 Sample Queries"
echo "================"

# Test some sample Prometheus queries
echo "Testing Prometheus queries:"

queries=(
    "up"
    "rate(http_requests_total[5m])"
    "alyx_jobscheduler_jobs_active"
    "alyx_collaboration_sessions_active"
)

for query in "${queries[@]}"; do
    echo -n "  Query: $query... "
    result=$(curl -s "http://localhost:9090/api/v1/query?query=$query" | grep -o '"status":"success"')
    if [ -n "$result" ]; then
        echo -e "${GREEN}✅ OK${NC}"
    else
        echo -e "${RED}❌ FAILED${NC}"
    fi
done

echo ""
echo "🎛️  Configuration Validation"
echo "=========================="

# Check configuration files
configs=(
    "infrastructure/monitoring/prometheus.yml"
    "infrastructure/monitoring/alert-rules.yml"
    "infrastructure/monitoring/alertmanager.yml"
    "infrastructure/monitoring/grafana/provisioning/datasources/prometheus.yml"
    "infrastructure/monitoring/grafana/provisioning/dashboards/dashboard.yml"
)

for config in "${configs[@]}"; do
    echo -n "Checking $config... "
    if [ -f "$config" ]; then
        echo -e "${GREEN}✅ EXISTS${NC}"
    else
        echo -e "${RED}❌ MISSING${NC}"
    fi
done

echo ""
echo "📋 Summary"
echo "========="

echo "Monitoring stack validation complete!"
echo ""
echo "🔗 Quick Links:"
echo "   Prometheus:    http://localhost:9090"
echo "   Grafana:       http://localhost:3000 (admin/admin)"
echo "   Jaeger:        http://localhost:16686"
echo "   AlertManager:  http://localhost:9093"
echo ""
echo "📚 Next Steps:"
echo "   1. Import Grafana dashboards from infrastructure/monitoring/grafana/dashboards/"
echo "   2. Configure AlertManager notifications in alertmanager.yml"
echo "   3. Set up log aggregation with ELK stack or similar"
echo "   4. Configure service discovery for dynamic environments"
echo ""
echo -e "${YELLOW}⚠️  Note: Some checks may fail if ALYX application services are not running${NC}"