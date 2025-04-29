package com.example.workfloworchestrator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "review_audit_logs")
public class ReviewAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_execution_id", nullable = false)
    private Long workflowExecutionId;

    @Column(name = "workflow_name", nullable = false)
    private String workflowName;

    @Column(name = "task_execution_id", nullable = false)
    private Long taskExecutionId;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    @Column(name = "review_point_id", nullable = false)
    private Long reviewPointId;

    @Column(name = "reviewer", nullable = false)
    private String reviewer;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "decision", nullable = false)
    @Enumerated(EnumType.STRING)
    private UserReviewPoint.ReviewDecision decision;

    @Column(name = "replay_strategy")
    @Enumerated(EnumType.STRING)
    private UserReviewPoint.ReplayStrategy replayStrategy;

    @Column(name = "override_status")
    @Enumerated(EnumType.STRING)
    private TaskStatus overrideStatus;

    @ElementCollection
    @CollectionTable(name = "review_audit_replay_tasks",
            joinColumns = @JoinColumn(name = "audit_log_id"))
    @Column(name = "task_id")
    private List<Long> replayTaskIds = new ArrayList<>();

    @Column(name = "comment", length = 4000)
    private String comment;
}
