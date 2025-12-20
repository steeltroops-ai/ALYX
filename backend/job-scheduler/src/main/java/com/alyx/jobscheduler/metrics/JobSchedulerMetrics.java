package com.alyx.jobscheduler.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom metrics for Job Scheduler service
 * Provides detailed metrics for job processing, queue management, and resource allocation
 */
@Component
public class JobSchedulerMetrics {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter jobSubmissionCounter;
    private final Counter jobCompletionCounter;
    private final Counter jobFailureCounter;
    private final Counter jobCancellationCounter;
    private final Counter mlPredictionCounter;
    private final Counter resourceAllocationCounter;

    // Timers
    private final Timer jobExecutionTimer;
    private final Timer jobValidationTimer;
    private final Timer mlPredictionTimer;
    private final Timer resourceAllocationTimer;

    // Gauge values
    private final AtomicLong activeJobs = new AtomicLong(0);
    private final AtomicLong queuedJobs = new AtomicLong(0);
    private final AtomicLong allocatedCores = new AtomicLong(0);
    private final AtomicLong allocatedMemoryMB = new AtomicLong(0);

    @Autowired
    public JobSchedulerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.jobSubmissionCounter = Counter.builder("alyx.jobscheduler.jobs.submitted")
                .tag("service", "job-scheduler")
                .description("Total number of jobs submitted")
                .register(meterRegistry);

        this.jobCompletionCounter = Counter.builder("alyx.jobscheduler.jobs.completed")
                .tag("service", "job-scheduler")
                .description("Total number of jobs completed successfully")
                .register(meterRegistry);

        this.jobFailureCounter = Counter.builder("alyx.jobscheduler.jobs.failed")
                .tag("service", "job-scheduler")
                .description("Total number of jobs that failed")
                .register(meterRegistry);

        this.jobCancellationCounter = Counter.builder("alyx.jobscheduler.jobs.cancelled")
                .tag("service", "job-scheduler")
                .description("Total number of jobs cancelled")
                .register(meterRegistry);

        this.mlPredictionCounter = Counter.builder("alyx.jobscheduler.ml.predictions")
                .tag("service", "job-scheduler")
                .description("Total number of ML predictions made")
                .register(meterRegistry);

        this.resourceAllocationCounter = Counter.builder("alyx.jobscheduler.resources.allocated")
                .tag("service", "job-scheduler")
                .description("Total number of resource allocations")
                .register(meterRegistry);

        // Initialize timers
        this.jobExecutionTimer = Timer.builder("alyx.jobscheduler.job.execution.duration")
                .tag("service", "job-scheduler")
                .description("Job execution duration")
                .register(meterRegistry);

        this.jobValidationTimer = Timer.builder("alyx.jobscheduler.job.validation.duration")
                .tag("service", "job-scheduler")
                .description("Job validation duration")
                .register(meterRegistry);

        this.mlPredictionTimer = Timer.builder("alyx.jobscheduler.ml.prediction.duration")
                .tag("service", "job-scheduler")
                .description("ML prediction duration")
                .register(meterRegistry);

        this.resourceAllocationTimer = Timer.builder("alyx.jobscheduler.resource.allocation.duration")
                .tag("service", "job-scheduler")
                .description("Resource allocation duration")
                .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("alyx.jobscheduler.jobs.active", this, JobSchedulerMetrics::getActiveJobs)
                .tag("service", "job-scheduler")
                .description("Number of currently active jobs")
                .register(meterRegistry);

        Gauge.builder("alyx.jobscheduler.jobs.queued", this, JobSchedulerMetrics::getQueuedJobs)
                .tag("service", "job-scheduler")
                .description("Number of jobs in queue")
                .register(meterRegistry);

        Gauge.builder("alyx.jobscheduler.resources.cores.allocated", this, JobSchedulerMetrics::getAllocatedCores)
                .tag("service", "job-scheduler")
                .description("Total allocated CPU cores")
                .register(meterRegistry);

        Gauge.builder("alyx.jobscheduler.resources.memory.allocated.mb", this, JobSchedulerMetrics::getAllocatedMemoryMB)
                .tag("service", "job-scheduler")
                .description("Total allocated memory in MB")
                .register(meterRegistry);
    }

    // Counter increment methods
    public void incrementJobSubmissions() {
        jobSubmissionCounter.increment();
    }

    public void incrementJobCompletions() {
        jobCompletionCounter.increment();
    }

    public void incrementJobFailures() {
        jobFailureCounter.increment();
    }

    public void incrementJobCancellations() {
        jobCancellationCounter.increment();
    }

    public void incrementMlPredictions() {
        mlPredictionCounter.increment();
    }

    public void incrementResourceAllocations() {
        resourceAllocationCounter.increment();
    }

    // Timer recording methods
    public Timer.Sample startJobExecutionTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordJobExecution(Timer.Sample sample) {
        sample.stop(jobExecutionTimer);
    }

    public Timer.Sample startJobValidationTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordJobValidation(Timer.Sample sample) {
        sample.stop(jobValidationTimer);
    }

    public Timer.Sample startMlPredictionTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordMlPrediction(Timer.Sample sample) {
        sample.stop(mlPredictionTimer);
    }

    public Timer.Sample startResourceAllocationTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordResourceAllocation(Timer.Sample sample) {
        sample.stop(resourceAllocationTimer);
    }

    // Gauge update methods
    public void setActiveJobs(long count) {
        activeJobs.set(count);
    }

    public void setQueuedJobs(long count) {
        queuedJobs.set(count);
    }

    public void setAllocatedCores(long cores) {
        allocatedCores.set(cores);
    }

    public void setAllocatedMemoryMB(long memoryMB) {
        allocatedMemoryMB.set(memoryMB);
    }

    // Gauge getter methods
    public long getActiveJobs() {
        return activeJobs.get();
    }

    public long getQueuedJobs() {
        return queuedJobs.get();
    }

    public long getAllocatedCores() {
        return allocatedCores.get();
    }

    public long getAllocatedMemoryMB() {
        return allocatedMemoryMB.get();
    }
}