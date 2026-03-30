package org.jboss.sbomer.dtrack.publisher.adapter.out.download;

import static org.jboss.sbomer.dtrack.publisher.adapter.out.download.ManifestStorageTestResource.TEST_CONTENT;
import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Integration tests for HttpSBOMDownloaderAdapter.
 * Tests REST client path encoding with HTTP server.
 */
@QuarkusTest
@QuarkusTestResource(ManifestStorageTestResource.class)
class HttpSBOMDownloaderAdapterIntegrationTest {

    @RegisterRestClient(configKey = "manifest-storage-broken")
    interface BrokenManifestStorageApiClient {
        @GET
        @jakarta.ws.rs.Path("/{path:.+}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        InputStream download(@PathParam("path") String path);
    }

    @Inject
    HttpSBOMDownloaderAdapter adapter;

    @Inject
    @RestClient
    BrokenManifestStorageApiClient brokenClient;

    @BeforeEach
    void clearPaths() {
        ManifestStorageTestResource.clearRequestedPaths();
    }

    @Test
    void testPathWithSlashesNotEncoded() throws Exception {
        String path = "/api/v1/storage/content/generations/123/files/bom.json";
        Path result = adapter.downloadSbom(path);
        assertNotNull(result);
        assertTrue(Files.exists(result));
        assertEquals(TEST_CONTENT, Files.readString(result));
        String requestedPath = ManifestStorageTestResource.getSingleRequestedPath();
        assertEquals(path, requestedPath);
        assertFalse(requestedPath.contains("%2F"), "Path slashes should not be encoded");
        Files.deleteIfExists(result);
    }

    @Test
    void testComplexPathWithSpecialCharacters() throws Exception {
        String path = "/api/v1/storage/content/generations/abc-123/files/bom-linux-amd64.json";
        Path result = adapter.downloadSbom(path);
        assertNotNull(result);
        assertEquals(TEST_CONTENT, Files.readString(result));
        String requestedPath = ManifestStorageTestResource.getSingleRequestedPath();
        assertEquals(path, requestedPath);
        assertTrue(requestedPath.contains("/generations/abc-123/files/"),
                "Path should preserve slashes and special characters");
        Files.deleteIfExists(result);
    }

    @Test
    void testPathWithSpaceEncodedButSlashesNot() throws Exception {
        String decodedPath = "/api/v1/storage/content/files/bom test file.json";
        String encodedPath = "/api/v1/storage/content/files/bom%20test%20file.json";
        Path result = adapter.downloadSbom(decodedPath);
        assertNotNull(result);
        assertEquals(TEST_CONTENT, Files.readString(result));
        String requestedPath = ManifestStorageTestResource.getSingleRequestedPath();
        assertEquals(encodedPath, requestedPath, "Spaces should be encoded to %20");
        assertFalse(requestedPath.contains("%2F"), "Slashes should not be encoded");
        assertTrue(requestedPath.contains("/files/"), "Path structure should be preserved");
        Files.deleteIfExists(result);
    }

    @Test
    void testBrokenClientEncodesSlashes() throws Exception {
        String path = "api/v1/storage/content/generations/999/files/broken.json";
        try (InputStream stream = brokenClient.download(path)) {
            assertNotNull(stream);
            byte[] content = stream.readAllBytes();
            assertEquals(TEST_CONTENT, new String(content));
        }
        String requestedPath = ManifestStorageTestResource.getSingleRequestedPath();
        assertTrue(requestedPath.contains("%2F"), "Without @Encoded, slashes are encoded to %2F");
        assertEquals("/api%2Fv1%2Fstorage%2Fcontent%2Fgenerations%2F999%2Ffiles%2Fbroken.json", requestedPath);
    }
}