const Footer = () => {
  const currentYear = new Date().getFullYear();
  
  return (
    <footer className="footer">
      <div className="footer-container">
        <div className="footer-section">
          <h3 className="footer-heading">Semantic Search</h3>
          <p className="footer-description">
            Hybrid document search built with Java and Spring Boot. Vector
            retrieval, re-ranked with BM25, metadata boosts and recency decay.
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
            <li><a href="https://github.com/QHarshil/SemanticSearch" target="_blank" rel="noopener noreferrer">GitHub Repository</a></li>
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
