import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { configureStore } from '@reduxjs/toolkit';
import LoginForm from '../LoginForm';
import authReducer from '../../../store/slices/authSlice';

const theme = createTheme();

const createTestStore = (initialState = {}) => {
    return configureStore({
        reducer: {
            auth: authReducer,
        },
        preloadedState: {
            auth: {
                user: null,
                token: null,
                isAuthenticated: false,
                isLoading: false,
                error: null,
                ...initialState,
            },
        },
    });
};

const renderWithProviders = (component: React.ReactElement, initialState = {}) => {
    const store = createTestStore(initialState);
    return render(
        <Provider store={store}>
            <BrowserRouter>
                <ThemeProvider theme={theme}>
                    {component}
                </ThemeProvider>
            </BrowserRouter>
        </Provider>
    );
};

describe('LoginForm', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders login form with required fields', () => {
        renderWithProviders(<LoginForm />);

        expect(screen.getByText('ALYX Login')).toBeInTheDocument();
        expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
    });

    it('disables submit button when fields are empty', () => {
        renderWithProviders(<LoginForm />);

        const submitButton = screen.getByRole('button', { name: /sign in/i });
        expect(submitButton).toBeDisabled();
    });

    it('enables submit button when both fields are filled', () => {
        renderWithProviders(<LoginForm />);

        const usernameInput = screen.getByLabelText(/username/i);
        const passwordInput = screen.getByLabelText(/password/i);
        const submitButton = screen.getByRole('button', { name: /sign in/i });

        fireEvent.change(usernameInput, { target: { value: 'testuser' } });
        fireEvent.change(passwordInput, { target: { value: 'password123' } });

        expect(submitButton).not.toBeDisabled();
    });

    it('shows loading state during authentication', () => {
        renderWithProviders(<LoginForm />, { isLoading: true });

        expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('displays error message when authentication fails', () => {
        const errorMessage = 'Invalid credentials';
        renderWithProviders(<LoginForm />, { error: errorMessage });

        expect(screen.getByText(errorMessage)).toBeInTheDocument();
    });
});