import React from 'react';
import {
    Box,
    Grid,
    Card,
    CardContent,
    Typography,
    Paper,
    List,
    ListItem,
    ListItemText,
    Chip,
} from '@mui/material';
import {
    TrendingUp,
    Speed,
    Storage,
    Group,
} from '@mui/icons-material';

const Dashboard: React.FC = () => {
    // Mock data - in real implementation, this would come from Redux store
    const stats = [
        {
            title: 'Active Jobs',
            value: '24',
            icon: <TrendingUp />,
            color: '#1976d2',
        },
        {
            title: 'Processing Rate',
            value: '47.2k/s',
            icon: <Speed />,
            color: '#2e7d32',
        },
        {
            title: 'Data Processed',
            value: '2.3 PB',
            icon: <Storage />,
            color: '#ed6c02',
        },
        {
            title: 'Active Users',
            value: '156',
            icon: <Group />,
            color: '#9c27b0',
        },
    ];

    const recentJobs = [
        { id: 'job-001', name: 'Higgs Boson Analysis', status: 'running', progress: 75 },
        { id: 'job-002', name: 'Particle Tracking', status: 'completed', progress: 100 },
        { id: 'job-003', name: 'Event Reconstruction', status: 'queued', progress: 0 },
        { id: 'job-004', name: 'Cross-section Calculation', status: 'running', progress: 45 },
    ];

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'running':
                return 'primary';
            case 'completed':
                return 'success';
            case 'queued':
                return 'default';
            case 'failed':
                return 'error';
            default:
                return 'default';
        }
    };

    return (
        <Box>
            <Typography variant="h4" gutterBottom>
                Dashboard
            </Typography>
            <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
                Welcome to ALYX - Distributed Analysis Orchestrator
            </Typography>

            {/* Statistics Cards */}
            <Grid container spacing={3} sx={{ mb: 4 }}>
                {stats.map((stat, index) => (
                    <Grid item xs={12} sm={6} md={3} key={index}>
                        <Card>
                            <CardContent>
                                <Box display="flex" alignItems="center" justifyContent="space-between">
                                    <Box>
                                        <Typography color="text.secondary" gutterBottom>
                                            {stat.title}
                                        </Typography>
                                        <Typography variant="h4" component="div">
                                            {stat.value}
                                        </Typography>
                                    </Box>
                                    <Box sx={{ color: stat.color }}>
                                        {stat.icon}
                                    </Box>
                                </Box>
                            </CardContent>
                        </Card>
                    </Grid>
                ))}
            </Grid>

            <Grid container spacing={3}>
                {/* Recent Jobs */}
                <Grid item xs={12} md={6}>
                    <Paper sx={{ p: 2 }}>
                        <Typography variant="h6" gutterBottom>
                            Recent Jobs
                        </Typography>
                        <List>
                            {recentJobs.map((job) => (
                                <ListItem key={job.id} divider>
                                    <ListItemText
                                        primary={job.name}
                                        secondary={`Job ID: ${job.id}`}
                                    />
                                    <Box display="flex" alignItems="center" gap={1}>
                                        <Typography variant="body2" color="text.secondary">
                                            {job.progress}%
                                        </Typography>
                                        <Chip
                                            label={job.status}
                                            color={getStatusColor(job.status) as any}
                                            size="small"
                                        />
                                    </Box>
                                </ListItem>
                            ))}
                        </List>
                    </Paper>
                </Grid>

                {/* System Status */}
                <Grid item xs={12} md={6}>
                    <Paper sx={{ p: 2 }}>
                        <Typography variant="h6" gutterBottom>
                            System Status
                        </Typography>
                        <Box sx={{ mt: 2 }}>
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                                All systems operational
                            </Typography>
                            <Box display="flex" flexDirection="column" gap={2}>
                                <Box display="flex" justifyContent="space-between" alignItems="center">
                                    <Typography variant="body2">Job Scheduler</Typography>
                                    <Chip label="Online" color="success" size="small" />
                                </Box>
                                <Box display="flex" justifyContent="space-between" alignItems="center">
                                    <Typography variant="body2">Data Router</Typography>
                                    <Chip label="Online" color="success" size="small" />
                                </Box>
                                <Box display="flex" justifyContent="space-between" alignItems="center">
                                    <Typography variant="body2">Resource Optimizer</Typography>
                                    <Chip label="Online" color="success" size="small" />
                                </Box>
                                <Box display="flex" justifyContent="space-between" alignItems="center">
                                    <Typography variant="body2">Database</Typography>
                                    <Chip label="Online" color="success" size="small" />
                                </Box>
                            </Box>
                        </Box>
                    </Paper>
                </Grid>
            </Grid>
        </Box>
    );
};

export default Dashboard;