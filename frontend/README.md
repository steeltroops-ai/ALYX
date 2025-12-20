# ALYX Frontend

React-based frontend for the ALYX Distributed Analysis Orchestrator.

## Features Implemented

### ✅ Task 7: React Frontend Foundation

- **React 18+ with TypeScript**: Modern React setup with full TypeScript support
- **Material-UI Component Library**: Complete UI component system with dark theme
- **React Router**: Client-side routing with protected routes
- **Redux Toolkit**: State management for authentication and application state
- **WebSocket Integration**: Real-time communication with Socket.io
- **Authentication System**: Complete user authentication and authorization
- **Responsive Layout**: Mobile-friendly navigation and layout

## Architecture

### Components Structure

```
src/
├── components/
│   ├── auth/
│   │   ├── LoginForm.tsx          # User login interface
│   │   ├── ProtectedRoute.tsx     # Route protection wrapper
│   │   └── UserProfile.tsx       # User profile dropdown
│   └── layout/
│       └── AppLayout.tsx          # Main application layout
├── pages/
│   ├── Dashboard.tsx              # Main dashboard
│   ├── Jobs.tsx                   # Job management (placeholder)
│   ├── Visualization.tsx          # 3D visualization (placeholder)
│   ├── QueryBuilder.tsx          # Query builder (placeholder)
│   ├── Collaboration.tsx         # Collaboration (placeholder)
│   ├── Notebooks.tsx             # Analysis notebooks (placeholder)
│   └── Settings.tsx               # Application settings (placeholder)
├── services/
│   └── websocket.ts               # WebSocket service
├── store/
│   ├── store.ts                   # Redux store configuration
│   └── slices/
│       └── authSlice.ts           # Authentication state management
└── types/
    └── auth.ts                    # Authentication type definitions
```

### State Management

- **Redux Toolkit** for predictable state management
- **Authentication slice** with async thunks for login/logout
- **WebSocket integration** with automatic reconnection
- **Token-based authentication** with localStorage persistence

### Routing

- **Protected routes** requiring authentication
- **Role-based access control** with permission checking
- **Automatic redirects** to login when unauthenticated
- **Navigation state preservation** during authentication flow

## Development

### Available Scripts

```bash
# Start development server
npm run dev

# Run tests
npm test

# Run tests in watch mode
npm run test:watch

# Build for production
npm run build

# Preview production build
npm run preview
```

### Testing

- **Vitest** for unit testing
- **React Testing Library** for component testing
- **Fast-check** for property-based testing (ready for future use)
- **Mock services** for WebSocket and API calls

### API Integration

The frontend is configured to proxy API calls to the backend:

- `/api/*` → `http://localhost:8080` (Backend services)
- `/ws/*` → `http://localhost:8081` (WebSocket server)

## Requirements Validation

This implementation satisfies the following requirements:

- **Requirement 1.1**: Web interface for job submission (foundation ready)
- **Requirement 2.5**: Real-time visualization updates via WebSocket
- **Requirement 5.1**: Real-time collaboration synchronization (WebSocket ready)

## Next Steps

The following features are ready for implementation in future tasks:

1. **3D Visualization Engine** (Task 8)
2. **Visual Query Builder** (Task 9)
3. **Real-time Collaboration** (Task 10)
4. **Analysis Notebooks** (Task 11)

Each placeholder page provides the foundation for these advanced features.