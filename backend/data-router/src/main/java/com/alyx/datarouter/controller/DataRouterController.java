package com.alyx.datarouter.controller;

import com.alyx.datarouter.model.DataDistributionRequest;
import com.alyx.datarouter.model.GridResource;
import com.alyx.datarouter.service.DataRouterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/data-router")
public class DataRouterController {
    
    @Autowired
    private DataRouterService dataRouterService;
    
    @PostMapping("/distribute")
    public ResponseEntity<List<GridResource>> distributeLoad(
            @RequestBody List<DataDistributionRequest> requests) {
        List<GridResource> allocatedResources = dataRouterService.distributeLoad(requests);
        return ResponseEntity.ok(allocatedResources);
    }
    
    @GetMapping("/resources")
    public ResponseEntity<List<GridResource>> getAvailableResources() {
        List<GridResource> resources = dataRouterService.getAvailableResources();
        return ResponseEntity.ok(resources);
    }
    
    @PostMapping("/resources")
    public ResponseEntity<Void> addGridResource(@RequestBody GridResource resource) {
        dataRouterService.addGridResource(resource);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/resources/{resourceId}")
    public ResponseEntity<Void> removeGridResource(@PathVariable String resourceId) {
        dataRouterService.removeGridResource(resourceId);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/resources/{resourceId}/status")
    public ResponseEntity<Void> updateResourceStatus(
            @PathVariable String resourceId,
            @RequestParam double cpuUtilization,
            @RequestParam double memoryUtilization) {
        dataRouterService.updateResourceStatus(resourceId, cpuUtilization, memoryUtilization);
        return ResponseEntity.ok().build();
    }
}