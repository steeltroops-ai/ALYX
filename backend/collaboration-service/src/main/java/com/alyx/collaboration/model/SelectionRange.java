package com.alyx.collaboration.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SelectionRange {
    @JsonProperty("startLine")
    private int startLine;
    
    @JsonProperty("startColumn")
    private int startColumn;
    
    @JsonProperty("endLine")
    private int endLine;
    
    @JsonProperty("endColumn")
    private int endColumn;
    
    @JsonProperty("elementId")
    private String elementId;

    public SelectionRange() {}

    public SelectionRange(int startLine, int startColumn, int endLine, int endColumn, String elementId) {
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.elementId = elementId;
    }

    // Getters and setters
    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public void setStartColumn(int startColumn) {
        this.startColumn = startColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public void setEndColumn(int endColumn) {
        this.endColumn = endColumn;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }
}