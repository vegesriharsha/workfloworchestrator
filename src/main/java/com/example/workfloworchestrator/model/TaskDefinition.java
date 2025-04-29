package com.example.workfloworchestrator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "task_definitions")
public class TaskDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String type;

    @Column(name = "execution_order")
    private Integer executionOrder;

    @ElementCollection
    @CollectionTable(name = "task_definition_config",
            joinColumns = @JoinColumn(name = "task_definition_id"))
    @MapKeyColumn(name = "config_key")
    @Column(name = "config_value")
    private Map<String, String> configuration = new HashMap<>();

    @Column(name = "retry_limit")
    private Integer retryLimit;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "execution_mode")
    @Enumerated(EnumType.STRING)
    private ExecutionMode executionMode = ExecutionMode.API;

    @Column(name = "require_user_review")
    private boolean requireUserReview = false;

    @Column(name = "conditional_expression")
    private String conditionalExpression;

    @Column(name = "next_task_on_success")
    private Long nextTaskOnSuccess;

    @Column(name = "next_task_on_failure")
    private Long nextTaskOnFailure;

    @ElementCollection
    @CollectionTable(name = "task_dependencies",
            joinColumns = @JoinColumn(name = "task_definition_id"))
    @Column(name = "dependency_task_id")
    private List<Long> dependsOn = new ArrayList<>();

    // Execution group for parallel execution within groups
    @Column(name = "execution_group")
    private String executionGroup = "default";

    // Whether this task can be executed in parallel within its group
    @Column(name = "parallel_execution")
    private boolean parallelExecution = false;

    /**
     * Convenience method to determine if this task has dependencies
     * @return true if the task has dependencies, false otherwise
     */
    @Transient
    public boolean hasDependencies() {
        return dependsOn != null && !dependsOn.isEmpty();
    }

    /**
     * Convenience method to get the ID of this task
     * @return the task ID
     */
    @Transient
    public Long getId() {
        return id;
    }
}
