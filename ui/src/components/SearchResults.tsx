import React from 'react';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from './ui/card';
import { Badge } from './ui/badge';
import { Skeleton } from './ui/skeleton';

interface SearchResult {
  id: string;
  title: string;
  content: string;
  score: number;
  highlights?: string[];
  metadata?: Record<string, string>;
}

interface SearchResultsProps {
  results: SearchResult[];
  loading: boolean;
}

const SearchResults: React.FC<SearchResultsProps> = ({ results, loading }) => {
  if (loading) {
    return (
      <div className="space-y-4">
        {[1, 2, 3].map((i) => (
          <Card key={i} className="w-full">
            <CardHeader className="pb-2">
              <Skeleton className="h-6 w-2/3" />
              <Skeleton className="h-4 w-1/4" />
            </CardHeader>
            <CardContent>
              <Skeleton className="h-4 w-full mb-2" />
              <Skeleton className="h-4 w-full mb-2" />
              <Skeleton className="h-4 w-2/3" />
            </CardContent>
            <CardFooter>
              <Skeleton className="h-4 w-1/3" />
            </CardFooter>
          </Card>
        ))}
      </div>
    );
  }

  if (results.length === 0) {
    return (
      <div className="text-center py-8">
        <p className="text-gray-500">No results found. Try a different search query.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h3 className="text-lg font-medium">Found {results.length} results</h3>
      
      {results.map((result) => (
        <Card key={result.id} className="w-full">
          <CardHeader className="pb-2">
            <div className="flex justify-between items-start">
              <CardTitle className="text-xl">{result.title}</CardTitle>
              <Badge variant="outline" className="ml-2">
                Score: {result.score.toFixed(2)}
              </Badge>
            </div>
            {result.metadata && Object.keys(result.metadata).length > 0 && (
              <CardDescription>
                {Object.entries(result.metadata).map(([key, value]) => (
                  <Badge key={key} variant="secondary" className="mr-2 mb-1">
                    {key}: {value}
                  </Badge>
                ))}
              </CardDescription>
            )}
          </CardHeader>
          
          <CardContent>
            {result.highlights && result.highlights.length > 0 ? (
              <div className="space-y-2">
                {result.highlights.map((highlight, index) => (
                  <p key={index} className="text-sm bg-yellow-50 p-2 rounded border-l-2 border-yellow-300">
                    {highlight}
                  </p>
                ))}
              </div>
            ) : (
              <p className="text-sm text-gray-700">
                {result.content.length > 300
                  ? `${result.content.substring(0, 300)}...`
                  : result.content}
              </p>
            )}
          </CardContent>
          
          <CardFooter className="text-xs text-gray-500">
            Document ID: {result.id}
          </CardFooter>
        </Card>
      ))}
    </div>
  );
};

export default SearchResults;
