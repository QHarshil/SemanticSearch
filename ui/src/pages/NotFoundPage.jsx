import React from 'react';

const NotFoundPage = () => {
  return (
    <div className="not-found-page">
      <div className="not-found-container">
        <h1 className="not-found-title">404</h1>
        <h2 className="not-found-subtitle">Page Not Found</h2>
        <p className="not-found-message">
          The page you are looking for doesn't exist or has been moved.
        </p>
        <a href="/" className="button-primary">
          Return to Home
        </a>
      </div>
    </div>
  );
};

export default NotFoundPage;
