import React, { useState, useEffect } from 'react';
import SearchForm from '../components/SearchForm';
import LoadingSpinner from '../components/LoadingSpinner';

const SearchPage = () => {
  const [isLoading, setIsLoading] = useState(true);
  const [apiStatus, setApiStatus] = useState(null);

  useEffect(() => {
    // Check if the search API is available
    const checkApiStatus = async () => {
      try {
        const response = await fetch('/api/search/health');
        if (response.ok) {
          setApiStatus('available');
        } else {
          setApiStatus('unavailable');
        }
      } catch (error) {
        console.error('API health check failed:', error);
        setApiStatus('error');
      } finally {
        setIsLoading(false);
      }
    };

    checkApiStatus();
  }, []);

  return (
    <div className="search-page">
      <h1 className="page-title">Semantic Search</h1>
      <p className="page-description">
        Search for documents based on meaning, not just keywords. Our semantic search
        uses AI to understand the concepts in your query and find relevant documents.
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
