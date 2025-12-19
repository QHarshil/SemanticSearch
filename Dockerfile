FROM eclipse-temurin:25-jdk-alpine as build
WORKDIR /workspace/app

# Copy maven executable and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Build dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Package the application
RUN ./mvnw package -DskipTests -Dspotless.skip=true
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
VOLUME /tmp
ARG DEPENDENCY=/workspace/app/target/dependency

# Copy dependencies
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app

# Set environment variables
ENV POSTGRES_HOST=postgres
ENV POSTGRES_PORT=5432
ENV POSTGRES_DB=semanticsearch
ENV POSTGRES_USER=postgres
ENV POSTGRES_PASSWORD=postgres
ENV ELASTICSEARCH_HOST=elasticsearch
ENV ELASTICSEARCH_PORT=9200
ENV ELASTICSEARCH_PROTOCOL=http
ENV REDIS_HOST=redis
ENV REDIS_PORT=6379

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-cp", "app:app/lib/*", "io.github.semanticsearch.SemanticSearchApplication"]
