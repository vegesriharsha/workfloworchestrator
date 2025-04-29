package com.example.workfloworchestrator.config;

import com.example.workfloworchestrator.engine.strategy.*;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for execution strategies
 * Maps strategy types to their corresponding implementations
 */
@Configuration
public class ExecutionStrategyConfig {

    /**
     * Create a map of strategy types to execution strategies
     *
     * @param sequentialStrategy the sequential execution strategy
     * @param parallelStrategy the parallel execution strategy
     * @param conditionalStrategy the conditional execution strategy
     * @return map of strategy types to strategies
     */
    @Bean
    public Map<WorkflowDefinition.ExecutionStrategyType, ExecutionStrategy> executionStrategies(
            SequentialExecutionStrategy sequentialStrategy,
            ParallelExecutionStrategy parallelStrategy,
            ConditionalExecutionStrategy conditionalStrategy,
            GraphExecutionStrategy graphStrategy,
            HybridExecutionStrategy hybridStrategy) {

        Map<WorkflowDefinition.ExecutionStrategyType, ExecutionStrategy> strategyMap = new HashMap<>();

        strategyMap.put(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL, sequentialStrategy);
        strategyMap.put(WorkflowDefinition.ExecutionStrategyType.PARALLEL, parallelStrategy);
        strategyMap.put(WorkflowDefinition.ExecutionStrategyType.CONDITIONAL, conditionalStrategy);
        strategyMap.put(WorkflowDefinition.ExecutionStrategyType.GRAPH, graphStrategy);
        strategyMap.put(WorkflowDefinition.ExecutionStrategyType.HYBRID, hybridStrategy);

        return strategyMap;
    }
}
