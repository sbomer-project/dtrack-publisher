package org.jboss.sbomer.dtrack.publisher.core.port.spi;

import java.nio.file.Path;
import java.util.Map;

import org.jboss.sbomer.dtrack.publisher.core.domain.PublishingTask;

public interface DependencyTrackUploader {
    /**
     * Streams the local SBOM file to OWASP Dependency-Track.
     *
     * @param target The build or image target.
     * @param sbomFile The path to the local SBOM file.
     * @param publisherConfig The config that might contain specific D-Track options.
     * @return A map of resulting metadata.
     */
    Map<String, String> uploadSbom(
            PublishingTask.Target target,
            Path sbomFile,
            PublishingTask.PublisherConfig publisherConfig
    );
}
