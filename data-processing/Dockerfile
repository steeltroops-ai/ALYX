# Multi-stage build for ALYX Data Processing Service
FROM openjdk:21-jdk-slim as builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom files
COPY ../mvnw .
COPY ../.mvn .mvn
COPY ../pom.xml .
COPY pom.xml data-processing/

# Download dependencies
RUN ./mvnw dependency:go-offline -pl data-processing

# Copy source code
COPY src data-processing/src

# Build the application
RUN ./mvnw clean package -pl data-processing -DskipTests

# Runtime stage
FROM openjdk:21-jre-slim

# Create non-root user
RUN groupadd -r alyx && useradd -r -g alyx alyx

# Set working directory
WORKDIR /app

# Copy the built JAR
COPY --from=builder /app/data-processing/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R alyx:alyx /app

# Switch to non-root user
USER alyx

# Expose port
EXPOSE 8085

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8085/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]