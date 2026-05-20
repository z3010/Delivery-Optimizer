# ── Stage 1: build ───────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Cache Maven deps before copying source
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q 2>/dev/null || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q && \
    mv target/*.jar app.jar


# ── Stage 2: runtime ─────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="Delivery Slot Optimizer"
LABEL org.opencontainers.image.description="Hyperlocal real-time slot optimizer backend"

# Non-root user for security
RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

COPY --from=builder /build/app.jar app.jar

# JVM tuning for container environments
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
