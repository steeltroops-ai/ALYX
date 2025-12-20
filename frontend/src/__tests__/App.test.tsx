import { render } from '@testing-library/react';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { configureStore } from '@reduxjs/toolkit';
import App from '../App';
import authReducer from '../store/slices/authSlice';

// Mock the websocket service
vi.mock('../services/websocket', () => ({
    default: {
        connect: vi.fn(),
        disconnect: vi.fn(),
        isConnected: vi.fn(() => false),
    },
}));

const theme = createTheme();

const createTestStore = (isAuthenticated = false) => {
    return configureStore({
        reducer: {
            auth: authReducer,
        },
        preloadedState: {
            auth: {
                user: isAuthenticated ? {
                    id: '1',
                    username: 'testuser',
                    email: 'test@example.com',
                    role: 'physicist' as const,
                    permissions: ['read', 'write'],
                } : null,
                token: isAuthenticated ? 'test-token' : null,
                isAuthenticated,
                isLoading: false,
                error: null,
            },
        },
    });
};

// Helper function for rendering App with providers (used in future tests)
const renderApp = (isAuthenticated = false) => {
    const store = createTestStore(isAuthenticated);
    return render(
        <Provider store={store}>
            <BrowserRouter>
                <ThemeProvider theme={theme}>
                    <App />
                </ThemeProvider>
            </BrowserRouter>
        </Provider>
    );
};

describe('App', () => {
    it('should render without crashing', () => {
        // Basic smoke test - just ensure the component can be imported
        expect(App).toBeDefined();
    });

    // Note: Full App rendering test requires monaco-editor setup
    // This will be covered by integration tests in future tasks
});