package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.repository.WorkflowDefinitionRepository;
import com.example.workfloworchestrator.util.WorkflowVersioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowVersioning workflowVersioning;

    @Transactional(readOnly = true)
    public List<WorkflowDefinition> getAllWorkflowDefinitions() {
        return workflowDefinitionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowDefinition> getWorkflowDefinition(Long id) {
        return workflowDefinitionRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowDefinition> getLatestWorkflowDefinition(String name) {
        return workflowDefinitionRepository.findFirstByNameOrderByCreatedAtDesc(name);
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowDefinition> getWorkflowDefinition(String name, String version) {
        return workflowDefinitionRepository.findByNameAndVersion(name, version);
    }

    @Transactional
    public WorkflowDefinition createWorkflowDefinition(WorkflowDefinition workflowDefinition) {
        // Set default values
        workflowDefinition.setCreatedAt(LocalDateTime.now());

        // Generate version if not provided
        if (workflowDefinition.getVersion() == null || workflowDefinition.getVersion().isEmpty()) {
            String version = workflowVersioning.generateNextVersion(workflowDefinition.getName());
            workflowDefinition.setVersion(version);
        }

        // Set execution order for tasks if not already set
        List<TaskDefinition> tasks = workflowDefinition.getTasks();
        for (int i = 0; i < tasks.size(); i++) {
            TaskDefinition task = tasks.get(i);
            if (task.getExecutionOrder() == null) {
                task.setExecutionOrder(i);
            }
        }

        return workflowDefinitionRepository.save(workflowDefinition);
    }

    @Transactional
    public WorkflowDefinition updateWorkflowDefinition(Long id, WorkflowDefinition updatedWorkflow) {
        return workflowDefinitionRepository.findById(id)
                .map(existingWorkflow -> {
                    // Create a new version instead of updating the existing one
                    WorkflowDefinition newVersion = new WorkflowDefinition();
                    newVersion.setName(existingWorkflow.getName());
                    newVersion.setDescription(updatedWorkflow.getDescription());
                    newVersion.setTasks(updatedWorkflow.getTasks());
                    newVersion.setStrategyType(updatedWorkflow.getStrategyType());
                    newVersion.setCreatedAt(LocalDateTime.now());

                    // Generate new version
                    String newVersionNumber = workflowVersioning.generateNextVersion(existingWorkflow.getName());
                    newVersion.setVersion(newVersionNumber);

                    return workflowDefinitionRepository.save(newVersion);
                })
                .orElseThrow(() -> new WorkflowException("Workflow definition not found with id: " + id));
    }

    @Transactional
    public void deleteWorkflowDefinition(Long id) {
        workflowDefinitionRepository.deleteById(id);
    }
}
