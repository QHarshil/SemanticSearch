# Validation, Evaluation, and Performance

## Relevance evaluation
- Endpoint: `GET /api/v1/eval/run` (auth required). Seeds demo docs if missing and returns MRR/NDCG@5/Recall@5.
- CI artifact: `eval-report` contains `target/eval/report.json` from `EvalServiceIntegrationTest`.
- Extend the gold set by adding titles + query pairs in `EvalService` (lookup by title) or calling the endpoint with your own seeds.

## Hybrid scoring
- Configure via `search.hybrid-enabled` (default `true`) and `search.hybrid-vector-weight` (0-1).
- Metadata boosts: set `search.metadata-boosts.<key>=<delta>` to add additive boosts when metadata keys are present.

## Performance check
- k6 script at `perf/k6-smoke.js`; threshold `p(95)<400ms`.
- Run locally (stub mode is fine): `BASE_URL=http://localhost:8080 k6 run perf/k6-smoke.js`.
- Document your run results here when you run against real backing services.

## Seeding
- Seed demo docs at startup: `SEED_DEMO_ENABLED=true ./mvnw spring-boot:run -DskipTests`.
- Seed on demand: `POST /api/v1/documents/seed` (auth required).
