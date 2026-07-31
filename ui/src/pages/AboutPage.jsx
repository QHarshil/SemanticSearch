const AboutPage = () => {
  return (
    <div className="about-page">
      <h1 className="page-title">About</h1>

      <section className="about-section">
        <h2>Overview</h2>
        <p>
          A document search service built with Java and Spring Boot. Documents
          are embedded into vectors and retrieved by cosine similarity, then
          re-ranked with lexical and freshness signals before being returned.
        </p>
        <p>
          Embeddings come from a pluggable provider. By default the service uses
          a built-in local embedder so it runs with no API key; set an OpenAI key
          to use a hosted model instead.
        </p>
      </section>

      <section className="about-section">
        <h2>Ranking</h2>
        <p>
          Retrieval is vector-first: the index returns nearest neighbours for the
          query vector. Those candidates are then re-scored, so lexical matching
          and boosts refine the ordering rather than widening recall.
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
