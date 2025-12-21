# Semantic Search Java

A high-performance semantic search microservice built with Java and Spring Boot. The bundled React UI is already compiled into the jar and served at `/`, so recruiters can click and try it without any frontend build steps.

## Overview

This project provides a robust semantic search capability using vector embeddings to find conceptually similar documents. It leverages an embedding provider for text vectorization and Elasticsearch for efficient vector search, positioning you for search/ranking/personalization roles.

## Features

- **Semantic Document Search**: Find documents based on meaning, not just keywords
- **Vector Embeddings**: Convert text to vector representations via a pluggable embedding provider
- **High Performance**: Optimized for speed and scalability with Elasticsearch
- **Metadata Filters & Field Projection**: Filter results on document metadata and control which metadata keys are returned
- **REST API**: Simple and intuitive API for document indexing and searching
- **Resilient Design**: Circuit breakers and fallbacks for external service dependencies
- **Monitoring**: Prometheus metrics and health endpoints
- **Security**: HTTP Basic authentication for document management, configurable CORS
- **Swagger Documentation**: Interactive API documentation
- **Hybrid Scoring**: Optional blending of vector and lexical signals plus metadata boosts
- **Eval Harness**: Built-in MRR/NDCG@k over a small gold set; reports exported from CI
- **Relevance Tuning**: BM25-style lexical scoring, recency decay, A/B-friendly scoring profiles

## Getting Started (fast demo)

1) Build (backend + precompiled React UI are packaged together):
```
./mvnw clean package -Dspotless.skip=true
```
2) Run in local demo mode (no auth, no external search stack, schema auto-created):
```
SECURITY_AUTH_ENABLED=false \
MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENABLED=false \
SPRING_PROFILES_ACTIVE=local \
SPRING_FLYWAY_ENABLED=false \
SPRING_JPA_HIBERNATE_DDL_AUTO=update \
ELASTICSEARCH_STUB_ENABLED=true \
EMBEDDING_STUB_ENABLED=true \
java -jar target/semantic-search-java-1.0.0.jar
```
3) Open the UI at http://localhost:8080/ (served from the bundled React build). Swagger UI is at `/swagger-ui.html`. Default basic auth (if you turn auth on) is `admin` / `admin`.

### Running with real services
- Bring up backing services:
  ```
  docker-compose up -d postgres elasticsearch redis
  ```
- Set `ELASTICSEARCH_STUB_ENABLED=false` and provide your connection details via environment variables (`POSTGRES_*`, `ELASTICSEARCH_*`). Keep `EMBEDDING_STUB_ENABLED=true` if you want local deterministic vectors; set it to `false` if you are supplying a real embedding provider.

### Running in stub mode (no Elasticsearch)

- Set `elasticsearch.stub-enabled=true` (env var `ELASTICSEARCH_STUB_ENABLED=true`).
- The service will use an in-memory vector store for smoke testing; no Elasticsearch host is needed.
- When you’re ready for real search, set the property back to `false` and provide `ELASTICSEARCH_HOST`/`ELASTICSEARCH_PORT`.
- For offline embedding without a provider key, keep `EMBEDDING_STUB_ENABLED=true` (default) so the app generates deterministic vectors locally.

### Seeding demo data
- To load demo documents at startup: `SEED_DEMO_ENABLED=true ./mvnw spring-boot:run -DskipTests`
- Or call the admin endpoint (requires basic auth): `POST /api/v1/documents/seed` (see SeedController).

### Evaluation harness
- Endpoint: `GET /api/v1/eval/run` (requires auth). Seeds demo docs, runs MRR/NDCG/Recall@5 over the curated gold set, and returns JSON.
- CI stores the report at `target/eval/report.json` (uploaded as the `eval-report` artifact).
- To run eval at startup and emit the report locally, set `EVAL_RUN_ON_STARTUP=true`.

### Performance check (k6)
- Script: `perf/k6-smoke.js` with p95 threshold `<400ms`.
- Run locally against a running app (stub mode ok):
  ```
  BASE_URL=http://localhost:8080 k6 run perf/k6-smoke.js
  ```

See `docs/VALIDATION.md` for details on hybrid scoring, eval, perf, and seeding.

### Docker Deployment

1. Build the Docker image
   ```
   docker build -t semantic-search-java .
   ```

2. Run with Docker Compose
   ```
   docker-compose up -d
   ```

## API Documentation

Once the application is running, access the Swagger UI at:
```
http://localhost:8080/swagger-ui.html
```

## Architecture

The application follows a clean architecture pattern with the following components:

- **Controllers**: REST API endpoints for document and search operations
- **Services**: Business logic for embedding generation, indexing, and searching
- **Repositories**: Data access layer for document storage
- **Models**: Data structures for documents and search results
- **Configuration**: Application settings and external service connections

See the [Architecture Documentation](docs/ARCHITECTURE.md) for more details.

## Tech Stack

- **Java 25**: Target runtime (compiled with `release 21` for tooling compatibility)
- **Spring Boot 3.4**: Application framework
- **Elasticsearch**: Vector database for document storage and search
- **PostgreSQL**: Relational database for document metadata
- **Redis**: Caching layer for improved performance
- **Embedding provider API**: Text embedding generation (default client configured via `embedding.*`)
- **Docker**: Containerization
- **React**: Frontend UI

## Testing & Validation

- Unit and integration-style tests use hand-rolled fakes (no Mockito/ByteBuddy) to stay compatible with Java 25.
- Run the suite with `./mvnw clean verify -Dspotless.skip=true`; JaCoCo is wired but disabled by default to keep builds fast.
- In-memory repositories and stub transports provide deterministic validation for controllers, services, and resilience utilities.

## Project Structure

```
semantic-search-java/
├── src/
│   ├── main/
│   │   ├── java/io/github/semanticsearch/
│   │   │   ├── config/         # Configuration classes
│   │   │   ├── controller/     # REST API controllers
│   │   │   ├── exception/      # Exception handling
│   │   │   ├── model/          # Data models
│   │   │   ├── repository/     # Data access layer
│   │   │   ├── security/       # Security configuration
│   │   │   ├── service/        # Business logic
│   │   │   └── util/           # Utility classes
│   │   └── resources/
│   │       ├── application.yml # Application configuration
│   │       └── static/         # Compiled UI assets
│   └── test/                   # Test classes
├── ui/                         # Frontend source code (React)
├── docs/                       # Documentation
├── Dockerfile                  # Docker configuration
├── docker-compose.yml          # Multi-container Docker setup
└── pom.xml                     # Maven build configuration
```

## UI Structure

The UI is a React application that provides a user-friendly interface for the semantic search functionality:

- **Source Code**: Located in the `ui/` directory (not included in the compiled archive)
- **Compiled Assets**: Located in `src/main/resources/static/` directory
- **Build Process**: The React application is built and the resulting assets are placed in the static resources directory for Spring Boot to serve

## License

This project is licensed under the MIT License - see the LICENSE file for details.
