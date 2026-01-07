# Semantic Search Java

A high-performance semantic search microservice built with Java and Spring Boot. The bundled React UI is compiled into the jar and served at `/`.

## Overview

Semantic search engine that finds conceptually similar documents using vector embeddings. Supports hybrid retrieval combining lexical (BM25) and semantic (vector) scoring with pluggable embedding providers and Elasticsearch for efficient search.

## Features

- **Hybrid Retrieval**: Blends BM25 lexical scoring with vector similarity for better relevance
- **Vector Search**: Elasticsearch kNN for fast approximate nearest neighbor lookup
- **Eval Harness**: Built-in MRR/NDCG@k metrics over configurable gold sets
- **Relevance Tuning**: Recency decay, metadata boosts, A/B-friendly scoring profiles
- **Resilient Design**: Circuit breakers and fallbacks for external dependencies
- **Monitoring**: Prometheus metrics and health endpoints
- **Security**: HTTP Basic auth, configurable CORS
- **React Dashboard**: Query testing and relevance visualization UI

## Quick Start

**Build:**
```bash
./mvnw clean package -Dspotless.skip=true
```

**Run (local demo mode):**
```bash
SECURITY_AUTH_ENABLED=false \
SPRING_PROFILES_ACTIVE=local \
SPRING_FLYWAY_ENABLED=false \
SPRING_JPA_HIBERNATE_DDL_AUTO=update \
ELASTICSEARCH_STUB_ENABLED=true \
EMBEDDING_STUB_ENABLED=true \
java -jar target/semantic-search-java-1.0.0.jar
```

**Access:**
- UI: `http://localhost:8080/`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Default auth: `admin` / `admin`

## Running with Real Services

```bash
docker-compose up -d postgres elasticsearch redis
```

Set `ELASTICSEARCH_STUB_ENABLED=false` and configure connection details via environment variables.

## Evaluation

Run relevance metrics against the gold set:

```bash
GET /api/v1/eval/run
```

Returns MRR, NDCG@k, and Recall@5. CI exports reports to `target/eval/report.json`.

## Performance Testing

```bash
BASE_URL=http://localhost:8080 k6 run perf/k6-smoke.js
```

Target: p95 < 400ms

## Architecture

```
├── controller/     # REST endpoints
├── service/        # Embedding, indexing, search logic
├── repository/     # Data access (Elasticsearch, PostgreSQL)
├── model/          # Document and search result DTOs
├── config/         # App configuration
└── ui/             # React frontend source
```

## Tech Stack

| Layer | Technology |
|-------|------------|
| Runtime | Java 21+ |
| Framework | Spring Boot 3.4 |
| Search | Elasticsearch (kNN) |
| Database | PostgreSQL |
| Cache | Redis |
| Frontend | React |
| Containerization | Docker |

## API

### Index Document
```bash
POST /api/v1/documents
Content-Type: application/json

{
  "title": "Document Title",
  "content": "Document content to index",
  "metadata": {"category": "tech"}
}
```

### Search
```bash
GET /api/v1/search?q=search+query&limit=10
```

### Hybrid Search with Filters
```bash
POST /api/v1/search
Content-Type: application/json

{
  "query": "search query",
  "filters": {"category": "tech"},
  "limit": 10,
  "profile": "hybrid_v1"
}
```

## Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `ELASTICSEARCH_HOST` | ES hostname | `localhost` |
| `ELASTICSEARCH_PORT` | ES port | `9200` |
| `ELASTICSEARCH_STUB_ENABLED` | Use in-memory store | `true` |
| `EMBEDDING_STUB_ENABLED` | Use deterministic vectors | `true` |
| `SECURITY_AUTH_ENABLED` | Enable basic auth | `true` |
| `EVAL_RUN_ON_STARTUP` | Run eval on boot | `false` |

## Testing

```bash
./mvnw clean verify -Dspotless.skip=true
```

Uses hand-rolled fakes (no Mockito) for Java 21+ compatibility. In-memory repositories provide deterministic validation.

## License

MIT
