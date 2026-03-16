package org.jboss.sbomer.dtrack.publisher.adapter.in;

import static org.jboss.sbomer.dtrack.publisher.core.ApplicationConstants.COMPONENT_NAME;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.sbomer.dtrack.publisher.core.domain.PublishingTask;
import org.jboss.sbomer.dtrack.publisher.core.port.api.SBOMPublishProcessor;
import org.jboss.sbomer.dtrack.publisher.core.port.spi.FailureNotifier;
import org.jboss.sbomer.dtrack.publisher.core.utility.FailureUtility;
import org.jboss.sbomer.events.orchestration.RequestsFinished;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class KafkaRequestsFinishedListener {

    @Inject
    SBOMPublishProcessor processor;
    @Inject
    FailureNotifier failureNotifier;

    @Incoming("requests-finished")
    public void onMessageReceived(RequestsFinished requestsFinished) {

        try {
            log.info("{} received task to publish SBOMs for request ID: {}", COMPONENT_NAME,
                    requestsFinished.getData().getRequestId());
            // Pass to the core
            if (isIntendedForPublisher(requestsFinished)) {
                // Map to Domain Object
                PublishingTask domainTask = PublishingTaskMapper.toPublishingTask(requestsFinished);
                processor.publishSBOMs(domainTask);
            }
        } catch (Exception e) {
            // Catch exceptions so we don't crash the consumer loop.
            log.error("Skipping malformed or incompatible event: {}", requestsFinished, e);
            Span span = Span.current();
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            if (requestsFinished != null) {
                failureNotifier.notify(FailureUtility.buildFailureSpecFromException(e), requestsFinished.getContext().getCorrelationId(), requestsFinished);
            } else {
                failureNotifier.notify(FailureUtility.buildFailureSpecFromException(e), null, null);
            }

        }
    }

    // Just checks the name for now and not the version of the publisher
    private boolean isIntendedForPublisher(RequestsFinished requestsFinished) {
        if (requestsFinished == null ||
                requestsFinished.getData() == null ||
                requestsFinished.getData().getPublishers() == null) {
            return false;
        }

        // Check if any of the PublisherSpec objects in the array have the matching name
        return requestsFinished.getData().getPublishers().stream()
                .anyMatch(publisherSpec ->
                        COMPONENT_NAME.equalsIgnoreCase(String.valueOf(publisherSpec.getName()))
                );
    }

}
