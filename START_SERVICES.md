# How to Start ALYX Services

## Current Issue
The frontend is running but can't connect to the backend API because the backend services aren't started.

## 🚀 Quick Solutions

### Option 1: Quick Start Script (Recommended)
```powershell
# Run this in the project root
.\quick-start.ps1
```
This will:
- Start essential infrastructure (PostgreSQL, Redis, Eureka)
- Build and start the API Gateway
- Connect your frontend to the backend

### Option 2: Mock Backend (Fastest for Testing)
```bash
# In the frontend directory
cd frontend
npm install express cors
node mock-backend.js
```
This starts a mock API server on port 8080 with demo data.

### Option 3: Manual Full Setup
```bash
# 1. Start infrastructure
cd infrastructure
docker-compose up -d postgres redis-node-1 eureka

# 2. Wait 30 seconds, then start API Gateway
cd ../backend/api-gateway
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

## What's Running Now

- **Frontend (Port 3000)**: React UI - ✅ Already running
- **API Gateway (Port 8080)**: Authentication & routing - ❌ Needs to start
- **Infrastructure**: Databases & monitoring - ❌ Needs to start

## Expected Result

Once the API Gateway is running on port 8080:
- ✅ Login with demo accounts will work
- ✅ Registration will work  
- ✅ API calls will succeed
- ✅ No more 404 errors

## 🔑 Demo Accounts

- **admin@alyx.physics.org** / `admin123` (Admin access)
- **physicist@alyx.physics.org** / `physicist123` (Physicist role)
- **analyst@alyx.physics.org** / `analyst123` (Analyst role)

## Troubleshooting

**If you get "Docker not running" error:**
- Start Docker Desktop
- Wait for it to fully start
- Try again

**If you get Java errors:**
- Make sure Java 17+ is installed
- Check: `java -version`

**If ports are busy:**
- Stop other services: `docker-compose down`
- Kill processes on port 8080: `netstat -ano | findstr :8080`