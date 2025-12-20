import React, { useState } from 'react';
import {
    Box,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TablePagination,
    Typography,
    Chip,
    IconButton,
    Collapse,
    Alert,
    CircularProgress,
} from '@mui/material';
import {
    KeyboardArrowDown as ExpandMoreIcon,
    KeyboardArrowUp as ExpandLessIcon,
    Schedule as TimeIcon,
} from '@mui/icons-material';
import { QueryResult } from '../../types/query';

interface QueryResultsProps {
    result: QueryResult | null;
    loading: boolean;
    error: string | null;
    onPageChange: (page: number, pageSize: number) => void;
}

const QueryResults: React.FC<QueryResultsProps> = ({
    result,
    loading,
    error,
    onPageChange,
}) => {
    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(25);
    const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());

    const handleChangePage = (_event: unknown, newPage: number) => {
        setPage(newPage);
        onPageChange(newPage + 1, rowsPerPage);
    };

    const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
        const newRowsPerPage = parseInt(event.target.value, 10);
        setRowsPerPage(newRowsPerPage);
        setPage(0);
        onPageChange(1, newRowsPerPage);
    };

    const toggleRowExpansion = (index: number) => {
        const newExpanded = new Set(expandedRows);
        if (newExpanded.has(index)) {
            newExpanded.delete(index);
        } else {
            newExpanded.add(index);
        }
        setExpandedRows(newExpanded);
    };

    const formatValue = (value: any): string => {
        if (value === null || value === undefined) {
            return '-';
        }

        if (typeof value === 'object') {
            return JSON.stringify(value);
        }

        if (typeof value === 'string' && value.includes('T') && value.includes('Z')) {
            // Format ISO date strings
            try {
                return new Date(value).toLocaleString();
            } catch {
                return value;
            }
        }

        if (typeof value === 'number') {
            // Format numbers with appropriate precision
            if (value % 1 === 0) {
                return value.toString();
            } else {
                return value.toFixed(6).replace(/\.?0+$/, '');
            }
        }

        return String(value);
    };

    const getTableColumns = (data: any[]): string[] => {
        if (data.length === 0) return [];

        const allKeys = new Set<string>();
        data.forEach(row => {
            Object.keys(row).forEach(key => allKeys.add(key));
        });

        return Array.from(allKeys).sort();
    };

    const renderExpandedContent = (row: any) => {
        const entries = Object.entries(row).filter(([_key, value]) =>
            typeof value === 'object' && value !== null
        );

        if (entries.length === 0) {
            return (
                <Typography variant="body2" color="text.secondary">
                    No additional details available
                </Typography>
            );
        }

        return (
            <Box sx={{ p: 2 }}>
                {entries.map(([key, value]) => (
                    <Box key={key} sx={{ mb: 2 }}>
                        <Typography variant="subtitle2" gutterBottom>
                            {key}:
                        </Typography>
                        <Paper sx={{ p: 1, bgcolor: 'grey.50' }}>
                            <Typography variant="body2" component="pre" sx={{ whiteSpace: 'pre-wrap' }}>
                                {JSON.stringify(value, null, 2)}
                            </Typography>
                        </Paper>
                    </Box>
                ))}
            </Box>
        );
    };

    if (loading) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', py: 4 }}>
                <CircularProgress />
                <Typography variant="body1" sx={{ ml: 2 }}>
                    Executing query...
                </Typography>
            </Box>
        );
    }

    if (error) {
        return (
            <Alert severity="error" sx={{ mt: 2 }}>
                <Typography variant="body1" gutterBottom>
                    Query execution failed
                </Typography>
                <Typography variant="body2">
                    {error}
                </Typography>
            </Alert>
        );
    }

    if (!result) {
        return (
            <Box sx={{ textAlign: 'center', py: 4 }}>
                <Typography variant="body1" color="text.secondary">
                    Build a query above to see results
                </Typography>
            </Box>
        );
    }

    const columns = getTableColumns(result.data);

    return (
        <Box>
            {/* Results Summary */}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                <Chip
                    label={`${result.totalCount.toLocaleString()} total results`}
                    color="primary"
                    variant="outlined"
                />
                <Chip
                    icon={<TimeIcon />}
                    label={`${result.executionTime}ms`}
                    color="secondary"
                    variant="outlined"
                />
            </Box>

            {/* Results Table */}
            <TableContainer component={Paper}>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell width={50} />
                            {columns.map((column) => (
                                <TableCell key={column}>
                                    <Typography variant="subtitle2" noWrap>
                                        {column.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}
                                    </Typography>
                                </TableCell>
                            ))}
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {result.data.map((row, index) => (
                            <React.Fragment key={index}>
                                <TableRow hover>
                                    <TableCell>
                                        <IconButton
                                            size="small"
                                            onClick={() => toggleRowExpansion(index)}
                                        >
                                            {expandedRows.has(index) ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                                        </IconButton>
                                    </TableCell>
                                    {columns.map((column) => (
                                        <TableCell key={column}>
                                            <Typography variant="body2" noWrap>
                                                {formatValue(row[column])}
                                            </Typography>
                                        </TableCell>
                                    ))}
                                </TableRow>
                                <TableRow>
                                    <TableCell style={{ paddingBottom: 0, paddingTop: 0 }} colSpan={columns.length + 1}>
                                        <Collapse in={expandedRows.has(index)} timeout="auto" unmountOnExit>
                                            {renderExpandedContent(row)}
                                        </Collapse>
                                    </TableCell>
                                </TableRow>
                            </React.Fragment>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>

            {/* Pagination */}
            <TablePagination
                component="div"
                count={result.totalCount}
                page={page}
                onPageChange={handleChangePage}
                rowsPerPage={rowsPerPage}
                onRowsPerPageChange={handleChangeRowsPerPage}
                rowsPerPageOptions={[10, 25, 50, 100]}
            />
        </Box>
    );
};

export default QueryResults;