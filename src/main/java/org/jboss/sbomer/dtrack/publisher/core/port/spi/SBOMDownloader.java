package org.jboss.sbomer.dtrack.publisher.core.port.spi;

import java.nio.file.Path;

public interface SBOMDownloader {
    /**
     * Downloads the SBOM and saves it to a temporary file.
     *
     * @param url The location of the enhanced SBOM.
     * @return The Path to the local temporary file.
     */
    Path downloadSbom(String url);
}
