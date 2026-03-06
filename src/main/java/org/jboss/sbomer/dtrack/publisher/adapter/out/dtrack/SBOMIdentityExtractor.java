package org.jboss.sbomer.dtrack.publisher.adapter.out.dtrack;

import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SBOMIdentityExtractor {

    public record ProjectIdentity(String name, String version) {}

    public static ProjectIdentity extract(Path sbomFile) {
        String name = null;
        String version = null;
        boolean isCycloneDx = false;
        boolean isSpdx = false;

        try (JsonParser parser = new JsonFactory().createParser(sbomFile.toFile())) {

            while (!parser.isClosed()) {
                JsonToken token = parser.nextToken();
                if (token == null) break;

                // We only care when the parser hits an actual string value
                if (token == JsonToken.VALUE_STRING) {

                    // Get the exact JSON path (e.g., "/metadata/component/name")
                    String jsonPath = parser.getParsingContext().pathAsPointer().toString();

                    // Detect the SBOM Format
                    if ("/bomFormat".equals(jsonPath) && "CycloneDX".equals(parser.getValueAsString())) {
                        isCycloneDx = true;
                    } else if ("/spdxVersion".equals(jsonPath)) {
                        isSpdx = true;
                    }

                    // Extract CycloneDX fields strictly from the root metadata
                    if (isCycloneDx) {
                        if ("/metadata/component/name".equals(jsonPath)) {
                            name = parser.getValueAsString();
                        } else if ("/metadata/component/version".equals(jsonPath)) {
                            version = parser.getValueAsString();
                        }
                    }

                    // Extract SPDX fields
                    if (isSpdx) {
                        if ("/name".equals(jsonPath)) {
                            name = parser.getValueAsString(); // SPDX document name
                        } else if ("/packages/0/versionInfo".equals(jsonPath)) {
                            // In SPDX JSON, the first package is conventionally the root target
                            version = parser.getValueAsString();
                        }
                    }

                    // Break early if we have both
                    if (name != null && version != null) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // This will safely catch issues like trying to parse an XML file with a JSON parser
            log.warn("Failed to extract name/version from SBOM stream: {}", sbomFile.getFileName(), e);
        }
        return new ProjectIdentity(name, version);
    }
}
