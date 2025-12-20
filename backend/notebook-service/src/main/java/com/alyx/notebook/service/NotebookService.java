package com.alyx.notebook.service;

import com.alyx.notebook.model.NotebookEntity;
import com.alyx.notebook.repository.NotebookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class NotebookService {
    
    @Autowired
    private NotebookRepository notebookRepository;
    
    @Autowired
    private NotebookExecutionService executionService;
    
    public List<NotebookEntity> getNotebooksByUser(String userId) {
        return notebookRepository.findByOwnerIdOrderByUpdatedAtDesc(userId);
    }
    
    public NotebookEntity getNotebook(String id) {
        return notebookRepository.findById(id).orElse(null);
    }
    
    public NotebookEntity createNotebook(NotebookEntity notebook) {
        if (notebook.getId() == null) {
            notebook.setId(UUID.randomUUID().toString());
        }
        
        if (notebook.getOwnerId() == null) {
            notebook.setOwnerId("user1"); // Default user for demo
        }
        
        // Initialize metadata if not provided
        if (notebook.getMetadata() == null) {
            NotebookEntity.NotebookMetadata metadata = new NotebookEntity.NotebookMetadata();
            
            NotebookEntity.NotebookMetadata.KernelSpec kernelSpec = new NotebookEntity.NotebookMetadata.KernelSpec();
            kernelSpec.setName("physics-kernel");
            kernelSpec.setLanguage("python");
            metadata.setKernelspec(kernelSpec);
            
            NotebookEntity.NotebookMetadata.LanguageInfo langInfo = new NotebookEntity.NotebookMetadata.LanguageInfo();
            langInfo.setName("python");
            langInfo.setVersion("3.9.0");
            metadata.setLanguageInfo(langInfo);
            
            metadata.setCreated(Instant.now().toString());
            metadata.setModified(Instant.now().toString());
            metadata.setVersion(1);
            
            notebook.setMetadata(metadata);
        }
        
        // Initialize cells if not provided
        if (notebook.getCells() == null || notebook.getCells().isEmpty()) {
            NotebookEntity.NotebookCell defaultCell = new NotebookEntity.NotebookCell();
            defaultCell.setId(UUID.randomUUID().toString());
            defaultCell.setType("code");
            defaultCell.setContent("# Welcome to ALYX Analysis Notebook\n# Access collision data: collision_data.getEvents()\n# Create visualizations: physics_plots.createTrajectoryPlot()\n# Submit to GRID: grid_resources.submitJob()");
            defaultCell.setExecutionCount(0);
            
            notebook.setCells(Arrays.asList(defaultCell));
        }
        
        notebook.setCreatedAt(Instant.now());
        notebook.setUpdatedAt(Instant.now());
        
        return notebookRepository.save(notebook);
    }
    
    public NotebookEntity updateNotebook(NotebookEntity notebook) {
        NotebookEntity existing = notebookRepository.findById(notebook.getId()).orElse(null);
        if (existing == null) {
            throw new RuntimeException("Notebook not found: " + notebook.getId());
        }
        
        // Update fields
        existing.setName(notebook.getName());
        existing.setCells(notebook.getCells());
        existing.setCollaborators(notebook.getCollaborators());
        
        // Update metadata
        if (notebook.getMetadata() != null) {
            existing.setMetadata(notebook.getMetadata());
        }
        
        return notebookRepository.save(existing);
    }
    
    public void deleteNotebook(String id) {
        notebookRepository.deleteById(id);
    }
    
    public NotebookEntity shareNotebook(String id, List<String> collaborators) {
        NotebookEntity notebook = getNotebook(id);
        if (notebook == null) {
            throw new RuntimeException("Notebook not found: " + id);
        }
        
        notebook.setCollaborators(collaborators);
        return notebookRepository.save(notebook);
    }
    
    public Map<String, Object> executeCell(String notebookId, String cellId, String content) {
        NotebookEntity notebook = getNotebook(notebookId);
        if (notebook == null) {
            throw new RuntimeException("Notebook not found: " + notebookId);
        }
        
        // Check if this is resource-intensive execution
        boolean isResourceIntensive = isResourceIntensive(content);
        
        Map<String, Object> result = new HashMap<>();
        
        if (isResourceIntensive) {
            // Submit to GRID resources asynchronously
            CompletableFuture<Map<String, Object>> future = executionService.executeOnGrid(content);
            
            String jobId = "job-" + System.currentTimeMillis();
            result.put("jobId", jobId);
            result.put("output", "Job submitted to GRID resources: " + jobId);
            result.put("executedAt", Instant.now().toString());
            
            // In a real implementation, we would track the job and update the cell when complete
            future.thenAccept(gridResult -> {
                // Update cell with final result
                updateCellOutput(notebookId, cellId, gridResult);
            });
            
        } else {
            // Execute locally
            result = executionService.executeLocally(content);
            result.put("executedAt", Instant.now().toString());
            
            // Update cell immediately
            updateCellOutput(notebookId, cellId, result);
        }
        
        return result;
    }
    
    private void updateCellOutput(String notebookId, String cellId, Map<String, Object> result) {
        NotebookEntity notebook = getNotebook(notebookId);
        if (notebook != null && notebook.getCells() != null) {
            for (NotebookEntity.NotebookCell cell : notebook.getCells()) {
                if (cellId.equals(cell.getId())) {
                    cell.setOutput(result);
                    cell.setExecutionCount((cell.getExecutionCount() != null ? cell.getExecutionCount() : 0) + 1);
                    break;
                }
            }
            notebookRepository.save(notebook);
        }
    }
    
    private boolean isResourceIntensive(String content) {
        String[] patterns = {
            "large_dataset",
            "parallel_processing", 
            "memory_intensive_operation",
            "grid_compute",
            "distributed_analysis"
        };
        
        for (String pattern : patterns) {
            if (content.contains(pattern)) {
                return true;
            }
        }
        
        return content.length() > 500;
    }
    
    public List<NotebookEntity> getNotebookVersions(String id) {
        // In a real implementation, this would fetch actual version history
        // For now, return the current notebook as the only version
        NotebookEntity current = getNotebook(id);
        if (current != null) {
            return Arrays.asList(current);
        }
        return new ArrayList<>();
    }
}