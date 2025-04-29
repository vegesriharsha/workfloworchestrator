package com.example.workfloworchestrator.messaging;

import com.example.workfloworchestrator.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Component for sending messages to RabbitMQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQSender {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Send a task message to the default task queue
     */
    public void sendTaskMessage(TaskMessage message) {
        log.info("Sending task message to queue: {}, taskType: {}, correlationId: {}",
                RabbitMQConfig.TASK_QUEUE, message.getTaskType(), message.getCorrelationId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TASK_EXCHANGE,
                RabbitMQConfig.TASK_ROUTING_KEY,
                message);
    }

    /**
     * Send a task result message to the default result queue
     */
    public void sendTaskResultMessage(TaskMessage resultMessage) {
        log.info("Sending task result message to queue: {}, taskType: {}, correlationId: {}, success: {}",
                RabbitMQConfig.WORKFLOW_RESULT_QUEUE,
                resultMessage.getTaskType(),
                resultMessage.getCorrelationId(),
                resultMessage.isSuccess());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.WORKFLOW_RESULT_EXCHANGE,
                RabbitMQConfig.WORKFLOW_RESULT_ROUTING_KEY,
                resultMessage);
    }

    /**
     * Send a message to a specific exchange and routing key
     */
    public void sendMessage(String exchange, String routingKey, Object message) {
        log.info("Sending message to exchange: {}, routingKey: {}", exchange, routingKey);

        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }
}
