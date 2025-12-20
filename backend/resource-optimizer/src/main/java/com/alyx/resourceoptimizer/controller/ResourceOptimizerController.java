package com.alyx.resourceoptimizer.controller;

import com.alyx.resourceoptimizer.model.Job;
import com.alyx.resourceoptimizer.model.Resource;
import com.alyx.resourceoptimizer.service.ResourceOptimizerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resource-optimizer")
public class ResourceOptimizerController {
    
    @Autowired
    private ResourceOptimizerService resourceOptimizerService;
    
    @PostMapping("/jobs/schedule")
    public ResponseEntity<Boolean> scheduleJob(@RequestBody Job job) {
        boolean scheduled = resourceOptimizerService.scheduleJobWithPreemption(job);
        return ResponseEntity.ok(scheduled);
    }
    
    @PostMapping("/jobs/{jobId}/complete")
    public ResponseEntity<Void> completeJob(@PathVariable String jobId) {
        resourceOptimizerService.completeJob(jobId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/jobs/running")
    public ResponseEntity<List<Job>> getRunningJobs() {
        List<Job> runningJobs = resourceOptimizerService.getRunningJobs();
        return ResponseEntity.ok(runningJobs);
    }
    
    @GetMapping("/jobs/queued")
    public ResponseEntity<List<Job>> getQueuedJobs() {
        List<Job> queuedJobs = resourceOptimizerService.getQueuedJobs();
        return ResponseEntity.ok(queuedJobs);
    }
    
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Job> getJob(@PathVariable String jobId) {
        Job job = resourceOptimizerService.getJob(jobId);
        if (job != null) {
            return ResponseEntity.ok(job);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/resources")
    public ResponseEntity<Void> addResource(@RequestBody Resource resource) {
        resourceOptimizerService.addResource(resource);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/resources/{resourceId}")
    public ResponseEntity<Void> removeResource(@PathVariable String resourceId) {
        resourceOptimizerService.removeResource(resourceId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/optimize")
    public ResponseEntity<Void> optimizeResources() {
        resourceOptimizerService.optimizeResourceAllocation();
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/utilization")
    public ResponseEntity<Double> getSystemUtilization() {
        double utilization = resourceOptimizerService.getOverallSystemUtilization();
        return ResponseEntity.ok(utilization);
    }
    
    @PostMapping("/resources/{resourceId}/failure")
    public ResponseEntity<Void> simulateResourceFailure(@PathVariable String resourceId) {
        resourceOptimizerService.simulateResourceFailure(resourceId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/resources/{resourceId}/restore")
    public ResponseEntity<Void> restoreResource(@PathVariable String resourceId) {
        resourceOptimizerService.restoreResource(resourceId);
        return ResponseEntity.ok().build();
    }
}