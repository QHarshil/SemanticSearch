import { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';

const Toast = ({ message, type, onClose }) => {
  const toastClasses = `toast toast-${type}`;
  
  useEffect(() => {
    const timer = setTimeout(() => {
      onClose();
    }, 5000);
    
    return () => clearTimeout(timer);
  }, [onClose]);
  
  return (
    <div className={toastClasses}>
      <div className="toast-content">
        <span className="toast-message">{message}</span>
      </div>
      <button className="toast-close" onClick={onClose}>×</button>
    </div>
  );
};

export const ToastContainer = () => {
  const [toasts, setToasts] = useState([]);
  const toastRoot = useRef(null);
  
  useEffect(() => {
        if (!toastRoot.current) {
      const div = document.createElement('div');
      div.className = 'toast-container';
      document.body.appendChild(div);
      toastRoot.current = div;
    }
    
        const handleToast = (event) => {
      const { message, type = 'info' } = event.detail;
      addToast(message, type);
    };
    
    window.addEventListener('toast', handleToast);
    
    return () => {
      window.removeEventListener('toast', handleToast);
      if (toastRoot.current) {
        document.body.removeChild(toastRoot.current);
      }
    };
  }, []);
  
  const addToast = (message, type) => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, message, type }]);
  };
  
  const removeToast = (id) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };
  
  if (!toastRoot.current) return null;
  
  return createPortal(
    <>
      {toasts.map(toast => (
        <Toast
          key={toast.id}
          message={toast.message}
          type={toast.type}
          onClose={() => removeToast(toast.id)}
        />
      ))}
    </>,
    toastRoot.current
  );
};
