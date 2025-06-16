import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from '../components/LoadingSpinner';
import { showSuccessToast, showErrorToast } from '../components/Toast';

const ProfilePage = () => {
  const { user, isLoading } = useAuth();
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState({
    name: user?.name || '',
    email: user?.email || '',
    bio: user?.bio || ''
  });
  const [isSaving, setIsSaving] = useState(false);

  if (isLoading) {
    return <LoadingSpinner />;
  }

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsSaving(true);
    
    try {
      // Simulate API call to update profile
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      // In a real app, you would update the user profile via API
      // const response = await fetch('/api/profile', {
      //   method: 'PUT',
      //   headers: { 'Content-Type': 'application/json' },
      //   body: JSON.stringify(formData)
      // });
      
      showSuccessToast('Profile updated successfully');
      setIsEditing(false);
    } catch (error) {
      showErrorToast('Failed to update profile');
      console.error('Profile update error:', error);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="profile-page">
      <h1 className="page-title">User Profile</h1>
      
      <div className="profile-container">
        <div className="profile-header">
          <div className="profile-avatar">
            {user?.username?.charAt(0).toUpperCase() || 'U'}
          </div>
          <div className="profile-info">
            <h2 className="profile-name">{user?.name || user?.username}</h2>
            <p className="profile-username">@{user?.username}</p>
          </div>
        </div>
        
        {isEditing ? (
          <form onSubmit={handleSubmit} className="profile-form">
            <div className="form-group">
              <label htmlFor="name" className="form-label">Name</label>
              <input
                type="text"
                id="name"
                name="name"
                value={formData.name}
                onChange={handleChange}
                className="form-input"
                disabled={isSaving}
              />
            </div>
            
            <div className="form-group">
              <label htmlFor="email" className="form-label">Email</label>
              <input
                type="email"
                id="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                className="form-input"
                disabled={isSaving}
              />
            </div>
            
            <div className="form-group">
              <label htmlFor="bio" className="form-label">Bio</label>
              <textarea
                id="bio"
                name="bio"
                value={formData.bio}
                onChange={handleChange}
                className="form-textarea"
                rows="4"
                disabled={isSaving}
              />
            </div>
            
            <div className="form-actions">
              <button
                type="button"
                className="button-secondary"
                onClick={() => setIsEditing(false)}
                disabled={isSaving}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="button-primary"
                disabled={isSaving}
              >
                {isSaving ? (
                  <>
                    <LoadingSpinner /> Saving...
                  </>
                ) : (
                  'Save Changes'
                )}
              </button>
            </div>
          </form>
        ) : (
          <div className="profile-details">
            <div className="profile-section">
              <h3 className="section-title">Account Information</h3>
              <div className="profile-field">
                <span className="field-label">Username:</span>
                <span className="field-value">{user?.username}</span>
              </div>
              <div className="profile-field">
                <span className="field-label">Name:</span>
                <span className="field-value">{user?.name || 'Not set'}</span>
              </div>
              <div className="profile-field">
                <span className="field-label">Email:</span>
                <span className="field-value">{user?.email || 'Not set'}</span>
              </div>
              <div className="profile-field">
                <span className="field-label">Role:</span>
                <span className="field-value">{user?.role || 'User'}</span>
              </div>
            </div>
            
            <div className="profile-section">
              <h3 className="section-title">Bio</h3>
              <p className="profile-bio">
                {user?.bio || 'No bio information provided.'}
              </p>
            </div>
            
            <div className="profile-actions">
              <button
                className="button-primary"
                onClick={() => setIsEditing(true)}
              >
                Edit Profile
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ProfilePage;
