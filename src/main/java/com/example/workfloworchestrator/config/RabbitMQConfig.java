package com.example.workfloworchestrator.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String TASK_QUEUE = "workflow.task.queue";
    public static final String TASK_EXCHANGE = "workflow.task.exchange";
    public static final String TASK_ROUTING_KEY = "workflow.task.routing.key";

    public static final String WORKFLOW_RESULT_QUEUE = "workflow.result.queue";
    public static final String WORKFLOW_RESULT_EXCHANGE = "workflow.result.exchange";
    public static final String WORKFLOW_RESULT_ROUTING_KEY = "workflow.result.routing.key";

    @Bean
    public Queue taskQueue() {
        return new Queue(TASK_QUEUE, true);
    }

    @Bean
    public Queue resultQueue() {
        return new Queue(WORKFLOW_RESULT_QUEUE, true);
    }

    @Bean
    public TopicExchange taskExchange() {
        return new TopicExchange(TASK_EXCHANGE);
    }

    @Bean
    public TopicExchange resultExchange() {
        return new TopicExchange(WORKFLOW_RESULT_EXCHANGE);
    }

    @Bean
    public Binding taskBinding(Queue taskQueue, TopicExchange taskExchange) {
        return BindingBuilder.bind(taskQueue).to(taskExchange).with(TASK_ROUTING_KEY);
    }

    @Bean
    public Binding resultBinding(Queue resultQueue, TopicExchange resultExchange) {
        return BindingBuilder.bind(resultQueue).to(resultExchange).with(WORKFLOW_RESULT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
