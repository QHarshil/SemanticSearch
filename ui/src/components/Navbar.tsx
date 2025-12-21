import React from 'react';
import { Sparkles, Radar } from 'lucide-react';

const Navbar: React.FC = () => {
  return (
    <header className="sticky top-0 z-20 border-b border-neutral-800 backdrop-blur bg-neutral-950/80">
      <div className="container mx-auto px-4 py-4 flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <div className="h-10 w-10 rounded-md border border-neutral-700 bg-neutral-900 grid place-items-center">
            <Radar className="h-5 w-5 text-indigo-300" />
          </div>
          <div>
            <h1 className="text-lg font-semibold tracking-tight text-slate-100">
              SemanticSearchJava
            </h1>
            <p className="text-xs uppercase tracking-[0.25em] text-neutral-400">
              Vector Retrieval
            </p>
          </div>
        </div>
        <div className="flex items-center space-x-2 text-sm text-neutral-300">
          <Sparkles className="h-4 w-4 text-amber-300" />
          <span>Hybrid + Eval Ready</span>
        </div>
      </div>
    </header>
  );
};

export default Navbar;
