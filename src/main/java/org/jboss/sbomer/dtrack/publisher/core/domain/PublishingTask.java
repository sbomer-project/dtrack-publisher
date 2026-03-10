package org.jboss.sbomer.dtrack.publisher.core.domain;

import java.util.List;
import java.util.Map;

public record PublishingTask(
        String requestId,
        PublisherConfig publisher, // Added this!
        List<GenerationResult> completedGenerations
) {
    // Pure domain representation of the PublisherSpec
    public record PublisherConfig(
            String name,
            String version,
            Map<String, String> options
    ) {}

    public record GenerationResult(
            String generationId,
            Target target,
            List<String> finalSbomUrls,
            Map<String, String> handlerOptions // Add this!
    ) {}

    public record Target(
            String type,
            String identifier
    ) {}
}