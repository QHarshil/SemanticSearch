import React from 'react';

const SearchResult = ({ result }) => {
  return (
    <div className="result-card">
      <h3 className="result-title">{result.title}</h3>
      <span className="result-score">Score: {result.score.toFixed(2)}</span>
      <p className="result-content">{result.content}</p>
      <div className="result-metadata">
        <span className="result-date">Added: {new Date(result.createdAt).toLocaleDateString()}</span>
        <span className="result-id">ID: {result.id}</span>
      </div>
    </div>
  );
};

export default SearchResult;
