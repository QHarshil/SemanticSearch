# Semantic Search Java

A high-performance, AI-powered semantic search microservice built with Java and Spring Boot.

## Overview

This project provides a robust semantic search capability using vector embeddings to find conceptually similar documents. It leverages OpenAI's embedding models for text vectorization and Elasticsearch for efficient vector search.

## Features

- **Semantic Document Search**: Find documents based on meaning, not just keywords
- **Vector Embeddings**: Convert text to vector representations using OpenAI's embedding models
- **High Performance**: Optimized for speed and scalability with Elasticsearch
- **REST API**: Simple and intuitive API for document indexing and searching
- **Resilient Design**: Circuit breakers and fallbacks for external service dependencies
- **Monitoring**: Prometheus metrics and health endpoints
- **Security**: Basic authentication and CORS configuration
- **Swagger Documentation**: Interactive API documentation

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Docker and Docker Compose (for containerized deployment)
- OpenAI API key

### Running Locally

1. Clone the repository
   ```
   git clone https://github.com/QHarshil/SemanticSearch-Java.git
   cd semantic-search-java
   ```

2. Configure environment variables in `application.yml` or set them in your environment:
   ```
   POSTGRES_HOST=localhost
   POSTGRES_PORT=5432
   POSTGRES_DB=semanticsearch
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=postgres
   ELASTICSEARCH_HOST=localhost
   ELASTICSEARCH_PORT=9200
   OPENAI_API_KEY=your_openai_api_key
   ```

3. Build the application
   ```
   mvn clean package
   ```

4. Run the application
   ```
   java -jar target/semantic-search-java-1.0.0.jar
   ```

5. Access the application at http://localhost:8080

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

- **Java 17**: Core programming language
- **Spring Boot 3**: Application framework
- **Elasticsearch**: Vector database for document storage and search
- **PostgreSQL**: Relational database for document metadata
- **Redis**: Caching layer for improved performance
- **OpenAI API**: Text embedding generation
- **Docker**: Containerization
- **React**: Frontend UI

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
