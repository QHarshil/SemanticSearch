import { Link } from 'react-router-dom';

const HomePage = () => {
  return (
    <div className="home-page">
      <section className="hero-section">
        <h1 className="hero-title">Semantic Search</h1>
        <p className="hero-subtitle">
          Hybrid document search. Every query is retrieved twice, by vector
          similarity over document passages and by BM25 over an inverted index,
          and the two rankings are combined.
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
              A document you add is stored in PostgreSQL, split into overlapping
              passages, and each passage is embedded into the search index.
            </p>
          </div>

          <div className="process-step">
            <div className="step-number">2</div>
            <h3>Retrieve twice</h3>
            <p>
              The query is embedded and matched against passages by cosine
              similarity, and separately scored against every document by BM25.
            </p>
          </div>

          <div className="process-step">
            <div className="step-number">3</div>
            <h3>Fuse</h3>
            <p>
              The two candidate lists are combined, so a document either
              retriever found is reachable, then scored together and adjusted for
              metadata boosts and document age.
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
