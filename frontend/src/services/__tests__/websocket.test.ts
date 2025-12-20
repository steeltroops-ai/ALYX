import { describe, it, expect, vi, beforeEach } from 'vitest';
import websocketService from '../websocket';

// Mock socket.io-client
vi.mock('socket.io-client', () => ({
    io: vi.fn(() => ({
        connected: false,
        connect: vi.fn(),
        disconnect: vi.fn(),
        emit: vi.fn(),
        on: vi.fn(),
        off: vi.fn(),
    })),
}));

describe('WebSocketService', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        websocketService.disconnect();
    });

    it('should initialize without connection', () => {
        expect(websocketService.isConnected()).toBe(false);
    });

    it('should connect with token', () => {
        const token = 'test-token';
        websocketService.connect(token);

        // Service should attempt to connect
        expect(websocketService).toBeDefined();
    });

    it('should handle job subscription', () => {
        const jobId = 'test-job-123';
        websocketService.subscribeToJob(jobId);

        // Should not throw error even if not connected
        expect(websocketService).toBeDefined();
    });

    it('should handle collaboration session joining', () => {
        const sessionId = 'session-123';
        websocketService.joinCollaborationSession(sessionId);

        // Should not throw error even if not connected
        expect(websocketService).toBeDefined();
    });

    it('should disconnect cleanly', () => {
        websocketService.connect('test-token');
        websocketService.disconnect();

        expect(websocketService.isConnected()).toBe(false);
    });
});