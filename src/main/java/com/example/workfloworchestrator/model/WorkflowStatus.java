package com.example.workfloworchestrator.model;

public enum WorkflowStatus {
    CREATED,
    RUNNING,
    PAUSED,
    AWAITING_USER_REVIEW,
    AWAITING_CALLBACK,
    COMPLETED,
    FAILED,
    CANCELLED
}
