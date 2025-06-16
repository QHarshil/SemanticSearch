import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { showErrorToast, showSuccessToast } from './Toast';
import LoadingSpinner from './LoadingSpinner';

const LoginForm = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // Get the redirect path from location state or default to home
  const from = location.state?.from?.pathname || '/';

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!username.trim() || !password.trim()) {
      showErrorToast('Please enter both username and password');
      return;
    }
    
    setIsSubmitting(true);
    
    try {
      const result = await login(username, password);
      
      if (result.success) {
        showSuccessToast('Login successful');
        // Redirect to the page they were trying to access or home
        navigate(from, { replace: true });
      } else {
        showErrorToast(result.error || 'Login failed');
      }
    } catch (error) {
      showErrorToast('An unexpected error occurred');
      console.error('Login error:', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="login-form-container">
      <h2>Login</h2>
      
      <form onSubmit={handleSubmit} className="login-form">
        <div className="form-group">
          <label htmlFor="username" className="form-label">Username</label>
          <input
            type="text"
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="form-input"
            placeholder="Enter your username"
            disabled={isSubmitting}
          />
        </div>
        
        <div className="form-group">
          <label htmlFor="password" className="form-label">Password</label>
          <input
            type="password"
            id="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="form-input"
            placeholder="Enter your password"
            disabled={isSubmitting}
          />
        </div>
        
        <div className="form-actions">
          <button 
            type="submit" 
            className="button-primary"
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <LoadingSpinner /> Logging in...
              </>
            ) : (
              'Login'
            )}
          </button>
        </div>
      </form>
      
      <div className="login-help">
        <p>
          Default credentials for demo: <br />
          Username: <strong>admin</strong> <br />
          Password: <strong>admin</strong>
        </p>
      </div>
    </div>
  );
};

export default LoginForm;
