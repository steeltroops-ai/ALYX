import React, { useState, useCallback } from 'react';
import {
    Box,
    Container,
    Typography,
    Button,
    AppBar,
    Toolbar,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    List,
    ListItem,
    ListItemText,
    Chip
} from '@mui/material';
import {
    Save,
    Share,
    Add,
    PlayArrow,
    People
} from '@mui/icons-material';
import NotebookEditor from './NotebookEditor';
import { Notebook, NotebookCell, NotebookExecutionContext } from '../../types/notebook';
import { v4 as uuidv4 } from 'uuid';

interface NotebookEnvironmentProps {
    initialNotebook?: Notebook;
    onSave?: (notebook: Notebook) => Promise<void>;
    onShare?: (notebookId: string, collaborators: string[]) => Promise<void>;
    onLoad?: (notebookId: string) => Promise<Notebook>;
}

const NotebookEnvironment: React.FC<NotebookEnvironmentProps> = ({
    initialNotebook,
    onSave,
    onShare,
    onLoad: _onLoad,
}) => {
    const [notebook, setNotebook] = useState<Notebook>(
        initialNotebook || {
            id: uuidv4(),
            name: 'Untitled Notebook',
            cells: [{
                id: uuidv4(),
                type: 'code',
                content: '# Welcome to ALYX Analysis Notebook\n# Access collision data: collision_data.getEvents()\n# Create visualizations: physics_plots.createTrajectoryPlot()\n# Submit to GRID: grid_resources.submitJob()',
                output: null,
                executionCount: 0,
            }],
            metadata: {
                kernelspec: {
                    name: 'physics-kernel',
                    language: 'python',
                },
                language_info: {
                    name: 'python',
                    version: '3.9.0',
                },
                created: new Date().toISOString(),
                modified: new Date().toISOString(),
                version: 1,
            },
        }
    );

    const [executingCells, setExecutingCells] = useState<Set<string>>(new Set());
    const [shareDialogOpen, setShareDialogOpen] = useState(false);
    const [collaboratorEmail, setCollaboratorEmail] = useState('');
    const [collaborators, setCollaborators] = useState<string[]>(notebook.collaborators || []);

    // Mock execution context - in real implementation, this would connect to actual services
    const executionContext: NotebookExecutionContext = {
        collisionDataAPI: {
            getEvents: async (_query: any) => {
                // Simulate API call
                await new Promise(resolve => setTimeout(resolve, 1000));
                return [
                    { id: '1', energy: 13000, particles: 150 },
                    { id: '2', energy: 12500, particles: 142 },
                ];
            },
            getEventById: async (id: string) => {
                await new Promise(resolve => setTimeout(resolve, 500));
                return { id, energy: 13000, particles: 150, tracks: [] };
            },
        },
        visualizationLibraries: {
            d3: { version: '7.8.0', select: () => ({}) },
            customPhysicsPlots: {
                createTrajectoryPlot: (data: any) => ({ type: 'trajectory', data }),
                createEnergyHistogram: (data: any) => ({ type: 'histogram', data }),
            },
        },
        gridResources: {
            submitJob: async (_code: string, _resources: any) => {
                // Simulate job submission
                await new Promise(resolve => setTimeout(resolve, 2000));
                return `job-${Date.now()}`;
            },
            getJobStatus: async (_jobId: string) => {
                return { status: 'running', progress: 0.5 };
            },
        },
    };

    const handleCellChange = useCallback((cellId: string, updatedCell: NotebookCell) => {
        setNotebook(prev => ({
            ...prev,
            cells: prev.cells.map(cell =>
                cell.id === cellId ? updatedCell : cell
            ),
            metadata: {
                ...prev.metadata,
                modified: new Date().toISOString(),
                version: prev.metadata.version + 1,
            },
        }));
    }, []);

    const handleExecuteCell = useCallback(async (cell: NotebookCell) => {
        setExecutingCells(prev => new Set(prev).add(cell.id));

        try {
            // Determine if this is resource-intensive
            const isResourceIntensive = cell.content.includes('large_dataset') ||
                cell.content.includes('parallel_processing') ||
                cell.content.includes('memory_intensive_operation') ||
                cell.content.length > 500;

            if (isResourceIntensive) {
                // Submit to GRID resources
                const jobId = await executionContext.gridResources.submitJob(
                    cell.content,
                    { cores: 8, memory: '16GB' }
                );
                return { output: `Job submitted to GRID resources`, jobId };
            } else {
                // Local execution simulation
                await new Promise(resolve => setTimeout(resolve, 1000));

                // Simple code evaluation simulation
                if (cell.content.includes('collision_data.getEvents')) {
                    const events = await executionContext.collisionDataAPI.getEvents({});
                    return { output: `Found ${events.length} collision events`, data: events };
                }

                if (cell.content.includes('physics_plots.createTrajectoryPlot')) {
                    return { output: 'Trajectory plot created', plot: 'trajectory_visualization' };
                }

                return { output: 'Cell executed successfully' };
            }
        } finally {
            setExecutingCells(prev => {
                const newSet = new Set(prev);
                newSet.delete(cell.id);
                return newSet;
            });
        }
    }, [executionContext]);

    const handleAddCell = useCallback((afterCellId?: string) => {
        const newCell: NotebookCell = {
            id: uuidv4(),
            type: 'code',
            content: '',
            output: null,
            executionCount: 0,
        };

        setNotebook(prev => {
            const cells = [...prev.cells];
            if (afterCellId) {
                const index = cells.findIndex(cell => cell.id === afterCellId);
                cells.splice(index + 1, 0, newCell);
            } else {
                cells.push(newCell);
            }

            return {
                ...prev,
                cells,
                metadata: {
                    ...prev.metadata,
                    modified: new Date().toISOString(),
                    version: prev.metadata.version + 1,
                },
            };
        });
    }, []);

    const handleDeleteCell = useCallback((cellId: string) => {
        setNotebook(prev => ({
            ...prev,
            cells: prev.cells.filter(cell => cell.id !== cellId),
            metadata: {
                ...prev.metadata,
                modified: new Date().toISOString(),
                version: prev.metadata.version + 1,
            },
        }));
    }, []);

    const handleSave = useCallback(async () => {
        if (onSave) {
            await onSave(notebook);
        }
    }, [notebook, onSave]);

    const handleShare = useCallback(async () => {
        if (onShare && collaborators.length > 0) {
            await onShare(notebook.id, collaborators);
            setShareDialogOpen(false);
        }
    }, [notebook.id, collaborators, onShare]);

    const handleAddCollaborator = () => {
        if (collaboratorEmail && !collaborators.includes(collaboratorEmail)) {
            setCollaborators(prev => [...prev, collaboratorEmail]);
            setCollaboratorEmail('');
        }
    };

    const handleExecuteAll = useCallback(async () => {
        for (const cell of notebook.cells) {
            if (cell.type === 'code') {
                await handleExecuteCell(cell);
            }
        }
    }, [notebook.cells, handleExecuteCell]);

    return (
        <Box sx={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
            <AppBar position="static" color="default" elevation={1}>
                <Toolbar>
                    <Typography variant="h6" sx={{ flexGrow: 1 }}>
                        {notebook.name}
                    </Typography>

                    <Box sx={{ display: 'flex', gap: 1 }}>
                        <Button
                            startIcon={<PlayArrow />}
                            onClick={handleExecuteAll}
                            disabled={executingCells.size > 0}
                        >
                            Run All
                        </Button>

                        <Button
                            startIcon={<Add />}
                            onClick={() => handleAddCell()}
                        >
                            Add Cell
                        </Button>

                        <Button
                            startIcon={<Save />}
                            onClick={handleSave}
                        >
                            Save
                        </Button>

                        <Button
                            startIcon={<Share />}
                            onClick={() => setShareDialogOpen(true)}
                        >
                            Share
                        </Button>
                    </Box>
                </Toolbar>
            </AppBar>

            <Container maxWidth="lg" sx={{ flexGrow: 1, py: 2, overflow: 'auto' }}>
                <Box sx={{ mb: 2, display: 'flex', gap: 1, alignItems: 'center' }}>
                    <Chip
                        label={`Version ${notebook.metadata.version}`}
                        size="small"
                        variant="outlined"
                    />
                    <Chip
                        label={notebook.metadata.kernelspec.name}
                        size="small"
                        color="primary"
                    />
                    {collaborators.length > 0 && (
                        <Chip
                            icon={<People />}
                            label={`${collaborators.length} collaborators`}
                            size="small"
                            color="secondary"
                        />
                    )}
                </Box>

                {notebook.cells.map((cell) => (
                    <NotebookEditor
                        key={cell.id}
                        cell={cell}
                        context={executionContext}
                        onCellChange={(updatedCell) => handleCellChange(cell.id, updatedCell)}
                        onExecute={handleExecuteCell}
                        onDelete={() => handleDeleteCell(cell.id)}
                        onAddCell={() => handleAddCell(cell.id)}
                        isExecuting={executingCells.has(cell.id)}
                    />
                ))}
            </Container>

            {/* Share Dialog */}
            <Dialog open={shareDialogOpen} onClose={() => setShareDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Share Notebook</DialogTitle>
                <DialogContent>
                    <Box sx={{ mb: 2 }}>
                        <TextField
                            fullWidth
                            label="Collaborator Email"
                            value={collaboratorEmail}
                            onChange={(e) => setCollaboratorEmail(e.target.value)}
                            onKeyPress={(e) => e.key === 'Enter' && handleAddCollaborator()}
                            sx={{ mb: 1 }}
                        />
                        <Button onClick={handleAddCollaborator} variant="outlined" size="small">
                            Add Collaborator
                        </Button>
                    </Box>

                    {collaborators.length > 0 && (
                        <Box>
                            <Typography variant="subtitle2" gutterBottom>
                                Current Collaborators:
                            </Typography>
                            <List dense>
                                {collaborators.map((email, index) => (
                                    <ListItem key={index}>
                                        <ListItemText primary={email} />
                                    </ListItem>
                                ))}
                            </List>
                        </Box>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShareDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleShare} variant="contained" disabled={collaborators.length === 0}>
                        Share
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};

export default NotebookEnvironment;