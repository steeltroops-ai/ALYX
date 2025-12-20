import { QueryCondition, QueryExecutionRequest, QueryValidationError } from '../types/query';

export class SQLGenerator {

    static generateSQL(request: QueryExecutionRequest): { sql: string; errors: QueryValidationError[] } {
        // Validate conditions
        const validationErrors = this.validateConditions(request.conditions);
        if (validationErrors.length > 0) {
            return { sql: '', errors: validationErrors };
        }

        // Determine primary table based on conditions
        const primaryTable = this.determinePrimaryTable(request.conditions);

        // Build SELECT clause
        const selectClause = this.buildSelectClause(primaryTable, request.conditions);

        // Build FROM clause with JOINs
        const fromClause = this.buildFromClause(primaryTable, request.conditions);

        // Build WHERE clause
        const whereClause = this.buildWhereClause(request.conditions);

        // Build ORDER BY clause
        const orderByClause = this.buildOrderByClause(request.orderBy, request.orderDirection);

        // Build LIMIT and OFFSET
        const limitClause = this.buildLimitClause(request.page, request.pageSize);

        // Combine all clauses
        const sql = [
            selectClause,
            fromClause,
            whereClause,
            orderByClause,
            limitClause
        ].filter(clause => clause.length > 0).join('\n');

        return { sql, errors: [] };
    }

    private static validateConditions(conditions: QueryCondition[]): QueryValidationError[] {
        const errors: QueryValidationError[] = [];

        conditions.forEach((condition, index) => {
            // Check if field is selected
            if (!condition.field) {
                errors.push({
                    field: `condition_${index}`,
                    message: 'Field is required'
                });
            }

            // Check if operator is selected
            if (!condition.operator) {
                errors.push({
                    field: `condition_${index}`,
                    message: 'Operator is required'
                });
            }

            // Check if operator supports field type
            if (condition.field && condition.operator) {
                if (!condition.operator.supportedTypes.includes(condition.field.type)) {
                    errors.push({
                        field: `condition_${index}`,
                        message: `Operator "${condition.operator.label}" is not supported for ${condition.field.type} fields`
                    });
                }
            }

            // Check if value is provided for operators that need it
            if (condition.operator && !['IS NULL', 'IS NOT NULL'].includes(condition.operator.value)) {
                if (condition.value === null || condition.value === undefined || condition.value === '') {
                    errors.push({
                        field: `condition_${index}`,
                        message: 'Value is required for this operator'
                    });
                }
            }

            // Validate value format based on field type
            if (condition.field && condition.value !== null && condition.value !== undefined && condition.value !== '') {
                const validationError = this.validateFieldValue(condition.field.type, condition.value);
                if (validationError) {
                    errors.push({
                        field: `condition_${index}`,
                        message: validationError
                    });
                }
            }
        });

        return errors;
    }

    private static validateFieldValue(fieldType: string, value: any): string | null {
        switch (fieldType) {
            case 'number':
                if (isNaN(Number(value))) {
                    return 'Value must be a valid number';
                }
                break;
            case 'date':
                if (isNaN(Date.parse(value))) {
                    return 'Value must be a valid date';
                }
                break;
            case 'boolean':
                if (typeof value !== 'boolean' && !['true', 'false', '1', '0'].includes(String(value).toLowerCase())) {
                    return 'Value must be true or false';
                }
                break;
        }
        return null;
    }

    private static determinePrimaryTable(conditions: QueryCondition[]): string {
        // Count conditions by table
        const tableCounts = conditions.reduce((acc, condition) => {
            if (condition.field) {
                acc[condition.field.table] = (acc[condition.field.table] || 0) + 1;
            }
            return acc;
        }, {} as Record<string, number>);

        // Return table with most conditions, defaulting to collision_events
        const primaryTable = Object.keys(tableCounts).reduce((a, b) =>
            tableCounts[a] > tableCounts[b] ? a : b, 'collision_events'
        );

        return primaryTable;
    }

    private static buildSelectClause(primaryTable: string, conditions: QueryCondition[]): string {
        // Get unique tables involved
        const tables = new Set([primaryTable]);
        conditions.forEach(condition => {
            if (condition.field) {
                tables.add(condition.field.table);
            }
        });

        // Build select fields based on primary table
        switch (primaryTable) {
            case 'collision_events':
                return `SELECT ce.event_id, ce.timestamp, ce.center_of_mass_energy, ce.run_number, ce.event_number,
                       ce.collision_vertex, ce.luminosity, ce.beam_energy_1, ce.beam_energy_2,
                       ce.trigger_mask, ce.data_quality_flags, ce.reconstruction_version`;
            case 'detector_hits':
                return `SELECT dh.hit_id, dh.detector_id, dh.energy_deposit, dh.hit_time, dh.position,
                       dh.signal_amplitude, dh.uncertainty, ce.event_id, ce.timestamp`;
            case 'particle_tracks':
                return `SELECT pt.track_id, pt.particle_type, pt.momentum, pt.charge, pt.trajectory,
                       pt.confidence_level, pt.chi_squared, pt.degrees_of_freedom, ce.event_id, ce.timestamp`;
            default:
                return `SELECT ce.*`;
        }
    }

