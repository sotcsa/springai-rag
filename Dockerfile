# syntax=docker/dockerfile:1.7

# Multi-stage build with dependency caching.
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Cache dependency resolution as a separate layer.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

# Copy sources only after dependencies are cached.
COPY src ./src

# Package the application.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy artifact and set ownership in one step.
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar /app/app.jar

# Create upload directory.
RUN mkdir -p /tmp/uploads && chown appuser:appgroup /tmp/uploads

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
