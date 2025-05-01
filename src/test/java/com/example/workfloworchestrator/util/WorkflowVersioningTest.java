package com.example.workfloworchestrator.util;

import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WorkflowVersioningTest {

    @Mock
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @InjectMocks
    private WorkflowVersioning workflowVersioning;

    private WorkflowDefinition createWorkflowDefinition(String name, String version) {
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setName(name);
        workflowDefinition.setVersion(version);
        workflowDefinition.setCreatedAt(LocalDateTime.now());
        return workflowDefinition;
    }

    @Test
    public void testGenerateNextVersion_FirstVersion() {
        // Test generating version for a new workflow (no existing versions)
        when(workflowDefinitionRepository.findByNameOrderByVersionDesc("NewWorkflow"))
                .thenReturn(Collections.emptyList());

        String version = workflowVersioning.generateNextVersion("NewWorkflow");
        assertEquals("1.0.0", version, "First version should be 1.0.0");
    }

    @Test
    public void testGenerateNextVersion_IncrementPatch() {
        // Test incrementing patch version (1.0.0 -> 1.0.1)
        WorkflowDefinition existingWorkflow = createWorkflowDefinition("TestWorkflow", "1.0.0");
        when(workflowDefinitionRepository.findByNameOrderByVersionDesc("TestWorkflow"))
                .thenReturn(Collections.singletonList(existingWorkflow));

        String version = workflowVersioning.generateNextVersion("TestWorkflow");
        assertEquals("1.0.1", version, "Patch version should be incremented");
    }

    @Test
    public void testGenerateNextVersion_MultiplePreviousVersions() {
        // Test with multiple previous versions
        WorkflowDefinition version1 = createWorkflowDefinition("TestWorkflow", "1.0.0");
        WorkflowDefinition version2 = createWorkflowDefinition("TestWorkflow", "1.0.1");
        WorkflowDefinition version3 = createWorkflowDefinition("TestWorkflow", "1.0.2");

        List<WorkflowDefinition> versions = Arrays.asList(version3, version2, version1);
        when(workflowDefinitionRepository.findByNameOrderByVersionDesc("TestWorkflow"))
                .thenReturn(versions);

        String version = workflowVersioning.generateNextVersion("TestWorkflow");
        assertEquals("1.0.3", version, "Patch version should be incremented from the latest version");
    }

    @Test
    public void testGenerateNextVersion_NonStandardVersion() {
        // Test with a non-standard version format
        WorkflowDefinition existingWorkflow = createWorkflowDefinition("TestWorkflow", "not-a-standard-version");
        when(workflowDefinitionRepository.findByNameOrderByVersionDesc("TestWorkflow"))
                .thenReturn(Collections.singletonList(existingWorkflow));

        String version = workflowVersioning.generateNextVersion("TestWorkflow");
        assertEquals("1.0.0", version, "Should default to 1.0.0 for non-standard version formats");
    }

    @Test
    public void testIncrementMajorVersion() {
        String version = workflowVersioning.incrementMajorVersion("1.2.3");
        assertEquals("2.0.0", version, "Major version should be incremented and others reset to zero");

        // Test with non-standard version format
        String nonStandardVersion = workflowVersioning.incrementMajorVersion("not-a-version");
        assertEquals("1.0.0", version, "Should default to 1.0.0 for non-standard version formats");
    }

    @Test
    public void testIncrementMinorVersion() {
        String version = workflowVersioning.incrementMinorVersion("1.2.3");
        assertEquals("1.3.0", version, "Minor version should be incremented and patch reset to zero");

        // Test with non-standard version format
        String nonStandardVersion = workflowVersioning.incrementMinorVersion("not-a-version");
        assertEquals("1.0.0", version, "Should default to 1.0.0 for non-standard version formats");
    }
}
