package org.jboss.sbomer.dtrack.publisher.core.domain;

import java.util.List;
import java.util.Map;

public record PublishFinishedResult(
        String requestId,
        PublishingTask.PublisherConfig publisher,
        List<String> publishedSbomUrls,
        List<FailedPublish> failures,
        Map<String, String> extraMetadata
) {
    // Nested record to hold the failure details
    public record FailedPublish(
            String sbomUrl,
            String errorCode,
            String reason
    ) {}
}