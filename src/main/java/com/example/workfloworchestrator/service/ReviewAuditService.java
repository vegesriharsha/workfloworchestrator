package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.model.ReviewAuditLog;
import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.UserReviewPoint;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.repository.ReviewAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewAuditService {

    private final ReviewAuditLogRepository reviewAuditLogRepository;
    private final TaskExecutionService taskExecutionService;

    /**
     * Create audit log for a review decision
     */
    @Transactional
    public ReviewAuditLog createAuditLog(WorkflowExecution workflowExecution, UserReviewPoint reviewPoint) {
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(reviewPoint.getTaskExecutionId());

        ReviewAuditLog auditLog = new ReviewAuditLog();
        auditLog.setWorkflowExecutionId(workflowExecution.getId());
        auditLog.setWorkflowName(workflowExecution.getWorkflowDefinition().getName());
        auditLog.setTaskExecutionId(taskExecution.getId());
        auditLog.setTaskName(taskExecution.getTaskDefinition().getName());
        auditLog.setReviewPointId(reviewPoint.getId());
        auditLog.setReviewer(reviewPoint.getReviewer());
        auditLog.setTimestamp(reviewPoint.getReviewedAt());
        auditLog.setDecision(reviewPoint.getDecision());
        auditLog.setReplayStrategy(reviewPoint.getReplayStrategy());
        auditLog.setOverrideStatus(reviewPoint.getOverrideStatus());
        auditLog.setComment(reviewPoint.getComment());

        if (reviewPoint.getReplayTaskIds() != null) {
            auditLog.setReplayTaskIds(new ArrayList<>(reviewPoint.getReplayTaskIds()));
        }

        return reviewAuditLogRepository.save(auditLog);
    }

    /**
     * Get audit logs for a workflow execution
     */
    @Transactional(readOnly = true)
    public List<ReviewAuditLog> getAuditLogsForWorkflow(Long workflowExecutionId) {
        return reviewAuditLogRepository.findByWorkflowExecutionIdOrderByTimestampDesc(workflowExecutionId);
    }

    /**
     * Get audit logs for a task execution
     */
    @Transactional(readOnly = true)
    public List<ReviewAuditLog> getAuditLogsForTask(Long taskExecutionId) {
        return reviewAuditLogRepository.findByTaskExecutionIdOrderByTimestampDesc(taskExecutionId);
    }

    /**
     * Get audit logs by reviewer
     */
    @Transactional(readOnly = true)
    public List<ReviewAuditLog> getAuditLogsByReviewer(String reviewer) {
        return reviewAuditLogRepository.findByReviewerOrderByTimestampDesc(reviewer);
    }
}
