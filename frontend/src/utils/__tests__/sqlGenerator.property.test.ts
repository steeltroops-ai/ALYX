import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { SQLGenerator } from '../sqlGenerator'
import { QueryField, QueryOperator, QueryCondition, QueryExecutionRequest } from '../../types/query'

// Test data generators
const queryFieldGenerator = fc.record({
    name: fc.constantFrom('eventId', 'timestamp', 'energy', 'momentum', 'charge', 'position', 'detectorId'),
    label: fc.string({ minLength: 1, maxLength: 20 }),
    type: fc.constantFrom('string', 'number', 'date', 'boolean', 'geometry') as fc.Arbitrary<'string' | 'number' | 'date' | 'boolean' | 'geometry'>,
    table: fc.constantFrom('collision_events', 'detector_hits', 'particle_tracks') as fc.Arbitrary<'collision_events' | 'detector_hits' | 'particle_tracks'>
})

const queryOperatorGenerator = fc.record({
    value: fc.constantFrom('=', '!=', '<', '>', '<=', '>=', 'LIKE', 'NOT LIKE', 'IN', 'NOT IN', 'IS NULL', 'IS NOT NULL', 'BETWEEN'),
    label: fc.string({ minLength: 1, maxLength: 20 }),
    supportedTypes: fc.array(fc.constantFrom('string', 'number', 'date', 'boolean', 'geometry'), { minLength: 1, maxLength: 5 })
})

// Type-safe generators that ensure field type matches value type
const stringConditionGenerator = fc.record({
    id: fc.uuid(),
    field: fc.record({
        name: fc.constantFrom('eventId'),
        label: fc.string({ minLength: 1, maxLength: 20 }),
        type: fc.constant('string') as fc.Arbitrary<'string'>,
        table: fc.constantFrom('collision_events') as fc.Arbitrary<'collision_events'>
    }),
    operator: fc.record({
        value: fc.constantFrom('=', '!=', 'LIKE'),
        label: fc.string({ minLength: 1, maxLength: 10 }),
        supportedTypes: fc.constant(['string'])
    }),
    value: fc.string({ minLength: 1, maxLength: 50 }).filter(s =>
        !s.includes("'") &&
        !s.includes("(") &&
        !s.includes(")") &&
        !s.includes("\\") &&
        !s.includes('"') &&
        !s.includes(";") &&
        s.trim().length > 0
    ),
    logicalOperator: fc.option(fc.constantFrom('AND', 'OR'), { nil: undefined }) as fc.Arbitrary<'AND' | 'OR' | undefined>
})

const numberConditionGenerator = fc.record({
    id: fc.uuid(),
    field: fc.record({
        name: fc.constantFrom('centerOfMassEnergy', 'runNumber'),
        label: fc.string({ minLength: 1, maxLength: 20 }),
        type: fc.constant('number') as fc.Arbitrary<'number'>,
        table: fc.constantFrom('collision_events') as fc.Arbitrary<'collision_events'>
    }),
    operator: fc.record({
        value: fc.constantFrom('=', '!=', '<', '>', '<=', '>='),
        label: fc.string({ minLength: 1, maxLength: 10 }),
        supportedTypes: fc.constant(['number'])
    }),
    value: fc.integer({ min: 1, max: 10000 }),
    logicalOperator: fc.option(fc.constantFrom('AND', 'OR'), { nil: undefined }) as fc.Arbitrary<'AND' | 'OR' | undefined>
})

const queryConditionGenerator = fc.oneof(stringConditionGenerator, numberConditionGenerator)

const queryExecutionRequestGenerator = fc.record({
    conditions: fc.array(queryConditionGenerator, { minLength: 0, maxLength: 3 }),
    page: fc.option(fc.integer({ min: 1, max: 10 }), { nil: undefined }),
    pageSize: fc.option(fc.integer({ min: 10, max: 100 }), { nil: undefined }),
    orderBy: fc.option(fc.constantFrom('ce.timestamp', 'ce.event_id', 'ce.run_number'), { nil: undefined }),
    orderDirection: fc.option(fc.constantFrom('ASC', 'DESC'), { nil: undefined }) as fc.Arbitrary<'ASC' | 'DESC' | undefined>
})

// SQL validation utilities
class SQLValidator {
    static isValidSQL(sql: string): boolean {
        if (!sql || sql.trim().length === 0) {
            return false
        }

        // Basic SQL structure validation
        const upperSQL = sql.toUpperCase()

        // Must start with SELECT
        if (!upperSQL.trim().startsWith('SELECT')) {
            return false
        }

        // Must have FROM clause
        if (!upperSQL.includes('FROM')) {
            return false
        }

        // Check for balanced parentheses
        let parenCount = 0
        for (const char of sql) {
            if (char === '(') parenCount++
            if (char === ')') parenCount--
            if (parenCount < 0) return false
        }
        if (parenCount !== 0) return false

        // Check for balanced quotes
        let singleQuoteCount = 0
        let inQuote = false
        for (let i = 0; i < sql.length; i++) {
            if (sql[i] === "'" && (i === 0 || sql[i - 1] !== '\\')) {
                if (inQuote && i + 1 < sql.length && sql[i + 1] === "'") {
                    // Escaped quote
                    i++ // Skip next quote
                } else {
                    inQuote = !inQuote
                    singleQuoteCount++
                }
            }
        }
        if (singleQuoteCount % 2 !== 0) return false

        return true
    }

