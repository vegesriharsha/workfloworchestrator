package com.example.workfloworchestrator.util;

import com.example.workfloworchestrator.repository.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for workflow definition versioning
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowVersioning {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    /**
     * Generate the next version number for a workflow
     * Uses semantic versioning: MAJOR.MINOR.PATCH
     *
     * @param workflowName the workflow name
     * @return the next version number
     */
    public String generateNextVersion(String workflowName) {
        List<String> versions = workflowDefinitionRepository.findByNameOrderByVersionDesc(workflowName)
                .stream()
                .map(wd -> wd.getVersion())
                .toList();

        if (versions.isEmpty()) {
            // First version
            return "1.0.0";
        }

        // Get the latest version
        String latestVersion = versions.get(0);

        // Parse version
        Matcher matcher = VERSION_PATTERN.matcher(latestVersion);

        if (matcher.matches()) {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = Integer.parseInt(matcher.group(3));

            // Increment patch version
            patch++;

            return String.format("%d.%d.%d", major, minor, patch);
        } else {
            // If version doesn't match pattern, start with 1.0.0
            return "1.0.0";
        }
    }

    /**
     * Increment the major version number
     *
     * @param version the current version
     * @return the next major version
     */
    public String incrementMajorVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);

        if (matcher.matches()) {
            int major = Integer.parseInt(matcher.group(1));

            // Increment major version, reset others
            major++;

            return String.format("%d.0.0", major);
        } else {
            return "1.0.0";
        }
    }

    /**
     * Increment the minor version number
     *
     * @param version the current version
     * @return the next minor version
     */
    public String incrementMinorVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);

        if (matcher.matches()) {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));

            // Increment minor version, reset patch
            minor++;

            return String.format("%d.%d.0", major, minor);
        } else {
            return "1.0.0";
        }
    }
}
