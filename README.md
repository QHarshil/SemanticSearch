# Semantic Search Java

A high-performance semantic search microservice built with Java and Spring Boot.

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

## Getting Started

### Prerequisites

- Java 25 (Temurin recommended) for runtime; build with JDK 21 for Maven tooling compatibility
- Maven 3.9+ (project ships with `./mvnw`)
- Docker and Docker Compose (for containerized deployment)
- Embedding provider API key
  - If you want to be prompted at startup instead, set `embedding.api.prompt=true` and the app will ask for the key on the CLI.
- For offline/demo mode without a remote embedding provider, you can still run, but embeddings will be empty unless you stub them yourself.

### Running Locally

1. Clone the repository
   ```
   git clone https://github.com/QHarshil/SemanticSearch-Java.git
   cd semantic-search-java
   ```

2. Bring up backing services (PostgreSQL, Elasticsearch, Redis)
   ```
   docker-compose up -d postgres elasticsearch redis
   ```

3. Configure environment variables in `application.yml` or set them in your environment:
   ```
   POSTGRES_HOST=localhost
   POSTGRES_PORT=5432
   POSTGRES_DB=semanticsearch
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=postgres
   ELASTICSEARCH_HOST=localhost
   ELASTICSEARCH_PORT=9200
   ELASTICSEARCH_STUB_ENABLED=false
   EMBEDDING_API_KEY=your_embedding_api_key
   EMBEDDING_STUB_ENABLED=false
   ```

4. Build and test the application (uses the Maven wrapper for reproducible builds)
   ```
   ./mvnw clean verify -Dspotless.skip=true
   ```

5. Run the application
   ```
   java -jar target/semantic-search-java-1.0.0.jar
   ```

6. Access the application at http://localhost:8080 (Swagger UI at `/swagger-ui.html`)

   Default basic auth credentials (can be overridden via env vars):
   ```
   ADMIN_USER=admin
   ADMIN_PASSWORD=admin
   ```

### Running in stub mode (no Elasticsearch)

- Set `elasticsearch.stub-enabled=true` (env var `ELASTICSEARCH_STUB_ENABLED=true`).
- The service will use an in-memory vector store for smoke testing; no Elasticsearch host is needed.
- When you’re ready for real search, set the property back to `false` and provide `ELASTICSEARCH_HOST`/`ELASTICSEARCH_PORT`.
- For offline embedding without a provider key, keep `EMBEDDING_STUB_ENABLED=true` (default) so the app generates deterministic vectors locally.

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
