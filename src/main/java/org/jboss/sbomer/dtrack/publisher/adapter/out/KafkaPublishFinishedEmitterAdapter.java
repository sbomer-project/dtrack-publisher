package org.jboss.sbomer.dtrack.publisher.adapter.out;

import static org.jboss.sbomer.dtrack.publisher.core.ApplicationConstants.COMPONENT_NAME;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.sbomer.dtrack.publisher.core.domain.PublishFinishedResult;
import org.jboss.sbomer.dtrack.publisher.core.port.spi.PublishFinishedEmitter;
import org.jboss.sbomer.events.common.ContextSpec;
import org.jboss.sbomer.events.common.PublisherSpec; // Make sure this is imported!
import org.jboss.sbomer.events.publisher.FailedPublish;
import org.jboss.sbomer.events.publisher.PublishFinished;
import org.jboss.sbomer.events.publisher.PublishFinishedData;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class KafkaPublishFinishedEmitterAdapter implements PublishFinishedEmitter {

    @Inject
    @Channel("publish-finished")
    Emitter<PublishFinished> kafkaEmitter;

    @Override
    public void emit(PublishFinishedResult result) {
        log.debug("Mapping domain result to Avro event for Request ID: {}", result.requestId());

        // Map the Failures list (if any exist)
        List<FailedPublish> avroFailures = null;

        if (result.failures() != null && !result.failures().isEmpty()) {
            avroFailures = result.failures().stream()
                    .map(f -> FailedPublish.newBuilder()
                            .setSbomUrl(f.sbomUrl())
                            .setErrorCode(f.errorCode())
                            .setReason(f.reason())
                            .build())
                    .collect(Collectors.toList());
        }

        // Map the Publisher object from Domain -> Avro
        PublisherSpec avroPublisher = PublisherSpec.newBuilder()
                .setName(result.publisher().name())
                .setVersion(result.publisher().version())
                .setOptions(result.publisher().options())
                .build();

        // Map the main Data block
        PublishFinishedData data = PublishFinishedData.newBuilder()
                .setRequestId(result.requestId())
                .setPublisher(avroPublisher)
                .setPublishedSbomUrls(result.publishedSbomUrls())
                .setFailures(avroFailures)
                .setResult(result.extraMetadata())
                .build();

        // Create a standard context for the event
        ContextSpec context = ContextSpec.newBuilder()
                .setEventId(java.util.UUID.randomUUID().toString())
                .setType("PublishFinished")
                .setTimestamp(Instant.now())
                .setSource(COMPONENT_NAME)
                .build();

        // 5. Assemble the final Event
        PublishFinished event = PublishFinished.newBuilder()
                .setContext(context)
                .setData(data)
                .build();

        // Send it to Kafka
        log.info("Emitting PublishFinished event to Kafka for Request ID: {}", result.requestId());
        kafkaEmitter.send(event).whenComplete((success, failure) -> {
            if (failure != null) {
                log.error("Failed to send PublishFinished event to Kafka for Request ID: {}", result.requestId(), failure);
            } else {
                log.debug("Successfully acknowledged by Kafka broker.");
            }
        });
    }
}
