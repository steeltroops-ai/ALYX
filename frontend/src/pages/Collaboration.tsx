import React, { useState, useEffect, useRef } from 'react';
import {
    Typography,
    Box,
    Paper,
    Grid,
    Card,
    CardContent,
    TextField,
    Chip,
    Avatar,
    List,
    ListItem,
    ListItemAvatar,
    ListItemText,
    Slider,
    FormControl,
    InputLabel,
    Select,
    MenuItem
} from '@mui/material';
import { Group, Person, Visibility } from '@mui/icons-material';
import websocketService from '../services/websocket';

interface Participant {
    userId: string;
    username: string;
    cursor?: { x: number; y: number; elementId?: string };
    selection?: { startLine: number; startColumn: number; endLine: number; endColumn: number; elementId: string };
    isActive: boolean;
    joinedAt: number;
}

interface SharedState {
    analysisParameters: {
        energyRange: { min: number; max: number };
        particleTypes: string[];
        detectorRegions: string[];
    };
    queryState: {
        filters: Array<{ field: string; operator: string; value: any }>;
        sortBy: string;
        limit: number;
    };
    visualizationState: {
        cameraPosition: { x: number; y: number; z: number };
        selectedEvents: string[];
        displayMode: string;
    };
    version: number;
}

const Collaboration: React.FC = () => {
    const [sessionId] = useState('demo-session-' + Date.now());
    const [participants, setParticipants] = useState<Participant[]>([]);
    const [sharedState, setSharedState] = useState<SharedState>({
        analysisParameters: {
            energyRange: { min: 1.0, max: 100.0 },
            particleTypes: ['electron', 'muon'],
            detectorRegions: ['barrel', 'endcap']
        },
        queryState: {
            filters: [],
            sortBy: 'timestamp',
            limit: 1000
        },
        visualizationState: {
            cameraPosition: { x: 0, y: 0, z: 10 },
            selectedEvents: [],
            displayMode: '3d'
        },
        version: 1
    });
    const [isConnected, setIsConnected] = useState(false);
    const [username, setUsername] = useState('User-' + Math.floor(Math.random() * 1000));
    const cursorRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });

    useEffect(() => {
        // Connect to WebSocket and join collaboration session
        if (websocketService.isConnected()) {
            joinSession();
        } else {
            websocketService.connect();
            setTimeout(() => {
                if (websocketService.isConnected()) {
                    joinSession();
                }
            }, 1000);
        }

        // Set up event listeners for collaboration events
        websocketService.on('collaboration_update', handleCollaborationUpdate);
        websocketService.on('participant_joined', handleParticipantJoined);
        websocketService.on('participant_left', handleParticipantLeft);
        websocketService.on('cursor_update', handleCursorUpdate);
        websocketService.on('selection_update', handleSelectionUpdate);

        // Track mouse movement for cursor sharing
        const handleMouseMove = (event: MouseEvent) => {
            cursorRef.current = { x: event.clientX, y: event.clientY };

            // Throttle cursor updates to avoid overwhelming the server
            if (Math.random() < 0.1) { // Send ~10% of cursor movements
                websocketService.emit('cursor_update', {
                    sessionId,
                    cursor: { x: event.clientX, y: event.clientY }
                });
            }
        };

        document.addEventListener('mousemove', handleMouseMove);

        return () => {
            document.removeEventListener('mousemove', handleMouseMove);
            websocketService.off('collaboration_update', handleCollaborationUpdate);
            websocketService.off('participant_joined', handleParticipantJoined);
            websocketService.off('participant_left', handleParticipantLeft);
            websocketService.off('cursor_update', handleCursorUpdate);
            websocketService.off('selection_update', handleSelectionUpdate);

            // Leave session on unmount
            websocketService.emit('leave_collaboration', { sessionId });
        };
    }, [sessionId]);

    const joinSession = () => {
        websocketService.emit('join_collaboration', {
            sessionId,
            username,
            initialState: sharedState
        });
        setIsConnected(true);
    };

    const handleCollaborationUpdate = (data: any) => {
        if (data.sessionId === sessionId) {
            setSharedState(data.sharedState);
        }
    };

    const handleParticipantJoined = (data: any) => {
        if (data.sessionId === sessionId) {
            setParticipants(data.participants);
        }
    };

    const handleParticipantLeft = (data: any) => {
        if (data.sessionId === sessionId) {
            setParticipants(data.participants);
        }
    };

    const handleCursorUpdate = (data: any) => {
        // Update participant cursor position
        setParticipants(prev => prev.map(p =>
            p.userId === data.userId
                ? { ...p, cursor: data.cursor }
                : p
        ));
    };

    const handleSelectionUpdate = (data: any) => {
        // Update participant selection
        setParticipants(prev => prev.map(p =>
            p.userId === data.userId
                ? { ...p, selection: data.selection }
                : p
        ));
    };

    const updateAnalysisParameter = (key: string, value: any) => {
        const newState = {
            ...sharedState,
            analysisParameters: {
                ...sharedState.analysisParameters,
                [key]: value
            },
            version: sharedState.version + 1
        };

        setSharedState(newState);

        // Send update to other collaborators
        websocketService.emit('collaboration_update', {
            sessionId,
            type: 'parameter_change',
            data: { [key]: value },
            version: newState.version
        });
    };

    const updateQueryState = (key: string, value: any) => {
        const newState = {
            ...sharedState,
            queryState: {
                ...sharedState.queryState,
                [key]: value
            },
            version: sharedState.version + 1
        };

        setSharedState(newState);

        // Send update to other collaborators
        websocketService.emit('collaboration_update', {
            sessionId,
            type: 'query_update',
            data: { [key]: value },
            version: newState.version
        });
    };

    const updateVisualizationState = (key: string, value: any) => {
        const newState = {
            ...sharedState,
            visualizationState: {
                ...sharedState.visualizationState,
                [key]: value
            },
            version: sharedState.version + 1
        };

        setSharedState(newState);

        // Send update to other collaborators
        websocketService.emit('collaboration_update', {
            sessionId,
            type: 'visualization_update',
            data: { [key]: value },
            version: newState.version
        });
    };

    return (
        <Box sx={{ p: 3 }}>
            <Typography variant="h4" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Group />
                Real-time Collaboration
                <Chip
                    label={isConnected ? 'Connected' : 'Disconnected'}
                    color={isConnected ? 'success' : 'error'}
                    size="small"
                />
            </Typography>

            <Grid container spacing={3}>
                {/* Participants Panel */}
                <Grid item xs={12} md={3}>
                    <Paper sx={{ p: 2, height: 'fit-content' }}>
                        <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Person />
                            Active Participants ({participants.length})
                        </Typography>
                        <List dense>
                            {participants.map((participant) => (
                                <ListItem key={participant.userId}>
                                    <ListItemAvatar>
                                        <Avatar sx={{ bgcolor: 'primary.main' }}>
                                            {participant.username.charAt(0).toUpperCase()}
                                        </Avatar>
                                    </ListItemAvatar>
                                    <ListItemText
                                        primary={participant.username}
                                        secondary={
                                            <Box>
                                                <Chip
                                                    label={participant.isActive ? 'Active' : 'Inactive'}
                                                    size="small"
                                                    color={participant.isActive ? 'success' : 'default'}
                                                />
                                                {participant.cursor && (
                                                    <Typography variant="caption" display="block">
                                                        Cursor: ({Math.round(participant.cursor.x)}, {Math.round(participant.cursor.y)})
                                                    </Typography>
                                                )}
                                            </Box>
                                        }
                                    />
                                </ListItem>
                            ))}
                        </List>

                        <Box sx={{ mt: 2 }}>
                            <TextField
                                fullWidth
                                size="small"
                                label="Your Username"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                disabled={isConnected}
                            />
                        </Box>
                    </Paper>
                </Grid>

                {/* Shared Analysis Parameters */}
                <Grid item xs={12} md={9}>
                    <Grid container spacing={2}>
                        {/* Analysis Parameters */}
                        <Grid item xs={12} md={6}>
                            <Card>
                                <CardContent>
                                    <Typography variant="h6" gutterBottom>
                                        Analysis Parameters
                                    </Typography>

                                    <Box sx={{ mb: 3 }}>
                                        <Typography gutterBottom>
                                            Energy Range: {sharedState.analysisParameters.energyRange.min} - {sharedState.analysisParameters.energyRange.max} GeV
                                        </Typography>
                                        <Slider
                                            value={[sharedState.analysisParameters.energyRange.min, sharedState.analysisParameters.energyRange.max]}
                                            onChange={(_, newValue) => {
                                                const [min, max] = newValue as number[];
                                                updateAnalysisParameter('energyRange', { min, max });
                                            }}
                                            valueLabelDisplay="auto"
                                            min={0.1}
                                            max={1000}
                                            step={0.1}
                                        />
                                    </Box>

                                    <Box sx={{ mb: 2 }}>
                                        <Typography gutterBottom>Particle Types:</Typography>
                                        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                                            {['electron', 'muon', 'pion', 'kaon', 'proton'].map((type) => (
                                                <Chip
                                                    key={type}
                                                    label={type}
                                                    clickable
                                                    color={sharedState.analysisParameters.particleTypes.includes(type) ? 'primary' : 'default'}
                                                    onClick={() => {
                                                        const current = sharedState.analysisParameters.particleTypes;
                                                        const newTypes = current.includes(type)
                                                            ? current.filter(t => t !== type)
                                                            : [...current, type];
                                                        updateAnalysisParameter('particleTypes', newTypes);
                                                    }}
                                                />
                                            ))}
                                        </Box>
                                    </Box>

                                    <Box>
                                        <Typography gutterBottom>Detector Regions:</Typography>
                                        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                                            {['barrel', 'endcap', 'forward'].map((region) => (
                                                <Chip
                                                    key={region}
                                                    label={region}
                                                    clickable
                                                    color={sharedState.analysisParameters.detectorRegions.includes(region) ? 'primary' : 'default'}
                                                    onClick={() => {
                                                        const current = sharedState.analysisParameters.detectorRegions;
                                                        const newRegions = current.includes(region)
                                                            ? current.filter(r => r !== region)
                                                            : [...current, region];
                                                        updateAnalysisParameter('detectorRegions', newRegions);
                                                    }}
                                                />
                                            ))}
                                        </Box>
                                    </Box>
                                </CardContent>
                            </Card>
                        </Grid>

                        {/* Query State */}
                        <Grid item xs={12} md={6}>
                            <Card>
                                <CardContent>
                                    <Typography variant="h6" gutterBottom>
                                        Query Configuration
                                    </Typography>

                                    <FormControl fullWidth sx={{ mb: 2 }}>
                                        <InputLabel>Sort By</InputLabel>
                                        <Select
                                            value={sharedState.queryState.sortBy}
                                            label="Sort By"
                                            onChange={(e) => updateQueryState('sortBy', e.target.value)}
                                        >
                                            <MenuItem value="timestamp">Timestamp</MenuItem>
                                            <MenuItem value="energy">Energy</MenuItem>
                                            <MenuItem value="momentum">Momentum</MenuItem>
                                        </Select>
                                    </FormControl>

                                    <TextField
                                        fullWidth
                                        type="number"
                                        label="Result Limit"
                                        value={sharedState.queryState.limit}
                                        onChange={(e) => updateQueryState('limit', parseInt(e.target.value) || 1000)}
                                        sx={{ mb: 2 }}
                                    />

                                    <Typography variant="subtitle2" gutterBottom>
                                        Active Filters: {sharedState.queryState.filters.length}
                                    </Typography>
                                </CardContent>
                            </Card>
                        </Grid>

                        {/* Visualization State */}
                        <Grid item xs={12}>
                            <Card>
                                <CardContent>
                                    <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                        <Visibility />
                                        Visualization Settings
                                    </Typography>

                                    <Grid container spacing={2}>
                                        <Grid item xs={12} md={4}>
                                            <FormControl fullWidth>
                                                <InputLabel>Display Mode</InputLabel>
                                                <Select
                                                    value={sharedState.visualizationState.displayMode}
                                                    label="Display Mode"
                                                    onChange={(e) => updateVisualizationState('displayMode', e.target.value)}
                                                >
                                                    <MenuItem value="3d">3D View</MenuItem>
                                                    <MenuItem value="2d">2D Projection</MenuItem>
                                                    <MenuItem value="hybrid">Hybrid Mode</MenuItem>
                                                </Select>
                                            </FormControl>
                                        </Grid>

                                        <Grid item xs={12} md={8}>
                                            <Typography gutterBottom>Camera Position:</Typography>
                                            <Box sx={{ display: 'flex', gap: 2 }}>
                                                <TextField
                                                    size="small"
                                                    label="X"
                                                    type="number"
                                                    value={sharedState.visualizationState.cameraPosition.x}
                                                    onChange={(e) => updateVisualizationState('cameraPosition', {
                                                        ...sharedState.visualizationState.cameraPosition,
                                                        x: parseFloat(e.target.value) || 0
                                                    })}
                                                />
                                                <TextField
                                                    size="small"
                                                    label="Y"
                                                    type="number"
                                                    value={sharedState.visualizationState.cameraPosition.y}
                                                    onChange={(e) => updateVisualizationState('cameraPosition', {
                                                        ...sharedState.visualizationState.cameraPosition,
                                                        y: parseFloat(e.target.value) || 0
                                                    })}
                                                />
                                                <TextField
                                                    size="small"
                                                    label="Z"
                                                    type="number"
                                                    value={sharedState.visualizationState.cameraPosition.z}
                                                    onChange={(e) => updateVisualizationState('cameraPosition', {
                                                        ...sharedState.visualizationState.cameraPosition,
                                                        z: parseFloat(e.target.value) || 0
                                                    })}
                                                />
                                            </Box>
                                        </Grid>
                                    </Grid>
                                </CardContent>
                            </Card>
                        </Grid>

                        {/* Session Info */}
                        <Grid item xs={12}>
                            <Paper sx={{ p: 2, bgcolor: 'grey.50' }}>
                                <Typography variant="body2" color="text.secondary">
                                    Session ID: {sessionId} | State Version: {sharedState.version} |
                                    Connection: {isConnected ? 'Active' : 'Disconnected'}
                                </Typography>
                            </Paper>
                        </Grid>
                    </Grid>
                </Grid>
            </Grid>
        </Box>
    );
};

export default Collaboration;