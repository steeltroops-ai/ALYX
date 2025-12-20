import React, { useState, useCallback, useEffect } from 'react';
import {
    Box,
    Typography,
    Button,
    Paper,
    Grid,
    Alert,
    Accordion,
    AccordionSummary,
    AccordionDetails,
    Tabs,
    Tab,
    // Divider,
    Chip,
    CircularProgress,
    Snackbar,
} from '@mui/material';
import {
    Add as AddIcon,
    PlayArrow as RunIcon,
    Code as CodeIcon,
    ExpandMore as ExpandMoreIcon,
    // History as HistoryIcon,
} from '@mui/icons-material';
import { DndProvider } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';
import { v4 as uuidv4 } from 'uuid';

import {
    QueryCondition as QueryConditionType,
    QueryResult,
    SavedQuery,
    QueryValidationError,
    QueryExecutionRequest,
    // QueryExecutionResponse
} from '../types/query';
import { ALL_QUERY_FIELDS, FIELD_GROUPS } from '../data/queryFields';
import { SQLGenerator } from '../utils/sqlGenerator';
import QueryCondition from '../components/query/QueryCondition';
import QueryResults from '../components/query/QueryResults';
import SavedQueries from '../components/query/SavedQueries';

interface TabPanelProps {
    children?: React.ReactNode;
    index: number;
    value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index, ...other }) => {
    return (
        <div
            role="tabpanel"
            hidden={value !== index}
            id={`query-tabpanel-${index}`}
            aria-labelledby={`query-tab-${index}`}
            {...other}
        >
            {value === index && <Box sx={{ p: 3 }}>{children}</Box>}
        </div>
    );
};

