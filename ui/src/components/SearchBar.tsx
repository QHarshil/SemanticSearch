import React, { useState } from 'react';
import { Input } from './ui/input';
import { Button } from './ui/button';
import { Slider } from './ui/slider';
import { Loader2 } from 'lucide-react';

interface SearchBarProps {
  onSearch: (query: string, limit: number, minScore: number) => void;
  loading: boolean;
}

const SearchBar: React.FC<SearchBarProps> = ({ onSearch, loading }) => {
  const [query, setQuery] = useState('');
  const [limit, setLimit] = useState(10);
  const [minScore, setMinScore] = useState(0.7);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim()) {
      onSearch(query, limit, minScore);
    }
  };

  return (
    <div className="mb-8">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="flex gap-2">
          <Input
            type="text"
            placeholder="Enter your search query..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="flex-1"
            disabled={loading}
          />
          <Button type="submit" disabled={loading || !query.trim()}>
            {loading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Searching...
              </>
            ) : (
              'Search'
            )}
          </Button>
        </div>

        <div className="flex items-center">
          <Button
            type="button"
            variant="link"
            onClick={() => setShowAdvanced(!showAdvanced)}
            className="p-0 h-auto text-sm"
          >
            {showAdvanced ? 'Hide Advanced Options' : 'Show Advanced Options'}
          </Button>
        </div>

        {showAdvanced && (
          <div className="bg-gray-50 p-4 rounded-md space-y-4">
            <div className="space-y-2">
              <div className="flex justify-between">
                <label className="text-sm font-medium">Results Limit: {limit}</label>
              </div>
              <Slider
                value={[limit]}
                min={1}
                max={50}
                step={1}
                onValueChange={(value) => setLimit(value[0])}
                disabled={loading}
              />
            </div>

            <div className="space-y-2">
              <div className="flex justify-between">
                <label className="text-sm font-medium">Minimum Score: {minScore.toFixed(2)}</label>
              </div>
              <Slider
                value={[minScore * 100]}
                min={0}
                max={100}
                step={1}
                onValueChange={(value) => setMinScore(value[0] / 100)}
                disabled={loading}
              />
            </div>
          </div>
        )}
      </form>
    </div>
  );
};

export default SearchBar;
