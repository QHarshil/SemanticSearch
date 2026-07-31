import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { showSuccessToast, showErrorToast, showInfoToast } from '../lib/toast';
import LoadingSpinner from '../components/LoadingSpinner';
import { search, DEFAULT_MIN_SCORE } from '../lib/api';

/** Reads a URL parameter as a number, falling back when it is absent or malformed. */
function numberOr(raw, fallback) {
  const parsed = Number.parseFloat(raw);
  return Number.isFinite(parsed) ? parsed : fallback;
}

const SearchForm = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const urlQuery = searchParams.get('q') ?? '';
  const urlMinScore = searchParams.get('minScore');
  const urlLimit = searchParams.get('limit');

  const [query, setQuery] = useState(urlQuery);
  const [minScore, setMinScore] = useState(() => numberOr(urlMinScore, DEFAULT_MIN_SCORE));
  const [limit, setLimit] = useState(() => numberOr(urlLimit, 10));
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

  const runSearch = useCallback(async (term, scoreFloor, maxResults) => {
    setIsLoading(true);
    setError(null);

    try {
      const data = await search({ query: term, limit: maxResults, minScore: scoreFloor });
      setResults(data);
      setSearchHistory((history) =>
        history.includes(term) ? history : [term, ...history].slice(0, 10)
      );

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
  }, []);

  // The query lives in the URL, so a search is a shareable link and the browser's
  // back button moves between searches. Submitting only rewrites the URL; this
  // effect is what actually issues the request.
  useEffect(() => {
    if (urlQuery.trim()) {
      runSearch(urlQuery, numberOr(urlMinScore, DEFAULT_MIN_SCORE), numberOr(urlLimit, 10));
    }
  }, [urlQuery, urlMinScore, urlLimit, runSearch]);

  const handleSearch = (e) => {
    e.preventDefault();

    if (!query.trim()) {
      setError('Please enter a search query');
      return;
    }

    setSearchParams({
      q: query.trim(),
      minScore: String(minScore),
      limit: String(limit),
    });
  };

  const handleHistoryItemClick = (item) => {
    setQuery(item);
    setSearchParams({ q: item, minScore: String(minScore), limit: String(limit) });
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
              <label htmlFor="limit">Max Results:</label>
              <input
                type="number"
                id="limit"
                min="1"
                max="100"
                value={limit}
                onChange={(e) => setLimit(parseInt(e.target.value, 10) || 1)}
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
                  <span className="result-score">Score: {result.score.toFixed(3)}</span>
                  <p className="result-content">{result.content}</p>
                  {result.highlights?.length > 0 && (
                    <ul className="result-highlights">
                      {result.highlights.map((highlight, index) => (
                        <li key={index}>{highlight}</li>
                      ))}
                    </ul>
                  )}
                  <div className="result-metadata">
                    {Object.entries(result.metadata ?? {}).map(([key, value]) => (
                      <span key={key} className="result-tag">{key}: {value}</span>
                    ))}
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

export default SearchForm;
