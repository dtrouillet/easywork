package fr.easywork.document.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Declares the RabbitMQ topology backing the two cross-module domain events
 * externalized via Spring Modulith ({@code @Externalized} on
 * {@link fr.easywork.document.event.DocumentUploadedEvent} and
 * {@link fr.easywork.document.event.IngestCompletedEvent}). Always active (no
 * {@code @Profile}) so every pod — including ones that only publish, like a
 * doc-service pod that never runs the {@code ingest} profile — declares the
 * same durable topology at startup rather than relying on whichever pod
 * happens to own the consumer to create it first.
 */
@Configuration
@EnableConfigurationProperties(RabbitTopologyProperties.class)
class RabbitTopologyConfig {

    @Bean
    FanoutExchange documentUploadedExchange(RabbitTopologyProperties props) {
        return new FanoutExchange(props.documentUploadedExchange(), true, false);
    }

    @Bean
    Queue documentUploadedQueue(RabbitTopologyProperties props) {
        return new Queue(props.documentUploadedQueue(), true);
    }

    @Bean
    Binding documentUploadedBinding(Queue documentUploadedQueue, FanoutExchange documentUploadedExchange) {
        return BindingBuilder.bind(documentUploadedQueue).to(documentUploadedExchange);
    }

    @Bean
    FanoutExchange ingestCompletedExchange(RabbitTopologyProperties props) {
        return new FanoutExchange(props.ingestCompletedExchange(), true, false);
    }

    @Bean
    Queue ingestCompletedQueue(RabbitTopologyProperties props) {
        return new Queue(props.ingestCompletedQueue(), true);
    }

    @Bean
    Binding ingestCompletedBinding(Queue ingestCompletedQueue, FanoutExchange ingestCompletedExchange) {
        return BindingBuilder.bind(ingestCompletedQueue).to(ingestCompletedExchange);
    }

    // Modulith's event externalization only customizes the autoconfigured RabbitTemplate
    // directly (see RabbitJacksonConfiguration) — it doesn't register a standalone
    // MessageConverter bean. Without one here, @RabbitListener's container factory falls
    // back to SimpleMessageConverter and fails to deserialize the JSON payloads Modulith
    // publishes.
    @Bean
    MessageConverter rabbitMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
