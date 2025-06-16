import React, { useState, useEffect } from 'react';
import DocumentForm from '../components/DocumentForm';
import LoadingSpinner from '../components/LoadingSpinner';
import { showErrorToast, showSuccessToast } from '../components/Toast';
import { useAuth } from '../context/AuthContext';

const DocumentsPage = () => {
  const [documents, setDocuments] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [selectedDocument, setSelectedDocument] = useState(null);
  const { isAuthenticated } = useAuth();

  useEffect(() => {
    fetchDocuments();
  }, []);

  const fetchDocuments = async () => {
    setIsLoading(true);
    setError(null);
    
    try {
      const response = await fetch('/api/documents');
      
      if (!response.ok) {
        throw new Error(`Error: ${response.status}`);
      }
      
      const data = await response.json();
      setDocuments(data);
    } catch (err) {
      setError(`Failed to fetch documents: ${err.message}`);
      showErrorToast(`Error loading documents: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Are you sure you want to delete this document?')) {
      return;
    }
    
    try {
      const response = await fetch(`/api/documents/${id}`, {
        method: 'DELETE',
      });
      
      if (!response.ok) {
        throw new Error(`Error: ${response.status}`);
      }
      
      // Remove the deleted document from the list
      setDocuments(documents.filter(doc => doc.id !== id));
      showSuccessToast('Document deleted successfully');
    } catch (err) {
      showErrorToast(`Failed to delete document: ${err.message}`);
    }
  };

  const handleView = (document) => {
    setSelectedDocument(document);
  };

  const handleCloseView = () => {
    setSelectedDocument(null);
  };

  const handleFormSuccess = () => {
    fetchDocuments();
    setShowForm(false);
  };

  return (
    <div className="documents-page">
      <div className="page-header">
        <h1 className="page-title">Documents</h1>
        <button 
          className="button-primary"
          onClick={() => setShowForm(!showForm)}
        >
          {showForm ? 'Hide Form' : 'Add New Document'}
        </button>
      </div>
      
      {error && <div className="error-message">{error}</div>}
      
      {showForm && (
        <div className="form-section">
          <DocumentForm onSuccess={handleFormSuccess} />
        </div>
      )}
      
      <div className="documents-list">
        <h2 className="section-title">Your Documents</h2>
        
        {isLoading ? (
          <LoadingSpinner />
        ) : documents.length > 0 ? (
          <div className="documents-grid">
            {documents.map(doc => (
              <div key={doc.id} className="document-card">
                <h3 className="document-title">{doc.title}</h3>
                <p className="document-preview">
                  {doc.content.length > 150 
                    ? `${doc.content.substring(0, 150)}...` 
                    : doc.content}
                </p>
                <div className="document-metadata">
                  <span className="document-date">
                    Added: {new Date(doc.createdAt).toLocaleDateString()}
                  </span>
                </div>
                <div className="document-actions">
                  <button 
                    className="button-secondary small"
                    onClick={() => handleView(doc)}
                  >
                    View
                  </button>
                  {isAuthenticated && (
                    <button 
                      className="button-danger small"
                      onClick={() => handleDelete(doc.id)}
                    >
                      Delete
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="empty-state">
            <p>No documents found. Add your first document to get started.</p>
            <button 
              className="button-primary"
              onClick={() => setShowForm(true)}
            >
              Add Document
            </button>
          </div>
        )}
      </div>
      
      {selectedDocument && (
        <div className="document-modal-overlay" onClick={handleCloseView}>
          <div className="document-modal" onClick={e => e.stopPropagation()}>
            <div className="document-modal-header">
              <h2>{selectedDocument.title}</h2>
              <button className="close-button" onClick={handleCloseView}>×</button>
            </div>
            <div className="document-modal-content">
              <p>{selectedDocument.content}</p>
            </div>
            <div className="document-modal-footer">
              <div className="document-metadata">
                <span>ID: {selectedDocument.id}</span>
                <span>Added: {new Date(selectedDocument.createdAt).toLocaleString()}</span>
              </div>
              <button className="button-primary" onClick={handleCloseView}>Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DocumentsPage;
