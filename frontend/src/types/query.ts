export interface QueryField {
    name: string;
    label: string;
    type: 'string' | 'number' | 'date' | 'boolean' | 'geometry';
    table: 'collision_events' | 'detector_hits' | 'particle_tracks';
}

export interface QueryCondition {
    id: string;
    field: QueryField;
    operator: QueryOperator;
    value: any;
    logicalOperator?: 'AND' | 'OR';
}

export interface QueryOperator {
    value: string;
    label: string;
    supportedTypes: string[];
}

export interface QueryResult {
    data: any[];
    totalCount: number;
    page: number;
    pageSize: number;
    executionTime: number;
}

export interface SavedQuery {
    id: string;
    name: string;
    description?: string;
    conditions: QueryCondition[];
    createdAt: Date;
    updatedAt: Date;
}

export interface QueryValidationError {
    field: string;
    message: string;
}

export interface QueryExecutionRequest {
    conditions: QueryCondition[];
    page?: number;
    pageSize?: number;
    orderBy?: string;
    orderDirection?: 'ASC' | 'DESC';
}

export interface QueryExecutionResponse {
    success: boolean;
    data?: QueryResult;
    errors?: QueryValidationError[];
    sql?: string;
}