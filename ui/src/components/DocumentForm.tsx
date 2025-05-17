import React, { useState, useEffect } from 'react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Textarea } from './ui/textarea';
import { Label } from './ui/label';
import { Loader2 } from 'lucide-react';

interface DocumentFormProps {
  onSubmit: (document: any) => void;
  document?: any;
  loading: boolean;
  onCancel: () => void;
}

const DocumentForm: React.FC<DocumentFormProps> = ({ onSubmit, document, loading, onCancel }) => {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [metadata, setMetadata] = useState<{ key: string; value: string }[]>([{ key: '', value: '' }]);
  
  // Initialize form when editing a document
  useEffect(() => {
    if (document) {
      setTitle(document.title || '');
      setContent(document.content || '');
      
      if (document.metadata && Object.keys(document.metadata).length > 0) {
        setMetadata(
          Object.entries(document.metadata).map(([key, value]) => ({ key, value: value as string }))
        );
      } else {
        setMetadata([{ key: '', value: '' }]);
      }
    } else {
      // Reset form for new document
      setTitle('');
      setContent('');
      setMetadata([{ key: '', value: '' }]);
    }
  }, [document]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    // Convert metadata array to object
    const metadataObj: Record<string, string> = {};
    metadata.forEach(item => {
      if (item.key.trim() && item.value.trim()) {
        metadataObj[item.key.trim()] = item.value.trim();
      }
    });
    
    const documentData = {
      ...(document ? { id: document.id } : {}),
      title: title.trim(),
      content: content.trim(),
      metadata: metadataObj
    };
    
    onSubmit(documentData);
  };

  const handleAddMetadata = () => {
    setMetadata([...metadata, { key: '', value: '' }]);
  };

  const handleRemoveMetadata = (index: number) => {
    const newMetadata = [...metadata];
    newMetadata.splice(index, 1);
    setMetadata(newMetadata.length ? newMetadata : [{ key: '', value: '' }]);
  };

  const handleMetadataChange = (index: number, field: 'key' | 'value', value: string) => {
    const newMetadata = [...metadata];
    newMetadata[index][field] = value;
    setMetadata(newMetadata);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <div className="space-y-2">
        <Label htmlFor="title">Title</Label>
        <Input
          id="title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Document title"
          required
          disabled={loading}
        />
      </div>
      
      <div className="space-y-2">
        <Label htmlFor="content">Content</Label>
        <Textarea
          id="content"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="Document content"
          required
          className="min-h-[200px]"
          disabled={loading}
        />
      </div>
      
      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <Label>Metadata (Optional)</Label>
          <Button 
            type="button" 
            variant="outline" 
            size="sm" 
            onClick={handleAddMetadata}
            disabled={loading}
          >
            Add Field
          </Button>
        </div>
        
        {metadata.map((item, index) => (
          <div key={index} className="flex gap-2 items-start">
            <div className="flex-1">
              <Input
                placeholder="Key"
                value={item.key}
                onChange={(e) => handleMetadataChange(index, 'key', e.target.value)}
                disabled={loading}
              />
            </div>
            <div className="flex-1">
              <Input
                placeholder="Value"
                value={item.value}
                onChange={(e) => handleMetadataChange(index, 'value', e.target.value)}
                disabled={loading}
              />
            </div>
            <Button 
              type="button" 
              variant="ghost" 
              size="sm" 
              onClick={() => handleRemoveMetadata(index)}
              disabled={loading || metadata.length === 1}
              className="mt-1"
            >
              Remove
            </Button>
          </div>
        ))}
      </div>
      
      <div className="flex justify-end gap-2">
        <Button 
          type="button" 
          variant="outline" 
          onClick={onCancel}
          disabled={loading}
        >
          Cancel
        </Button>
        <Button type="submit" disabled={loading || !title.trim() || !content.trim()}>
          {loading ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              {document ? 'Updating...' : 'Creating...'}
            </>
          ) : (
            document ? 'Update Document' : 'Create Document'
          )}
        </Button>
      </div>
    </form>
  );
};

export default DocumentForm;