const QueryBuilder: React.FC = () => {
    // State management
    const [conditions, setConditions] = useState<QueryConditionType[]>([]);
    const [queryResult, setQueryResult] = useState<QueryResult | null>(null);
    const [savedQueries, setSavedQueries] = useState<SavedQuery[]>([]);
    const [validationErrors, setValidationErrors] = useState<QueryValidationError[]>([]);
    const [generatedSQL, setGeneratedSQL] = useState<string>('');
    const [isExecuting, setIsExecuting] = useState(false);
    const [executionError, setExecutionError] = useState<string | null>(null);
    const [tabValue, setTabValue] = useState(0);
    const [snackbarOpen, setSnackbarOpen] = useState(false);
    const [snackbarMessage, setSnackbarMessage] = useState('');

    // Real-time validation
    useEffect(() => {
        if (conditions.length > 0) {
            const request: QueryExecutionRequest = {
                conditions,
                page: 1,
                pageSize: 25
            };

            const result = SQLGenerator.generateSQL(request);
            setValidationErrors(result.errors);
            setGeneratedSQL(result.sql);
        } else {
            setValidationErrors([]);
            setGeneratedSQL('');
        }
    }, [conditions]);

    // Add new condition
    const handleAddCondition = useCallback(() => {
        const newCondition: QueryConditionType = {
            id: uuidv4(),
            field: {} as any,
            operator: {} as any,
            value: null,
            logicalOperator: conditions.length > 0 ? 'AND' : undefined,
        };
        setConditions(prev => [...prev, newCondition]);
    }, [conditions.length]);

    // Update condition
    const handleUpdateCondition = useCallback((index: number, updatedCondition: QueryConditionType) => {
        setConditions(prev => prev.map((condition, i) =>
            i === index ? updatedCondition : condition
        ));
    }, []);

    // Delete condition
    const handleDeleteCondition = useCallback((index: number) => {
        setConditions(prev => prev.filter((_, i) => i !== index));
    }, []);

    // Move condition (for drag and drop)
    const handleMoveCondition = useCallback((dragIndex: number, hoverIndex: number) => {
        setConditions(prev => {
            const draggedCondition = prev[dragIndex];
            const newConditions = [...prev];
            newConditions.splice(dragIndex, 1);
            newConditions.splice(hoverIndex, 0, draggedCondition);
            return newConditions;
        });
    }, []);

    // Execute query
    const handleExecuteQuery = useCallback(async () => {
        if (validationErrors.length > 0) {
            setSnackbarMessage('Please fix validation errors before executing the query');
            setSnackbarOpen(true);
            return;
        }

        if (conditions.length === 0) {
            setSnackbarMessage('Please add at least one condition to execute the query');
            setSnackbarOpen(true);
            return;
        }

        setIsExecuting(true);
        setExecutionError(null);

        try {
            // Prepare request for API call
            // const request: QueryExecutionRequest = {
            //     conditions,
            //     page: 1,
            //     pageSize: 100
            // };

            // Simulate API call to backend
            await new Promise(resolve => setTimeout(resolve, 1000));

            // Mock result for demonstration
            const mockResult: QueryResult = {
                data: Array.from({ length: 50 }, (_, i) => ({
                    eventId: `event-${i + 1}`,
                    timestamp: new Date(Date.now() - i * 3600000).toISOString(),
                    centerOfMassEnergy: 7000 + Math.random() * 7000,
                    runNumber: 123456 + i,
                    eventNumber: i + 1,
                })),
                totalCount: 15000 + Math.floor(Math.random() * 50000),
                page: 1,
                pageSize: 100,
                executionTime: 450 + Math.floor(Math.random() * 1000)
            };

            setQueryResult(mockResult);
            setTabValue(1); // Switch to results tab
            setSnackbarMessage(`Query executed successfully in ${mockResult.executionTime}ms`);
            setSnackbarOpen(true);
        } catch (error) {
            setExecutionError(error instanceof Error ? error.message : 'Query execution failed');
            setSnackbarMessage('Query execution failed');
            setSnackbarOpen(true);
        } finally {
            setIsExecuting(false);
        }
    }, [conditions, validationErrors]);

    // Handle pagination
    const handlePageChange = useCallback(async (page: number, pageSize: number) => {
        if (!queryResult) return;

        setIsExecuting(true);
        try {
            // Simulate pagination API call
            await new Promise(resolve => setTimeout(resolve, 300));

            const startIndex = (page - 1) * pageSize;
            const mockData = Array.from({ length: Math.min(pageSize, queryResult.totalCount - startIndex) }, (_, i) => ({
                eventId: `event-${startIndex + i + 1}`,
                timestamp: new Date(Date.now() - (startIndex + i) * 3600000).toISOString(),
                centerOfMassEnergy: 7000 + Math.random() * 7000,
                runNumber: 123456 + startIndex + i,
                eventNumber: startIndex + i + 1,
            }));

            setQueryResult(prev => prev ? {
                ...prev,
                data: mockData,
                page,
                pageSize,
                executionTime: 200 + Math.floor(Math.random() * 300)
            } : null);
        } catch (error) {
            setExecutionError('Failed to load page');
        } finally {
            setIsExecuting(false);
        }
    }, [queryResult]);

    // Save query
    const handleSaveQuery = useCallback((name: string, description: string, queryConditions: QueryConditionType[]) => {
        const newSavedQuery: SavedQuery = {
            id: uuidv4(),
            name,
            description,
            conditions: queryConditions,
            createdAt: new Date(),
            updatedAt: new Date()
        };

        setSavedQueries(prev => [...prev, newSavedQuery]);
        setSnackbarMessage(`Query "${name}" saved successfully`);
        setSnackbarOpen(true);
    }, []);

    // Load saved query
    const handleLoadQuery = useCallback((query: SavedQuery) => {
        setConditions(query.conditions);
        setTabValue(0); // Switch to builder tab
        setSnackbarMessage(`Query "${query.name}" loaded`);
        setSnackbarOpen(true);
    }, []);

    // Delete saved query
    const handleDeleteSavedQuery = useCallback((queryId: string) => {
        setSavedQueries(prev => prev.filter(q => q.id !== queryId));
        setSnackbarMessage('Query deleted successfully');
        setSnackbarOpen(true);
    }, []);

    const hasValidationErrors = validationErrors.length > 0;
    const canExecute = conditions.length > 0 && !hasValidationErrors && !isExecuting;

    return (
        <DndProvider backend={HTML5Backend}>
            <Box sx={{ p: 3 }}>
                <Typography variant="h4" gutterBottom>
                    Query Builder
                </Typography>
                <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
                    Build complex queries to analyze collision event data using a visual interface
                </Typography>

                {/* Main Content */}
                <Grid container spacing={3}>
                    {/* Query Builder Panel */}
                    <Grid item xs={12} lg={8}>
                        <Paper sx={{ height: 'fit-content' }}>
                            <Tabs value={tabValue} onChange={(_, newValue) => setTabValue(newValue)}>
                                <Tab label="Query Builder" />
                                <Tab label="Results" />
                                <Tab label="SQL Preview" />
                            </Tabs>

                            {/* Query Builder Tab */}
                            <TabPanel value={tabValue} index={0}>
                                <Box sx={{ mb: 3 }}>
                                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                                        <Typography variant="h6">Query Conditions</Typography>
                                        <Box sx={{ display: 'flex', gap: 1 }}>
                                            <Button
                                                variant="outlined"
                                                startIcon={<AddIcon />}
                                                onClick={handleAddCondition}
                                            >
                                                Add Condition
                                            </Button>
                                            <Button
                                                variant="contained"
                                                startIcon={isExecuting ? <CircularProgress size={16} /> : <RunIcon />}
                                                onClick={handleExecuteQuery}
                                                disabled={!canExecute}
                                            >
                                                {isExecuting ? 'Executing...' : 'Execute Query'}
                                            </Button>
                                        </Box>
                                    </Box>

                                    {/* Validation Errors */}
                                    {hasValidationErrors && (
                                        <Alert severity="error" sx={{ mb: 2 }}>
                                            <Typography variant="subtitle2" gutterBottom>
                                                Please fix the following errors:
                                            </Typography>
                                            <ul style={{ margin: 0, paddingLeft: 20 }}>
                                                {validationErrors.map((error, index) => (
                                                    <li key={index}>{error.message}</li>
                                                ))}
                                            </ul>
                                        </Alert>
                                    )}

                                    {/* Query Conditions */}
                                    {conditions.length === 0 ? (
                                        <Paper sx={{ p: 4, textAlign: 'center', bgcolor: 'grey.50' }}>
                                            <Typography variant="body1" color="text.secondary">
                                                No conditions added yet. Click "Add Condition" to start building your query.
                                            </Typography>
                                        </Paper>
                                    ) : (
                                        <Box>
                                            {conditions.map((condition, index) => (
                                                <QueryCondition
                                                    key={condition.id}
                                                    condition={condition}
                                                    index={index}
                                                    availableFields={ALL_QUERY_FIELDS}
                                                    onUpdate={handleUpdateCondition}
                                                    onDelete={handleDeleteCondition}
                                                    onMove={handleMoveCondition}
                                                    showLogicalOperator={index > 0}
                                                />
                                            ))}
                                        </Box>
                                    )}
                                </Box>

                                {/* Field Groups Reference */}
                                <Accordion>
                                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                        <Typography variant="h6">Available Fields</Typography>
                                    </AccordionSummary>
                                    <AccordionDetails>
                                        <Grid container spacing={2}>
                                            {Object.entries(FIELD_GROUPS).map(([groupName, fields]) => (
                                                <Grid item xs={12} md={4} key={groupName}>
                                                    <Typography variant="subtitle1" gutterBottom>
                                                        {groupName}
                                                    </Typography>
                                                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                                                        {fields.map(field => (
                                                            <Chip
                                                                key={`${field.table}.${field.name}`}
                                                                label={field.label}
                                                                size="small"
                                                                variant="outlined"
                                                                sx={{ justifyContent: 'flex-start' }}
                                                            />
                                                        ))}
                                                    </Box>
                                                </Grid>
                                            ))}
                                        </Grid>
                                    </AccordionDetails>
                                </Accordion>
                            </TabPanel>

                            {/* Results Tab */}
                            <TabPanel value={tabValue} index={1}>
                                <QueryResults
                                    result={queryResult}
                                    loading={isExecuting}
                                    error={executionError}
                                    onPageChange={handlePageChange}
                                />
                            </TabPanel>

                            {/* SQL Preview Tab */}
                            <TabPanel value={tabValue} index={2}>
                                <Box>
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                                        <CodeIcon />
                                        <Typography variant="h6">Generated SQL</Typography>
                                    </Box>
                                    {generatedSQL ? (
                                        <Paper sx={{ p: 2, bgcolor: 'grey.50' }}>
                                            <Typography
                                                component="pre"
                                                sx={{
                                                    fontFamily: 'monospace',
                                                    fontSize: '0.875rem',
                                                    whiteSpace: 'pre-wrap',
                                                    margin: 0
                                                }}
                                            >
                                                {generatedSQL}
                                            </Typography>
                                        </Paper>
                                    ) : (
                                        <Paper sx={{ p: 4, textAlign: 'center', bgcolor: 'grey.50' }}>
                                            <Typography variant="body1" color="text.secondary">
                                                Add query conditions to see the generated SQL
                                            </Typography>
                                        </Paper>
                                    )}
                                </Box>
                            </TabPanel>
                        </Paper>
                    </Grid>

                    {/* Saved Queries Panel */}
                    <Grid item xs={12} lg={4}>
                        <Paper sx={{ p: 2, height: 'fit-content' }}>
                            <SavedQueries
                                savedQueries={savedQueries}
                                onSaveQuery={handleSaveQuery}
                                onLoadQuery={handleLoadQuery}
                                onDeleteQuery={handleDeleteSavedQuery}
                                currentConditions={conditions}
                            />
                        </Paper>
                    </Grid>
                </Grid>

                {/* Snackbar for notifications */}
                <Snackbar
                    open={snackbarOpen}
                    autoHideDuration={4000}
                    onClose={() => setSnackbarOpen(false)}
                    message={snackbarMessage}
                />
            </Box>
        </DndProvider>
    );
};

export default QueryBuilder;