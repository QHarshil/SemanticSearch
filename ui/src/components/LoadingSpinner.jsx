import React from 'react';

const LoadingSpinner = ({ fullScreen = false }) => {
  const spinnerClasses = fullScreen 
    ? 'loading-spinner-fullscreen' 
    : 'loading-spinner';

  return (
    <div className={spinnerClasses}>
      <div className="spinner">
        <div className="bounce1"></div>
        <div className="bounce2"></div>
        <div className="bounce3"></div>
      </div>
      <p className="loading-text">Loading...</p>
    </div>
  );
};

export default LoadingSpinner;
