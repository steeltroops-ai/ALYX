# How to Start ALYX Services

## Current Issue
The frontend is running but can't connect to the backend API because the backend services aren't started.

## Quick Fix - Start Backend Services

### Option 1: Start Infrastructure + API Gateway (Recommended)

1. **Start Infrastructure Services**:
   ```bash
   cd infrastructure
   docker-compose up -d
   ```
   This starts: PostgreSQL, Redis, Kafka, MinIO, Eureka, Prometheus, Grafana

2. **Wait for Services to Start** (about 30-60 seconds)

3. **Start API Gateway** (the main service you need):
   ```bash
   cd backend/api-gateway
   # If you have Maven installed:
   mvn spring-boot:run
   
   # OR if you have Java 17+ installed:
   java -jar target/api-gateway-1.0.0-SNAPSHOT.jar
   ```

### Option 2: Quick Test with Mock Backend

If you can't start the Java services, I can create a simple Node.js mock server:

1. **Create mock server**:
   ```bash
   cd frontend
   npm install express cors
   ```

2. **Start mock server** (I'll create this file):
   ```bash
   node mock-server.js
   ```

## What Each Service Does

- **Frontend (Port 3000)**: React UI - ✅ Already running
- **API Gateway (Port 8080)**: Authentication & routing - ❌ Needs to start
- **Infrastructure**: Databases & monitoring - ❌ Needs to start

## Expected Result

Once the API Gateway is running on port 8080:
- Login with demo accounts will work
- Registration will work
- API calls will succeed
- No more 404 errors

## Demo Accounts (Once Backend is Running)

- **admin@alyx.physics.org** / `admin123`
- **physicist@alyx.physics.org** / `physicist123`  
- **analyst@alyx.physics.org** / `analyst123`