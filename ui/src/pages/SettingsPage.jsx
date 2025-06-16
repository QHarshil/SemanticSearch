import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from '../components/LoadingSpinner';
import { showSuccessToast, showErrorToast } from '../components/Toast';

const SettingsPage = () => {
  const { user, isLoading } = useAuth();
  const [settings, setSettings] = useState({
    emailNotifications: true,
    darkMode: false,
    searchHistory: true,
    apiKey: '••••••••••••••••'
  });
  const [isSaving, setIsSaving] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);

  if (isLoading) {
    return <LoadingSpinner />;
  }

  const handleToggle = (setting) => {
    setSettings(prev => ({
      ...prev,
      [setting]: !prev[setting]
    }));
  };

  const handleSaveSettings = async () => {
    setIsSaving(true);
    
    try {
      // Simulate API call to save settings
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      // In a real app, you would save settings via API
      // const response = await fetch('/api/settings', {
      //   method: 'PUT',
      //   headers: { 'Content-Type': 'application/json' },
      //   body: JSON.stringify(settings)
      // });
      
      showSuccessToast('Settings saved successfully');
    } catch (error) {
      showErrorToast('Failed to save settings');
      console.error('Settings save error:', error);
    } finally {
      setIsSaving(false);
    }
  };

  const generateNewApiKey = async () => {
    if (!window.confirm('Are you sure you want to generate a new API key? This will invalidate your current key.')) {
      return;
    }
    
    setIsSaving(true);
    
    try {
      // Simulate API call to generate new key
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      // In a real app, you would generate a new key via API
      // const response = await fetch('/api/settings/api-key', {
      //   method: 'POST'
      // });
      // const data = await response.json();
      
      // Mock new API key
      const newApiKey = 'sk_' + Math.random().toString(36).substring(2, 15);
      
      setSettings(prev => ({
        ...prev,
        apiKey: newApiKey
      }));
      
      setShowApiKey(true);
      showSuccessToast('New API key generated');
    } catch (error) {
      showErrorToast('Failed to generate new API key');
      console.error('API key generation error:', error);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="settings-page">
      <h1 className="page-title">Settings</h1>
      
      <div className="settings-container">
        <section className="settings-section">
          <h2 className="section-title">Preferences</h2>
          
          <div className="setting-item">
            <div className="setting-info">
              <h3 className="setting-title">Email Notifications</h3>
              <p className="setting-description">
                Receive email notifications about search results and document updates.
              </p>
            </div>
            <div className="setting-control">
              <label className="toggle-switch">
                <input
                  type="checkbox"
                  checked={settings.emailNotifications}
                  onChange={() => handleToggle('emailNotifications')}
                />
                <span className="toggle-slider"></span>
              </label>
            </div>
          </div>
          
          <div className="setting-item">
            <div className="setting-info">
              <h3 className="setting-title">Dark Mode</h3>
              <p className="setting-description">
                Use dark theme for the application interface.
              </p>
            </div>
            <div className="setting-control">
              <label className="toggle-switch">
                <input
                  type="checkbox"
                  checked={settings.darkMode}
                  onChange={() => handleToggle('darkMode')}
                />
                <span className="toggle-slider"></span>
              </label>
            </div>
          </div>
          
          <div className="setting-item">
            <div className="setting-info">
              <h3 className="setting-title">Save Search History</h3>
              <p className="setting-description">
                Save your recent searches for quick access.
              </p>
            </div>
            <div className="setting-control">
              <label className="toggle-switch">
                <input
                  type="checkbox"
                  checked={settings.searchHistory}
                  onChange={() => handleToggle('searchHistory')}
                />
                <span className="toggle-slider"></span>
              </label>
            </div>
          </div>
        </section>
        
        <section className="settings-section">
          <h2 className="section-title">API Access</h2>
          
          <div className="setting-item">
            <div className="setting-info">
              <h3 className="setting-title">API Key</h3>
              <p className="setting-description">
                Use this key to access the Semantic Search API programmatically.
              </p>
            </div>
            <div className="api-key-container">
              <div className="api-key-display">
                <input
                  type={showApiKey ? "text" : "password"}
                  value={settings.apiKey}
                  readOnly
                  className="api-key-input"
                />
                <button
                  className="button-secondary small"
                  onClick={() => setShowApiKey(!showApiKey)}
                >
                  {showApiKey ? "Hide" : "Show"}
                </button>
              </div>
              <button
                className="button-primary"
                onClick={generateNewApiKey}
                disabled={isSaving}
              >
                {isSaving ? "Generating..." : "Generate New Key"}
              </button>
            </div>
          </div>
        </section>
        
        <section className="settings-section">
          <h2 className="section-title">Account Security</h2>
          
          <div className="setting-item">
            <div className="setting-info">
              <h3 className="setting-title">Change Password</h3>
              <p className="setting-description">
                Update your account password.
              </p>
            </div>
            <div className="setting-control">
              <button className="button-secondary">
                Change Password
              </button>
            </div>
          </div>
          
          <div className="setting-item">
            <div className="setting-info">
              <h3 className="setting-title">Two-Factor Authentication</h3>
              <p className="setting-description">
                Add an extra layer of security to your account.
              </p>
            </div>
            <div className="setting-control">
              <button className="button-secondary">
                Enable 2FA
              </button>
            </div>
          </div>
        </section>
        
        <div className="settings-actions">
          <button
            className="button-primary"
            onClick={handleSaveSettings}
            disabled={isSaving}
          >
            {isSaving ? (
              <>
                <LoadingSpinner /> Saving...
              </>
            ) : (
              'Save Settings'
            )}
          </button>
        </div>
      </div>
    </div>
  );
};

export default SettingsPage;
