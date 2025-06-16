import React, { useState, useEffect } from 'react';
import { showSuccessToast, showErrorToast } from '../components/Toast';
import LoadingSpinner from '../components/LoadingSpinner';

const DocumentForm = () => {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  const [charCount, setCharCount] = useState(0);
  const [validationErrors, setValidationErrors] = useState({});

  useEffect(() => {
    setCharCount(content.length);
  }, [content]);

  const validateForm = () => {
    const errors = {};
    
    if (!title.trim()) {
      errors.title = 'Title is required';
    } else if (title.length < 3) {
      errors.title = 'Title must be at least 3 characters';
    } else if (title.length > 100) {
      errors.title = 'Title must be less than 100 characters';
    }
    
    if (!content.trim()) {
      errors.content = 'Content is required';
    } else if (content.length < 10) {
      errors.content = 'Content must be at least 10 characters';
    }
    
    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!validateForm()) {
      return;
    }
    
    setIsSubmitting(true);
    setError(null);
    setMessage(null);
    
    try {
      const response = await fetch('/api/documents', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          title,
          content
        }),
      });
      
      if (!response.ok) {
        const errorData = await response.json().catch(() => null);
        throw new Error(errorData?.message || `Error: ${response.status}`);
      }
      
      const data = await response.json();
      setMessage(`Document created successfully with ID: ${data.id}`);
      showSuccessToast('Document created successfully');
      
      // Reset form
      setTitle('');
      setContent('');
      setCharCount(0);
      setValidationErrors({});
    } catch (err) {
      setError(`Failed to create document: ${err.message}`);
      showErrorToast(`Error: ${err.message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="document-form-container">
      <h2>Add New Document</h2>
      
      {message && <div className="success-message">{message}</div>}
      {error && <div className="error-message">{error}</div>}
      
      <form onSubmit={handleSubmit} className="document-form">
        <div className={`form-group ${validationErrors.title ? 'has-error' : ''}`}>
          <label htmlFor="title" className="form-label">Title</label>
          <input
            type="text"
            id="title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="form-input"
            placeholder="Enter document title"
            disabled={isSubmitting}
          />
          {validationErrors.title && (
            <div className="error-feedback">{validationErrors.title}</div>
          )}
        </div>
        
        <div className={`form-group ${validationErrors.content ? 'has-error' : ''}`}>
          <label htmlFor="content" className="form-label">
            Content <span className="char-count">{charCount} characters</span>
          </label>
          <textarea
            id="content"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            className="form-textarea"
            placeholder="Enter document content"
            rows="10"
            disabled={isSubmitting}
          />
          {validationErrors.content && (
            <div className="error-feedback">{validationErrors.content}</div>
          )}
        </div>
        
        <div className="form-actions">
          <button 
            type="submit" 
            className="button-primary"
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <LoadingSpinner /> Submitting...
              </>
            ) : (
              'Add Document'
            )}
          </button>
        </div>
      </form>
    </div>
  );
};

export default DocumentForm;
