package com.example.workfloworchestrator.event;

/**
 * Enum for workflow event types
 */
public enum WorkflowEventType {
    CREATED,
    STARTED,
    COMPLETED,
    FAILED,
    REPLAY,
    PAUSED,
    RESUMED,
    CANCELLED,
    RETRY,
    STATUS_CHANGED
}
