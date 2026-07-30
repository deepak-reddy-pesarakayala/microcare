package com.microcare.appointment.config;

import lombok.extern.slf4j.Slf4j;
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
 * RabbitMQ configuration for the Appointment Service.
 *
 * This config sets up:
 * 1. A TopicExchange "appointment.exchange" for publishing appointment events.
 * 2. A Dead-Letter Exchange (DLX) "appointment.dlx" with a Dead-Letter Queue (DLQ).
 * 3. The main queue "billing.invoice.queue" bound to the exchange with routing key
 *    "appointment.confirmed", and configured to route failed messages to the DLX/DLQ.
 * 4. A retry interceptor that uses RepublishMessageRecoverer — after max attempts
 *    (3), the message is republished to the DLX/DLQ with error headers attached,
 *    allowing manual inspection and replay.
 *
 * The TopicExchange allows other services in the future to subscribe to different
 * appointment events (e.g., "appointment.cancelled", "appointment.rescheduled")
 * with their own routing keys.
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange:appointment.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.dlx:appointment.dlx}")
    private String dlxName;

    @Value("${app.rabbitmq.queue.billing-invoice:billing.invoice.queue}")
    private String billingInvoiceQueue;

    @Value("${app.rabbitmq.dlq.billing-invoice:billing.invoice.dlq}")
    private String billingInvoiceDlq;

    @Value("${app.rabbitmq.routing-key.appointment-confirmed:appointment.confirmed}")
    private String appointmentConfirmedRoutingKey;

    @Value("${app.rabbitmq.max-retries:3}")
    private int maxRetries;

    // ---------------------------------------------------------------
    // Exchanges
    // ---------------------------------------------------------------

    /**
     * Topic exchange for appointment-related events.
     * Using TopicExchange allows flexible routing for future event types.
     */
    @Bean
    public TopicExchange appointmentExchange() {
        return new TopicExchange(exchangeName);
    }

    /**
     * Dead-Letter Exchange (DLX) — a DirectExchange that holds messages
     * that failed after all retry attempts.
     */
    @Bean
    public DirectExchange appointmentDlx() {
        return new DirectExchange(dlxName);
    }

    // ---------------------------------------------------------------
    // Queues
    // ---------------------------------------------------------------

    /**
     * Main queue for billing invoice events.
     * Configured with:
     *   - x-dead-letter-exchange: appointment.dlx (failed messages go here)
     *   - x-dead-letter-routing-key: billing.invoice.dlq (routed to DLQ)
     */
    @Bean
    public Queue billingInvoiceQueue() {
        return QueueBuilder.durable(billingInvoiceQueue)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", billingInvoiceDlq)
                .build();
    }

    /**
     * Dead-Letter Queue (DLQ) — stores messages that exhausted retries.
     * These can be inspected and replayed via RabbitMQ Management UI
     * or a manual reprocess script.
     */
    @Bean
    public Queue billingInvoiceDlq() {
        return QueueBuilder.durable(billingInvoiceDlq).build();
    }

    // ---------------------------------------------------------------
    // Bindings
    // ---------------------------------------------------------------

    /**
     * Binds the main queue to the topic exchange with the "appointment.confirmed" key.
     */
    @Bean
    public Binding billingInvoiceBinding() {
        return BindingBuilder.bind(billingInvoiceQueue())
                .to(appointmentExchange())
                .with(appointmentConfirmedRoutingKey);
    }

    /**
     * Binds the DLQ to the DLX with its own routing key.
     */
    @Bean
    public Binding billingInvoiceDlqBinding() {
        return BindingBuilder.bind(billingInvoiceDlq())
                .to(appointmentDlx())
                .with(billingInvoiceDlq);
    }

    // ---------------------------------------------------------------
    // Message Converter
    // ---------------------------------------------------------------

    /**
     * JSON message converter for RabbitTemplate — serializes/deserializes
     * messages as JSON automatically.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---------------------------------------------------------------
    // Retry Interceptor (Publisher-side — for the outbox publisher)
    // ---------------------------------------------------------------

    /**
     * Retry interceptor for the RabbitTemplate used by the outbox publisher.
     * Uses RepublishMessageRecoverer: after maxRetries attempts, the failed
     * message is republished to the DLX with error stack trace in headers.
     *
     * This is an alternative to simple retry — the outbox poller has its own
     * retry via the database, but this interceptor adds an extra layer of
     * resilience for transient broker failures.
     */
    @Bean
    public RetryOperationsInterceptor retryInterceptor(RabbitTemplate rabbitTemplate) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxRetries)
                .backOffOptions(1000L, 2.0, 10000L) // initial 1s, 2x multiplier, max 10s
                .recoverer(new RepublishMessageRecoverer(rabbitTemplate, dlxName, billingInvoiceDlq))
                .build();
    }

    /**
     * Customizes the RabbitListener container factory to use JSON conversion
     * and the retry interceptor for message consumption.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor retryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(retryInterceptor);
        factory.setDefaultRequeueRejected(false); // don't requeue after DLX routing
        return factory;
    }
}
