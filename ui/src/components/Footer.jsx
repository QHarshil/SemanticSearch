import React from 'react';

const Footer = () => {
  const currentYear = new Date().getFullYear();
  
  return (
    <footer className="footer">
      <div className="footer-container">
        <div className="footer-section">
          <h3 className="footer-heading">Semantic Search</h3>
          <p className="footer-description">
            Semantic search microservice built with Java and Spring Boot.
            Find documents based on meaning, not just keywords.
          </p>
        </div>
        
        <div className="footer-section">
          <h3 className="footer-heading">Navigation</h3>
          <ul className="footer-links">
            <li><a href="/">Home</a></li>
            <li><a href="/search">Search</a></li>
            <li><a href="/documents">Documents</a></li>
            <li><a href="/about">About</a></li>
          </ul>
        </div>
        
        <div className="footer-section">
          <h3 className="footer-heading">Resources</h3>
          <ul className="footer-links">
            <li><a href="/swagger-ui.html">API Documentation</a></li>
            <li><a href="/v3/api-docs">OpenAPI Spec</a></li>
            <li><a href="https://github.com/yourusername/semantic-search-java" target="_blank" rel="noopener noreferrer">GitHub Repository</a></li>
          </ul>
        </div>
      </div>
      
      <div className="footer-bottom">
        <p className="copyright">© {currentYear} Semantic Search. All rights reserved.</p>
      </div>
    </footer>
  );
};

export default Footer;
