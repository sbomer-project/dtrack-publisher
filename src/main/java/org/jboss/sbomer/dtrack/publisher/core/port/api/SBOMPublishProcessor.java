package org.jboss.sbomer.dtrack.publisher.core.port.api;

import org.jboss.sbomer.dtrack.publisher.core.domain.PublishingTask;

/**
 * Primary Inbound Port (API) for the SBOM Publishing Domain.
 * <p>
 * This service is responsible for orchestrating the end-to-end publishing
 * workflow for a batch of SBOMs. It serves as the bridge between inbound
 * triggers (e.g., Kafka events) and outbound infrastructure (e.g.,
 * Dependency-Track, Storage Services).
 */
public interface SBOMPublishProcessor {

    /**
     * Processes a {@link PublishingTask} by downloading, identifying,
     * and uploading all associated SBOMs to the target destination.
     * <p>
     * <b>Orchestration Logic:</b>
     * <ul>
     * <li>Iterates through all completed generations in the task.</li>
     * <li>Downloads each SBOM from its storage URL.</li>
     * <li>Determines the project identity (Name/Version) using a hierarchy of
     * metadata from the task config and the SBOM file itself.</li>
     * <li>Uploads the SBOM to OWASP Dependency-Track.</li>
     * <li>Aggregates results and emits a final status event via the outbound ports.</li>
     * </ul>
     * <p>
     * This method is designed to be <b>fault-tolerant</b>: individual SBOM
     * failures will be recorded in the final output rather than causing
     * the entire batch to fail.
     *
     * @param task The domain model containing all necessary metadata and
     * SBOM locations for a specific publishing request.
     */
    void publishSBOMs(PublishingTask task);

}
