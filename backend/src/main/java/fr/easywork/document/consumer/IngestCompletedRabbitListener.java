package fr.easywork.document.consumer;

import com.rabbitmq.client.Channel;
import fr.easywork.document.event.IngestCompletedEvent;
import fr.easywork.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Bridges the {@code easywork.ingest.completed} queue to
 * {@link DocumentService#onIngestCompleted}. Deliberately a separate bean rather than an
 * AMQP-plumbing method on {@code DocumentService} itself: {@code onIngestCompleted} carries
 * {@code @Transactional(propagation = REQUIRES_NEW)}, and a same-class self-invocation would
 * silently bypass the transactional proxy. See {@code DocumentUploadedRabbitListener} for why
 * this is AMQP-only rather than an {@code @ApplicationModuleListener}.
 */
@Component
@RequiredArgsConstructor
class IngestCompletedRabbitListener {

    private static final Logger log = LoggerFactory.getLogger(IngestCompletedRabbitListener.class);

    private final DocumentService documentService;

    @RabbitListener(queues = "${easywork.rabbit.ingest-completed-queue}")
    void handle(IngestCompletedEvent event, Channel channel,
                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            documentService.onIngestCompleted(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to process IngestCompletedEvent {}", event.documentId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
