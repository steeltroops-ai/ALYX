import React, { useState, useEffect } from 'react';
import {
    Box,
    Typography,
    Button,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    List,
    ListItem,
    ListItemText,
    ListItemButton,
    ListItemSecondaryAction,
    IconButton,
    Paper,
    Chip
} from '@mui/material';
import { Add, Delete } from '@mui/icons-material';
import NotebookEnvironment from '../components/notebook/NotebookEnvironment';
import { Notebook } from '../types/notebook';
import { notebookService } from '../services/notebookService';
import { v4 as uuidv4 } from 'uuid';

const Notebooks: React.FC = () => {
    const [notebooks, setNotebooks] = useState<Notebook[]>([]);
    const [selectedNotebook, setSelectedNotebook] = useState<Notebook | null>(null);
    const [createDialogOpen, setCreateDialogOpen] = useState(false);
    const [newNotebookName, setNewNotebookName] = useState('');
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadNotebooks();
    }, []);

    const loadNotebooks = async () => {
        try {
            setLoading(true);
            const notebookList = await notebookService.listNotebooks();
            setNotebooks(notebookList);
        } catch (error) {
            console.error('Failed to load notebooks:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleCreateNotebook = async () => {
        if (!newNotebookName.trim()) return;

        const newNotebook: Notebook = {
            id: uuidv4(),
            name: newNotebookName,
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
        };

        try {
            await notebookService.saveNotebook(newNotebook);
            setNotebooks(prev => [newNotebook, ...prev]);
            setNewNotebookName('');
            setCreateDialogOpen(false);
            setSelectedNotebook(newNotebook);
        } catch (error) {
            console.error('Failed to create notebook:', error);
        }
    };

    const handleDeleteNotebook = async (notebookId: string) => {
        try {
            await notebookService.deleteNotebook(notebookId);
            setNotebooks(prev => prev.filter(nb => nb.id !== notebookId));
            if (selectedNotebook?.id === notebookId) {
                setSelectedNotebook(null);
            }
        } catch (error) {
            console.error('Failed to delete notebook:', error);
        }
    };

    const handleSaveNotebook = async (notebook: Notebook) => {
        try {
            await notebookService.saveNotebook(notebook);
            setNotebooks(prev => prev.map(nb => nb.id === notebook.id ? notebook : nb));
        } catch (error) {
            console.error('Failed to save notebook:', error);
        }
    };

    const handleShareNotebook = async (notebookId: string, collaborators: string[]) => {
        try {
            await notebookService.shareNotebook(notebookId, collaborators);
            // Refresh the notebook list to show updated collaborators
            await loadNotebooks();
        } catch (error) {
            console.error('Failed to share notebook:', error);
        }
    };

    if (selectedNotebook) {
        return (
            <NotebookEnvironment
                initialNotebook={selectedNotebook}
                onSave={handleSaveNotebook}
                onShare={handleShareNotebook}
                onLoad={notebookService.loadNotebook.bind(notebookService)}
            />
        );
    }

    return (
        <Box sx={{ p: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                <Typography variant="h4" gutterBottom>
                    Analysis Notebooks
                </Typography>
                <Button
                    variant="contained"
                    startIcon={<Add />}
                    onClick={() => setCreateDialogOpen(true)}
                >
                    New Notebook
                </Button>
            </Box>

            {loading ? (
                <Typography>Loading notebooks...</Typography>
            ) : notebooks.length === 0 ? (
                <Paper sx={{ p: 4, textAlign: 'center' }}>
                    <Typography variant="h6" color="text.secondary" gutterBottom>
                        No notebooks yet
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                        Create your first analysis notebook to get started with ALYX.
                    </Typography>
                    <Button
                        variant="contained"
                        startIcon={<Add />}
                        onClick={() => setCreateDialogOpen(true)}
                    >
                        Create Notebook
                    </Button>
                </Paper>
            ) : (
                <List>
                    {notebooks.map((notebook) => (
                        <ListItem key={notebook.id} component={Paper} sx={{ mb: 1 }}>
                            <ListItemButton onClick={() => setSelectedNotebook(notebook)}>
                                <ListItemText
                                    primary={notebook.name}
                                    secondary={
                                        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mt: 1 }}>
                                            <Chip
                                                label={`v${notebook.metadata.version}`}
                                                size="small"
                                                variant="outlined"
                                            />
                                            <Chip
                                                label={`${notebook.cells.length} cells`}
                                                size="small"
                                                color="primary"
                                            />
                                            {notebook.collaborators && notebook.collaborators.length > 0 && (
                                                <Chip
                                                    label={`${notebook.collaborators.length} collaborators`}
                                                    size="small"
                                                    color="secondary"
                                                />
                                            )}
                                            <Typography variant="caption" color="text.secondary">
                                                Modified: {new Date(notebook.metadata.modified).toLocaleDateString()}
                                            </Typography>
                                        </Box>
                                    }
                                />
                            </ListItemButton>
                            <ListItemSecondaryAction>
                                <IconButton
                                    edge="end"
                                    onClick={() => handleDeleteNotebook(notebook.id)}
                                    color="error"
                                >
                                    <Delete />
                                </IconButton>
                            </ListItemSecondaryAction>
                        </ListItem>
                    ))}
                </List>
            )}

            {/* Create Notebook Dialog */}
            <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Create New Notebook</DialogTitle>
                <DialogContent>
                    <TextField
                        autoFocus
                        fullWidth
                        label="Notebook Name"
                        value={newNotebookName}
                        onChange={(e) => setNewNotebookName(e.target.value)}
                        onKeyPress={(e) => e.key === 'Enter' && handleCreateNotebook()}
                        sx={{ mt: 1 }}
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
                    <Button
                        onClick={handleCreateNotebook}
                        variant="contained"
                        disabled={!newNotebookName.trim()}
                    >
                        Create
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};

export default Notebooks;