import React from 'react';
import { Typography, Box } from '@mui/material';

const Settings: React.FC = () => {
    return (
        <Box>
            <Typography variant="h4" gutterBottom>
                Settings
            </Typography>
            <Typography variant="body1" color="text.secondary">
                Application settings will be implemented in future tasks.
            </Typography>
        </Box>
    );
};

export default Settings;