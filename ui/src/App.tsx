import React, { useState, useEffect } from 'react';
import './App.css';

// Components
import SearchBar from './components/SearchBar';
import SearchResults from './components/SearchResults';
import DocumentForm from './components/DocumentForm';
import DocumentList from './components/DocumentList';
import Navbar from './components/Navbar';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './components/ui/tabs';
import { Toaster } from './components/ui/toaster';
import { useToast } from './components/ui/use-toast';

function App() {
  const [searchResults, setSearchResults] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('search');
  const [selectedDocument, setSelectedDocument] = useState(null);
  const { toast } = useToast();

  // Fetch documents on component mount
  useEffect(() => {
    fetchDocuments();
  }, []);

  // Fetch all documents
  const fetchDocuments = async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/v1/documents');
      if (!response.ok) {
        throw new Error('Failed to fetch documents');
      }
      const data = await response.json();
      setDocuments(data.content || []);
    } catch (error) {
      console.error('Error fetching documents:', error);
      toast({
        title: 'Error',
        description: 'Failed to fetch documents. Please try again.',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  // Handle search
  const handleSearch = async (query, limit = 10, minScore = 0.7) => {
    if (!query.trim()) return;
    
    setLoading(true);
    try {
      const response = await fetch(`/api/v1/search?query=${encodeURIComponent(query)}&limit=${limit}&minScore=${minScore}`);
      if (!response.ok) {
        throw new Error('Search failed');
      }
      const data = await response.json();
      setSearchResults(data);
    } catch (error) {
      console.error('Error performing search:', error);
      toast({
        title: 'Search Error',
        description: 'Failed to perform search. Please try again.',
        variant: 'destructive',
      });
      setSearchResults([]);
    } finally {
      setLoading(false);
    }
  };

  // Handle document creation
  const handleCreateDocument = async (document) => {
    setLoading(true);
    try {
      const response = await fetch('/api/v1/documents', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(document),
      });
      
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Failed to create document');
      }
      
      toast({
        title: 'Success',
        description: 'Document created successfully!',
      });
      
      fetchDocuments();
      setActiveTab('documents');
    } catch (error) {
      console.error('Error creating document:', error);
      toast({
        title: 'Error',
        description: error.message || 'Failed to create document. Please try again.',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  // Handle document update
  const handleUpdateDocument = async (document) => {
    setLoading(true);
    try {
      const response = await fetch(`/api/v1/documents/${document.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(document),
      });
      
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Failed to update document');
      }
      
      toast({
        title: 'Success',
        description: 'Document updated successfully!',
      });
      
      fetchDocuments();
      setSelectedDocument(null);
    } catch (error) {
      console.error('Error updating document:', error);
      toast({
        title: 'Error',
        description: error.message || 'Failed to update document. Please try again.',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  // Handle document deletion
  const handleDeleteDocument = async (id) => {
    if (!window.confirm('Are you sure you want to delete this document?')) {
      return;
    }
    
    setLoading(true);
    try {
      const response = await fetch(`/api/v1/documents/${id}`, {
        method: 'DELETE',
      });
      
      if (!response.ok) {
        throw new Error('Failed to delete document');
      }
      
      toast({
        title: 'Success',
        description: 'Document deleted successfully!',
      });
      
      fetchDocuments();
    } catch (error) {
      console.error('Error deleting document:', error);
      toast({
        title: 'Error',
        description: 'Failed to delete document. Please try again.',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  // Handle document edit
  const handleEditDocument = (document) => {
    setSelectedDocument(document);
    setActiveTab('add');
  };

  return (
    <div className="min-h-screen bg-neutral-950 text-slate-100">
      <Toaster />
      <Navbar />

      <main className="container mx-auto px-4 py-10">
        <div className="flex flex-col gap-6">
          <div className="panel p-4 sm:p-6 border border-neutral-800">
            <div className="flex items-center justify-between">
              <h1 className="text-3xl font-semibold tracking-tight">Semantic Search Studio</h1>
              <span className="text-xs uppercase tracking-[0.2em] text-neutral-400">
                Live Demo
              </span>
            </div>
          </div>

          <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
            <TabsList className="grid w-full grid-cols-3 bg-neutral-900 border border-neutral-800 rounded-lg">
              <TabsTrigger value="search">Search</TabsTrigger>
              <TabsTrigger value="add">Add Document</TabsTrigger>
              <TabsTrigger value="documents">Manage</TabsTrigger>
            </TabsList>

            <TabsContent value="search" className="mt-6">
              <div className="panel p-6 border border-neutral-800">
                <h2 className="text-2xl font-semibold mb-6">Semantic Search</h2>
                <SearchBar onSearch={handleSearch} loading={loading} />
                <SearchResults results={searchResults} loading={loading} />
              </div>
            </TabsContent>

            <TabsContent value="add" className="mt-6">
              <div className="panel p-6 border border-neutral-800">
                <h2 className="text-2xl font-semibold mb-6">
                  {selectedDocument ? 'Edit Document' : 'Add New Document'}
                </h2>
                <DocumentForm
                  onSubmit={selectedDocument ? handleUpdateDocument : handleCreateDocument}
                  document={selectedDocument}
                  loading={loading}
                  onCancel={() => setSelectedDocument(null)}
                />
              </div>
            </TabsContent>

            <TabsContent value="documents" className="mt-6">
              <div className="panel p-6 border border-neutral-800">
                <h2 className="text-2xl font-semibold mb-6">Manage Documents</h2>
                <DocumentList
                  documents={documents}
                  onEdit={handleEditDocument}
                  onDelete={handleDeleteDocument}
                  loading={loading}
                  onRefresh={fetchDocuments}
                />
              </div>
            </TabsContent>
          </Tabs>
        </div>
      </main>

      <footer className="footer text-slate-100">
        <div className="container mx-auto px-4 text-center">
          <p className="text-sm uppercase tracking-[0.25em] text-neutral-400">
            SemanticSearchJava — Vector Search Stack
          </p>
        </div>
      </footer>
    </div>
  );
}

export default App;
