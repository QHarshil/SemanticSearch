import React from 'react';
import { Routes, Route } from 'react-router-dom';
import LoginForm from '../components/LoginForm';
import ProtectedRoute from '../components/ProtectedRoute';
import HomePage from './HomePage';
import SearchPage from './SearchPage';
import DocumentsPage from './DocumentsPage';
import AboutPage from './AboutPage';
import ProfilePage from './ProfilePage';
import SettingsPage from './SettingsPage';
import NotFoundPage from './NotFoundPage';

const AppRoutes = () => {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/search" element={<SearchPage />} />
      <Route path="/documents" element={<DocumentsPage />} />
      <Route path="/about" element={<AboutPage />} />
      <Route path="/login" element={<LoginForm />} />
      
      {/* Protected routes */}
      <Route path="/profile" element={
        <ProtectedRoute>
          <ProfilePage />
        </ProtectedRoute>
      } />
      <Route path="/settings" element={
        <ProtectedRoute>
          <SettingsPage />
        </ProtectedRoute>
      } />
      
      {/* 404 route */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
};

export default AppRoutes;
