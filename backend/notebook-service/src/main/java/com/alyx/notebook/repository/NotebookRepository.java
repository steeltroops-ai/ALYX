package com.alyx.notebook.repository;

import com.alyx.notebook.model.NotebookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotebookRepository extends JpaRepository<NotebookEntity, String> {
    
    List<NotebookEntity> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);
    
    List<NotebookEntity> findByCollaboratorsContaining(String collaboratorId);
    
    List<NotebookEntity> findByOwnerIdOrCollaboratorsContaining(String ownerId, String collaboratorId);
}