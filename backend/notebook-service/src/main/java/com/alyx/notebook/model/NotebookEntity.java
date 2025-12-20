package com.alyx.notebook.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "notebooks")
public class NotebookEntity {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<NotebookCell> cells;
    
    @Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
    @Column(columnDefinition = "jsonb")
    private NotebookMetadata metadata;
    
    @Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> collaborators;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "owner_id", nullable = false)
    private String ownerId;
    
    // Constructors
    public NotebookEntity() {}
    
    public NotebookEntity(String id, String name, String ownerId) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public List<NotebookCell> getCells() { return cells; }
    public void setCells(List<NotebookCell> cells) { this.cells = cells; }
    
    public NotebookMetadata getMetadata() { return metadata; }
    public void setMetadata(NotebookMetadata metadata) { this.metadata = metadata; }
    
    public List<String> getCollaborators() { return collaborators; }
    public void setCollaborators(List<String> collaborators) { this.collaborators = collaborators; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
        if (this.metadata != null) {
            this.metadata.setModified(this.updatedAt.toString());
            this.metadata.setVersion(this.metadata.getVersion() + 1);
        }
    }
    
    // Inner classes for JSON structure
    public static class NotebookCell {
        private String id;
        private String type;
        private String content;
        private Object output;
        private Integer executionCount;
        private Map<String, Object> metadata;
        
        // Constructors
        public NotebookCell() {}
        
        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public Object getOutput() { return output; }
        public void setOutput(Object output) { this.output = output; }
        
        @JsonProperty("executionCount")
        public Integer getExecutionCount() { return executionCount; }
        public void setExecutionCount(Integer executionCount) { this.executionCount = executionCount; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    public static class NotebookMetadata {
        private KernelSpec kernelspec;
        private LanguageInfo languageInfo;
        private String created;
        private String modified;
        private Integer version;
        
        // Constructors
        public NotebookMetadata() {}
        
        // Getters and Setters
        public KernelSpec getKernelspec() { return kernelspec; }
        public void setKernelspec(KernelSpec kernelspec) { this.kernelspec = kernelspec; }
        
        @JsonProperty("language_info")
        public LanguageInfo getLanguageInfo() { return languageInfo; }
        public void setLanguageInfo(LanguageInfo languageInfo) { this.languageInfo = languageInfo; }
        
        public String getCreated() { return created; }
        public void setCreated(String created) { this.created = created; }
        
        public String getModified() { return modified; }
        public void setModified(String modified) { this.modified = modified; }
        
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        
        public static class KernelSpec {
            private String name;
            private String language;
            
            public KernelSpec() {}
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            
            public String getLanguage() { return language; }
            public void setLanguage(String language) { this.language = language; }
        }
        
        public static class LanguageInfo {
            private String name;
            private String version;
            
            public LanguageInfo() {}
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            
            public String getVersion() { return version; }
            public void setVersion(String version) { this.version = version; }
        }
    }
}