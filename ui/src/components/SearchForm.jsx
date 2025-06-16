import React, { useState, useEffect } from 'react';
import { showSuccessToast, showErrorToast } from '../components/Toast';
import LoadingSpinner from '../components/LoadingSpinner';

const SearchForm = () => {
  const [query, setQuery] = useState('');
  const [minScore, setMinScore] = useState(0.7);
  const [maxResults, setMaxResults] = useState(10);
  const [isLoading, setIsLoading] = useState(false);
  const [results, setResults] = useState([]);
  const [error, setError] = useState(null);
  const [searchHistory, setSearchHistory] = useState([]);
  const [showAdvanced, setShowAdvanced] = useState(false);

  // Load search history from localStorage on component mount
  useEffect(() => {
    const savedHistory = localStorage.getItem('searchHistory');
    if (savedHistory) {
      try {
        setSearchHistory(JSON.parse(savedHistory));
      } catch (e) {
        console.error('Failed to parse search history:', e);
      }
    }
  }, []);

  // Save search history to localStorage when it changes
  useEffect(() => {
    if (searchHistory.length > 0) {
      localStorage.setItem('searchHistory', JSON.stringify(searchHistory));
    }
  }, [searchHistory]);

  const handleSearch = async (e) => {
    e.preventDefault();
    
    if (!query.trim()) {
      setError('Please enter a search query');
      return;
    }
    
    setIsLoading(true);
    setError(null);
    
    try {
      const response = await fetch('/api/search', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          query,
          minScore,
          maxResults
        }),
      });
      
      if (!response.ok) {
        throw new Error(`Error ${response.status}: ${response.statusText}`);
      }
      
      const data = await response.json();
      setResults(data);
      
      // Add to search history if not already present
      if (!searchHistory.includes(query)) {
        const newHistory = [query, ...searchHistory].slice(0, 10); // Keep only 10 most recent
        setSearchHistory(newHistory);
      }
      
      if (data.length === 0) {
        showInfoToast('No results found for your query');
      } else {
        showSuccessToast(`Found ${data.length} results`);
      }
    } catch (err) {
      setError(`Failed to perform search: ${err.message}`);
      setResults([]);
      showErrorToast(`Search failed: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  const handleHistoryItemClick = (item) => {
    setQuery(item);
  };

  const clearHistory = () => {
    setSearchHistory([]);
    localStorage.removeItem('searchHistory');
    showInfoToast('Search history cleared');
  };

  return (
    <div className="search-form-container">
      <form onSubmit={handleSearch} className="search-form">
        <div className="search-input-group">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Enter your search query..."
            className="search-input"
            aria-label="Search query"
          />
          <button type="submit" className="search-button" disabled={isLoading}>
            {isLoading ? 'Searching...' : 'Search'}
          </button>
        </div>
        
        <div className="search-options-toggle">
          <button 
            type="button" 
            className="toggle-button"
            onClick={() => setShowAdvanced(!showAdvanced)}
          >
            {showAdvanced ? 'Hide Advanced Options' : 'Show Advanced Options'}
          </button>
        </div>
        
        {showAdvanced && (
          <div className="search-options">
            <div className="option-group">
              <label htmlFor="minScore">Minimum Score: {minScore.toFixed(2)}</label>
              <input
                type="range"
                id="minScore"
                min="0"
                max="1"
                step="0.05"
                value={minScore}
                onChange={(e) => setMinScore(parseFloat(e.target.value))}
              />
            </div>
            
            <div className="option-group">
              <label htmlFor="maxResults">Max Results:</label>
              <input
                type="number"
                id="maxResults"
                min="1"
                max="100"
                value={maxResults}
                onChange={(e) => setMaxResults(parseInt(e.target.value))}
              />
            </div>
          </div>
        )}
      </form>
      
      {searchHistory.length > 0 && (
        <div className="search-history">
          <div className="history-header">
            <h3>Recent Searches</h3>
            <button 
              className="clear-history-button"
              onClick={clearHistory}
            >
              Clear
            </button>
          </div>
          <ul className="history-list">
            {searchHistory.map((item, index) => (
              <li key={index} className="history-item">
                <button 
                  className="history-button"
                  onClick={() => handleHistoryItemClick(item)}
                >
                  {item}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
      
      {error && <div className="error-message">{error}</div>}
      
      <div className="search-results">
        {isLoading ? (
          <LoadingSpinner />
        ) : results.length > 0 ? (
          <>
            <h2 className="results-heading">Search Results</h2>
            <div className="results-list">
              {results.map((result) => (
                <div key={result.id} className="result-card">
                  <h3 className="result-title">{result.title}</h3>
                  <span className="result-score">Score: {result.score.toFixed(2)}</span>
                  <p className="result-content">{result.content}</p>
                  <div className="result-metadata">
                    <span className="result-date">Added: {new Date(result.createdAt).toLocaleDateString()}</span>
                    <span className="result-id">ID: {result.id}</span>
                  </div>
                </div>
              ))}
            </div>
          </>
        ) : query && !error && !isLoading ? (
          <div className="no-results">No results found</div>
        ) : null}
      </div>
    </div>
  );
};

// Helper function for info toast
const showInfoToast = (message) => {
  window.dispatchEvent(
    new CustomEvent('toast', { detail: { message, type: 'info' } })
  );
};

export default SearchForm;
