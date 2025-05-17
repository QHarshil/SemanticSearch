import React, { useState } from 'react';
import './App.css';

function App() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!query.trim()) return;
    
    setLoading(true);
    try {
      const response = await fetch(`/api/v1/search?query=${encodeURIComponent(query)}`);
      if (!response.ok) {
        throw new Error('Search failed');
      }
      const data = await response.json();
      setResults(data);
    } catch (error) {
      console.error('Error performing search:', error);
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-100">
      <header className="bg-blue-600 text-white shadow-md">
        <div className="container mx-auto px-4 py-4">
          <h1 className="text-xl font-bold">SemanticSearchJava</h1>
          <p className="text-sm opacity-75">AI-Powered Semantic Search</p>
        </div>
      </header>

      <main className="container mx-auto px-4 py-8">
        <div className="bg-white p-6 rounded-lg shadow-md">
          <h2 className="text-2xl font-bold mb-6">Semantic Search</h2>
          
          <form onSubmit={handleSearch} className="mb-8">
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Enter your search query..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="flex-1 px-4 py-2 border rounded-md"
              />
              <button 
                type="submit" 
                disabled={loading || !query.trim()}
                className="bg-blue-600 text-white px-4 py-2 rounded-md disabled:opacity-50"
              >
                {loading ? 'Searching...' : 'Search'}
              </button>
            </div>
          </form>

          {results.length > 0 ? (
            <div className="space-y-4">
              <h3 className="text-lg font-medium">Found {results.length} results</h3>
              
              {results.map((result) => (
                <div key={result.id} className="border rounded-md p-4">
                  <div className="flex justify-between items-start">
                    <h4 className="text-xl font-semibold">{result.title}</h4>
                    <span className="bg-blue-100 text-blue-800 text-xs px-2 py-1 rounded">
                      Score: {result.score.toFixed(2)}
                    </span>
                  </div>
                  
                  <p className="mt-2 text-gray-700">
                    {result.content.length > 300
                      ? `${result.content.substring(0, 300)}...`
                      : result.content}
                  </p>
                  
                  <div className="mt-2 text-xs text-gray-500">
                    Document ID: {result.id}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-8">
              <p className="text-gray-500">
                {loading ? 'Searching...' : 'No results found. Try a different search query.'}
              </p>
            </div>
          )}
        </div>
      </main>
      
      <footer className="bg-gray-800 text-white py-6 mt-12">
        <div className="container mx-auto px-4 text-center">
          <p>SemanticSearchJava - AI-Powered Semantic Search</p>
        </div>
      </footer>
    </div>
  );
}

export default App;
