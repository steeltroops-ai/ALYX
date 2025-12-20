package com.alyx.jobscheduler.repository;

import com.alyx.jobscheduler.model.AnalysisJob;
import com.alyx.jobscheduler.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for AnalysisJob entities
 */
@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {
    
    /**
     * Find jobs by user ID
     */
    List<AnalysisJob> findByUserIdOrderBySubmittedAtDesc(String userId);
    
    /**
     * Find jobs by status
     */
    List<AnalysisJob> findByStatusOrderByPriorityAscSubmittedAtAsc(JobStatus status);
    
    /**
     * Find job by ID and user ID (for security)
     */
    Optional<AnalysisJob> findByJobIdAndUserId(UUID jobId, String userId);
    
    /**
     * Find queued jobs ordered by priority
     */
    @Query("SELECT j FROM AnalysisJob j WHERE j.status = 'QUEUED' ORDER BY j.priority ASC, j.submittedAt ASC")
    List<AnalysisJob> findQueuedJobsByPriority();
    
    /**
     * Count jobs by status
     */
    long countByStatus(JobStatus status);
    
    /**
     * Find running jobs for a user
     */
    @Query("SELECT j FROM AnalysisJob j WHERE j.userId = :userId AND j.status IN ('RUNNING', 'QUEUED')")
    List<AnalysisJob> findActiveJobsByUser(@Param("userId") String userId);
    
    /**
     * Count jobs by job type (using parameters field)
     */
    @Query("SELECT COUNT(j) FROM AnalysisJob j WHERE j.parameters.jobType = :jobType")
    long countByJobType(@Param("jobType") String jobType);
    
    /**
     * Count jobs by job type and status
     */
    @Query("SELECT COUNT(j) FROM AnalysisJob j WHERE j.parameters.jobType = :jobType AND j.status = :status")
    long countByJobTypeAndStatus(@Param("jobType") String jobType, @Param("status") JobStatus status);
    
    /**
     * Count completed jobs today for a job type
     */
    @Query("SELECT COUNT(j) FROM AnalysisJob j WHERE j.parameters.jobType = :jobType AND j.status = 'COMPLETED' AND j.actualCompletion >= CURRENT_DATE")
    long countCompletedToday(@Param("jobType") String jobType);
    
    /**
     * Count active jobs (running or queued)
     */
    @Query("SELECT COUNT(j) FROM AnalysisJob j WHERE j.status IN ('RUNNING', 'QUEUED')")
    long countActiveJobs();
    
    /**
     * Find active users in a time period
     */
    @Query("SELECT DISTINCT j.userId FROM AnalysisJob j WHERE j.submittedAt >= :since")
    List<String> findActiveUsersInPeriod(@Param("since") java.time.Instant since);
    
    /**
     * Count active jobs by user
     */
    @Query("SELECT COUNT(j) FROM AnalysisJob j WHERE j.userId = :userId AND j.status IN ('RUNNING', 'QUEUED')")
    long countActiveJobsByUser(@Param("userId") String userId);
}