    static hasRequiredClauses(sql: string): boolean {
        const upperSQL = sql.toUpperCase()

        // Must have SELECT, FROM, and LIMIT
        return upperSQL.includes('SELECT') &&
            upperSQL.includes('FROM') &&
            upperSQL.includes('LIMIT')
    }

    static hasValidTableReferences(sql: string): boolean {
        const upperSQL = sql.toUpperCase()

        // Should reference known tables
        const validTables = ['COLLISION_EVENTS', 'DETECTOR_HITS', 'PARTICLE_TRACKS']
        return validTables.some(table => upperSQL.includes(table))
    }

    static hasValidFieldReferences(sql: string): boolean {
        // Check for common field patterns
        const fieldPatterns = [
            /\bce\.\w+/i,  // collision_events fields
            /\bdh\.\w+/i,  // detector_hits fields
            /\bpt\.\w+/i   // particle_tracks fields
        ]

        return fieldPatterns.some(pattern => pattern.test(sql))
    }
}

describe('SQL Generator Property Tests', () => {
    describe('Property 5: Query generation correctness', () => {
        /**
         * **Feature: alyx-system-fix, Property 5: Query generation correctness**
         * **Validates: Requirements 3.3**
         * 
         * For any valid query builder input, the generated SQL should be syntactically correct 
         * and execute successfully against the database
         */
        it('should generate syntactically correct SQL for valid query conditions', () => {
            fc.assert(
                fc.property(
                    queryExecutionRequestGenerator.filter(request => {
                        // Only test with valid conditions (field and operator types match and values are valid)
                        return request.conditions.every(condition => {
                            if (!condition.field || !condition.operator) {
                                return false
                            }

                            // Check operator supports field type
                            if (!condition.operator.supportedTypes.includes(condition.field.type)) {
                                return false
                            }

                            // Check value is provided for operators that need it
                            if (!['IS NULL', 'IS NOT NULL'].includes(condition.operator.value)) {
                                if (condition.value === null || condition.value === undefined || condition.value === '') {
                                    return false
                                }
                            }

                            // Check value type matches field type
                            if (condition.value !== null && condition.value !== undefined && condition.value !== '') {
                                switch (condition.field.type) {
                                    case 'number':
                                        return !isNaN(Number(condition.value))
                                    case 'date':
                                        return !isNaN(Date.parse(condition.value))
                                    case 'boolean':
                                        return typeof condition.value === 'boolean' ||
                                            ['true', 'false', '1', '0'].includes(String(condition.value).toLowerCase())
                                    case 'string':
                                        return typeof condition.value === 'string' && condition.value.length > 0
                                    default:
                                        return true
                                }
                            }

                            return true
                        })
                    }),
                    (request) => {
                        // Generate SQL
                        const result = SQLGenerator.generateSQL(request)

                        // Property: Should generate SQL without validation errors
                        const noValidationErrors = result.errors.length === 0

                        // Property: Generated SQL should be syntactically valid
                        const syntacticallyValid = SQLValidator.isValidSQL(result.sql)

                        // Property: Should have required SQL clauses
                        const hasRequiredClauses = SQLValidator.hasRequiredClauses(result.sql)

                        // Property: Should reference valid tables
                        const hasValidTables = SQLValidator.hasValidTableReferences(result.sql)

                        // Property: Should reference valid fields when conditions exist
                        const hasValidFields = request.conditions.length === 0 ||
                            SQLValidator.hasValidFieldReferences(result.sql)

                        return noValidationErrors &&
                            syntacticallyValid &&
                            hasRequiredClauses &&
                            hasValidTables &&
                            hasValidFields
                    }
                ),
                { numRuns: 100 }
            )
        })

        it('should handle edge cases correctly', () => {
            fc.assert(
                fc.property(
                    fc.oneof(
                        // Empty conditions
                        fc.record({
                            conditions: fc.constant([]) as fc.Arbitrary<QueryCondition[]>,
                            page: fc.option(fc.integer({ min: 1, max: 10 }), { nil: undefined }),
                            pageSize: fc.option(fc.integer({ min: 10, max: 100 }), { nil: undefined }),
                            orderBy: fc.option(fc.constantFrom('ce.timestamp', 'ce.event_id'), { nil: undefined }),
                            orderDirection: fc.option(fc.constantFrom('ASC', 'DESC'), { nil: undefined }) as fc.Arbitrary<'ASC' | 'DESC' | undefined>
                        }),
                        // Single string condition
                        fc.record({
                            conditions: fc.array(stringConditionGenerator, { minLength: 1, maxLength: 1 }),
                            page: fc.option(fc.integer({ min: 1, max: 10 }), { nil: undefined }),
                            pageSize: fc.option(fc.integer({ min: 10, max: 100 }), { nil: undefined }),
                            orderBy: fc.option(fc.constantFrom('ce.timestamp', 'ce.event_id'), { nil: undefined }),
                            orderDirection: fc.option(fc.constantFrom('ASC', 'DESC'), { nil: undefined }) as fc.Arbitrary<'ASC' | 'DESC' | undefined>
                        }),
                        // Single number condition
                        fc.record({
                            conditions: fc.array(numberConditionGenerator, { minLength: 1, maxLength: 1 }),
                            page: fc.option(fc.integer({ min: 1, max: 10 }), { nil: undefined }),
                            pageSize: fc.option(fc.integer({ min: 10, max: 100 }), { nil: undefined }),
                            orderBy: fc.option(fc.constantFrom('ce.timestamp', 'ce.event_id'), { nil: undefined }),
                            orderDirection: fc.option(fc.constantFrom('ASC', 'DESC'), { nil: undefined }) as fc.Arbitrary<'ASC' | 'DESC' | undefined>
                        })
                    ),
                    (request) => {
                        // Generate SQL
                        const result = SQLGenerator.generateSQL(request)

                        // Property: Should always generate valid SQL for edge cases
                        const syntacticallyValid = SQLValidator.isValidSQL(result.sql)

                        // Property: Should have required clauses
                        const hasRequiredClauses = SQLValidator.hasRequiredClauses(result.sql)

                        // Property: Should handle pagination correctly
                        const hasPagination = result.sql.toUpperCase().includes('LIMIT')

                        return syntacticallyValid && hasRequiredClauses && hasPagination
                    }
                ),
                { numRuns: 100 }
            )
        })

        it('should validate input conditions correctly', () => {
            // Test with invalid conditions (mismatched field and operator types)
            const invalidCondition: QueryCondition = {
                id: 'test-id',
                field: {
                    name: 'timestamp',
                    label: 'Timestamp',
                    type: 'date',
                    table: 'collision_events'
                },
                operator: {
                    value: 'LIKE',
                    label: 'Contains',
                    supportedTypes: ['string'] // Doesn't support date type
                },
                value: '2023-01-01',
                logicalOperator: 'AND'
            }

            const invalidRequest: QueryExecutionRequest = {
                conditions: [invalidCondition]
            }

            const result = SQLGenerator.generateSQL(invalidRequest)

            // Should return validation errors
            expect(result.errors.length).toBeGreaterThan(0)
            expect(result.sql).toBe('')

            // Test with missing required values
            const missingValueCondition: QueryCondition = {
                id: 'test-id-2',
                field: {
                    name: 'energy',
                    label: 'Energy',
                    type: 'number',
                    table: 'detector_hits'
                },
                operator: {
                    value: '=',
                    label: 'Equals',
                    supportedTypes: ['number']
                },
                value: null, // Missing required value
                logicalOperator: 'AND'
            }

            const missingValueRequest: QueryExecutionRequest = {
                conditions: [missingValueCondition]
            }

            const result2 = SQLGenerator.generateSQL(missingValueRequest)

            // Should return validation errors
            expect(result2.errors.length).toBeGreaterThan(0)
            expect(result2.sql).toBe('')
        })

        it('should generate correct SQL structure for different table combinations', () => {
            // Test collision_events only
            const collisionEventsRequest: QueryExecutionRequest = {
                conditions: [{
                    id: 'test-1',
                    field: { name: 'eventId', label: 'Event ID', type: 'string', table: 'collision_events' },
                    operator: { value: '=', label: 'Equals', supportedTypes: ['string'] },
                    value: 'test-event-123'
                }]
            }

            const result1 = SQLGenerator.generateSQL(collisionEventsRequest)
            expect(result1.errors.length).toBe(0)
            expect(result1.sql.toUpperCase()).toContain('FROM COLLISION_EVENTS CE')
            expect(result1.sql.toUpperCase()).not.toContain('JOIN')

            // Test with detector_hits (should include JOIN)
            const detectorHitsRequest: QueryExecutionRequest = {
                conditions: [{
                    id: 'test-2',
                    field: { name: 'energy', label: 'Energy', type: 'number', table: 'detector_hits' },
                    operator: { value: '>', label: 'Greater than', supportedTypes: ['number'] },
                    value: 100
                }]
            }

            const result2 = SQLGenerator.generateSQL(detectorHitsRequest)
            expect(result2.errors.length).toBe(0)
            expect(result2.sql.toUpperCase()).toContain('FROM DETECTOR_HITS DH')
            expect(result2.sql.toUpperCase()).toContain('JOIN COLLISION_EVENTS CE')
        })
    })
})