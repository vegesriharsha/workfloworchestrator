package com.example.workfloworchestrator.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Message for task execution via RabbitMQ
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage implements Serializable {

    private Long taskExecutionId;
    private String correlationId;
    private String taskType;
    private Map<String, String> inputs = new HashMap<>();
    private Map<String, String> configuration = new HashMap<>();
    private Map<String, String> outputs = new HashMap<>();
    private boolean success = false;
    private String errorMessage;
}
