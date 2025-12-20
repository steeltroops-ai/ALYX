package com.alyx.notebook.controller;

import com.alyx.notebook.model.NotebookEntity;
import com.alyx.notebook.service.NotebookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notebooks")
@CrossOrigin(origins = "*")
public class NotebookController {
    
    @Autowired
    private NotebookService notebookService;
    
    @GetMapping
    public ResponseEntity<List<NotebookEntity>> getAllNotebooks(
            @RequestParam(defaultValue = "user1") String userId) {
        List<NotebookEntity> notebooks = notebookService.getNotebooksByUser(userId);
        return ResponseEntity.ok(notebooks);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<NotebookEntity> getNotebook(@PathVariable String id) {
        NotebookEntity notebook = notebookService.getNotebook(id);
        if (notebook != null) {
            return ResponseEntity.ok(notebook);
        }
        return ResponseEntity.notFound().build();
    }
    
    @PostMapping
    public ResponseEntity<NotebookEntity> createNotebook(@RequestBody NotebookEntity notebook) {
        NotebookEntity created = notebookService.createNotebook(notebook);
        return ResponseEntity.ok(created);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<NotebookEntity> updateNotebook(
            @PathVariable String id, 
            @RequestBody NotebookEntity notebook) {
        notebook.setId(id);
        NotebookEntity updated = notebookService.updateNotebook(notebook);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotebook(@PathVariable String id) {
        notebookService.deleteNotebook(id);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{id}/share")
    public ResponseEntity<NotebookEntity> shareNotebook(
            @PathVariable String id,
            @RequestBody Map<String, List<String>> request) {
        List<String> collaborators = request.get("collaborators");
        NotebookEntity shared = notebookService.shareNotebook(id, collaborators);
        return ResponseEntity.ok(shared);
    }
    
    @PostMapping("/{id}/execute")
    public ResponseEntity<Map<String, Object>> executeCell(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        String cellId = (String) request.get("cellId");
        String content = (String) request.get("content");
        
        Map<String, Object> result = notebookService.executeCell(id, cellId, content);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<NotebookEntity>> getNotebookVersions(@PathVariable String id) {
        List<NotebookEntity> versions = notebookService.getNotebookVersions(id);
        return ResponseEntity.ok(versions);
    }
}