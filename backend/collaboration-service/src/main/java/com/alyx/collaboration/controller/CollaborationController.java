package com.alyx.collaboration.controller;

import com.alyx.collaboration.model.*;
import com.alyx.collaboration.service.CollaborationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
public class CollaborationController {

    private final CollaborationService collaborationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public CollaborationController(CollaborationService collaborationService, 
                                 SimpMessagingTemplate messagingTemplate) {
        this.collaborationService = collaborationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/join")
    public void joinSession(@Payload JoinSessionRequest request, Principal principal) {
        String userId = principal.getName();
        
        Participant participant = new Participant(userId, request.getUsername());
        boolean success = collaborationService.joinSession(request.getSessionId(), participant);
        
        if (success) {
            // Notify all participants in the session about the new participant
            CollaborationSession session = collaborationService.getSession(request.getSessionId());
            messagingTemplate.convertAndSend(
                "/topic/session/" + request.getSessionId() + "/participants",
                new ParticipantJoinedEvent(participant, session.getParticipants())
            );
            
            // Send current session state to the new participant
            messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/session-state",
                new SessionStateResponse(session.getSharedState(), session.getParticipants())
            );
        }
    }

    @MessageMapping("/leave")
    public void leaveSession(@Payload LeaveSessionRequest request, Principal principal) {
        String userId = principal.getName();
        
        boolean success = collaborationService.leaveSession(request.getSessionId(), userId);
        
        if (success) {
            // Notify all remaining participants
            CollaborationSession session = collaborationService.getSession(request.getSessionId());
            messagingTemplate.convertAndSend(
                "/topic/session/" + request.getSessionId() + "/participants",
                new ParticipantLeftEvent(userId, session != null ? session.getParticipants() : List.of())
            );
        }
    }

    @MessageMapping("/state-update")
    public void handleStateUpdate(@Payload StateUpdateRequest request, Principal principal) {
        String userId = principal.getName();
        
        StateUpdate update = new StateUpdate(
            request.getType(),
            userId,
            request.getSessionId(),
            request.getData(),
            request.getVersion()
        );
        
        CollaborationService.SynchronizationResult result = 
            collaborationService.synchronizeState(request.getSessionId(), List.of(update));
        
        if (result.isSuccess()) {
            // Broadcast the state update to all participants in the session
            messagingTemplate.convertAndSend(
                "/topic/session/" + request.getSessionId() + "/state",
                new StateUpdateEvent(update, result.getSynchronizedState())
            );
        }
    }

    @MessageMapping("/cursor-update")
    public void handleCursorUpdate(@Payload CursorUpdateRequest request, Principal principal) {
        String userId = principal.getName();
        
        boolean success = collaborationService.updateParticipantPresence(
            request.getSessionId(),
            userId,
            request.getCursor(),
            null
        );
        
        if (success) {
            // Broadcast cursor update to other participants (not the sender)
            messagingTemplate.convertAndSend(
                "/topic/session/" + request.getSessionId() + "/cursors",
                new CursorUpdateEvent(userId, request.getCursor())
            );
        }
    }

    @MessageMapping("/selection-update")
    public void handleSelectionUpdate(@Payload SelectionUpdateRequest request, Principal principal) {
        String userId = principal.getName();
        
        boolean success = collaborationService.updateParticipantPresence(
            request.getSessionId(),
            userId,
            null,
            request.getSelection()
        );
        
        if (success) {
            // Broadcast selection update to other participants (not the sender)
            messagingTemplate.convertAndSend(
                "/topic/session/" + request.getSessionId() + "/selections",
                new SelectionUpdateEvent(userId, request.getSelection())
            );
        }
    }

    @MessageMapping("/resolve-conflicts")
    public void resolveConflicts(@Payload ConflictResolutionRequest request, Principal principal) {
        CollaborationService.ConflictResolution resolution = 
            collaborationService.resolveConflicts(request.getSessionId(), request.getConflictingUpdates());
        
        if (resolution.isSuccess()) {
            // Broadcast the resolved state to all participants
            messagingTemplate.convertAndSend(
                "/topic/session/" + request.getSessionId() + "/state",
                new ConflictResolvedEvent(resolution.getResolvedState(), resolution.getResolution())
            );
        }
    }

    // REST endpoints for session management
    @PostMapping("/api/sessions")
    @ResponseBody
    public CollaborationSession createSession(@RequestBody CreateSessionRequest request) {
        return collaborationService.createSession(request.getSessionId(), request.getInitialState());
    }

    @GetMapping("/api/sessions/{sessionId}")
    @ResponseBody
    public CollaborationSession getSession(@PathVariable String sessionId) {
        return collaborationService.getSession(sessionId);
    }

