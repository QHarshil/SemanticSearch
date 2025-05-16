# SemanticSearchJava

AI-Powered Semantic Search Microservice in Java

## Overview

SemanticSearchJava is a Spring Boot microservice that provides sub-100 ms semantic search over arbitrary text corpora. It ingests documents, generates embeddings via OpenAI's Embeddings API, indexes vectors with Elasticsearch, and exposes a RESTful API for semantic search—all in idiomatic, modular Java.

## Features

- **Semantic Search**: Find documents based on meaning, not just keywords
- **High Performance**: Sub-100 ms search response times
- **Scalable Architecture**: Built with Elasticsearch for distributed search
- **Caching**: Redis-based caching for frequently accessed results
- **Comprehensive API**: RESTful endpoints for document management and search
- **Monitoring**: Prometheus and Grafana integration for metrics
- **Security**: Built-in authentication and authorization
- **Fault Tolerance**: Circuit breakers, retries, and fallback mechanisms
- **Containerized**: Docker and Kubernetes ready

## Tech Stack

- **Java 17 + Spring Boot 3.1** (WebFlux for async I/O)
- **OpenAI Java SDK** (text-embedding-ada-002)
- **Elasticsearch 8.x** (Vector search)
- **PostgreSQL** (Document metadata)
- **Redis** (Caching)
- **Micrometer + Prometheus/Grafana** (Metrics)
- **Docker & Docker Compose**
- **GitHub Actions** (CI/CD)

## Getting Started

### Prerequisites

- Java 17 or higher
- Docker and Docker Compose
- OpenAI API key

### Running with Docker Compose

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/semantic-search-java.git
   cd semantic-search-java
   ```

2. Set your OpenAI API key:
   ```bash
   export OPENAI_API_KEY=your_openai_api_key
   ```

3. Start the services:
   ```bash
   docker-compose up -d
   ```

4. The application will be available at http://localhost:8080

### Running Locally

1. Start PostgreSQL, Elasticsearch, and Redis:
   ```bash
   docker-compose up -d postgres elasticsearch redis
   ```

2. Build and run the application:
   ```bash
   ./mvnw clean package
   java -jar target/semantic-search-java-1.0.0.jar
   ```

## API Documentation

Once the application is running, you can access the Swagger UI at:
```
http://localhost:8080/swagger-ui.html
```

### Key Endpoints

- `POST /api/v1/documents` - Create a new document
- `GET /api/v1/documents/{id}` - Retrieve a document by ID
- `PUT /api/v1/documents/{id}` - Update a document
- `DELETE /api/v1/documents/{id}` - Delete a document
- `GET /api/v1/search?query=...` - Perform semantic search
- `POST /api/v1/search/advanced` - Advanced search with filters
- `GET /api/v1/search/similar/{id}` - Find similar documents

## Architecture

The service follows a modular architecture with clear separation of concerns:

- **Document Service**: Manages document CRUD operations
- **Embedding Service**: Generates vector embeddings using OpenAI
- **Index Service**: Manages the Elasticsearch vector index
- **Search Service**: Coordinates search operations

For more details, see the [architecture documentation](architecture.md).

## Monitoring

The application exposes metrics through Micrometer and Prometheus:

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (default credentials: admin/admin)

## Testing

Run the tests with:

```bash
./mvnw test
```

Generate a test coverage report:

```bash
./mvnw jacoco:report
```

The report will be available at `target/site/jacoco/index.html`.

## CI/CD Pipeline

The project includes a GitHub Actions workflow for continuous integration and deployment:

- Builds and tests the application
- Checks code quality and test coverage
- Performs security scanning
- Builds and publishes Docker images

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Commit your changes: `git commit -am 'Add some feature'`
4. Push to the branch: `git push origin feature/your-feature-name`
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
