package com.alyx.collaboration.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom metrics for Collaboration service
 * Provides detailed metrics for real-time collaboration, WebSocket connections, and operational transforms
 */
@Component
public class CollaborationMetrics {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter sessionCreationCounter;
    private final Counter sessionJoinCounter;
    private final Counter sessionLeaveCounter;
    private final Counter messagesSentCounter;
    private final Counter messagesReceivedCounter;
    private final Counter operationalTransformCounter;
    private final Counter conflictResolutionCounter;
    private final Counter websocketConnectionCounter;
    private final Counter websocketDisconnectionCounter;

    // Timers
    private final Timer messageProcessingTimer;
    private final Timer operationalTransformTimer;
    private final Timer conflictResolutionTimer;
    private final Timer sessionSyncTimer;

    // Gauge values
    private final AtomicLong activeSessions = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong totalParticipants = new AtomicLong(0);
    private final AtomicLong pendingOperations = new AtomicLong(0);

    @Autowired
    public CollaborationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.sessionCreationCounter = Counter.builder("alyx.collaboration.sessions.created")
                .tag("service", "collaboration")
                .description("Total number of collaboration sessions created")
                .register(meterRegistry);

        this.sessionJoinCounter = Counter.builder("alyx.collaboration.sessions.joined")
                .tag("service", "collaboration")
                .description("Total number of session joins")
                .register(meterRegistry);

        this.sessionLeaveCounter = Counter.builder("alyx.collaboration.sessions.left")
                .tag("service", "collaboration")
                .description("Total number of session leaves")
                .register(meterRegistry);

        this.messagesSentCounter = Counter.builder("alyx.collaboration.messages.sent")
                .tag("service", "collaboration")
                .description("Total number of messages sent")
                .register(meterRegistry);

        this.messagesReceivedCounter = Counter.builder("alyx.collaboration.messages.received")
                .tag("service", "collaboration")
                .description("Total number of messages received")
                .register(meterRegistry);

        this.operationalTransformCounter = Counter.builder("alyx.collaboration.operations.transformed")
                .tag("service", "collaboration")
                .description("Total number of operational transforms applied")
                .register(meterRegistry);

        this.conflictResolutionCounter = Counter.builder("alyx.collaboration.conflicts.resolved")
                .tag("service", "collaboration")
                .description("Total number of conflicts resolved")
                .register(meterRegistry);

        this.websocketConnectionCounter = Counter.builder("alyx.collaboration.websocket.connections")
                .tag("service", "collaboration")
                .description("Total number of WebSocket connections established")
                .register(meterRegistry);

        this.websocketDisconnectionCounter = Counter.builder("alyx.collaboration.websocket.disconnections")
                .tag("service", "collaboration")
                .description("Total number of WebSocket disconnections")
                .register(meterRegistry);

        // Initialize timers
        this.messageProcessingTimer = Timer.builder("alyx.collaboration.message.processing.duration")
                .tag("service", "collaboration")
                .description("Message processing duration")
                .register(meterRegistry);

        this.operationalTransformTimer = Timer.builder("alyx.collaboration.operation.transform.duration")
                .tag("service", "collaboration")
                .description("Operational transform duration")
                .register(meterRegistry);

        this.conflictResolutionTimer = Timer.builder("alyx.collaboration.conflict.resolution.duration")
                .tag("service", "collaboration")
                .description("Conflict resolution duration")
                .register(meterRegistry);

        this.sessionSyncTimer = Timer.builder("alyx.collaboration.session.sync.duration")
                .tag("service", "collaboration")
                .description("Session synchronization duration")
                .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("alyx.collaboration.sessions.active")
                .tag("service", "collaboration")
                .description("Number of active collaboration sessions")
                .register(meterRegistry, this, CollaborationMetrics::getActiveSessions);

        Gauge.builder("alyx.collaboration.connections.active")
                .tag("service", "collaboration")
                .description("Number of active WebSocket connections")
                .register(meterRegistry, this, CollaborationMetrics::getActiveConnections);

        Gauge.builder("alyx.collaboration.participants.total")
                .tag("service", "collaboration")
                .description("Total number of participants across all sessions")
                .register(meterRegistry, this, CollaborationMetrics::getTotalParticipants);

        Gauge.builder("alyx.collaboration.operations.pending")
                .tag("service", "collaboration")
                .description("Number of pending operations awaiting processing")
                .register(meterRegistry, this, CollaborationMetrics::getPendingOperations);
    }

    // Counter increment methods
    public void incrementSessionCreations() {
        sessionCreationCounter.increment();
    }

    public void incrementSessionJoins() {
        sessionJoinCounter.increment();
    }

    public void incrementSessionLeaves() {
        sessionLeaveCounter.increment();
    }

    public void incrementMessagesSent() {
        messagesSentCounter.increment();
    }

    public void incrementMessagesReceived() {
        messagesReceivedCounter.increment();
    }

    public void incrementOperationalTransforms() {
        operationalTransformCounter.increment();
    }

    public void incrementConflictResolutions() {
        conflictResolutionCounter.increment();
    }

    public void incrementWebSocketConnections() {
        websocketConnectionCounter.increment();
    }

    public void incrementWebSocketDisconnections() {
        websocketDisconnectionCounter.increment();
    }

    // Timer recording methods
    public Timer.Sample startMessageProcessingTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordMessageProcessing(Timer.Sample sample) {
        sample.stop(messageProcessingTimer);
    }

    public Timer.Sample startOperationalTransformTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordOperationalTransform(Timer.Sample sample) {
        sample.stop(operationalTransformTimer);
    }

    public Timer.Sample startConflictResolutionTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordConflictResolution(Timer.Sample sample) {
        sample.stop(conflictResolutionTimer);
    }

    public Timer.Sample startSessionSyncTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordSessionSync(Timer.Sample sample) {
        sample.stop(sessionSyncTimer);
    }

    // Gauge update methods
    public void setActiveSessions(long count) {
        activeSessions.set(count);
    }

    public void setActiveConnections(long count) {
        activeConnections.set(count);
    }

    public void setTotalParticipants(long count) {
        totalParticipants.set(count);
    }

    public void setPendingOperations(long count) {
        pendingOperations.set(count);
    }

    // Gauge getter methods
    public long getActiveSessions() {
        return activeSessions.get();
    }

    public long getActiveConnections() {
        return activeConnections.get();
    }

    public long getTotalParticipants() {
        return totalParticipants.get();
    }

    public long getPendingOperations() {
        return pendingOperations.get();
    }
}