package com.example.workfloworchestrator.repository;

import com.example.workfloworchestrator.model.ReviewAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for handling ReviewAuditLog entity persistence
 */
@Repository
public interface ReviewAuditLogRepository extends JpaRepository<ReviewAuditLog, Long> {

    /**
     * Find all audit logs for a specific workflow execution
     *
     * @param workflowExecutionId The workflow execution ID
     * @return List of audit logs ordered by timestamp (descending)
     */
    List<ReviewAuditLog> findByWorkflowExecutionIdOrderByTimestampDesc(Long workflowExecutionId);

    /**
     * Find all audit logs for a specific task execution
     *
     * @param taskExecutionId The task execution ID
     * @return List of audit logs ordered by timestamp (descending)
     */
    List<ReviewAuditLog> findByTaskExecutionIdOrderByTimestampDesc(Long taskExecutionId);

    /**
     * Find all audit logs created by a specific reviewer
     *
     * @param reviewer The reviewer's username or ID
     * @return List of audit logs ordered by timestamp (descending)
     */
    List<ReviewAuditLog> findByReviewerOrderByTimestampDesc(String reviewer);

    /**
     * Find audit logs for a specific review point
     *
     * @param reviewPointId The review point ID
     * @return List of audit logs ordered by timestamp (descending)
     */
    List<ReviewAuditLog> findByReviewPointIdOrderByTimestampDesc(Long reviewPointId);

    /**
     * Find audit logs within a specific time period
     *
     * @param startTime The start time
     * @param endTime The end time
     * @return List of audit logs ordered by timestamp (descending)
     */
    List<ReviewAuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Find audit logs for a specific decision type
     *
     * @param decision The review decision
     * @return List of audit logs ordered by timestamp (descending)
     */
    List<ReviewAuditLog> findByDecisionOrderByTimestampDesc(String decision);

    /**
     * Search for audit logs by keyword in comments
     *
     * @param keyword The keyword to search for
     * @return List of matching audit logs
     */
    @Query("SELECT log FROM ReviewAuditLog log WHERE log.comment LIKE %:keyword%")
    List<ReviewAuditLog> searchByCommentKeyword(@Param("keyword") String keyword);

    /**
     * Count the number of audit logs by decision type
     *
     * @param decision The review decision
     * @return Count of audit logs with the specified decision
     */
    long countByDecision(String decision);

    /**
     * Find the most recent audit log for a specific task
     *
     * @param taskExecutionId The task execution ID
     * @return The most recent audit log, if any
     */
    @Query("SELECT log FROM ReviewAuditLog log WHERE log.taskExecutionId = :taskId " +
            "ORDER BY log.timestamp DESC LIMIT 1")
    ReviewAuditLog findMostRecentByTaskExecutionId(@Param("taskId") Long taskExecutionId);

    /**
     * Find overridden task audits
     *
     * @return List of audit logs for tasks with overridden status
     */
    @Query("SELECT log FROM ReviewAuditLog log WHERE log.overrideStatus IS NOT NULL")
    List<ReviewAuditLog> findAllOverrides();
}
