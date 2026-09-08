const AboutPage = () => {
  return (
    <div className="about-page">
      <h1 className="page-title">About</h1>

      <section className="about-section">
        <h2>Overview</h2>
        <p>
          A document search service built with Java and Spring Boot. Documents
          are split into overlapping passages and embedded. Every query runs
          twice, once against those vectors and once against a BM25 inverted
          index, and the two rankings are combined.
        </p>
        <p>
          Three embedding providers. The default is a lexical feature-hashing
          model that needs nothing at all. Set EMBEDDING_PROVIDER=onnx to run
          all-MiniLM-L6-v2 in the same process and match on meaning without an
          API key, or openai to call a hosted model.
        </p>
      </section>

      <section className="about-section">
        <h2>Ranking</h2>
        <p>
          Both retrievers run over the whole corpus and their candidates are
          unioned, so a document the words point at is found even when its
          embedding sits nowhere near the query. The union is then re-scored.
        </p>
        <ul>
          <li>Vector similarity (cosine) for candidate retrieval</li>
          <li>BM25 lexical score blended in by a configurable weight</li>
          <li>Additive boosts for configured metadata keys</li>
          <li>Exponential recency decay with a configurable half-life</li>
        </ul>
      </section>

      <section className="about-section">
        <h2>Stack</h2>
        <div className="tech-stack">
          <div className="tech-item">
            <h3>Backend</h3>
            <ul>
              <li>Java 21</li>
              <li>Spring Boot 3.5</li>
              <li>Elasticsearch (kNN)</li>
              <li>PostgreSQL</li>
              <li>Redis (embedding cache)</li>
            </ul>
          </div>

          <div className="tech-item">
            <h3>Frontend</h3>
            <ul>
              <li>React 18</li>
              <li>React Router</li>
              <li>Vite</li>
            </ul>
          </div>
        </div>
      </section>

      <section className="about-section">
        <h2>API</h2>
        <p>
          The HTTP API is documented with OpenAPI at{' '}
          <a href="/swagger-ui.html">/swagger-ui.html</a>. It covers document
          CRUD, search, and a relevance eval endpoint that reports MRR, NDCG@k
          and Recall@k over a curated query set.
        </p>
      </section>
    </div>
  );
};

export default AboutPage;
