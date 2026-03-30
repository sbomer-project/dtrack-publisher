package org.jboss.sbomer.dtrack.publisher.adapter.out.download;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.sbomer.dtrack.publisher.adapter.out.download.exception.SBOMDownloadException;
import org.jboss.sbomer.dtrack.publisher.core.port.spi.SBOMDownloader;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class HttpSBOMDownloaderAdapter implements SBOMDownloader {

    private final ManifestStorageApiClient storageClient;

    public HttpSBOMDownloaderAdapter(@RestClient ManifestStorageApiClient storageClient) {
        this.storageClient = storageClient;
    }

    @Override
    @WithSpan
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    public Path downloadSbom(@SpanAttribute("sbom.path") String path) {
        log.debug("Downloading SBOM from path: {}", path);
        Path tempFile = null;

        try {
            // Create a temporary file on the OS
            tempFile = Files.createTempFile("sbomer-download-", ".json");

            String cleanPath = path.startsWith("/") ? path.substring(1) : path;

            try (InputStream is = storageClient.download(cleanPath)) {
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            log.debug("Successfully downloaded SBOM to temp file: {}", tempFile.toAbsolutePath());
            return tempFile;

        } catch (Exception e) {
            // Clean up temp file on error
            cleanupTempFile(tempFile);

            // Record error on span
            Span span = Span.current();
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());

            // Extract HTTP status from REST client errors
            String errorCode = null;
            if (e instanceof WebApplicationException wae) {
                errorCode = String.valueOf(wae.getResponse().getStatus());
                log.error("Storage Service rejected download request. Status: {}, Path: {}", errorCode, path);
            }

            // Re-throw so the core domain registers this as a failure for this specific SBOM
            if (e instanceof SBOMDownloadException) {
                throw (SBOMDownloadException) e;
            }
            throw new SBOMDownloadException(errorCode, "Error downloading SBOM: " + e.getMessage(), e);
        }
    }

    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception cleanupEx) {
                log.warn("Failed to clean up temp file after download failure: {}", tempFile, cleanupEx);
            }
        }
    }
}
