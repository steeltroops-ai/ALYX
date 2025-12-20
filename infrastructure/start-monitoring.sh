#!/bin/bash

# ALYX Monitoring Stack Startup Script
# This script starts all monitoring and observability components

set -e

echo "🚀 Starting ALYX Monitoring and Observability Stack..."

# Check if Docker and Docker Compose are available
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed or not in PATH"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed or not in PATH"
    exit 1
fi

# Create necessary directories
echo "📁 Creating monitoring directories..."
mkdir -p logs
mkdir -p monitoring/grafana/dashboards
mkdir -p monitoring/grafana/provisioning/dashboards
mkdir -p monitoring/grafana/provisioning/datasources

# Set proper permissions for Grafana
echo "🔐 Setting up permissions..."
sudo chown -R 472:472 monitoring/grafana/ || echo "⚠️  Could not set Grafana permissions (may need to run as root)"

# Start monitoring infrastructure
echo "🐳 Starting monitoring infrastructure..."
docker-compose -f infrastructure/docker-compose.yml up -d prometheus grafana jaeger alertmanager

# Start exporters
echo "📊 Starting metric exporters..."
docker-compose -f infrastructure/docker-compose.yml up -d postgres-exporter redis-exporter node-exporter kafka-exporter

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 30

# Check service health
echo "🏥 Checking service health..."

# Check Prometheus
if curl -f http://localhost:9090/-/healthy &> /dev/null; then
    echo "✅ Prometheus is healthy"
else
    echo "❌ Prometheus is not responding"
fi

# Check Grafana
if curl -f http://localhost:3000/api/health &> /dev/null; then
    echo "✅ Grafana is healthy"
else
    echo "❌ Grafana is not responding"
fi

# Check Jaeger
if curl -f http://localhost:16686/ &> /dev/null; then
    echo "✅ Jaeger is healthy"
else
    echo "❌ Jaeger is not responding"
fi

# Check AlertManager
if curl -f http://localhost:9093/-/healthy &> /dev/null; then
    echo "✅ AlertManager is healthy"
else
    echo "❌ AlertManager is not responding"
fi

echo ""
echo "🎉 Monitoring stack startup complete!"
echo ""
echo "📊 Access URLs:"
echo "   Prometheus:    http://localhost:9090"
echo "   Grafana:       http://localhost:3000 (admin/admin)"
echo "   Jaeger:        http://localhost:16686"
echo "   AlertManager:  http://localhost:9093"
echo ""
echo "📈 Metric Exporters:"
echo "   Node Exporter:     http://localhost:9100/metrics"
echo "   Postgres Exporter: http://localhost:9187/metrics"
echo "   Redis Exporter:    http://localhost:9121/metrics"
echo "   Kafka Exporter:    http://localhost:9308/metrics"
echo ""
echo "🔍 To view logs: docker-compose -f infrastructure/docker-compose.yml logs -f [service-name]"
echo "🛑 To stop: docker-compose -f infrastructure/docker-compose.yml down"
echo ""
echo "⚠️  Note: Make sure your ALYX services are configured to export metrics to Prometheus"
echo "   and send traces to Jaeger at http://localhost:14268/api/traces"