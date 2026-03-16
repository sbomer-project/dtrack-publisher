package org.jboss.sbomer.dtrack.publisher.core.service;

import static org.jboss.sbomer.dtrack.publisher.core.ApplicationConstants.PUBLISHED_URL_KEY;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.sbomer.dtrack.publisher.adapter.out.download.exception.SBOMDownloadException;
import org.jboss.sbomer.dtrack.publisher.adapter.out.dtrack.exception.DTrackUploadException;
import org.jboss.sbomer.dtrack.publisher.core.domain.PublishFinishedResult;
import org.jboss.sbomer.dtrack.publisher.core.domain.PublishingTask;
import org.jboss.sbomer.dtrack.publisher.core.port.api.SBOMPublishProcessor;
import org.jboss.sbomer.dtrack.publisher.core.port.spi.DependencyTrackUploader;
import org.jboss.sbomer.dtrack.publisher.core.port.spi.PublishFinishedEmitter;
import org.jboss.sbomer.dtrack.publisher.core.port.spi.SBOMDownloader;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class SBOMPublishingService implements SBOMPublishProcessor {

    @Inject
    SBOMDownloader sbomDownloader;
    @Inject
    DependencyTrackUploader dependencyTrackUploader;
    @Inject
    PublishFinishedEmitter publishFinishedEmitter;

    // Inject the internal storage URL
    @ConfigProperty(name = "manifest.storage.internal.url")
    String internalStorageUrl;

    @Override
    @WithSpan
    public void publishSBOMs(PublishingTask task) {
        Span span = Span.current();
        span.setAttribute("request.id", task.requestId());
        log.info("Starting Dependency-Track publishing for Request ID: {}", task.requestId());
        List<String> publishedUrls = new ArrayList<>();
        List<PublishFinishedResult.FailedPublish> failures = new ArrayList<>();
        Map<String, String> aggregatedMetadata = new HashMap<>();

        for (PublishingTask.GenerationResult generation : task.completedGenerations()) {
            for (String sbomUrl : generation.finalSbomUrls()) {
                Path tempSbomFile = null;
                try {

                    // Extract the path (e.g. "/api/v1/storage/content/...") from the public URL
                    URI originalUri = URI.create(sbomUrl);
                    String urlPath = originalUri.getPath();

                    // Safely combine the internal base URL and the path
                    // We do this to have explicit control over the URL base that hosts the SBOM
                    String baseUrl = internalStorageUrl.endsWith("/") ?
                            internalStorageUrl.substring(0, internalStorageUrl.length() - 1) : internalStorageUrl;
                    String internalDownloadUrl = baseUrl + urlPath;
                    // -----------------------------

                    log.debug("Translating SBOM Storage URL {} to URL: {}", sbomUrl, internalDownloadUrl);

                    // Pass the internal URL to the downloader
                    tempSbomFile = sbomDownloader.downloadSbom(internalDownloadUrl);

                    log.debug("Uploading SBOM to Dependency-Track for target: {}", generation.target().identifier());
                    Map<String, String> uploadResult = dependencyTrackUploader.uploadSbom(
                            generation.target(),
                            tempSbomFile,
                            task.publisher(),
                            generation.handlerOptions()
                    );

                    // We expect a certain key-value pair to be returned representing the published url
                    // to access the SBOM from the published platform
                    // If it exists we put it in publishedUrls
                    if (uploadResult.containsKey(PUBLISHED_URL_KEY)) {
                        publishedUrls.add(uploadResult.get(PUBLISHED_URL_KEY));
                    }
                    aggregatedMetadata.putAll(uploadResult);

                } catch (Exception e) {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    // CATCH AND RECORD, DO NOT THROW
                    // We log and record the ORIGINAL sbomUrl so the orchestrator recognizes it!
                    log.error("Failed to publish SBOM from URL: {}", sbomUrl, e);

                    // Extracting a meaningful reason
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    String errorCode = null;

                    // If it's our specific D-Track exception, pull out the HTTP status
                    if (e instanceof DTrackUploadException dtrackEx) {
                        errorCode = dtrackEx.getErrorCode();
                    } else if (e instanceof SBOMDownloadException downloadEx) {
                        errorCode = downloadEx.getErrorCode();
                    }

                    failures.add(new PublishFinishedResult.FailedPublish(sbomUrl, errorCode, reason));

                } finally {
                    if (tempSbomFile != null) {
                        try {
                            Files.deleteIfExists(tempSbomFile);
                        } catch (Exception e) {
                            log.warn("Failed to delete temporary SBOM file: {}", tempSbomFile, e);
                        }
                    }
                }
            }
        }

        // Construct the final result domain object with both successes and failures
        PublishFinishedResult finalResult = new PublishFinishedResult(
                task.requestId(),
                task.publisher(),
                publishedUrls.isEmpty() ? null : publishedUrls,
                failures.isEmpty() ? null : failures,
                aggregatedMetadata
        );

        // Emit the success/partial-success event
        log.info("Finished publishing for Request ID: {}. Successes: {}, Failures: {}. Emitting event.",
                task.requestId(), publishedUrls.size(), failures.size());
        publishFinishedEmitter.emit(finalResult);
    }
}
