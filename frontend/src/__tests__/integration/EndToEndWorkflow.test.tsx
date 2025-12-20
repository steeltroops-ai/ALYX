import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { configureStore } from '@reduxjs/toolkit';
import userEvent from '@testing-library/user-event';
import { vi, beforeEach, afterEach, describe, it, expect } from 'vitest';

import App from '../../App';
import authReducer from '../../store/slices/authSlice';
import * as websocketService from '../../services/websocket';

/**
 * Integration tests for complete end-to-end workflows in ALYX frontend.
 * Tests complete user journeys from authentication to job submission to visualization,
 * multi-user collaboration scenarios, and real-time data updates.
 * 
 * Requirements: 1.1, 2.1, 5.1
 */

// Mock the websocket service
vi.mock('../../services/websocket', () => ({
    default: {
        connect: vi.fn(),
        disconnect: vi.fn(),
        isConnected: vi.fn(() => true),
        emit: vi.fn(),
        on: vi.fn(),
        off: vi.fn(),
    },
}));

// Mock fetch for API calls
const mockFetch = vi.fn();
global.fetch = mockFetch;

const theme = createTheme();

const createTestStore = (initialState = {}) => {
    return configureStore({
        reducer: {
            auth: authReducer,
        },
        preloadedState: {
            auth: {
                user: null,
                token: null,
                isAuthenticated: false,
                isLoading: false,
                error: null,
                ...(initialState as any).auth,
            },
        },
    });
};

const renderApp = (initialState = {}) => {
    const store = createTestStore(initialState);
    const user = userEvent.setup();

    const utils = render(
        <Provider store={store}>
            <BrowserRouter>
                <ThemeProvider theme={theme}>
                    <App />
                </ThemeProvider>
            </BrowserRouter>
        </Provider>
    );

    return { ...utils, store, user };
};

