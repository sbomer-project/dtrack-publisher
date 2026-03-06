package org.jboss.sbomer.dtrack.publisher.adapter.out.download;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.sbomer.dtrack.publisher.adapter.out.download.exception.SBOMDownloadException;
import org.jboss.sbomer.dtrack.publisher.core.port.spi.SBOMDownloader;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class HttpSBOMDownloaderAdapter implements SBOMDownloader {

    private final HttpClient httpClient;

    public HttpSBOMDownloaderAdapter() {
        // It is best practice to reuse a single HttpClient instance across the application
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    public Path downloadSbom(String url) {
        log.debug("Downloading SBOM from URL: {}", url);
        Path tempFile = null;
        
        try {
            // 1. Create a temporary file on the OS
            tempFile = Files.createTempFile("sbomer-download-", ".json");

            // 2. Build the HTTP GET Request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMinutes(2)) // Give it plenty of time for massive 100MB+ files
                    .build();

            // 3. Execute the request and stream straight to the file
            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));

            // 4. Check for success
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Successfully downloaded SBOM to temp file: {}", tempFile.toAbsolutePath());
                return response.body();
            } else {
                // If the storage service returns a 404 or 500, we need to throw an error
                log.error("Failed to download SBOM. HTTP Status: {}, URL: {}", response.statusCode(), url);
                throw new SBOMDownloadException(String.valueOf(response.statusCode()), "Storage Service returned HTTP " + response.statusCode());
            }
            
        } catch (Exception e) {
            // If anything goes wrong (network drop, 404, etc.), we MUST clean up the empty temp file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception cleanupEx) {
                    log.warn("Failed to clean up temp file after download failure: {}", tempFile, cleanupEx);
                }
            }
            
            // Re-throw so the core domain registers this as a failure for this specific SBOM
            if (e instanceof SBOMDownloadException) {
                throw (SBOMDownloadException) e;
            }
            throw new SBOMDownloadException(null, "Network error while downloading SBOM: " + e.getMessage(), e);
        }
    }
}