    private static buildFromClause(primaryTable: string, conditions: QueryCondition[]): string {
        const tables = new Set([primaryTable]);
        conditions.forEach(condition => {
            if (condition.field) {
                tables.add(condition.field.table);
            }
        });

        let fromClause = '';

        switch (primaryTable) {
            case 'collision_events':
                fromClause = 'FROM collision_events ce';
                if (tables.has('detector_hits')) {
                    fromClause += '\n  LEFT JOIN detector_hits dh ON ce.event_id = dh.event_id';
                }
                if (tables.has('particle_tracks')) {
                    fromClause += '\n  LEFT JOIN particle_tracks pt ON ce.event_id = pt.event_id';
                }
                break;
            case 'detector_hits':
                fromClause = 'FROM detector_hits dh\n  JOIN collision_events ce ON dh.event_id = ce.event_id';
                if (tables.has('particle_tracks')) {
                    fromClause += '\n  LEFT JOIN particle_tracks pt ON ce.event_id = pt.event_id';
                }
                break;
            case 'particle_tracks':
                fromClause = 'FROM particle_tracks pt\n  JOIN collision_events ce ON pt.event_id = ce.event_id';
                if (tables.has('detector_hits')) {
                    fromClause += '\n  LEFT JOIN detector_hits dh ON ce.event_id = dh.event_id';
                }
                break;
        }

        return fromClause;
    }

    private static buildWhereClause(conditions: QueryCondition[]): string {
        if (conditions.length === 0) {
            return '';
        }

        const whereConditions = conditions.map((condition, index) => {
            const tableAlias = this.getTableAlias(condition.field.table);
            const fieldName = `${tableAlias}.${this.convertFieldName(condition.field.name)}`;
            const operator = condition.operator.value;

            let conditionSQL = '';

            switch (operator) {
                case 'IS NULL':
                case 'IS NOT NULL':
                    conditionSQL = `${fieldName} ${operator}`;
                    break;
                case 'LIKE':
                case 'NOT LIKE':
                    const likeValue = this.formatValue(condition.field.type, condition.value, operator);
                    conditionSQL = `${fieldName} ${operator} '%${likeValue}%'`;
                    break;
                case 'IN':
                case 'NOT IN':
                    const values = Array.isArray(condition.value) ? condition.value : [condition.value];
                    const formattedValues = values.map(v => this.formatValue(condition.field.type, v, '=')).join(', ');
                    conditionSQL = `${fieldName} ${operator} (${formattedValues})`;
                    break;
                case 'BETWEEN':
                    if (Array.isArray(condition.value) && condition.value.length === 2) {
                        const val1 = this.formatValue(condition.field.type, condition.value[0], '=');
                        const val2 = this.formatValue(condition.field.type, condition.value[1], '=');
                        conditionSQL = `${fieldName} BETWEEN ${val1} AND ${val2}`;
                    }
                    break;
                case 'ST_DWithin':
                    if (Array.isArray(condition.value) && condition.value.length === 3) {
                        const [x, y, distance] = condition.value;
                        conditionSQL = `ST_DWithin(${fieldName}, ST_Point(${x}, ${y}), ${distance})`;
                    }
                    break;
                case 'ST_Contains':
                    const containsValue = this.formatValue(condition.field.type, condition.value, operator);
                    conditionSQL = `ST_Contains(${fieldName}, ${containsValue})`;
                    break;
                default:
                    const defaultValue = this.formatValue(condition.field.type, condition.value, operator);
                    conditionSQL = `${fieldName} ${operator} ${defaultValue}`;
            }

            // Add logical operator for non-first conditions
            if (index > 0) {
                const logicalOp = condition.logicalOperator || 'AND';
                conditionSQL = `${logicalOp} ${conditionSQL}`;
            }

            return conditionSQL;
        });

        return `WHERE ${whereConditions.join('\n  ')}`;
    }

    private static buildOrderByClause(orderBy?: string, orderDirection?: 'ASC' | 'DESC'): string {
        if (!orderBy) {
            return 'ORDER BY ce.timestamp DESC';
        }

        const direction = orderDirection || 'ASC';
        return `ORDER BY ${orderBy} ${direction}`;
    }

    private static buildLimitClause(page?: number, pageSize?: number): string {
        const limit = pageSize || 100;
        const offset = page ? (page - 1) * limit : 0;

        return `LIMIT ${limit} OFFSET ${offset}`;
    }

    private static getTableAlias(table: string): string {
        switch (table) {
            case 'collision_events': return 'ce';
            case 'detector_hits': return 'dh';
            case 'particle_tracks': return 'pt';
            default: return 'ce';
        }
    }

    private static convertFieldName(fieldName: string): string {
        // Convert camelCase to snake_case for database fields
        return fieldName.replace(/([A-Z])/g, '_$1').toLowerCase();
    }

    private static formatValue(fieldType: string, value: any, operator: string): string {
        if (value === null || value === undefined) {
            return 'NULL';
        }

        switch (fieldType) {
            case 'string':
                return `'${String(value).replace(/'/g, "''")}'`;
            case 'number':
                return String(Number(value));
            case 'date':
                const dateValue = new Date(value);
                if (isNaN(dateValue.getTime())) {
                    throw new Error(`Invalid date value: ${value}`);
                }
                return `'${dateValue.toISOString()}'`;
            case 'boolean':
                return String(Boolean(value));
            case 'geometry':
                if (operator.startsWith('ST_')) {
                    return String(value);
                }
                return `'${String(value)}'`;
            default:
                return `'${String(value)}'`;
        }
    }
}