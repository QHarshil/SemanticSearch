import { useState, useEffect } from 'react';
import SearchForm from '../components/SearchForm';
import LoadingSpinner from '../components/LoadingSpinner';
import { checkHealth } from '../lib/api';

const SearchPage = () => {
  const [isLoading, setIsLoading] = useState(true);
  const [apiStatus, setApiStatus] = useState(null);

  useEffect(() => {
    checkHealth()
      .then((healthy) => setApiStatus(healthy ? 'available' : 'unavailable'))
      .finally(() => setIsLoading(false));
  }, []);

  return (
    <div className="search-page">
      <h1 className="page-title">Search</h1>
      <p className="page-description">
        Queries are embedded and matched against document vectors, then re-ranked with
        BM25 lexical scoring, metadata boosts and recency decay. The default embedder
        runs locally and matches on shared words and word fragments; set an OpenAI key
        to retrieve on meaning instead.
      </p>
      
      {isLoading ? (
        <LoadingSpinner />
      ) : apiStatus === 'available' ? (
        <SearchForm />
      ) : (
        <div className="api-error">
          <h2>Search API {apiStatus === 'unavailable' ? 'Unavailable' : 'Error'}</h2>
          <p>
            {apiStatus === 'unavailable'
              ? 'The search API is currently unavailable. Please try again later.'
              : 'There was an error connecting to the search API. Please check your connection and try again.'}
          </p>
          <button 
            className="button-primary"
            onClick={() => window.location.reload()}
          >
            Retry
          </button>
        </div>
      )}
    </div>
  );
};

export default SearchPage;
