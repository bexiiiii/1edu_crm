# ================================================
# Stage 1: Build (Debian-based for glibc — needed by protoc-gen-grpc-java)
# Requires Docker BuildKit (enabled by default in Docker 20+).
# Gradle home is cached between builds — first build ~18 min, subsequent ~2-3 min.
# ================================================
# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# Copy Gradle wrapper and root build files
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Copy all module build.gradle files (for dependency resolution caching)
COPY services/common/build.gradle            services/common/build.gradle
COPY services/common-proto/build.gradle      services/common-proto/build.gradle
COPY services/api-gateway/build.gradle       services/api-gateway/build.gradle
COPY services/service-registry/build.gradle  services/service-registry/build.gradle
COPY services/tenant-service/build.gradle    services/tenant-service/build.gradle
COPY services/auth-service/build.gradle      services/auth-service/build.gradle
COPY services/student-service/build.gradle   services/student-service/build.gradle
COPY services/lead-service/build.gradle      services/lead-service/build.gradle
COPY services/course-service/build.gradle    services/course-service/build.gradle
COPY services/schedule-service/build.gradle  services/schedule-service/build.gradle
COPY services/payment-service/build.gradle   services/payment-service/build.gradle
COPY services/finance-service/build.gradle   services/finance-service/build.gradle
COPY services/analytics-service/build.gradle services/analytics-service/build.gradle
COPY services/notification-service/build.gradle services/notification-service/build.gradle
COPY services/file-service/build.gradle      services/file-service/build.gradle
COPY services/report-service/build.gradle    services/report-service/build.gradle
COPY services/staff-service/build.gradle     services/staff-service/build.gradle
COPY services/task-service/build.gradle      services/task-service/build.gradle
COPY services/lesson-service/build.gradle    services/lesson-service/build.gradle
COPY services/settings-service/build.gradle  services/settings-service/build.gradle
COPY services/audit-service/build.gradle     services/audit-service/build.gradle
COPY services/communication-service/build.gradle services/communication-service/build.gradle
COPY services/config-server/build.gradle     services/config-server/build.gradle

# Copy proto files (needed for common-proto build)
COPY services/common-proto/src/ services/common-proto/src/

# Download dependencies with BuildKit cache — Gradle home persists between builds
ARG SERVICE_NAME
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && \
    ./gradlew :services:${SERVICE_NAME}:dependencies --no-daemon -q 2>/dev/null || true

# Copy all sources and build the service (Gradle cache reused)
COPY services/ services/
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :services:${SERVICE_NAME}:bootJar -x test --no-daemon

# ================================================
# Stage 2: Runtime
# ================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

ARG SERVICE_NAME

# Copy built JAR (exclude plain JAR if any)
COPY --from=builder /build/services/${SERVICE_NAME}/build/libs/ /tmp/jars/
RUN find /tmp/jars -name "*.jar" ! -name "*plain*" | head -1 \
    | xargs -I{} cp {} /app/app.jar \
    && rm -rf /tmp/jars \
    && chown appuser:appgroup /app/app.jar

USER appuser

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Xss512k", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
