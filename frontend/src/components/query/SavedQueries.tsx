import React, { useState } from 'react';
import {
    Box,
    Paper,
    Typography,
    List,
    ListItem,
    ListItemText,
    ListItemSecondaryAction,
    IconButton,
    Button,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    Chip,
    Menu,
    MenuItem,
    Divider,
} from '@mui/material';
import {
    Save as SaveIcon,
    Delete as DeleteIcon,
    MoreVert as MoreIcon,
    PlayArrow as RunIcon,
    // Edit as EditIcon,
} from '@mui/icons-material';
import { SavedQuery, QueryCondition } from '../../types/query';

interface SavedQueriesProps {
    savedQueries: SavedQuery[];
    onSaveQuery: (name: string, description: string, conditions: QueryCondition[]) => void;
    onLoadQuery: (query: SavedQuery) => void;
    onDeleteQuery: (queryId: string) => void;
    currentConditions: QueryCondition[];
}

const SavedQueries: React.FC<SavedQueriesProps> = ({
    savedQueries,
    onSaveQuery,
    onLoadQuery,
    onDeleteQuery,
    currentConditions,
}) => {
    const [saveDialogOpen, setSaveDialogOpen] = useState(false);
    const [queryName, setQueryName] = useState('');
    const [queryDescription, setQueryDescription] = useState('');
    const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
    const [selectedQuery, setSelectedQuery] = useState<SavedQuery | null>(null);

    const handleSaveClick = () => {
        if (currentConditions.length === 0) {
            return;
        }
        setSaveDialogOpen(true);
    };

    const handleSaveConfirm = () => {
        if (queryName.trim()) {
            onSaveQuery(queryName.trim(), queryDescription.trim(), currentConditions);
            setQueryName('');
            setQueryDescription('');
            setSaveDialogOpen(false);
        }
    };

    const handleMenuClick = (event: React.MouseEvent<HTMLElement>, query: SavedQuery) => {
        setMenuAnchor(event.currentTarget);
        setSelectedQuery(query);
    };

    const handleMenuClose = () => {
        setMenuAnchor(null);
        setSelectedQuery(null);
    };

    const handleLoadQuery = () => {
        if (selectedQuery) {
            onLoadQuery(selectedQuery);
        }
        handleMenuClose();
    };

    const handleDeleteQuery = () => {
        if (selectedQuery) {
            onDeleteQuery(selectedQuery.id);
        }
        handleMenuClose();
    };

    const formatDate = (date: Date) => {
        return new Date(date).toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        });
    };

    const getQuerySummary = (conditions: QueryCondition[]) => {
        if (conditions.length === 0) return 'No conditions';

        const summary = conditions
            .slice(0, 2)
            .map(condition => {
                if (!condition.field || !condition.operator) return '';
                return `${condition.field.label} ${condition.operator.label}`;
            })
            .filter(Boolean)
            .join(', ');

        if (conditions.length > 2) {
            return `${summary} +${conditions.length - 2} more`;
        }

        return summary;
    };

    return (
        <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant="h6">Saved Queries</Typography>
                <Button
                    startIcon={<SaveIcon />}
                    onClick={handleSaveClick}
                    disabled={currentConditions.length === 0}
                    variant="outlined"
                    size="small"
                >
                    Save Current Query
                </Button>
            </Box>

            <Paper sx={{ maxHeight: 400, overflow: 'auto' }}>
                {savedQueries.length === 0 ? (
                    <Box sx={{ p: 3, textAlign: 'center' }}>
                        <Typography variant="body2" color="text.secondary">
                            No saved queries yet. Build a query and save it for later use.
                        </Typography>
                    </Box>
                ) : (
                    <List>
                        {savedQueries.map((query, index) => (
                            <React.Fragment key={query.id}>
                                {index > 0 && <Divider />}
                                <ListItem>
                                    <ListItemText
                                        primary={
                                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                <Typography variant="subtitle2">{query.name}</Typography>
                                                <Chip
                                                    label={`${query.conditions.length} conditions`}
                                                    size="small"
                                                    variant="outlined"
                                                />
                                            </Box>
                                        }
                                        secondary={
                                            <Box>
                                                {query.description && (
                                                    <Typography variant="body2" color="text.secondary" gutterBottom>
                                                        {query.description}
                                                    </Typography>
                                                )}
                                                <Typography variant="caption" color="text.secondary">
                                                    {getQuerySummary(query.conditions)}
                                                </Typography>
                                                <br />
                                                <Typography variant="caption" color="text.secondary">
                                                    Created: {formatDate(query.createdAt)}
                                                    {query.updatedAt !== query.createdAt && (
                                                        <> • Updated: {formatDate(query.updatedAt)}</>
                                                    )}
                                                </Typography>
                                            </Box>
                                        }
                                    />
                                    <ListItemSecondaryAction>
                                        <IconButton
                                            edge="end"
                                            onClick={(e) => handleMenuClick(e, query)}
                                        >
                                            <MoreIcon />
                                        </IconButton>
                                    </ListItemSecondaryAction>
                                </ListItem>
                            </React.Fragment>
                        ))}
                    </List>
                )}
            </Paper>

            {/* Save Query Dialog */}
            <Dialog open={saveDialogOpen} onClose={() => setSaveDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Save Query</DialogTitle>
                <DialogContent>
                    <TextField
                        autoFocus
                        margin="dense"
                        label="Query Name"
                        fullWidth
                        variant="outlined"
                        value={queryName}
                        onChange={(e) => setQueryName(e.target.value)}
                        sx={{ mb: 2 }}
                    />
                    <TextField
                        margin="dense"
                        label="Description (optional)"
                        fullWidth
                        multiline
                        rows={3}
                        variant="outlined"
                        value={queryDescription}
                        onChange={(e) => setQueryDescription(e.target.value)}
                    />
                    <Box sx={{ mt: 2 }}>
                        <Typography variant="body2" color="text.secondary">
                            This query has {currentConditions.length} condition{currentConditions.length !== 1 ? 's' : ''}
                        </Typography>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setSaveDialogOpen(false)}>Cancel</Button>
                    <Button
                        onClick={handleSaveConfirm}
                        variant="contained"
                        disabled={!queryName.trim()}
                    >
                        Save
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Query Actions Menu */}
            <Menu
                anchorEl={menuAnchor}
                open={Boolean(menuAnchor)}
                onClose={handleMenuClose}
            >
                <MenuItem onClick={handleLoadQuery}>
                    <RunIcon sx={{ mr: 1 }} />
                    Load Query
                </MenuItem>
                <MenuItem onClick={handleDeleteQuery}>
                    <DeleteIcon sx={{ mr: 1 }} />
                    Delete
                </MenuItem>
            </Menu>
        </Box>
    );
};

export default SavedQueries;