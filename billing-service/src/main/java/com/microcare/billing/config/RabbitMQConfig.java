package com.microcare.billing.config;

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
 * RabbitMQ configuration for the Billing Service (consumer side).
 *
 * This config mirrors the exchanges, queues, and DLQ declared by the
 * appointment-service publisher. Both services declare the same topology
 * to ensure idempotent setup — if the billing-service starts before
 * appointment-service, the infrastructure is still created.
 *
 * Retry Policy:
 * - Max 3 attempts with exponential backoff (1s, 2s, 4s).
 * - After exhausting retries, the RepublishMessageRecoverer sends the
 *   failed message to the DLX/DLQ with error headers attached.
 * - Messages in the DLQ can be inspected and replayed via RabbitMQ
 *   Management UI or a custom admin tool.
 *
 * Idempotent Consumption:
 * - The consumer checks if an invoice already exists for the appointmentId
 *   before creating a new one (see InvoiceEventListener).
 */
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
     * Declares the same TopicExchange as appointment-service.
     * Idempotent: if the exchange already exists, RabbitMQ ignores the declaration.
     */
    @Bean
    public TopicExchange appointmentExchange() {
        return new TopicExchange(exchangeName);
    }

    /**
     * Declares the same DLX as appointment-service.
     */
    @Bean
    public DirectExchange appointmentDlx() {
        return new DirectExchange(dlxName);
    }

    // ---------------------------------------------------------------
    // Queues
    // ---------------------------------------------------------------

    /**
     * Declares the main billing invoice queue with DLX config.
     */
    @Bean
    public Queue billingInvoiceQueue() {
        return QueueBuilder.durable(billingInvoiceQueue)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", billingInvoiceDlq)
                .build();
    }

    /**
     * Declares the Dead-Letter Queue.
     */
    @Bean
    public Queue billingInvoiceDlq() {
        return QueueBuilder.durable(billingInvoiceDlq).build();
    }

    // ---------------------------------------------------------------
    // Bindings
    // ---------------------------------------------------------------

    @Bean
    public Binding billingInvoiceBinding() {
        return BindingBuilder.bind(billingInvoiceQueue())
                .to(appointmentExchange())
                .with(appointmentConfirmedRoutingKey);
    }

    @Bean
    public Binding billingInvoiceDlqBinding() {
        return BindingBuilder.bind(billingInvoiceDlq())
                .to(appointmentDlx())
                .with(billingInvoiceDlq);
    }

    // ---------------------------------------------------------------
    // Message Converter
    // ---------------------------------------------------------------

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---------------------------------------------------------------
    // Retry Interceptor (Consumer-side)
    // ---------------------------------------------------------------

    /**
     * Retry interceptor for the RabbitListener that consumes billing events.
     * Uses RepublishMessageRecoverer: after maxRetries (3) attempts with
     * exponential backoff (1s initial, 2x multiplier, max 10s), the failed
     * message is sent to the DLX with error stack trace in headers.
     *
     * This prevents poison messages from blocking the main queue while
     * preserving the message for later debugging and reprocessing.
     */
    @Bean
    public RetryOperationsInterceptor retryInterceptor(RabbitTemplate rabbitTemplate) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxRetries)
                .backOffOptions(1000L, 2.0, 10000L)
                .recoverer(new RepublishMessageRecoverer(rabbitTemplate, dlxName, billingInvoiceDlq))
                .build();
    }

    /**
     * Customizes the RabbitListener container factory with JSON conversion
     * and the retry interceptor.
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
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
