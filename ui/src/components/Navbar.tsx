import React from 'react';
import { MagnifyingGlassIcon } from 'lucide-react';

const Navbar: React.FC = () => {
  return (
    <header className="bg-gray-800 text-white shadow-md">
      <div className="container mx-auto px-4 py-4 flex items-center justify-between">
        <div className="flex items-center space-x-2">
          <MagnifyingGlassIcon className="h-6 w-6" />
          <h1 className="text-xl font-bold">SemanticSearchJava</h1>
        </div>
        <div>
          <span className="text-sm opacity-75">AI-Powered Semantic Search</span>
        </div>
      </div>
    </header>
  );
};

export default Navbar;
