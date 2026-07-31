import { Link } from 'react-router-dom';

const HomePage = () => {
  return (
    <div className="home-page">
      <section className="hero-section">
        <h1 className="hero-title">Semantic Search</h1>
        <p className="hero-subtitle">
          A hybrid document search service: results are retrieved by vector
          similarity, then re-ranked with BM25 lexical scoring, metadata boosts
          and recency decay.
        </p>
        <div className="hero-actions">
          <Link to="/search" className="button-primary">Search</Link>
          <Link to="/documents" className="button-secondary">Manage Documents</Link>
        </div>
      </section>

      <section className="how-it-works">
        <h2 className="section-title">How It Works</h2>
        <div className="process-steps">
          <div className="process-step">
            <div className="step-number">1</div>
            <h3>Index</h3>
            <p>
              A document you add is stored in PostgreSQL and its text is turned
              into an embedding vector, which is written to the search index.
            </p>
          </div>

          <div className="process-step">
            <div className="step-number">2</div>
            <h3>Retrieve</h3>
            <p>
              Your query is embedded with the same model and the index returns
              the nearest documents by cosine similarity.
            </p>
          </div>

          <div className="process-step">
            <div className="step-number">3</div>
            <h3>Re-rank</h3>
            <p>
              Candidates are re-scored by blending the vector score with a BM25
              lexical score, then adjusted for metadata boosts and document age.
            </p>
          </div>

          <div className="process-step">
            <div className="step-number">4</div>
            <h3>Measure</h3>
            <p>
              A built-in eval endpoint reports MRR, NDCG@k and Recall@k over a
              curated query set so ranking changes can be compared.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
};

export default HomePage;
