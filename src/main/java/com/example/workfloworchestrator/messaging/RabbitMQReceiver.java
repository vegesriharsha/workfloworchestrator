package com.example.workfloworchestrator.messaging;

import com.example.workfloworchestrator.config.RabbitMQConfig;
import com.example.workfloworchestrator.engine.executor.RabbitMQTaskExecutor;
import com.example.workfloworchestrator.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Component for receiving messages from RabbitMQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQReceiver {

    private final TaskExecutionService taskExecutionService;
    private final RabbitMQTaskExecutor rabbitMQTaskExecutor;

    /**
     * Receive task result message from the result queue
     */
    @RabbitListener(queues = RabbitMQConfig.WORKFLOW_RESULT_QUEUE)
    public void receiveTaskResult(TaskMessage resultMessage) {
        log.info("Received task result message from queue: {}, taskType: {}, correlationId: {}, success: {}",
                RabbitMQConfig.WORKFLOW_RESULT_QUEUE,
                resultMessage.getTaskType(),
                resultMessage.getCorrelationId(),
                resultMessage.isSuccess());

        try {
            Long taskExecutionId = resultMessage.getTaskExecutionId();

            if (taskExecutionId != null) {
                // Update task execution with result
                if (resultMessage.isSuccess()) {
                    taskExecutionService.completeTaskExecution(
                            taskExecutionId, resultMessage.getOutputs());
                } else {
                    taskExecutionService.failTaskExecution(
                            taskExecutionId, resultMessage.getErrorMessage());
                }
            } else {
                // Task execution ID not available, use correlation ID for mapping
                String correlationId = resultMessage.getCorrelationId();

                if (correlationId != null) {
                    // Convert outputs from string map to object map for the RabbitMQTaskExecutor
                    Map<String, Object> outputs = new HashMap<>();
                    outputs.putAll(resultMessage.getOutputs());
                    outputs.put("success", resultMessage.isSuccess());

                    if (!resultMessage.isSuccess() && resultMessage.getErrorMessage() != null) {
                        outputs.put("error", resultMessage.getErrorMessage());
                    }

                    // Handle the response
                    rabbitMQTaskExecutor.handleResponse(correlationId, outputs);
                } else {
                    log.error("Neither taskExecutionId nor correlationId is available in result message");
                }
            }
        } catch (Exception e) {
            log.error("Error processing task result message", e);
        }
    }
}