describe('End-to-End Workflow Integration Tests', () => {
    beforeEach(() => {
        vi.clearAllMocks();

        // Setup default successful responses
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/api/auth/login')) {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({
                        token: 'test-jwt-token',
                        user: {
                            id: '1',
                            username: 'physicist',
                            email: 'physicist@alyx.org',
                            role: 'physicist',
                            permissions: ['read', 'write', 'analyze'],
                        },
                    }),
                });
            }

            if (url.includes('/api/jobs/submit')) {
                return Promise.resolve({
                    ok: true,
                    status: 201,
                    json: () => Promise.resolve({
                        jobId: 'test-job-123',
                        estimatedCompletion: '2024-01-01T13:00:00Z',
                        status: 'QUEUED',
                    }),
                });
            }

            if (url.includes('/api/jobs/') && url.includes('/status')) {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({
                        jobId: 'test-job-123',
                        status: 'COMPLETED',
                        progress: 100,
                        allocatedCores: 16,
                        memoryAllocationMB: 32768,
                    }),
                });
            }

            if (url.includes('/api/visualization/')) {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({
                        events: [
                            {
                                eventId: 'event-1',
                                trajectories: [
                                    { particleType: 'muon', path: [[0, 0, 0], [1, 1, 1]] },
                                ],
                                detectorHits: [
                                    { detectorId: 'CENTRAL_1', position: [1.5, 2.1, 0.5], energy: 150.0 },
                                ],
                            },
                        ],
                        renderingMetadata: {
                            totalEvents: 1,
                            renderTime: 1200,
                        },
                    }),
                });
            }

            if (url.includes('/api/collaboration/sessions')) {
                if (url.includes('POST')) {
                    return Promise.resolve({
                        ok: true,
                        status: 201,
                        json: () => Promise.resolve({
                            sessionId: 'session-123',
                            sessionName: 'Test Session',
                            participants: ['physicist@alyx.org'],
                        }),
                    });
                } else {
                    return Promise.resolve({
                        ok: true,
                        json: () => Promise.resolve({
                            sessionId: 'session-123',
                            participants: [
                                { userId: 'physicist@alyx.org', role: 'OWNER' },
                                { userId: 'physicist2@alyx.org', role: 'PARTICIPANT' },
                            ],
                            sharedState: {
                                parameters: {
                                    energyThreshold: 2500,
                                    detectorRegions: ['CENTRAL'],
                                },
                            },
                        }),
                    });
                }
            }

            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve({}),
            });
        });
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    /**
     * Test complete user journey: Authentication -> Job Submission -> Monitoring -> Visualization
     * Validates Requirements 1.1, 2.1
     */
    it('should complete full analysis workflow from login to visualization', async () => {
        const { user } = renderApp();

        // Step 1: User authentication
        const emailInput = await screen.findByLabelText(/email/i);
        const passwordInput = await screen.findByLabelText(/password/i);
        const loginButton = await screen.findByRole('button', { name: /sign in/i });

        await user.type(emailInput, 'physicist@alyx.org');
        await user.type(passwordInput, 'test_password');
        await user.click(loginButton);

        // Wait for authentication to complete
        await waitFor(() => {
            expect(screen.getByText(/dashboard/i)).toBeInTheDocument();
        });

        // Step 2: Navigate to job submission
        const jobsNavItem = await screen.findByText(/jobs/i);
        await user.click(jobsNavItem);

        // Step 3: Submit analysis job
        const submitJobButton = await screen.findByRole('button', { name: /submit.*job/i });
        await user.click(submitJobButton);

        // Fill job parameters
        const analysisTypeSelect = await screen.findByLabelText(/analysis.*type/i);
        await user.click(analysisTypeSelect);

        const particleReconOption = await screen.findByText(/particle.*reconstruction/i);
        await user.click(particleReconOption);

        const energyMinInput = await screen.findByLabelText(/energy.*min/i);
        await user.type(energyMinInput, '1000');

        const energyMaxInput = await screen.findByLabelText(/energy.*max/i);
        await user.type(energyMaxInput, '5000');

        const submitButton = await screen.findByRole('button', { name: /submit/i });
        await user.click(submitButton);

        // Step 4: Verify job submission success
        await waitFor(() => {
            expect(screen.getByText(/job.*submitted.*successfully/i)).toBeInTheDocument();
            expect(screen.getByText(/test-job-123/)).toBeInTheDocument();
        });

        // Step 5: Monitor job status
        const jobStatusCard = await screen.findByTestId('job-status-test-job-123');
        expect(jobStatusCard).toBeInTheDocument();

        // Wait for job completion status update
        await waitFor(() => {
            expect(screen.getByText(/completed/i)).toBeInTheDocument();
        }, { timeout: 5000 });

        // Step 6: Navigate to visualization
        const visualizationNavItem = await screen.findByText(/visualization/i);
        await user.click(visualizationNavItem);

        // Step 7: Load visualization data
        const loadVisualizationButton = await screen.findByRole('button', { name: /load.*visualization/i });
        await user.click(loadVisualizationButton);

        // Step 8: Verify visualization renders
        await waitFor(() => {
            const canvas = screen.getByTestId('visualization-canvas');
            expect(canvas).toBeInTheDocument();
        });

        // Verify visualization controls are available
        expect(screen.getByLabelText(/rotate/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/zoom/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/pan/i)).toBeInTheDocument();

        // Verify performance requirement (2 second rendering)
        const renderTimeElement = await screen.findByTestId('render-time');
        const renderTime = parseInt(renderTimeElement.textContent || '0');
        expect(renderTime).toBeLessThan(2000); // Requirement 2.1
    });

    /**
     * Test multi-user collaboration workflow
     * Validates Requirement 5.1
     */
    it('should handle multi-user collaboration scenarios', async () => {
        const { user } = renderApp({
            auth: {
                user: {
                    id: '1',
                    username: 'physicist',
                    email: 'physicist@alyx.org',
                    role: 'physicist',
                    permissions: ['read', 'write', 'collaborate'],
                },
                token: 'test-jwt-token',
                isAuthenticated: true,
            },
        });

        // Step 1: Navigate to collaboration
        const collaborationNavItem = await screen.findByText(/collaboration/i);
        await user.click(collaborationNavItem);

        // Step 2: Create collaboration session
        const createSessionButton = await screen.findByRole('button', { name: /create.*session/i });
        await user.click(createSessionButton);

        const sessionNameInput = await screen.findByLabelText(/session.*name/i);
        await user.type(sessionNameInput, 'Physics Analysis Session');

        const createButton = await screen.findByRole('button', { name: /create/i });
        await user.click(createButton);

        // Step 3: Verify session creation
        await waitFor(() => {
            expect(screen.getByText(/session.*created/i)).toBeInTheDocument();
            expect(screen.getByText(/session-123/)).toBeInTheDocument();
        });

        // Step 4: Simulate another user joining (via WebSocket)
        const mockWebSocket = websocketService.default;
        const onCallback = vi.mocked(mockWebSocket.on).mock.calls.find(
            call => call[0] === 'user-joined'
        )?.[1];

        if (onCallback) {
            onCallback({
                sessionId: 'session-123',
                user: {
                    userId: 'physicist2@alyx.org',
                    role: 'PARTICIPANT',
                },
            });
        }

        // Step 5: Verify participant list updates
        await waitFor(() => {
            expect(screen.getByText(/physicist2@alyx\.org/)).toBeInTheDocument();
        });

        // Step 6: Test parameter synchronization
        const parameterInput = await screen.findByLabelText(/energy.*threshold/i);
        await user.type(parameterInput, '2500');

        // Simulate parameter update from another user
        const parameterUpdateCallback = vi.mocked(mockWebSocket.on).mock.calls.find(
            call => call[0] === 'parameter-updated'
        )?.[1];

        if (parameterUpdateCallback) {
            parameterUpdateCallback({
                sessionId: 'session-123',
                parameterId: 'detectorRegions',
                value: ['CENTRAL', 'FORWARD'],
                updatedBy: 'physicist2@alyx.org',
            });
        }

        // Step 7: Verify real-time synchronization
        await waitFor(() => {
            const detectorRegionsDisplay = screen.getByTestId('detector-regions-display');
            expect(detectorRegionsDisplay).toHaveTextContent('CENTRAL, FORWARD');
        });

        // Step 8: Test cursor position sharing
        const analysisCanvas = await screen.findByTestId('analysis-canvas');
        fireEvent.mouseMove(analysisCanvas, { clientX: 100, clientY: 200 });

        // Verify cursor position is emitted
        expect(mockWebSocket.emit).toHaveBeenCalledWith('cursor-position', {
            sessionId: 'session-123',
            position: { x: 100, y: 200 },
            userId: 'physicist@alyx.org',
        });
    });

    /**
     * Test real-time data pipeline updates
     * Validates data flow from ingestion to visualization
     */
    it('should handle real-time data pipeline updates', async () => {
        const { user } = renderApp({
            auth: {
                user: {
                    id: '1',
                    username: 'physicist',
                    email: 'physicist@alyx.org',
                    role: 'physicist',
                    permissions: ['read', 'write', 'analyze'],
                },
                token: 'test-jwt-token',
                isAuthenticated: true,
            },
        });

        // Step 1: Navigate to data pipeline
        const pipelineNavItem = await screen.findByText(/pipeline/i);
        await user.click(pipelineNavItem);

        // Step 2: Monitor real-time data ingestion
        const dataIngestionStatus = await screen.findByTestId('data-ingestion-status');
        expect(dataIngestionStatus).toBeInTheDocument();

        // Step 3: Simulate real-time data updates via WebSocket
        const mockWebSocket = websocketService.default;
        const dataUpdateCallback = vi.mocked(mockWebSocket.on).mock.calls.find(
            call => call[0] === 'data-ingested'
        )?.[1];

        if (dataUpdateCallback) {
            dataUpdateCallback({
                batchId: 'batch-456',
                eventsCount: 1000,
                processingTime: 1500,
                timestamp: '2024-01-01T12:00:00Z',
            });
        }

        // Step 4: Verify real-time updates in UI
        await waitFor(() => {
            expect(screen.getByText(/1000.*events.*ingested/i)).toBeInTheDocument();
            expect(screen.getByText(/batch-456/)).toBeInTheDocument();
        });

        // Step 5: Test pipeline processing updates
        const processingUpdateCallback = vi.mocked(mockWebSocket.on).mock.calls.find(
            call => call[0] === 'pipeline-progress'
        )?.[1];

        if (processingUpdateCallback) {
            processingUpdateCallback({
                pipelineId: 'pipeline-789',
                stage: 'PARTICLE_RECONSTRUCTION',
                progress: 75,
                estimatedCompletion: '2024-01-01T12:05:00Z',
            });
        }

        // Step 6: Verify pipeline progress updates
        await waitFor(() => {
            const progressBar = screen.getByTestId('pipeline-progress-pipeline-789');
            expect(progressBar).toHaveAttribute('aria-valuenow', '75');
        });

        // Step 7: Test analysis results updates
        const resultsUpdateCallback = vi.mocked(mockWebSocket.on).mock.calls.find(
            call => call[0] === 'analysis-results'
        )?.[1];

        if (resultsUpdateCallback) {
            resultsUpdateCallback({
                jobId: 'job-999',
                results: {
                    particleTracks: 250,
                    collisionEvents: 100,
                    qualityScore: 0.95,
                },
                completedAt: '2024-01-01T12:10:00Z',
            });
        }

        // Step 8: Verify results display updates
        await waitFor(() => {
            expect(screen.getByText(/250.*particle.*tracks/i)).toBeInTheDocument();
            expect(screen.getByText(/100.*collision.*events/i)).toBeInTheDocument();
            expect(screen.getByText(/quality.*score.*0\.95/i)).toBeInTheDocument();
        });
    });

    /**
     * Test query builder and visualization integration
     * Validates Requirements 3.4, 2.1
     */
    it('should integrate query builder with visualization', async () => {
        const { user } = renderApp({
            auth: {
                user: {
                    id: '1',
                    username: 'physicist',
                    email: 'physicist@alyx.org',
                    role: 'physicist',
                    permissions: ['read', 'write', 'query'],
                },
                token: 'test-jwt-token',
                isAuthenticated: true,
            },
        });

        // Step 1: Navigate to query builder
        const queryBuilderNavItem = await screen.findByText(/query.*builder/i);
        await user.click(queryBuilderNavItem);

        // Step 2: Build query using drag-and-drop
        const energyRangeFilter = await screen.findByTestId('filter-energy-range');
        const queryCanvas = await screen.findByTestId('query-canvas');

        // Simulate drag and drop
        fireEvent.dragStart(energyRangeFilter);
        fireEvent.dragOver(queryCanvas);
        fireEvent.drop(queryCanvas);

        // Step 3: Configure filter parameters
        const minEnergyInput = await screen.findByLabelText(/minimum.*energy/i);
        const maxEnergyInput = await screen.findByLabelText(/maximum.*energy/i);

        await user.type(minEnergyInput, '2000');
        await user.type(maxEnergyInput, '4000');

        // Step 4: Execute query
        const executeQueryButton = await screen.findByRole('button', { name: /execute.*query/i });
        await user.click(executeQueryButton);

        // Step 5: Verify query execution time (Requirement 3.4)
        await waitFor(() => {
            const queryTimeElement = screen.getByTestId('query-execution-time');
            const queryTime = parseInt(queryTimeElement.textContent || '0');
            expect(queryTime).toBeLessThan(2000); // 2 second requirement
        });

        // Step 6: Visualize query results
        const visualizeResultsButton = await screen.findByRole('button', { name: /visualize.*results/i });
        await user.click(visualizeResultsButton);

        // Step 7: Verify visualization loads with query data
        await waitFor(() => {
            const visualizationCanvas = screen.getByTestId('visualization-canvas');
            expect(visualizationCanvas).toBeInTheDocument();

            // Verify filtered data is displayed
            expect(screen.getByText(/energy.*range.*2000.*4000/i)).toBeInTheDocument();
        });

        // Step 8: Test interactive visualization controls
        const rotateControl = screen.getByLabelText(/rotate/i);
        fireEvent.change(rotateControl, { target: { value: '45' } });

        const zoomControl = screen.getByLabelText(/zoom/i);
        fireEvent.change(zoomControl, { target: { value: '1.5' } });

        // Verify controls are responsive
        await waitFor(() => {
            expect(screen.getByDisplayValue('45')).toBeInTheDocument();
            expect(screen.getByDisplayValue('1.5')).toBeInTheDocument();
        });
    });
});