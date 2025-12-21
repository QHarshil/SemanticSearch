import React from 'react';

const AboutPage = () => {
  return (
    <div className="about-page">
      <h1 className="page-title">About Semantic Search</h1>
      
      <section className="about-section">
        <h2>Overview</h2>
        <p>
          Semantic Search is a vector-driven search microservice built with Java and Spring Boot.
          It leverages embeddings to find conceptually similar documents, going beyond
          traditional keyword-based search to understand the intent behind your queries.
        </p>
        <p>
          Our platform is designed for organizations that need to quickly find relevant information
          across large document collections, where traditional keyword search falls short.
        </p>
      </section>
      
      <section className="about-section">
        <h2>How It Works</h2>
        <div className="process-steps">
          <div className="process-step">
            <div className="step-number">1</div>
            <h3>Document Indexing</h3>
            <p>
              When you add a document, its text is converted into a high-dimensional vector
              using a pluggable embedding provider. This vector represents the semantic meaning
              of the document in a mathematical space.
            </p>
          </div>
          
          <div className="process-step">
            <div className="step-number">2</div>
            <h3>Query Processing</h3>
            <p>
              When you search, your query is converted into a vector using the same embedding model,
              ensuring that it's represented in the same semantic space as the documents.
            </p>
          </div>
          
          <div className="process-step">
            <div className="step-number">3</div>
            <h3>Vector Similarity</h3>
            <p>
              The system finds documents whose vectors are closest to your query vector,
              using cosine similarity to measure the semantic relatedness between concepts.
            </p>
          </div>
          
          <div className="process-step">
            <div className="step-number">4</div>
            <h3>Result Ranking</h3>
            <p>
              Results are ranked by similarity score, with the most semantically relevant
              documents appearing first in your search results, regardless of exact keyword matches.
            </p>
          </div>
        </div>
      </section>
      
      <section className="about-section">
        <h2>Technology Stack</h2>
        <div className="tech-stack">
          <div className="tech-item">
            <h3>Backend</h3>
            <ul>
              <li>Java 21+</li>
              <li>Spring Boot 3.4</li>
              <li>Elasticsearch</li>
              <li>PostgreSQL</li>
              <li>Redis</li>
            </ul>
          </div>
          
          <div className="tech-item">
            <h3>Vector Search</h3>
            <ul>
              <li>Embedding provider integration</li>
              <li>Vector Search</li>
              <li>Cosine Similarity</li>
              <li>Resilience Patterns</li>
            </ul>
          </div>
          
          <div className="tech-item">
            <h3>Frontend</h3>
            <ul>
              <li>React</li>
              <li>Tailwind CSS</li>
              <li>Responsive Design</li>
              <li>Context API</li>
            </ul>
          </div>
        </div>
      </section>
      
      <section className="about-section">
        <h2>Use Cases</h2>
        <div className="use-cases">
          <div className="use-case">
            <h3>Knowledge Management</h3>
            <p>
              Find relevant information across internal documentation, knowledge bases, and wikis
              even when searching with different terminology than what's in the documents.
            </p>
          </div>
          
          <div className="use-case">
            <h3>Research & Development</h3>
            <p>
              Discover connections between research papers, patents, and technical documents
              based on conceptual similarity rather than keyword overlap.
            </p>
          </div>
          
          <div className="use-case">
            <h3>Customer Support</h3>
            <p>
              Quickly find relevant support articles and documentation to answer customer
              queries, even when customers describe issues in non-technical language.
            </p>
          </div>
        </div>
      </section>
      
      <section className="about-section">
        <h2>API Documentation</h2>
        <p>
          Our API is fully documented using OpenAPI/Swagger. You can explore the API
          documentation at <a href="/swagger-ui.html">/swagger-ui.html</a>.
        </p>
        <p>
          The API allows you to programmatically:
        </p>
        <ul>
          <li>Add, update, and delete documents</li>
          <li>Perform semantic searches</li>
          <li>Manage document collections</li>
          <li>Monitor system health and performance</li>
        </ul>
      </section>
    </div>
  );
};

export default AboutPage;
