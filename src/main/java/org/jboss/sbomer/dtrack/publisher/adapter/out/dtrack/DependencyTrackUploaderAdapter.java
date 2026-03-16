package org.jboss.sbomer.dtrack.publisher.adapter.out.dtrack;

import static org.jboss.sbomer.dtrack.publisher.core.ApplicationConstants.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.sbomer.dtrack.publisher.adapter.out.dtrack.exception.DTrackUploadException;
import org.jboss.sbomer.dtrack.publisher.core.domain.PublishingTask;
import org.jboss.sbomer.dtrack.publisher.core.port.spi.DependencyTrackUploader;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class DependencyTrackUploaderAdapter implements DependencyTrackUploader {

    private final DependencyTrackApiClient apiClient;
    private final String dtrackApiKey;
    private final String dtrackApiUrl;

    public DependencyTrackUploaderAdapter(
            @RestClient DependencyTrackApiClient apiClient,
            @ConfigProperty(name = "dtrack.api.key") String dtrackApiKey,
            @ConfigProperty(name = "dtrack-api/mp-rest/url") String dtrackApiUrl) {
        this.apiClient = apiClient;
        this.dtrackApiKey = dtrackApiKey;
        this.dtrackApiUrl = dtrackApiUrl;
    }

    @Override
    @WithSpan
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS,
            abortOn = { IllegalArgumentException.class })
    public Map<String, String> uploadSbom(
            PublishingTask.Target target,
            Path sbomFile,
            PublishingTask.PublisherConfig publisherConfig,
            Map<String, String> handlerOptions) {

        Map<String, String> pubOptions = publisherConfig.options() != null ?
                publisherConfig.options() : new HashMap<>();

        Map<String, String> genOptions = handlerOptions != null ?
                handlerOptions : new HashMap<>();

        String projectName;
        String projectVersion;

        if (genOptions.containsKey(HANDLER_PROJECT_NAME_KEY) && genOptions.containsKey(HANDLER_PROJECT_VERSION_KEY)) {
            projectName = genOptions.get(HANDLER_PROJECT_NAME_KEY);
            projectVersion = genOptions.get(HANDLER_PROJECT_VERSION_KEY);
            log.debug("Resolved D-Track identity from Generation Handler Options");
        } else if (pubOptions.containsKey(PUBLISHER_PROJECT_NAME_KEY) && pubOptions.containsKey(PUBLISHER_PROJECT_VERSION_KEY)) {
            projectName = pubOptions.get(PUBLISHER_PROJECT_NAME_KEY);
            projectVersion = pubOptions.get(PUBLISHER_PROJECT_VERSION_KEY);
            log.debug("Resolved D-Track identity from Batch Publisher Options");
        } else {
            log.debug("Identity not provided in options. Falling back to physical SBOM extraction...");
            SBOMIdentityExtractor.ProjectIdentity identity = SBOMIdentityExtractor.extract(sbomFile);

            if (identity.name() != null && identity.version() != null) {
                projectName = identity.name();
                projectVersion = identity.version();
                log.debug("Resolved D-Track identity from physical SBOM file metadata");
            } else {
                projectName = target.identifier();
                projectVersion = target.type();
                log.debug("Resolved D-Track identity from Target fallback");
            }
        }

        Span span = Span.current();
        span.setAttribute("dtrack.project.name", projectName);
        span.setAttribute("dtrack.project.version", projectVersion);
        span.setAttribute("target.identifier", target.identifier());

        log.info("Uploading SBOM to D-Track - Project: '{}', Version: '{}'", projectName, projectVersion);

        try {
            Map<String, String> response = apiClient.uploadSbom(
                    dtrackApiKey,
                    projectName,
                    projectVersion,
                    true,
                    sbomFile.toFile()
            );

            Map<String, String> metadata = new HashMap<>();
            metadata.put("dtrack.projectName", projectName);
            metadata.put("dtrack.projectVersion", projectVersion);

            String lookupUrl = String.format("%s/api/v1/project/lookup?name=%s&version=%s",
                    dtrackApiUrl,
                    URLEncoder.encode(projectName, StandardCharsets.UTF_8),
                    URLEncoder.encode(projectVersion, StandardCharsets.UTF_8));
            metadata.put(PUBLISHED_URL_KEY, lookupUrl);

            if (response != null && response.containsKey("token")) {
                String token = response.get("token");
                metadata.put("dtrack.processingToken", token);

                String tokenUrl = String.format("%s/api/v1/event/token/%s", dtrackApiUrl, token);
                metadata.put("dtrack.tokenUrl", tokenUrl);

                log.info("Successfully submitted SBOM to D-Track. Tracking token: {}", token);
            }

            return metadata;

        } catch (WebApplicationException e) {
            // This catches clean HTTP errors (e.g., server stayed connected and replied with a 400 or 403)
            int status = e.getResponse().getStatus();
            String errorBody = "No body provided";
            try {
                errorBody = e.getResponse().readEntity(String.class);
            } catch (Exception ignored) {
                // Ignore if body is unreadable
            }
            log.error("Dependency-Track API cleanly rejected the upload. Status: {}, Body: {}", status, errorBody);
            throw new DTrackUploadException(String.valueOf(status), "D-Track API rejected upload: " + errorBody, e);

        } catch (Exception e) {
            // This catches Broken Pipes, Vert.x StreamResetExceptions, and connection timeouts
            log.error("Network or IO error while streaming SBOM to Dependency-Track. " +
                    "A 'Stream reset' or 'Broken pipe' during an upload usually means the server or reverse proxy " +
                    "(like Nginx) forcefully closed the connection. Check your API Key permissions (needs BOM_UPLOAD " +
                    "and PROJECT_CREATION_UPLOAD) and check your proxy's max body size limits.", e);
            throw new DTrackUploadException("IO_ERROR", "Upload stream interrupted by server: " + e.getMessage(), e);
        }
    }
}
