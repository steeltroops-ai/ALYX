package com.alyx.collaboration.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CursorPosition {
    @JsonProperty("x")
    private double x;
    
    @JsonProperty("y")
    private double y;
    
    @JsonProperty("elementId")
    private String elementId;

    public CursorPosition() {}

    public CursorPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public CursorPosition(double x, double y, String elementId) {
        this.x = x;
        this.y = y;
        this.elementId = elementId;
    }

    // Getters and setters
    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }
}