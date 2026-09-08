FROM eclipse-temurin:21-jdk-jammy AS build
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

# Debian rather than Alpine. ONNX Runtime and the Hugging Face tokenizer both
# ship native libraries linked against glibc and none against musl, so on Alpine
# EMBEDDING_PROVIDER=onnx fails at startup while the pure-Java providers keep
# working. That is the worst kind of difference between the image and the jar.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# curl is for the health check below. The base image ships neither curl nor wget.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user.
RUN groupadd --system app && useradd --system --gid app --create-home app
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
    REDIS_PORT=6379 \
    EMBEDDING_ONNX_MODEL_DIR=/var/cache/semantic-search/models

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD curl -sf http://localhost:8080/actuator/health || exit 1

# Runs the Spring Boot launcher from the jar. Exploding the jar and invoking the
# main class with a hand-built classpath bypasses the launcher and breaks
# whenever the layout changes.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
