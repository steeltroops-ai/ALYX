import React, { useState } from 'react';
import {
    Box,
    Avatar,
    Menu,
    MenuItem,
    IconButton,
    Typography,
    Divider,
    ListItemIcon,
    ListItemText,
} from '@mui/material';
import {
    Settings,
    Logout,
    Person,
} from '@mui/icons-material';
import { useSelector, useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { RootState, AppDispatch } from '../../store/store';
import { logoutUser } from '../../store/slices/authSlice';

const UserProfile: React.FC = () => {
    const dispatch = useDispatch<AppDispatch>();
    const navigate = useNavigate();
    const { user } = useSelector((state: RootState) => state.auth);
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

    const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const handleMenuClose = () => {
        setAnchorEl(null);
    };

    const handleProfile = () => {
        handleMenuClose();
        navigate('/profile');
    };

    const handleSettings = () => {
        handleMenuClose();
        navigate('/settings');
    };

    const handleLogout = () => {
        handleMenuClose();
        dispatch(logoutUser());
        navigate('/login');
    };

    if (!user) {
        return null;
    }

    const getInitials = (name: string) => {
        return name
            .split(' ')
            .map(part => part.charAt(0))
            .join('')
            .toUpperCase()
            .slice(0, 2);
    };

    const getRoleColor = (role: string) => {
        switch (role) {
            case 'admin':
                return '#f44336';
            case 'physicist':
                return '#2196f3';
            case 'collaborator':
                return '#4caf50';
            default:
                return '#9e9e9e';
        }
    };

    return (
        <Box>
            <IconButton
                size="large"
                edge="end"
                aria-label="account of current user"
                aria-controls="user-menu"
                aria-haspopup="true"
                onClick={handleMenuOpen}
                color="inherit"
            >
                <Avatar
                    sx={{
                        bgcolor: getRoleColor(user.role),
                        width: 32,
                        height: 32,
                        fontSize: '0.875rem',
                    }}
                >
                    {getInitials(user.username)}
                </Avatar>
            </IconButton>

            <Menu
                id="user-menu"
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={handleMenuClose}
                onClick={handleMenuClose}
                PaperProps={{
                    elevation: 0,
                    sx: {
                        overflow: 'visible',
                        filter: 'drop-shadow(0px 2px 8px rgba(0,0,0,0.32))',
                        mt: 1.5,
                        minWidth: 200,
                        '& .MuiAvatar-root': {
                            width: 32,
                            height: 32,
                            ml: -0.5,
                            mr: 1,
                        },
                        '&:before': {
                            content: '""',
                            display: 'block',
                            position: 'absolute',
                            top: 0,
                            right: 14,
                            width: 10,
                            height: 10,
                            bgcolor: 'background.paper',
                            transform: 'translateY(-50%) rotate(45deg)',
                            zIndex: 0,
                        },
                    },
                }}
                transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
            >
                <Box sx={{ px: 2, py: 1 }}>
                    <Typography variant="subtitle1" fontWeight="medium">
                        {user.username}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {user.email}
                    </Typography>
                    <Typography
                        variant="caption"
                        sx={{
                            color: getRoleColor(user.role),
                            textTransform: 'capitalize',
                            fontWeight: 'medium',
                        }}
                    >
                        {user.role}
                    </Typography>
                </Box>

                <Divider />

                <MenuItem onClick={handleProfile}>
                    <ListItemIcon>
                        <Person fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>Profile</ListItemText>
                </MenuItem>

                <MenuItem onClick={handleSettings}>
                    <ListItemIcon>
                        <Settings fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>Settings</ListItemText>
                </MenuItem>

                <Divider />

                <MenuItem onClick={handleLogout}>
                    <ListItemIcon>
                        <Logout fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>Logout</ListItemText>
                </MenuItem>
            </Menu>
        </Box>
    );
};

export default UserProfile;