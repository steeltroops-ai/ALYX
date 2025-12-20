import React, { useState, useEffect } from 'react';
import {
    Box,
    Card,
    CardContent,
    TextField,
    Button,
    Typography,
    Alert,
    CircularProgress,
    Link,
    Divider,
} from '@mui/material';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate, useLocation } from 'react-router-dom';
import { loginUser, clearError } from '../../store/slices/authSlice';
import { RootState, AppDispatch } from '../../store/store';
import { LoginCredentials } from '../../types/auth';

interface LoginFormProps {
    onSwitchToRegister?: () => void;
}

const LoginForm: React.FC<LoginFormProps> = ({ onSwitchToRegister }) => {
    const dispatch = useDispatch<AppDispatch>();
    const navigate = useNavigate();
    const location = useLocation();
    const { isLoading, error, isAuthenticated } = useSelector((state: RootState) => state.auth);

    const [credentials, setCredentials] = useState<LoginCredentials>({
        username: '',
        password: '',
    });

    // Redirect to dashboard after successful login
    useEffect(() => {
        if (isAuthenticated) {
            const from = (location.state as any)?.from?.pathname || '/';
            navigate(from, { replace: true });
        }
    }, [isAuthenticated, navigate, location]);

    const handleInputChange = (field: keyof LoginCredentials) => (
        event: React.ChangeEvent<HTMLInputElement>
    ) => {
        setCredentials(prev => ({
            ...prev,
            [field]: event.target.value,
        }));

        // Clear error when user starts typing
        if (error) {
            dispatch(clearError());
        }
    };

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();

        if (!credentials.username.trim() || !credentials.password.trim()) {
            return;
        }

        dispatch(loginUser(credentials));
    };

    return (
        <Box
            display="flex"
            justifyContent="center"
            alignItems="center"
            minHeight="100vh"
            bgcolor="background.default"
        >
            <Card sx={{ maxWidth: 400, width: '100%', mx: 2 }}>
                <CardContent sx={{ p: 4 }}>
                    <Typography variant="h4" component="h1" gutterBottom align="center">
                        ALYX Login
                    </Typography>
                    <Typography variant="body2" color="text.secondary" align="center" sx={{ mb: 3 }}>
                        Distributed Analysis Orchestrator
                    </Typography>

                    {error && (
                        <Alert severity="error" sx={{ mb: 2 }}>
                            {error}
                        </Alert>
                    )}

                    <Box component="form" onSubmit={handleSubmit}>
                        <TextField
                            fullWidth
                            label="Email"
                            type="email"
                            variant="outlined"
                            margin="normal"
                            value={credentials.username}
                            onChange={handleInputChange('username')}
                            disabled={isLoading}
                            required
                            autoComplete="email"
                            autoFocus
                            placeholder="Enter your email address"
                        />
                        <TextField
                            fullWidth
                            label="Password"
                            type="password"
                            variant="outlined"
                            margin="normal"
                            value={credentials.password}
                            onChange={handleInputChange('password')}
                            disabled={isLoading}
                            required
                            autoComplete="current-password"
                        />
                        <Button
                            type="submit"
                            fullWidth
                            variant="contained"
                            size="large"
                            disabled={isLoading || !credentials.username.trim() || !credentials.password.trim()}
                            sx={{ mt: 3, mb: 2 }}
                        >
                            {isLoading ? (
                                <CircularProgress size={24} color="inherit" />
                            ) : (
                                'Sign In'
                            )}
                        </Button>

                        {/* Demo Accounts Section */}
                        <Divider sx={{ my: 2 }}>
                            <Typography variant="body2" color="text.secondary">
                                Demo Accounts
                            </Typography>
                        </Divider>

                        <Box sx={{ mb: 2 }}>
                            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                                Try these demo accounts:
                            </Typography>
                            <Typography variant="caption" display="block" sx={{ fontFamily: 'monospace', mb: 0.5 }}>
                                <strong>Admin:</strong> admin@alyx.physics.org / admin123
                            </Typography>
                            <Typography variant="caption" display="block" sx={{ fontFamily: 'monospace', mb: 0.5 }}>
                                <strong>Physicist:</strong> physicist@alyx.physics.org / physicist123
                            </Typography>
                            <Typography variant="caption" display="block" sx={{ fontFamily: 'monospace' }}>
                                <strong>Analyst:</strong> analyst@alyx.physics.org / analyst123
                            </Typography>
                        </Box>

                        {onSwitchToRegister && (
                            <Box textAlign="center">
                                <Typography variant="body2">
                                    Don't have an account?{' '}
                                    <Link
                                        component="button"
                                        type="button"
                                        onClick={onSwitchToRegister}
                                        sx={{ textDecoration: 'none' }}
                                    >
                                        Create one here
                                    </Link>
                                </Typography>
                            </Box>
                        )}
                    </Box>
                </CardContent>
            </Card>
        </Box>
    );
};

export default LoginForm;