package com.alyx.jobscheduler.model;

/**
 * Enumeration representing the various states of an analysis job
 */
public enum JobStatus {
    SUBMITTED,      // Job has been submitted and is awaiting validation
    QUEUED,         // Job has been validated and is waiting for resources
    RUNNING,        // Job is currently executing
    COMPLETED,      // Job has finished successfully
    FAILED,         // Job has failed due to an error
    CANCELLED,      // Job was cancelled by user or system
    PAUSED          // Job is temporarily paused
}