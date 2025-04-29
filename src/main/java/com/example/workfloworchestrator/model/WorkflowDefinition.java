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
@Table(name = "workflow_definitions")
public class WorkflowDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "workflow_definition_id")
    private List<TaskDefinition> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "workflowDefinition")
    private List<WorkflowExecution> executions = new ArrayList<>();

    @Column(name = "strategy_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ExecutionStrategyType strategyType = ExecutionStrategyType.SEQUENTIAL;

    public enum ExecutionStrategyType {
        SEQUENTIAL,
        PARALLEL,
        CONDITIONAL,
        GRAPH,
        HYBRID
    }
}
