package org.jboss.sbomer.dtrack.publisher.adapter.in;

import static org.jboss.sbomer.dtrack.publisher.core.ApplicationConstants.COMPONENT_NAME;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jboss.sbomer.dtrack.publisher.core.domain.PublishingTask;
import org.jboss.sbomer.events.common.PublisherSpec;
import org.jboss.sbomer.events.orchestration.CompletedGeneration;
import org.jboss.sbomer.events.orchestration.RequestsFinished;

public class PublishingTaskMapper {

    private PublishingTaskMapper() {}

    public static PublishingTask toPublishingTask(RequestsFinished requestsFinished) {
        if (requestsFinished == null || requestsFinished.getData() == null) {
            throw new IllegalArgumentException("RequestsFinished event or data cannot be null");
        }

        var data = requestsFinished.getData();
        String requestId = String.valueOf(data.getRequestId());

        // Find and map the specific PublisherConfig for this component
        PublishingTask.PublisherConfig publisherConfig = data.getPublishers().stream()
                .filter(p -> COMPONENT_NAME.equalsIgnoreCase(String.valueOf(p.getName())))
                .findFirst()
                .map(PublishingTaskMapper::toPublisherConfig)
                .orElseThrow(() -> new IllegalStateException("Publisher config for " + COMPONENT_NAME + " not found in event"));

        // Map the completed generations
        List<PublishingTask.GenerationResult> generations = Optional.ofNullable(data.getCompletedGenerations())
                .orElse(Collections.emptyList())
                .stream()
                .map(PublishingTaskMapper::toGenerationResult)
                .toList();

        return new PublishingTask(requestId, publisherConfig, generations);
    }

    private static PublishingTask.PublisherConfig toPublisherConfig(PublisherSpec spec) {
        String name = String.valueOf(spec.getName());
        // Avro strings might be null, so handle version gracefully if needed
        String version = spec.getVersion() != null ? String.valueOf(spec.getVersion()) : "unknown";

        // Map the options, converting CharSequence keys/values to Strings
        Map<String, String> options = Optional.ofNullable(spec.getOptions())
                .orElse(Collections.emptyMap())
                .entrySet().stream()
                .collect(Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(e.getValue())
                ));

        return new PublishingTask.PublisherConfig(name, version, options);
    }

    private static PublishingTask.GenerationResult toGenerationResult(CompletedGeneration gen) {
        var req = gen.getGenerationRequest();
        var avroTarget = req.getTarget();

        PublishingTask.Target target = new PublishingTask.Target(
                String.valueOf(avroTarget.getType()),
                String.valueOf(avroTarget.getIdentifier())
        );

        List<String> urls = Optional.ofNullable(gen.getFinalSbomUrls())
                .orElse(Collections.emptyList())
                .stream()
                .map(String::valueOf)
                .toList();

        return new PublishingTask.GenerationResult(
                String.valueOf(req.getGenerationId()),
                target,
                urls
        );
    }
}
