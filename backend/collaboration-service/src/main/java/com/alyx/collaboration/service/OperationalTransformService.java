package com.alyx.collaboration.service;

import com.alyx.collaboration.model.SharedState;
import com.alyx.collaboration.model.StateUpdate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OperationalTransformService {

    /**
     * Resolves conflicts between concurrent updates using operational transformation
     * This is a simplified implementation - in production, you'd want more sophisticated OT algorithms
     */
    public SharedState resolveConflicts(SharedState baseState, List<StateUpdate> conflictingUpdates) {
        SharedState resolvedState = baseState.copy();
        
        // Sort updates by timestamp to ensure consistent ordering
        conflictingUpdates.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        
        // Apply updates in chronological order with conflict resolution
        for (StateUpdate update : conflictingUpdates) {
            resolvedState = applyUpdateWithTransform(resolvedState, update, conflictingUpdates);
        }
        
        return resolvedState;
    }

    private SharedState applyUpdateWithTransform(SharedState state, StateUpdate update, List<StateUpdate> allUpdates) {
        SharedState newState = state.copy();
        
        switch (update.getType()) {
            case PARAMETER_CHANGE:
                transformAndApplyParameterChanges(newState, update, allUpdates);
                break;
            case QUERY_UPDATE:
                transformAndApplyQueryUpdates(newState, update, allUpdates);
                break;
            case VISUALIZATION_UPDATE:
                transformAndApplyVisualizationUpdates(newState, update, allUpdates);
                break;
            default:
                // Other update types don't require transformation
                break;
        }
        
        return newState;
    }

    private void transformAndApplyParameterChanges(SharedState state, StateUpdate update, List<StateUpdate> allUpdates) {
        Map<String, Object> parameters = state.getAnalysisParameters();
        
        // Apply the update data
        for (Map.Entry<String, Object> entry : update.getData().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Check for conflicts with other concurrent updates
            Object transformedValue = transformParameterValue(key, value, update, allUpdates);
            parameters.put(key, transformedValue);
        }
    }

    private void transformAndApplyQueryUpdates(SharedState state, StateUpdate update, List<StateUpdate> allUpdates) {
        Map<String, Object> queryState = state.getQueryState();
        
        // Apply the update data with transformation
        for (Map.Entry<String, Object> entry : update.getData().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Transform value based on concurrent changes
            Object transformedValue = transformQueryValue(key, value, update, allUpdates);
            queryState.put(key, transformedValue);
        }
    }

    private void transformAndApplyVisualizationUpdates(SharedState state, StateUpdate update, List<StateUpdate> allUpdates) {
        Map<String, Object> visualizationState = state.getVisualizationState();
        
        // Apply the update data with transformation
        for (Map.Entry<String, Object> entry : update.getData().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Transform value based on concurrent changes
            Object transformedValue = transformVisualizationValue(key, value, update, allUpdates);
            visualizationState.put(key, transformedValue);
        }
    }

    private Object transformParameterValue(String key, Object value, StateUpdate currentUpdate, List<StateUpdate> allUpdates) {
        // Simple conflict resolution: for numeric values, take the average of conflicting updates
        // For other types, use last-writer-wins based on timestamp
        
        List<StateUpdate> conflictingParameterUpdates = allUpdates.stream()
                .filter(u -> u.getType() == StateUpdate.UpdateType.PARAMETER_CHANGE)
                .filter(u -> u.getData().containsKey(key))
                .filter(u -> !u.getUserId().equals(currentUpdate.getUserId()))
                .toList();
        
        if (conflictingParameterUpdates.isEmpty()) {
            return value;
        }
        
        // For numeric ranges (like energy ranges), merge intelligently
        if (key.equals("energyRange") && value instanceof Map) {
            return mergeEnergyRanges((Map<String, Object>) value, conflictingParameterUpdates, key);
        }
        
        // For arrays (like particle types), merge unique values
        if (value instanceof List) {
            return mergeArrayValues((List<?>) value, conflictingParameterUpdates, key);
        }
        
        // Default: last writer wins
        return value;
    }

    private Object transformQueryValue(String key, Object value, StateUpdate currentUpdate, List<StateUpdate> allUpdates) {
        // For query updates, generally use last-writer-wins
        // But for filters, we can merge them intelligently
        
        if (key.equals("filters") && value instanceof List) {
            List<StateUpdate> conflictingQueryUpdates = allUpdates.stream()
                    .filter(u -> u.getType() == StateUpdate.UpdateType.QUERY_UPDATE)
                    .filter(u -> u.getData().containsKey(key))
                    .filter(u -> !u.getUserId().equals(currentUpdate.getUserId()))
                    .toList();
            
            return mergeFilters((List<?>) value, conflictingQueryUpdates, key);
        }
        
        return value;
    }

    private Object transformVisualizationValue(String key, Object value, StateUpdate currentUpdate, List<StateUpdate> allUpdates) {
        // For visualization updates, generally use last-writer-wins
        // But for camera positions, we can interpolate between concurrent changes
        
        if (key.equals("cameraPosition") && value instanceof Map) {
            List<StateUpdate> conflictingVisualizationUpdates = allUpdates.stream()
                    .filter(u -> u.getType() == StateUpdate.UpdateType.VISUALIZATION_UPDATE)
                    .filter(u -> u.getData().containsKey(key))
                    .filter(u -> !u.getUserId().equals(currentUpdate.getUserId()))
                    .toList();
            
            return interpolateCameraPositions((Map<String, Object>) value, conflictingVisualizationUpdates, key);
        }
        
        return value;
    }

    @SuppressWarnings("unchecked")
    private Object mergeEnergyRanges(Map<String, Object> currentRange, List<StateUpdate> conflictingUpdates, String key) {
        Map<String, Object> mergedRange = new ConcurrentHashMap<>(currentRange);
        
        for (StateUpdate update : conflictingUpdates) {
            Map<String, Object> conflictingRange = (Map<String, Object>) update.getData().get(key);
            if (conflictingRange != null) {
                // Take the minimum of min values and maximum of max values
                if (conflictingRange.containsKey("min") && mergedRange.containsKey("min")) {
                    double currentMin = ((Number) mergedRange.get("min")).doubleValue();
                    double conflictingMin = ((Number) conflictingRange.get("min")).doubleValue();
                    mergedRange.put("min", Math.min(currentMin, conflictingMin));
                }
                if (conflictingRange.containsKey("max") && mergedRange.containsKey("max")) {
                    double currentMax = ((Number) mergedRange.get("max")).doubleValue();
                    double conflictingMax = ((Number) conflictingRange.get("max")).doubleValue();
                    mergedRange.put("max", Math.max(currentMax, conflictingMax));
                }
            }
        }
        
        return mergedRange;
    }

    @SuppressWarnings("unchecked")
    private Object mergeArrayValues(List<?> currentList, List<StateUpdate> conflictingUpdates, String key) {
        List<Object> mergedList = new java.util.ArrayList<>(currentList);
        
        for (StateUpdate update : conflictingUpdates) {
            List<?> conflictingList = (List<?>) update.getData().get(key);
            if (conflictingList != null) {
                // Add unique values from conflicting list
                for (Object item : conflictingList) {
                    if (!mergedList.contains(item)) {
                        mergedList.add(item);
                    }
                }
            }
        }
        
        return mergedList;
    }

    @SuppressWarnings("unchecked")
    private Object mergeFilters(List<?> currentFilters, List<StateUpdate> conflictingUpdates, String key) {
        List<Object> mergedFilters = new java.util.ArrayList<>(currentFilters);
        
        for (StateUpdate update : conflictingUpdates) {
            List<?> conflictingFilters = (List<?>) update.getData().get(key);
            if (conflictingFilters != null) {
                // Merge filters, avoiding duplicates based on field name
                for (Object filter : conflictingFilters) {
                    if (filter instanceof Map) {
                        Map<String, Object> filterMap = (Map<String, Object>) filter;
                        String field = (String) filterMap.get("field");
                        
                        // Remove existing filter for the same field
                        mergedFilters.removeIf(existingFilter -> {
                            if (existingFilter instanceof Map) {
                                return field.equals(((Map<String, Object>) existingFilter).get("field"));
                            }
                            return false;
                        });
                        
                        // Add the new filter
                        mergedFilters.add(filter);
                    }
                }
            }
        }
        
        return mergedFilters;
    }

    @SuppressWarnings("unchecked")
    private Object interpolateCameraPositions(Map<String, Object> currentPosition, List<StateUpdate> conflictingUpdates, String key) {
        if (conflictingUpdates.isEmpty()) {
            return currentPosition;
        }
        
        Map<String, Object> interpolatedPosition = new ConcurrentHashMap<>(currentPosition);
        
        // Simple interpolation: average the positions
        double totalX = ((Number) currentPosition.getOrDefault("x", 0.0)).doubleValue();
        double totalY = ((Number) currentPosition.getOrDefault("y", 0.0)).doubleValue();
        double totalZ = ((Number) currentPosition.getOrDefault("z", 0.0)).doubleValue();
        int count = 1;
        
        for (StateUpdate update : conflictingUpdates) {
            Map<String, Object> conflictingPosition = (Map<String, Object>) update.getData().get(key);
            if (conflictingPosition != null) {
                totalX += ((Number) conflictingPosition.getOrDefault("x", 0.0)).doubleValue();
                totalY += ((Number) conflictingPosition.getOrDefault("y", 0.0)).doubleValue();
                totalZ += ((Number) conflictingPosition.getOrDefault("z", 0.0)).doubleValue();
                count++;
            }
        }
        
        interpolatedPosition.put("x", totalX / count);
        interpolatedPosition.put("y", totalY / count);
        interpolatedPosition.put("z", totalZ / count);
        
        return interpolatedPosition;
    }
}