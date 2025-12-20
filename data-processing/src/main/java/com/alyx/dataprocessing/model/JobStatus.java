package com.alyx.dataprocessing.model;

/**
 * Enumeration of possible analysis job statuses.
 * Represents the lifecycle states of an analysis job.
 */
public enum JobStatus {
    /**
     * Job has been submitted and is waiting to be processed
     */
    QUEUED,
    
    /**
     * Job is currently being executed
     */
    RUNNING,
    
    /**
     * Job has completed successfully
     */
    COMPLETED,
    
    /**
     * Job has failed due to an error
     */
    FAILED,
    
    /**
     * Job has been cancelled by user or system
     */
    CANCELLED,
    
    /**
     * Job is paused and can be resumed
     */
    PAUSED
}