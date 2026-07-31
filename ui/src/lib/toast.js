/**
 * Toast helpers. Kept separate from the ToastContainer component so that file
 * only exports a component, which is what react-refresh requires for HMR.
 */
export const showToast = (message, type = 'info') => {
  window.dispatchEvent(new CustomEvent('toast', { detail: { message, type } }));
};

export const showSuccessToast = (message) => showToast(message, 'success');
export const showErrorToast = (message) => showToast(message, 'error');
export const showInfoToast = (message) => showToast(message, 'info');
