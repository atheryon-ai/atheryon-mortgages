# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /build

# Cache Gradle wrapper + dependencies
COPY gradlew gradlew
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew --version

COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true

# Copy source and build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Production runtime
FROM eclipse-temurin:17-jre-jammy

RUN groupadd -r appuser && useradd -r -g appuser -u 1001 appuser

WORKDIR /app

COPY --from=builder /build/build/libs/atheryon-mortgages-*.jar app.jar

# Static assets (dashboard HTML)
COPY --from=builder /build/src/main/resources/static/ /app/static/

RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseContainerSupport"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -sf http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
