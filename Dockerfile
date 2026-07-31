FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Resolve dependencies in their own layer so source edits do not re-download them.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B

COPY src src
# Tests and the format check run in CI against the full toolchain; repeating them
# here would only slow image builds down.
RUN ./mvnw package -B -DskipTests -Dspotless.check.skip=true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user.
RUN addgroup -S app && adduser -S -G app app
USER app

COPY --from=build --chown=app:app /workspace/app/target/*.jar app.jar

ENV POSTGRES_HOST=postgres \
    POSTGRES_PORT=5432 \
    POSTGRES_DB=semanticsearch \
    POSTGRES_USER=postgres \
    ELASTICSEARCH_HOST=elasticsearch \
    ELASTICSEARCH_PORT=9200 \
    ELASTICSEARCH_PROTOCOL=http \
    ELASTICSEARCH_STUB_ENABLED=false \
    REDIS_HOST=redis \
    REDIS_PORT=6379

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -q -O- http://localhost:8080/actuator/health || exit 1

# Runs the Spring Boot launcher from the jar. This previously exploded the jar
# and invoked the main class with a hand-built classpath, which bypasses the
# launcher and breaks whenever the layout changes.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
