import React from 'react';
import {
    Box,
    Card,
    CardContent,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    TextField,
    IconButton,
    Chip,
    Typography,
    Autocomplete,
} from '@mui/material';
import { Delete as DeleteIcon, DragIndicator as DragIcon } from '@mui/icons-material';
import { useDrag, useDrop } from 'react-dnd';
import { QueryCondition as QueryConditionType, QueryField, QueryOperator } from '../../types/query';
import { QUERY_OPERATORS } from '../../data/queryFields';

interface QueryConditionProps {
    condition: QueryConditionType;
    index: number;
    availableFields: QueryField[];
    onUpdate: (index: number, condition: QueryConditionType) => void;
    onDelete: (index: number) => void;
    onMove: (dragIndex: number, hoverIndex: number) => void;
    showLogicalOperator: boolean;
}

const QueryCondition: React.FC<QueryConditionProps> = ({
    condition,
    index,
    availableFields,
    onUpdate,
    onDelete,
    onMove,
    showLogicalOperator,
}) => {
    const [{ isDragging }, drag] = useDrag({
        type: 'condition',
        item: { index },
        collect: (monitor) => ({
            isDragging: monitor.isDragging(),
        }),
    });

    const [, drop] = useDrop({
        accept: 'condition',
        hover: (item: { index: number }) => {
            if (item.index !== index) {
                onMove(item.index, index);
                item.index = index;
            }
        },
    });

    const handleFieldChange = (field: QueryField | null) => {
        const updatedCondition = {
            ...condition,
            field: field!,
            operator: {} as QueryOperator,
            value: null,
        };
        onUpdate(index, updatedCondition);
    };

    const handleOperatorChange = (operatorValue: string) => {
        const operator = QUERY_OPERATORS.find(op => op.value === operatorValue);
        if (operator) {
            const updatedCondition = {
                ...condition,
                operator,
                value: ['IS NULL', 'IS NOT NULL'].includes(operator.value) ? null : condition.value,
            };
            onUpdate(index, updatedCondition);
        }
    };

    const handleValueChange = (value: any) => {
        const updatedCondition = {
            ...condition,
            value,
        };
        onUpdate(index, updatedCondition);
    };

    const handleLogicalOperatorChange = (logicalOperator: 'AND' | 'OR') => {
        const updatedCondition = {
            ...condition,
            logicalOperator,
        };
        onUpdate(index, updatedCondition);
    };

    const getAvailableOperators = (): QueryOperator[] => {
        if (!condition.field) return [];
        return QUERY_OPERATORS.filter(op =>
            op.supportedTypes.includes(condition.field.type)
        );
    };

    const renderValueInput = () => {
        if (!condition.operator || ['IS NULL', 'IS NOT NULL'].includes(condition.operator.value)) {
            return null;
        }

        const { field, operator } = condition;
        if (!field) return null;

        switch (operator.value) {
            case 'IN':
            case 'NOT IN':
                return (
                    <Autocomplete
                        multiple
                        freeSolo
                        options={[]}
                        value={Array.isArray(condition.value) ? condition.value : []}
                        onChange={(_, newValue) => handleValueChange(newValue)}
                        renderTags={(value, getTagProps) =>
                            value.map((option, index) => (
                                <Chip variant="outlined" label={option} {...getTagProps({ index })} />
                            ))
                        }
                        renderInput={(params) => (
                            <TextField
                                {...params}
                                label="Values"
                                placeholder="Enter values and press Enter"
                                size="small"
                            />
                        )}
                    />
                );

            case 'BETWEEN':
                const betweenValues = Array.isArray(condition.value) ? condition.value : ['', ''];
                return (
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                        <TextField
                            label="From"
                            type={field.type === 'number' ? 'number' : field.type === 'date' ? 'datetime-local' : 'text'}
                            value={betweenValues[0] || ''}
                            onChange={(e) => handleValueChange([e.target.value, betweenValues[1]])}
                            size="small"
                            sx={{ flex: 1 }}
                        />
                        <Typography variant="body2">and</Typography>
                        <TextField
                            label="To"
                            type={field.type === 'number' ? 'number' : field.type === 'date' ? 'datetime-local' : 'text'}
                            value={betweenValues[1] || ''}
                            onChange={(e) => handleValueChange([betweenValues[0], e.target.value])}
                            size="small"
                            sx={{ flex: 1 }}
                        />
                    </Box>
                );

            case 'ST_DWithin':
                const spatialValues = Array.isArray(condition.value) ? condition.value : ['', '', ''];
                return (
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                        <TextField
                            label="X"
                            type="number"
                            value={spatialValues[0] || ''}
                            onChange={(e) => handleValueChange([e.target.value, spatialValues[1], spatialValues[2]])}
                            size="small"
                            sx={{ flex: 1 }}
                        />
                        <TextField
                            label="Y"
                            type="number"
                            value={spatialValues[1] || ''}
                            onChange={(e) => handleValueChange([spatialValues[0], e.target.value, spatialValues[2]])}
                            size="small"
                            sx={{ flex: 1 }}
                        />
                        <TextField
                            label="Distance"
                            type="number"
                            value={spatialValues[2] || ''}
                            onChange={(e) => handleValueChange([spatialValues[0], spatialValues[1], e.target.value])}
                            size="small"
                            sx={{ flex: 1 }}
                        />
                    </Box>
                );

            default:
                return (
                    <TextField
                        label="Value"
                        type={field.type === 'number' ? 'number' : field.type === 'date' ? 'datetime-local' : 'text'}
                        value={condition.value || ''}
                        onChange={(e) => handleValueChange(e.target.value)}
                        size="small"
                        fullWidth
                    />
                );
        }
    };

    return (
        <Box ref={(node) => drag(drop(node as any))}>
            {showLogicalOperator && (
                <Box sx={{ display: 'flex', justifyContent: 'center', mb: 1 }}>
                    <FormControl size="small" sx={{ minWidth: 80 }}>
                        <Select
                            value={condition.logicalOperator || 'AND'}
                            onChange={(e) => handleLogicalOperatorChange(e.target.value as 'AND' | 'OR')}
                        >
                            <MenuItem value="AND">AND</MenuItem>
                            <MenuItem value="OR">OR</MenuItem>
                        </Select>
                    </FormControl>
                </Box>
            )}

            <Card
                sx={{
                    opacity: isDragging ? 0.5 : 1,
                    cursor: 'move',
                    mb: 2,
                    border: '1px solid',
                    borderColor: 'divider',
                }}
            >
                <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
                        <IconButton size="small" sx={{ mt: 1, cursor: 'grab' }}>
                            <DragIcon />
                        </IconButton>

                        <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
                            <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start' }}>
                                <FormControl sx={{ minWidth: 200 }} size="small">
                                    <InputLabel>Field</InputLabel>
                                    <Select
                                        value={condition.field?.name || ''}
                                        label="Field"
                                        onChange={(e) => {
                                            const field = availableFields.find(f => f.name === e.target.value);
                                            handleFieldChange(field || null);
                                        }}
                                    >
                                        {availableFields.map((field) => (
                                            <MenuItem key={`${field.table}.${field.name}`} value={field.name}>
                                                <Box>
                                                    <Typography variant="body2">{field.label}</Typography>
                                                    <Typography variant="caption" color="text.secondary">
                                                        {field.table}
                                                    </Typography>
                                                </Box>
                                            </MenuItem>
                                        ))}
                                    </Select>
                                </FormControl>

                                <FormControl sx={{ minWidth: 150 }} size="small">
                                    <InputLabel>Operator</InputLabel>
                                    <Select
                                        value={condition.operator?.value || ''}
                                        label="Operator"
                                        onChange={(e) => handleOperatorChange(e.target.value)}
                                        disabled={!condition.field}
                                    >
                                        {getAvailableOperators().map((operator) => (
                                            <MenuItem key={operator.value} value={operator.value}>
                                                {operator.label}
                                            </MenuItem>
                                        ))}
                                    </Select>
                                </FormControl>

                                <Box sx={{ flex: 1, minWidth: 200 }}>
                                    {renderValueInput()}
                                </Box>
                            </Box>
                        </Box>

                        <IconButton
                            size="small"
                            onClick={() => onDelete(index)}
                            color="error"
                            sx={{ mt: 1 }}
                        >
                            <DeleteIcon />
                        </IconButton>
                    </Box>
                </CardContent>
            </Card>
        </Box>
    );
};

export default QueryCondition;