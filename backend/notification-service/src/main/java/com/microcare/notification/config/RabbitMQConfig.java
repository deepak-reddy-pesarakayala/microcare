package com.microcare.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * RabbitMQ configuration for the Notification Service (consumer side).
 *
 * <p>Declares the shared {@code appointment.exchange} topic exchange plus a
 * dedicated {@code notification.queue} bound with the {@code appointment.confirmed}
 * routing key. Failed messages are retried (max 3 attempts with backoff) and then
 * routed to the shared DLX/DLQ for inspection and replay.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange:appointment.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.dlx:appointment.dlx}")
    private String dlxName;

    @Value("${app.rabbitmq.queue.notification:notification.queue}")
    private String notificationQueue;

    @Value("${app.rabbitmq.dlq.notification:notification.dlq}")
    private String notificationDlq;

    @Value("${app.rabbitmq.routing-key.appointment-confirmed:appointment.confirmed}")
    private String appointmentConfirmedRoutingKey;

    @Value("${app.rabbitmq.max-retries:3}")
    private int maxRetries;

    @Bean
    public TopicExchange appointmentExchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public DirectExchange appointmentDlx() {
        return new DirectExchange(dlxName);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(notificationQueue)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", notificationDlq)
                .build();
    }

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable(notificationDlq).build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(appointmentExchange())
                .with(appointmentConfirmedRoutingKey);
    }

    @Bean
    public Binding notificationDlqBinding() {
        return BindingBuilder.bind(notificationDlq())
                .to(appointmentDlx())
                .with(notificationDlq);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RetryOperationsInterceptor retryInterceptor(RabbitTemplate rabbitTemplate) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxRetries)
                .backOffOptions(1000L, 2.0, 10000L)
                .recoverer(new RepublishMessageRecoverer(rabbitTemplate, dlxName, notificationDlq))
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor retryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(retryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