    @GetMapping("/api/sessions/{sessionId}/participants")
    @ResponseBody
    public List<Participant> getActiveParticipants(@PathVariable String sessionId) {
        return collaborationService.getActiveParticipants(sessionId);
    }

    // Request/Response DTOs
    public static class JoinSessionRequest {
        private String sessionId;
        private String username;
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }

    public static class LeaveSessionRequest {
        private String sessionId;
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }

    public static class StateUpdateRequest {
        private String sessionId;
        private StateUpdate.UpdateType type;
        private Map<String, Object> data;
        private long version;
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public StateUpdate.UpdateType getType() { return type; }
        public void setType(StateUpdate.UpdateType type) { this.type = type; }
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }
    }

    public static class CursorUpdateRequest {
        private String sessionId;
        private CursorPosition cursor;
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public CursorPosition getCursor() { return cursor; }
        public void setCursor(CursorPosition cursor) { this.cursor = cursor; }
    }

    public static class SelectionUpdateRequest {
        private String sessionId;
        private SelectionRange selection;
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public SelectionRange getSelection() { return selection; }
        public void setSelection(SelectionRange selection) { this.selection = selection; }
    }

    public static class ConflictResolutionRequest {
        private String sessionId;
        private List<StateUpdate> conflictingUpdates;
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public List<StateUpdate> getConflictingUpdates() { return conflictingUpdates; }
        public void setConflictingUpdates(List<StateUpdate> conflictingUpdates) { this.conflictingUpdates = conflictingUpdates; }
    }

    public static class CreateSessionRequest {
        private String sessionId;
        private SharedState initialState;
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public SharedState getInitialState() { return initialState; }
        public void setInitialState(SharedState initialState) { this.initialState = initialState; }
    }

    // Event DTOs
    public static class ParticipantJoinedEvent {
        private final Participant participant;
        private final List<Participant> allParticipants;
        
        public ParticipantJoinedEvent(Participant participant, List<Participant> allParticipants) {
            this.participant = participant;
            this.allParticipants = allParticipants;
        }
        
        public Participant getParticipant() { return participant; }
        public List<Participant> getAllParticipants() { return allParticipants; }
    }

    public static class ParticipantLeftEvent {
        private final String userId;
        private final List<Participant> remainingParticipants;
        
        public ParticipantLeftEvent(String userId, List<Participant> remainingParticipants) {
            this.userId = userId;
            this.remainingParticipants = remainingParticipants;
        }
        
        public String getUserId() { return userId; }
        public List<Participant> getRemainingParticipants() { return remainingParticipants; }
    }

    public static class StateUpdateEvent {
        private final StateUpdate update;
        private final SharedState newState;
        
        public StateUpdateEvent(StateUpdate update, SharedState newState) {
            this.update = update;
            this.newState = newState;
        }
        
        public StateUpdate getUpdate() { return update; }
        public SharedState getNewState() { return newState; }
    }

    public static class CursorUpdateEvent {
        private final String userId;
        private final CursorPosition cursor;
        
        public CursorUpdateEvent(String userId, CursorPosition cursor) {
            this.userId = userId;
            this.cursor = cursor;
        }
        
        public String getUserId() { return userId; }
        public CursorPosition getCursor() { return cursor; }
    }

    public static class SelectionUpdateEvent {
        private final String userId;
        private final SelectionRange selection;
        
        public SelectionUpdateEvent(String userId, SelectionRange selection) {
            this.userId = userId;
            this.selection = selection;
        }
        
        public String getUserId() { return userId; }
        public SelectionRange getSelection() { return selection; }
    }

    public static class ConflictResolvedEvent {
        private final SharedState resolvedState;
        private final CollaborationService.ConflictResolution.ResolutionStrategy strategy;
        
        public ConflictResolvedEvent(SharedState resolvedState, CollaborationService.ConflictResolution.ResolutionStrategy strategy) {
            this.resolvedState = resolvedState;
            this.strategy = strategy;
        }
        
        public SharedState getResolvedState() { return resolvedState; }
        public CollaborationService.ConflictResolution.ResolutionStrategy getStrategy() { return strategy; }
    }

    public static class SessionStateResponse {
        private final SharedState sharedState;
        private final List<Participant> participants;
        
        public SessionStateResponse(SharedState sharedState, List<Participant> participants) {
            this.sharedState = sharedState;
            this.participants = participants;
        }
        
        public SharedState getSharedState() { return sharedState; }
        public List<Participant> getParticipants() { return participants; }
    }
}