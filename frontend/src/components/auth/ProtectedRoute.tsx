import React, { useEffect } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { Navigate, useLocation } from 'react-router-dom';
import { Box, CircularProgress, Typography } from '@mui/material';
import { RootState, AppDispatch } from '../../store/store';
import { verifyToken } from '../../store/slices/authSlice';
import websocketService from '../../services/websocket';

interface ProtectedRouteProps {
    children: React.ReactNode;
    requiredPermissions?: string[];
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
    children,
    requiredPermissions = []
}) => {
    const dispatch = useDispatch<AppDispatch>();
    const location = useLocation();
    const { isAuthenticated, isLoading, token, user } = useSelector(
        (state: RootState) => state.auth
    );

    useEffect(() => {
        // If we have a token but no user, verify the token
        if (token && !user && !isLoading) {
            dispatch(verifyToken(token));
        }
    }, [dispatch, token, user, isLoading]);

    useEffect(() => {
        // Connect to WebSocket when authenticated
        if (isAuthenticated && token) {
            websocketService.connect(token);
        } else {
            websocketService.disconnect();
        }

        return () => {
            // Cleanup on unmount
            websocketService.disconnect();
        };
    }, [isAuthenticated, token]);

    // Show loading spinner while verifying token
    if (isLoading || (token && !user)) {
        return (
            <Box
                display="flex"
                flexDirection="column"
                justifyContent="center"
                alignItems="center"
                minHeight="100vh"
            >
                <CircularProgress size={60} />
                <Typography variant="h6" sx={{ mt: 2 }}>
                    Verifying authentication...
                </Typography>
            </Box>
        );
    }

    // Redirect to login if not authenticated
    if (!isAuthenticated) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    // Check permissions if required
    if (requiredPermissions.length > 0 && user) {
        const hasPermission = requiredPermissions.every(permission =>
            user.permissions.includes(permission)
        );

        if (!hasPermission) {
            return (
                <Box
                    display="flex"
                    flexDirection="column"
                    justifyContent="center"
                    alignItems="center"
                    minHeight="100vh"
                >
                    <Typography variant="h4" color="error" gutterBottom>
                        Access Denied
                    </Typography>
                    <Typography variant="body1" color="text.secondary">
                        You don't have permission to access this resource.
                    </Typography>
                </Box>
            );
        }
    }

    return <>{children}</>;
};

export default ProtectedRoute;