# UI Documentation

## Overview
This document provides documentation for the Semantic Search Java UI, a React-based frontend for the semantic search microservice. The UI is designed to be responsive, accessible, and user-friendly, with a focus on providing an intuitive interface for semantic search operations.

## Architecture

### Component Structure
The UI follows a modular component architecture with the following main directories:
- `src/components/`: Reusable UI components
- `src/pages/`: Page-level components
- `src/context/`: React context providers
- `src/assets/`: Static assets like images and icons

### State Management
- React Context API for global state (authentication, theme)
- Local component state for UI-specific state
- API integration for data fetching and mutations

### Routing
- React Router v6 for client-side routing
- Protected routes for authenticated sections
- Redirect handling for unauthorized access

## Key Features

### Semantic Search
The search functionality allows users to find documents based on meaning rather than just keywords:
- Query input with advanced options
- Minimum score threshold adjustment
- Maximum results limit
- Search history tracking
- Real-time results display with relevance scores

### Document Management
Users can manage documents in the system:
- Add new documents with title and content
- View existing documents
- Delete documents (authenticated users only)
- Preview document content

### User Authentication
The application includes a complete authentication system:
- Login/logout functionality
- Protected routes for authenticated users
- User profile management
- Role-based access control

### Settings and Preferences
Users can customize their experience:
- Toggle dark/light mode
- Manage notification preferences
- Control search history settings
- API key management for developers

## Component Documentation

### Core Components

#### Navbar
The main navigation component that appears on all pages.
- Props: `darkMode`, `toggleDarkMode`
- State: `menuOpen`, `dropdownOpen`
- Features: Responsive design, user dropdown, theme toggle

#### Footer
The footer component that appears on all pages.
- Features: Navigation links, resources, copyright information

#### SearchForm
The main search interface component.
- State: `query`, `minScore`, `maxResults`, `results`
- Features: Advanced options, search history, results display

#### DocumentForm
Form for adding new documents to the system.
- State: `title`, `content`, `isSubmitting`
- Features: Form validation, character count, submission handling

#### LoginForm
Authentication form for user login.
- State: `username`, `password`, `isSubmitting`
- Features: Form validation, error handling, redirect after login

#### ErrorBoundary
React error boundary component to catch and display UI errors.
- Features: Fallback UI, error details, refresh option

#### Toast
Notification system for user feedback.
- Features: Success, error, warning, and info notifications
- Auto-dismiss functionality

### Context Providers

#### AuthContext
Manages authentication state across the application.
- Provides: `user`, `isAuthenticated`, `login`, `logout`
- Features: Token management, auth status check, secure routes

### Pages

#### HomePage
Landing page with feature overview and call-to-action.
- Features: Hero section, feature cards, how-it-works section

#### SearchPage
Main search interface page.
- Features: Search form, API status check, results display

#### DocumentsPage
Document management interface.
- Features: Document list, add document form, document preview

#### AboutPage
Information about the application and technology.
- Features: Overview, how it works, technology stack, use cases

#### ProfilePage
User profile management.
- Features: Profile information, edit functionality

#### SettingsPage
User preferences and settings.
- Features: Theme toggle, notification settings, API key management

## Styling

The UI uses a combination of custom CSS and utility classes:
- Base styles in `index.css`
- Component-specific styles in `App.css`
- Responsive design for all screen sizes
- Dark/light theme support

## API Integration

The UI integrates with the following backend endpoints:

### Search API
- `POST /api/search`: Perform semantic search
- `GET /api/search/health`: Check search API status

### Document API
- `GET /api/documents`: Get all documents
- `POST /api/documents`: Add new document
- `GET /api/documents/{id}`: Get document by ID
- `DELETE /api/documents/{id}`: Delete document

### Authentication API
- `POST /api/auth/login`: User login
- `POST /api/auth/logout`: User logout
- `GET /api/auth/status`: Check authentication status

## Browser Compatibility

The UI is compatible with:
- Chrome (latest 2 versions)
- Firefox (latest 2 versions)
- Safari (latest 2 versions)
- Edge (latest 2 versions)
- Mobile browsers (iOS Safari, Android Chrome)

## Accessibility

The UI follows WCAG 2.1 AA guidelines:
- Proper heading hierarchy
- ARIA attributes where necessary
- Keyboard navigation support
- Color contrast compliance
- Screen reader compatibility

## Performance Optimization

- Code splitting for reduced bundle size
- Lazy loading of components
- Optimized asset loading
- Efficient state management
- Debounced search inputs

## Deployment

The UI is built and served as static files from the Spring Boot application:
- Build process compiles to `src/main/resources/static`
- No separate deployment needed
- Single origin for API and UI

## Development

### Prerequisites
- Node.js 16+
- npm or yarn

### Setup
1. Navigate to the `ui` directory
2. Run `npm install` or `yarn install`
3. Run `npm start` or `yarn start` for development server

### Building
- Run `npm run build` or `yarn build`
- Output is automatically placed in the correct resources directory

### Testing
- Run `npm test` or `yarn test` for unit tests
- Run `npm run e2e` or `yarn e2e` for end-to-end tests

## Troubleshooting

### Common Issues

#### API Connection Problems
- Check that the backend is running
- Verify API endpoints in network tab
- Check for CORS issues in console

#### Authentication Issues
- Clear browser cookies and local storage
- Check token expiration
- Verify credentials

#### UI Rendering Problems
- Check browser console for errors
- Verify React version compatibility
- Clear browser cache

## Future Enhancements

- Advanced filtering options
- Saved searches
- Document tagging and categorization
- Real-time collaboration
- Mobile app version
