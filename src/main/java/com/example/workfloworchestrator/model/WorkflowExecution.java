package com.example.workfloworchestrator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workflow_executions")
public class WorkflowExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private WorkflowStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "current_task_index")
    private Integer currentTaskIndex;

    @ElementCollection
    @CollectionTable(name = "workflow_execution_variables",
            joinColumns = @JoinColumn(name = "workflow_execution_id"))
    @MapKeyColumn(name = "variable_key")
    @Column(name = "variable_value")
    private Map<String, String> variables = new HashMap<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "workflow_execution_id")
    private List<TaskExecution> taskExecutions = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "workflow_execution_id")
    private List<UserReviewPoint> reviewPoints = new ArrayList<>();

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;
}
