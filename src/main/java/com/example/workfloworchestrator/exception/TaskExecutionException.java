package com.example.workfloworchestrator.exception;

/**
 * Exception for task execution errors
 */
public class TaskExecutionException extends RuntimeException {

    public TaskExecutionException(String message) {
        super(message);
    }

    public TaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
