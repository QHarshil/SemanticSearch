import React from 'react';
import { Link } from 'react-router-dom';

const HomePage = () => {
  return (
    <div className="home-page">
      <section className="hero-section">
        <h1 className="hero-title">AI-Powered Semantic Search</h1>
        <p className="hero-subtitle">
          Find documents based on meaning, not just keywords. Discover the power of vector embeddings and AI-driven search.
        </p>
        <div className="hero-actions">
          <Link to="/search" className="button-primary">Try Searching</Link>
          <Link to="/documents" className="button-secondary">Manage Documents</Link>
        </div>
      </section>
      
      <section className="features-section">
        <h2 className="section-title">Key Features</h2>
        <div className="features-grid">
          <div className="feature-card">
            <div className="feature-icon">🔍</div>
            <h3 className="feature-title">Semantic Search</h3>
            <p className="feature-description">
              Search for concepts and meaning, not just exact keyword matches. Find relevant documents even when they use different terminology.
            </p>
          </div>
          
          <div className="feature-card">
            <div className="feature-icon">🧠</div>
            <h3 className="feature-title">AI Embeddings</h3>
            <p className="feature-description">
              Powered by state-of-the-art language models that understand context and semantics for more accurate results.
            </p>
          </div>
          
          <div className="feature-card">
            <div className="feature-icon">⚡</div>
            <h3 className="feature-title">High Performance</h3>
            <p className="feature-description">
              Optimized for speed and scalability with Elasticsearch vector search and efficient caching mechanisms.
            </p>
          </div>
          
          <div className="feature-card">
            <div className="feature-icon">🔒</div>
            <h3 className="feature-title">Secure & Reliable</h3>
            <p className="feature-description">
              Built with resilience patterns, circuit breakers, and security best practices for enterprise-grade reliability.
            </p>
          </div>
        </div>
      </section>
      
      <section className="how-it-works">
        <h2 className="section-title">How It Works</h2>
        <div className="process-steps">
          <div className="process-step">
            <div className="step-number">1</div>
            <h3>Add Documents</h3>
            <p>
              Upload your documents through our simple interface or API. Each document is processed and prepared for semantic indexing.
            </p>
          </div>
          
          <div className="process-step">
            <div className="step-number">2</div>
            <h3>Generate Embeddings</h3>
            <p>
              Our system converts document text into high-dimensional vector embeddings that capture semantic meaning.
            </p>
          </div>
          
          <div className="process-step">
            <div className="step-number">3</div>
            <h3>Search Semantically</h3>
            <p>
              Enter natural language queries and our system finds the most semantically similar documents using vector similarity.
            </p>
          </div>
          
          <div className="process-step">
            <div className="step-number">4</div>
            <h3>Review Results</h3>
            <p>
              Get ranked results based on semantic relevance, not just keyword matching, for more accurate information retrieval.
            </p>
          </div>
        </div>
      </section>
      
      <section className="cta-section">
        <h2 className="section-title">Ready to get started?</h2>
        <p className="cta-description">
          Add your documents and start searching semantically today. Experience the difference of AI-powered search.
        </p>
        <div className="cta-buttons">
          <Link to="/documents" className="button-primary">Add Documents</Link>
          <Link to="/about" className="button-secondary">Learn More</Link>
        </div>
      </section>
    </div>
  );
};

export default HomePage;
