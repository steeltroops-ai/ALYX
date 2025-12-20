import { io, Socket } from 'socket.io-client';
import { store } from '../store/store';

class WebSocketService {
    private socket: Socket | null = null;
    private reconnectAttempts = 0;
    private maxReconnectAttempts = 5;
    private reconnectDelay = 1000;

    connect(token?: string): void {
        if (this.socket?.connected) {
            return;
        }

        const socketUrl = process.env.NODE_ENV === 'production'
            ? window.location.origin
            : 'http://localhost:8080';

        this.socket = io(socketUrl, {
            auth: {
                token: token || localStorage.getItem('token'),
            },
            transports: ['websocket', 'polling'],
        });

        this.setupEventListeners();
    }

    private setupEventListeners(): void {
        if (!this.socket) return;

        this.socket.on('connect', () => {
            console.log('WebSocket connected');
            this.reconnectAttempts = 0;
        });

        this.socket.on('disconnect', (reason) => {
            console.log('WebSocket disconnected:', reason);
            if (reason === 'io server disconnect') {
                // Server initiated disconnect, don't reconnect automatically
                return;
            }
            this.handleReconnect();
        });

        this.socket.on('connect_error', (error) => {
            console.error('WebSocket connection error:', error);
            this.handleReconnect();
        });

        // Job status updates
        this.socket.on('job_status_update', (data) => {
            store.dispatch({ type: 'jobs/updateJobStatus', payload: data });
        });

        // Collaboration events
        this.socket.on('collaboration_update', (data) => {
            store.dispatch({ type: 'collaboration/updateState', payload: data });
        });

        // Visualization updates
        this.socket.on('visualization_update', (data) => {
            store.dispatch({ type: 'visualization/updateData', payload: data });
        });

        // System notifications
        this.socket.on('system_notification', (data) => {
            store.dispatch({ type: 'notifications/addNotification', payload: data });
        });
    }

    private handleReconnect(): void {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('Max reconnection attempts reached');
            return;
        }

        this.reconnectAttempts++;
        const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1);

        setTimeout(() => {
            console.log(`Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
            this.connect();
        }, delay);
    }

    disconnect(): void {
        if (this.socket) {
            this.socket.disconnect();
            this.socket = null;
        }
    }

    emit(event: string, data: any): void {
        if (this.socket?.connected) {
            this.socket.emit(event, data);
        } else {
            console.warn('WebSocket not connected, cannot emit event:', event);
        }
    }

    on(event: string, callback: (data: any) => void): void {
        if (this.socket) {
            this.socket.on(event, callback);
        }
    }

    off(event: string, callback?: (data: any) => void): void {
        if (this.socket) {
            this.socket.off(event, callback);
        }
    }

    isConnected(): boolean {
        return this.socket?.connected || false;
    }

    // Job-specific methods
    subscribeToJob(jobId: string): void {
        this.emit('subscribe_job', { jobId });
    }

    unsubscribeFromJob(jobId: string): void {
        this.emit('unsubscribe_job', { jobId });
    }

    // Collaboration-specific methods
    joinCollaborationSession(sessionId: string, username?: string, initialState?: any): void {
        this.emit('join_collaboration', { sessionId, username, initialState });
    }

    leaveCollaborationSession(sessionId: string): void {
        this.emit('leave_collaboration', { sessionId });
    }

    sendCollaborationUpdate(sessionId: string, type: string, data: any, version: number): void {
        this.emit('collaboration_update', { sessionId, type, data, version });
    }

    sendCursorUpdate(sessionId: string, cursor: { x: number; y: number; elementId?: string }): void {
        this.emit('cursor_update', { sessionId, cursor });
    }

    sendSelectionUpdate(sessionId: string, selection: any): void {
        this.emit('selection_update', { sessionId, selection });
    }
}

export const websocketService = new WebSocketService();
export default websocketService;