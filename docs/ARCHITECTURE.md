# Semantic Search Java - Architecture Documentation

## System Architecture Overview

The Semantic Search Java application follows a layered architecture pattern with clear separation of concerns. This document outlines the high-level architecture, component interactions, and data flow within the system.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Applications                       │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                           REST API Layer                         │
│                                                                 │
│  ┌─────────────────────┐            ┌────────────────────────┐  │
│  │  DocumentController │            │    SearchController    │  │
│  └─────────────────────┘            └────────────────────────┘  │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Service Layer                           │
│                                                                 │
│  ┌─────────────────┐  ┌────────────────┐  ┌─────────────────┐  │
│  │ EmbeddingService│  │  IndexService   │  │  SearchService  │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  │
└───────────┼─────────────────────┼─────────────────────┼─────────┘
            │                     │                     │
            ▼                     ▼                     ▼
┌──────────────────┐  ┌─────────────────────┐  ┌─────────────────┐
│                  │  │                     │  │                 │
│ Embedding API    │  │    Elasticsearch    │  │   PostgreSQL    │
│                  │  │                     │  │                 │
└──────────────────┘  └─────────────────────┘  └─────────────────┘
```

## Component Descriptions

### 1. REST API Layer

The REST API layer exposes the application's functionality through HTTP endpoints.

#### DocumentController
- **Responsibility**: Manages document CRUD operations
- **Endpoints**:
  - `POST /api/documents`: Create a new document
  - `GET /api/documents/{id}`: Retrieve a document by ID
  - `PUT /api/documents/{id}`: Update an existing document
  - `DELETE /api/documents/{id}`: Delete a document
  - `GET /api/documents`: List all documents with pagination

#### SearchController
- **Responsibility**: Handles semantic search requests
- **Endpoints**:
  - `POST /api/search`: Search for semantically similar documents
  - `GET /api/search/health`: Check search service health

### 2. Service Layer

The service layer contains the business logic of the application.

#### EmbeddingService
- **Responsibility**: Generates vector embeddings from text
- **Key Functions**:
  - Convert text to vector embeddings using the configured provider
  - Implement resilience patterns for API calls
  - Cache frequently used embeddings

#### IndexService
- **Responsibility**: Manages document indexing in Elasticsearch
- **Key Functions**:
  - Create and update document vectors in Elasticsearch
  - Delete documents from the index
  - Manage index settings and mappings

#### SearchService
- **Responsibility**: Performs semantic searches
- **Key Functions**:
  - Convert search queries to vector embeddings
  - Execute vector similarity searches in Elasticsearch
  - Process and rank search results

### 3. Data Layer

#### DocumentRepository
- **Responsibility**: Provides data access to PostgreSQL
- **Key Functions**:
  - CRUD operations for document metadata
  - Transaction management

### 4. External Services

#### Embedding provider API
- **Usage**: Generates vector embeddings from text
- **Integration**: REST API calls with resilience patterns (circuit breaker, retry)

#### Elasticsearch
- **Usage**: Stores and searches vector embeddings
- **Integration**: Java High-Level REST Client

#### PostgreSQL
- **Usage**: Stores document metadata and user information
- **Integration**: Spring Data JPA

#### Redis
- **Usage**: Caches embeddings and frequently accessed data
- **Integration**: Spring Data Redis

## Data Flow

### Document Indexing Flow

1. Client submits a document through the DocumentController
2. DocumentController validates the request and passes it to IndexService
3. IndexService stores document metadata in PostgreSQL via DocumentRepository
4. IndexService requests vector embedding from EmbeddingService
5. EmbeddingService calls the embedding provider to generate the embedding
6. IndexService stores the document with its embedding in Elasticsearch
7. Response with document ID is returned to the client

### Search Flow

1. Client submits a search query through the SearchController
2. SearchController validates the request and passes it to SearchService
3. SearchService requests vector embedding for the query from EmbeddingService
4. EmbeddingService calls the embedding provider to generate the embedding
5. SearchService performs a vector similarity search in Elasticsearch
6. SearchService retrieves matching document metadata from PostgreSQL
7. Ranked search results are returned to the client

## Resilience Patterns

The application implements several resilience patterns to ensure stability:

1. **Circuit Breaker**: Prevents cascading failures when external services are unavailable
2. **Retry Mechanism**: Automatically retries failed API calls with exponential backoff
3. **Fallback Strategies**: Provides alternative responses when primary operations fail
4. **Caching**: Reduces load on external services and improves response times
5. **Timeouts**: Prevents requests from hanging indefinitely

## Security Architecture

1. **Authentication**: Basic authentication with username/password
2. **Authorization**: Role-based access control for API endpoints
3. **CORS Configuration**: Controlled cross-origin resource sharing
4. **Input Validation**: Request validation to prevent injection attacks
5. **Secure Communication**: HTTPS for all external communications

## UI Architecture

The frontend is built using React with a component-based architecture:

1. **Source Location**: `ui/` directory (source code)
2. **Compiled Assets**: `src/main/resources/static/` directory
3. **Key Components**:
   - Search interface with query input
   - Results display with relevance scoring
   - Document management interface
   - Authentication forms

## Monitoring and Observability

1. **Health Endpoints**: Spring Boot Actuator health indicators
2. **Metrics**: Prometheus metrics for performance monitoring
3. **Logging**: Structured logging with configurable levels
4. **Tracing**: Request tracing for distributed operations

## Deployment Architecture

The application is designed to be deployed in various environments:

1. **Containerized**: Docker for consistent deployment across environments
2. **Scalable**: Stateless design allows horizontal scaling
3. **Configuration**: Externalized configuration via environment variables
4. **Infrastructure**: Compatible with Kubernetes, AWS, GCP, and Azure

## Future Architecture Considerations

1. **Asynchronous Processing**: Message queues for handling large indexing jobs
2. **Multiple Embedding Models**: Support for different embedding providers
3. **Advanced Caching**: Distributed caching for improved performance
4. **Sharding**: Elasticsearch index sharding for larger document collections
5. **Multi-tenancy**: Support for multiple isolated user groups
