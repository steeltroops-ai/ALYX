package com.alyx.collaboration.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SharedState {
    @JsonProperty("analysisParameters")
    private Map<String, Object> analysisParameters = new ConcurrentHashMap<>();
    
    @JsonProperty("queryState")
    private Map<String, Object> queryState = new ConcurrentHashMap<>();
    
    @JsonProperty("visualizationState")
    private Map<String, Object> visualizationState = new ConcurrentHashMap<>();
    
    @JsonProperty("version")
    private long version = 1;

    public SharedState() {}

    public SharedState(Map<String, Object> analysisParameters, 
                      Map<String, Object> queryState, 
                      Map<String, Object> visualizationState) {
        this.analysisParameters = new ConcurrentHashMap<>(analysisParameters);
        this.queryState = new ConcurrentHashMap<>(queryState);
        this.visualizationState = new ConcurrentHashMap<>(visualizationState);
    }

    // Getters and setters
    public Map<String, Object> getAnalysisParameters() {
        return analysisParameters;
    }

    public void setAnalysisParameters(Map<String, Object> analysisParameters) {
        this.analysisParameters = analysisParameters;
    }

    public Map<String, Object> getQueryState() {
        return queryState;
    }

    public void setQueryState(Map<String, Object> queryState) {
        this.queryState = queryState;
    }

    public Map<String, Object> getVisualizationState() {
        return visualizationState;
    }

    public void setVisualizationState(Map<String, Object> visualizationState) {
        this.visualizationState = visualizationState;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public void incrementVersion() {
        this.version++;
    }

    public SharedState copy() {
        SharedState copy = new SharedState();
        copy.analysisParameters = new ConcurrentHashMap<>(this.analysisParameters);
        copy.queryState = new ConcurrentHashMap<>(this.queryState);
        copy.visualizationState = new ConcurrentHashMap<>(this.visualizationState);
        copy.version = this.version;
        return copy;
    }
}