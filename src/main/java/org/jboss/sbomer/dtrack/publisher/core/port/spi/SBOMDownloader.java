package org.jboss.sbomer.dtrack.publisher.core.port.spi;

import java.nio.file.Path;

public interface SBOMDownloader {
    /**
     * Downloads the SBOM and saves it to a temporary file.
     *
     * @param path The URL path of the SBOM on the storage service.
     * @return The Path to the local temporary file.
     */
    Path downloadSbom(String path);
}
