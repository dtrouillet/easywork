package fr.easywork.ingest.consumer;

import com.rabbitmq.client.Channel;
import fr.easywork.document.event.DocumentUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Bridges the {@code easywork.document.uploaded} queue to {@link IngestEventConsumer}.
 * This is the only entry point into ingest processing — doc-service and ingest-worker are
 * separate pods in production (see CLAUDE.md), so an in-process
 * {@code @ApplicationModuleListener} on the consumer side would never fire there. Routing
 * exclusively through AMQP, in local dev too, keeps a single delivery path and avoids
 * double-processing a document that a same-process listener would otherwise also pick up.
 */
@Component
@Profile("ingest")
@RequiredArgsConstructor
class DocumentUploadedRabbitListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadedRabbitListener.class);

    private final IngestEventConsumer consumer;

    @RabbitListener(queues = "${easywork.rabbit.document-uploaded-queue}")
    void handle(DocumentUploadedEvent event, Channel channel,
                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            consumer.onDocumentUploaded(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to process DocumentUploadedEvent {}", event.documentId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
