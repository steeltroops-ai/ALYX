import React, { useState } from 'react';
import {
    Box,
    Card,
    CardContent,
    TextField,
    Button,
    Typography,
    Alert,
    CircularProgress,
    MenuItem,
    Link,
} from '@mui/material';

interface RegisterCredentials {
    email: string;
    password: string;
    confirmPassword: string;
    firstName: string;
    lastName: string;
    role: string;
    organization: string;
}

interface RegisterFormProps {
    onSwitchToLogin: () => void;
}

const RegisterForm: React.FC<RegisterFormProps> = ({ onSwitchToLogin }) => {
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);

    const [credentials, setCredentials] = useState<RegisterCredentials>({
        email: '',
        password: '',
        confirmPassword: '',
        firstName: '',
        lastName: '',
        role: 'ANALYST',
        organization: '',
    });

    const roles = [
        { value: 'ANALYST', label: 'Analyst - Read-only access to analysis results' },
        { value: 'PHYSICIST', label: 'Physicist - Full analysis access and job submission' },
    ];

    const organizations = [
        'CERN',
        'FERMILAB',
        'DESY',
        'KEK',
        'SLAC',
        'TRIUMF',
        'Other',
    ];

    const handleInputChange = (field: keyof RegisterCredentials) => (
        event: React.ChangeEvent<HTMLInputElement>
    ) => {
        setCredentials(prev => ({
            ...prev,
            [field]: event.target.value,
        }));

        // Clear errors when user starts typing
        if (error) {
            setError(null);
        }
    };

    const validateForm = (): string | null => {
        if (!credentials.email.trim()) return 'Email is required';
        if (!credentials.email.includes('@')) return 'Please enter a valid email';
        if (!credentials.password.trim()) return 'Password is required';
        if (credentials.password.length < 8) return 'Password must be at least 8 characters';
        if (credentials.password !== credentials.confirmPassword) return 'Passwords do not match';
        if (!credentials.firstName.trim()) return 'First name is required';
        if (!credentials.lastName.trim()) return 'Last name is required';
        if (!credentials.organization.trim()) return 'Organization is required';
        return null;
    };

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();

        const validationError = validateForm();
        if (validationError) {
            setError(validationError);
            return;
        }

        setIsLoading(true);
        setError(null);

        try {
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    email: credentials.email,
                    password: credentials.password,
                    firstName: credentials.firstName,
                    lastName: credentials.lastName,
                    role: credentials.role,
                    organization: credentials.organization,
                }),
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.error || 'Registration failed');
            }

            setSuccess('Registration successful! You can now log in with your credentials.');
            // Clear form
            setCredentials({
                email: '',
                password: '',
                confirmPassword: '',
                firstName: '',
                lastName: '',
                role: 'ANALYST',
                organization: '',
            });

        } catch (error) {
            setError(error instanceof Error ? error.message : 'Registration failed');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Box
            display="flex"
            justifyContent="center"
            alignItems="center"
            minHeight="100vh"
            bgcolor="background.default"
        >
            <Card sx={{ maxWidth: 500, width: '100%', mx: 2 }}>
                <CardContent sx={{ p: 4 }}>
                    <Typography variant="h4" component="h1" gutterBottom align="center">
                        Join ALYX
                    </Typography>
                    <Typography variant="body2" color="text.secondary" align="center" sx={{ mb: 3 }}>
                        Create your account for the Distributed Analysis Orchestrator
                    </Typography>

                    {error && (
                        <Alert severity="error" sx={{ mb: 2 }}>
                            {error}
                        </Alert>
                    )}

                    {success && (
                        <Alert severity="success" sx={{ mb: 2 }}>
                            {success}
                        </Alert>
                    )}

                    <Box component="form" onSubmit={handleSubmit}>
                        <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
                            <TextField
                                fullWidth
                                label="First Name"
                                variant="outlined"
                                value={credentials.firstName}
                                onChange={handleInputChange('firstName')}
                                disabled={isLoading}
                                required
                            />
                            <TextField
                                fullWidth
                                label="Last Name"
                                variant="outlined"
                                value={credentials.lastName}
                                onChange={handleInputChange('lastName')}
                                disabled={isLoading}
                                required
                            />
                        </Box>

                        <TextField
                            fullWidth
                            label="Email"
                            type="email"
                            variant="outlined"
                            margin="normal"
                            value={credentials.email}
                            onChange={handleInputChange('email')}
                            disabled={isLoading}
                            required
                            autoComplete="email"
                        />

                        <TextField
                            fullWidth
                            label="Organization"
                            select
                            variant="outlined"
                            margin="normal"
                            value={credentials.organization}
                            onChange={handleInputChange('organization')}
                            disabled={isLoading}
                            required
                        >
                            {organizations.map((org) => (
                                <MenuItem key={org} value={org}>
                                    {org}
                                </MenuItem>
                            ))}
                        </TextField>

                        <TextField
                            fullWidth
                            label="Role"
                            select
                            variant="outlined"
                            margin="normal"
                            value={credentials.role}
                            onChange={handleInputChange('role')}
                            disabled={isLoading}
                            required
                            helperText="Select your intended role in the system"
                        >
                            {roles.map((role) => (
                                <MenuItem key={role.value} value={role.value}>
                                    {role.label}
                                </MenuItem>
                            ))}
                        </TextField>

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
                            autoComplete="new-password"
                            helperText="Minimum 8 characters"
                        />

                        <TextField
                            fullWidth
                            label="Confirm Password"
                            type="password"
                            variant="outlined"
                            margin="normal"
                            value={credentials.confirmPassword}
                            onChange={handleInputChange('confirmPassword')}
                            disabled={isLoading}
                            required
                            autoComplete="new-password"
                        />

                        <Button
                            type="submit"
                            fullWidth
                            variant="contained"
                            size="large"
                            disabled={isLoading}
                            sx={{ mt: 3, mb: 2 }}
                        >
                            {isLoading ? (
                                <CircularProgress size={24} color="inherit" />
                            ) : (
                                'Create Account'
                            )}
                        </Button>

                        <Box textAlign="center">
                            <Typography variant="body2">
                                Already have an account?{' '}
                                <Link
                                    component="button"
                                    type="button"
                                    onClick={onSwitchToLogin}
                                    sx={{ textDecoration: 'none' }}
                                >
                                    Sign in here
                                </Link>
                            </Typography>
                        </Box>
                    </Box>
                </CardContent>
            </Card>
        </Box>
    );
};

export default RegisterForm;