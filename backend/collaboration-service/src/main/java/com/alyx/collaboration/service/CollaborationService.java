package com.alyx.collaboration.service;

import com.alyx.collaboration.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class CollaborationService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final OperationalTransformService transformService;
    private final Map<String, CollaborationSession> activeSessions = new ConcurrentHashMap<>();
    
    private static final String SESSION_KEY_PREFIX = "collaboration:session:";
    private static final String PARTICIPANT_KEY_PREFIX = "collaboration:participant:";
    private static final long SESSION_TIMEOUT_HOURS = 24;

    @Autowired
    public CollaborationService(RedisTemplate<String, Object> redisTemplate, 
                               OperationalTransformService transformService) {
        this.redisTemplate = redisTemplate;
        this.transformService = transformService;
    }

    public CollaborationSession createSession(String sessionId, SharedState initialState) {
        CollaborationSession session = new CollaborationSession(sessionId, initialState);
        
        // Store in memory for fast access
        activeSessions.put(sessionId, session);
        
        // Persist to Redis for durability
        String redisKey = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(redisKey, session, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        
        return session;
    }

    public CollaborationSession getSession(String sessionId) {
        // Try memory first
        CollaborationSession session = activeSessions.get(sessionId);
        if (session != null) {
            return session;
        }
        
        // Fallback to Redis
        String redisKey = SESSION_KEY_PREFIX + sessionId;
        session = (CollaborationSession) redisTemplate.opsForValue().get(redisKey);
        if (session != null) {
            activeSessions.put(sessionId, session);
        }
        
        return session;
    }

    public boolean joinSession(String sessionId, Participant participant) {
        CollaborationSession session = getSession(sessionId);
        if (session == null) {
            return false;
        }

        participant.setActive(true);
        participant.setJoinedAt(Instant.now());
        session.addParticipant(participant);
        
        // Update Redis
        updateSessionInRedis(session);
        
        return true;
    }

    public boolean leaveSession(String sessionId, String userId) {
        CollaborationSession session = getSession(sessionId);
        if (session == null) {
            return false;
        }

        session.removeParticipant(userId);
        
        // Update Redis
        updateSessionInRedis(session);
        
        return true;
    }

    public SynchronizationResult synchronizeState(String sessionId, List<StateUpdate> updates) {
        CollaborationSession session = getSession(sessionId);
        if (session == null) {
            return new SynchronizationResult(false, null, 0);
        }

        long startTime = System.currentTimeMillis();
        
        // Sort updates by timestamp for consistent ordering
        updates.sort(Comparator.comparing(StateUpdate::getTimestamp));
        
        SharedState currentState = session.getSharedState().copy();
        
        // Apply updates using operational transformation
        for (StateUpdate update : updates) {
            currentState = applyUpdate(currentState, update);
            currentState.incrementVersion();
        }
        
        session.setSharedState(currentState);
        
        // Update Redis
        updateSessionInRedis(session);
        
        long propagationTime = System.currentTimeMillis() - startTime;
        
        return new SynchronizationResult(true, currentState, propagationTime);
    }

    public ConflictResolution resolveConflicts(String sessionId, List<StateUpdate> conflictingUpdates) {
        CollaborationSession session = getSession(sessionId);
        if (session == null) {
            return new ConflictResolution(
                ConflictResolution.ConflictType.VERSION_MISMATCH,
                ConflictResolution.ResolutionStrategy.LAST_WRITER_WINS,
                null,
                false
            );
        }

        // Use operational transformation to resolve conflicts
        SharedState resolvedState = transformService.resolveConflicts(
            session.getSharedState(), 
            conflictingUpdates
        );
        
        resolvedState.incrementVersion();
        session.setSharedState(resolvedState);
        
        // Update Redis
        updateSessionInRedis(session);
        
        return new ConflictResolution(
            ConflictResolution.ConflictType.CONCURRENT_EDIT,
            ConflictResolution.ResolutionStrategy.OPERATIONAL_TRANSFORM,
            resolvedState,
            true
        );
    }

    public boolean updateParticipantPresence(String sessionId, String userId, 
                                           CursorPosition cursor, SelectionRange selection) {
        CollaborationSession session = getSession(sessionId);
        if (session == null) {
            return false;
        }

        Participant participant = session.getParticipant(userId);
        if (participant == null) {
            return false;
        }

        if (cursor != null) {
            participant.setCursor(cursor);
        }
        if (selection != null) {
            participant.setSelection(selection);
        }
        
        // Update Redis
        updateSessionInRedis(session);
        
        return true;
    }

    public List<Participant> getActiveParticipants(String sessionId) {
        CollaborationSession session = getSession(sessionId);
        if (session == null) {
            return Collections.emptyList();
        }

        return session.getParticipants().stream()
                .filter(Participant::isActive)
                .toList();
    }

    private SharedState applyUpdate(SharedState state, StateUpdate update) {
        SharedState newState = state.copy();
        
        switch (update.getType()) {
            case PARAMETER_CHANGE:
                newState.getAnalysisParameters().putAll(update.getData());
                break;
            case QUERY_UPDATE:
                newState.getQueryState().putAll(update.getData());
                break;
            case VISUALIZATION_UPDATE:
                newState.getVisualizationState().putAll(update.getData());
                break;
            default:
                // Cursor and selection updates don't affect shared state
                break;
        }
        
        return newState;
    }

    private void updateSessionInRedis(CollaborationSession session) {
        String redisKey = SESSION_KEY_PREFIX + session.getSessionId();
        redisTemplate.opsForValue().set(redisKey, session, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
    }

    public static class SynchronizationResult {
        private final boolean success;
        private final SharedState synchronizedState;
        private final long propagationTime;

        public SynchronizationResult(boolean success, SharedState synchronizedState, long propagationTime) {
            this.success = success;
            this.synchronizedState = synchronizedState;
            this.propagationTime = propagationTime;
        }

        public boolean isSuccess() { return success; }
        public SharedState getSynchronizedState() { return synchronizedState; }
        public long getPropagationTime() { return propagationTime; }
    }

    public static class ConflictResolution {
        public enum ConflictType {
            CONCURRENT_EDIT, VERSION_MISMATCH, PARAMETER_CONFLICT
        }
        
        public enum ResolutionStrategy {
            OPERATIONAL_TRANSFORM, LAST_WRITER_WINS, USER_CHOICE
        }

        private final ConflictType conflictType;
        private final ResolutionStrategy resolution;
        private final SharedState resolvedState;
        private final boolean success;

        public ConflictResolution(ConflictType conflictType, ResolutionStrategy resolution, 
                                SharedState resolvedState, boolean success) {
            this.conflictType = conflictType;
            this.resolution = resolution;
            this.resolvedState = resolvedState;
            this.success = success;
        }

        public ConflictType getConflictType() { return conflictType; }
        public ResolutionStrategy getResolution() { return resolution; }
        public SharedState getResolvedState() { return resolvedState; }
        public boolean isSuccess() { return success; }
    }
}