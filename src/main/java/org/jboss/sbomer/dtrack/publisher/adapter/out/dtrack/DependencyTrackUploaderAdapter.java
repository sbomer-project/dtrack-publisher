package org.jboss.sbomer.dtrack.publisher.adapter.out.dtrack;

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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;

import static org.jboss.sbomer.dtrack.publisher.core.ApplicationConstants.*;

@ApplicationScoped
@Slf4j
public class DependencyTrackUploaderAdapter implements DependencyTrackUploader {

    private final DependencyTrackApiClient apiClient;
    private final String dtrackApiKey;
    private final String dtrackApiUrl;

    public DependencyTrackUploaderAdapter(
            @RestClient DependencyTrackApiClient apiClient,
            @ConfigProperty(name = "dtrack.api.key") String dtrackApiKey,
            // Injecting the base URL you configured in application.properties
            @ConfigProperty(name = "dtrack-api/mp-rest/url") String dtrackApiUrl) {
        this.apiClient = apiClient;
        this.dtrackApiKey = dtrackApiKey;
        this.dtrackApiUrl = dtrackApiUrl;
    }

    @Override
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS,
            abortOn = { IllegalArgumentException.class })
    public Map<String, String> uploadSbom(
            PublishingTask.Target target,
            Path sbomFile,
            PublishingTask.PublisherConfig publisherConfig,
            Map<String, String> handlerOptions) {

        // Safely get the global publisher options map
        Map<String, String> pubOptions = publisherConfig.options() != null ?
                publisherConfig.options() : new HashMap<>();

        // Ensure handlerOptions isn't null
        Map<String, String> genOptions = handlerOptions != null ?
                handlerOptions : new HashMap<>();

        // --- DYNAMIC RESOLUTION (ATOMIC PAIRS) ---

        String projectName;
        String projectVersion;

        // 1. Try Generation-level Handler Options (Must have both)
        if (genOptions.containsKey(HANDLER_PROJECT_NAME_KEY) && genOptions.containsKey(HANDLER_PROJECT_VERSION_KEY)) {
            projectName = genOptions.get(HANDLER_PROJECT_NAME_KEY);
            projectVersion = genOptions.get(HANDLER_PROJECT_VERSION_KEY);
            log.debug("Resolved D-Track identity from Generation Handler Options");
        }
        // 2. Fallback to Batch-level Publisher Options (Must have both)
        else if (pubOptions.containsKey(PUBLISHER_PROJECT_NAME_KEY) && pubOptions.containsKey(PUBLISHER_PROJECT_VERSION_KEY)) {
            projectName = pubOptions.get(PUBLISHER_PROJECT_NAME_KEY);
            projectVersion = pubOptions.get(PUBLISHER_PROJECT_VERSION_KEY);
            log.debug("Resolved D-Track identity from Batch Publisher Options");
        }
        // 3. Lazy-load fallback: Only parse the file if we absolutely have to!
        else {
            log.debug("Identity not provided in options. Falling back to physical SBOM extraction... (name and version of SBOM)");
            SBOMIdentityExtractor.ProjectIdentity identity = SBOMIdentityExtractor.extract(sbomFile);

            if (identity.name() != null && identity.version() != null) {
                projectName = identity.name();
                projectVersion = identity.version();
                log.debug("Resolved D-Track identity from physical SBOM file metadata");
            } else {
                // Absolute Fallback
                projectName = target.identifier();
                projectVersion = target.type();
                log.debug("Resolved D-Track identity from Target fallback");
            }
        }

        log.debug("Uploading to D-Track - Project: {}, Version: {}", projectName, projectVersion);

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

            // Construct the deterministic Lookup URL and encode the variables safely
            String lookupUrl = String.format("%s/api/v1/project/lookup?name=%s&version=%s",
                    dtrackApiUrl,
                    URLEncoder.encode(projectName, StandardCharsets.UTF_8),
                    URLEncoder.encode(projectVersion, StandardCharsets.UTF_8));

            // The Core Domain is already looking for this exact key to populate publishedSbomUrls!
            metadata.put(PUBLISHED_URL_KEY, lookupUrl);

            // Extract the token and construct the clickable/usable Token Status URL
            if (response != null && response.containsKey("token")) {
                String token = response.get("token");
                metadata.put("dtrack.processingToken", token);

                String tokenUrl = String.format("%s/api/v1/bom/token/%s", dtrackApiUrl, token);
                // This goes into the extra metadata (the `result` map in your Avro schema)
                metadata.put("dtrack.tokenUrl", tokenUrl);
            }

            return metadata;

        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            String errorBody = e.getResponse().readEntity(String.class);
            log.error("Dependency-Track API rejected the upload. Status: {}, Body: {}", status, errorBody);
            throw new DTrackUploadException(String.valueOf(status), "D-Track API error: " + errorBody, e);
        }
    }
}
