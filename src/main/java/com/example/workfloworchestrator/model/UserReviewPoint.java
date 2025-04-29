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
@Table(name = "user_review_points")
public class UserReviewPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_execution_id")
    private Long taskExecutionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewer")
    private String reviewer;

    @Column(name = "comment")
    private String comment;

    @Column(name = "decision")
    @Enumerated(EnumType.STRING)
    private ReviewDecision decision;

    // Fields to support task replay options
    @Column(name = "replay_strategy")
    @Enumerated(EnumType.STRING)
    private ReplayStrategy replayStrategy;

    @ElementCollection
    @CollectionTable(name = "review_point_replay_tasks",
            joinColumns = @JoinColumn(name = "review_point_id"))
    @Column(name = "task_id")
    private List<Long> replayTaskIds = new ArrayList<>();

    @Column(name = "previous_review_point_id")
    private Long previousReviewPointId;

    @Column(name = "override_status")
    @Enumerated(EnumType.STRING)
    private TaskStatus overrideStatus;

    public enum ReviewDecision {
        APPROVE,          // Approve task and continue
        REJECT,           // Reject task and fail
        RESTART,          // Restart this task
        REPLAY_SUBSET,    // Replay selected tasks
        REPLAY_ALL,       // Replay all prior tasks
        REPLAY_FROM_LAST, // Replay from last review point
        OVERRIDE          // Override task status and continue
    }

    public enum ReplayStrategy {
        NONE,             // No replay
        SINGLE_TASK,      // Replay single task
        SELECTED_TASKS,   // Replay selected tasks
        ALL_PRIOR_TASKS,  // Replay all prior tasks
        FROM_LAST_REVIEW  // Replay from last review point
    }
}
