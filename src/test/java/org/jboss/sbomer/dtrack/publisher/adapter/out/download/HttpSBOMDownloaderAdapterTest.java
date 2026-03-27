package org.jboss.sbomer.dtrack.publisher.adapter.out.download;

import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.sbomer.dtrack.publisher.adapter.out.download.exception.SBOMDownloadException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Unit tests for HttpSBOMDownloaderAdapter.
 * Tests SBOM download operations from manifest storage service.
 */
@ExtendWith(MockitoExtension.class)
class HttpSBOMDownloaderAdapterTest {

    private static final String ERROR_MESSAGE = "Error downloading SBOM";

    @Mock
    ManifestStorageApiClient storageClient;

    @InjectMocks
    HttpSBOMDownloaderAdapter adapter;

    @Test
    void testDownloadSuccessfully() throws Exception {
        String json = "{\"foo\": \"bar\"}";
        String path = "api/v1/storage/content/generations/123/files/bom.json";
        InputStream mockStream = new ByteArrayInputStream(json.getBytes());
        when(storageClient.download(path)).thenReturn(mockStream);
        Path result = adapter.downloadSbom("/" + path);
        assertNotNull(result);
        assertTrue(Files.exists(result));
        assertEquals(json, Files.readString(result));
        Files.deleteIfExists(result);
        verify(storageClient).download(path);
    }

    @Test
    void testStripLeadingSlashFromPath() throws Exception {
        String path = "downloads/strip-slash.json";
        InputStream mockStream = new ByteArrayInputStream("foo".getBytes());
        when(storageClient.download(path)).thenReturn(mockStream);
        Path result = adapter.downloadSbom("/" + path);
        Files.deleteIfExists(result);
        verify(storageClient).download(path);
    }

    @Test
    void testHandlePathWithoutLeadingSlash() throws Exception {
        String path = "downloads/no-slash.json";
        InputStream mockStream = new ByteArrayInputStream("foo".getBytes());
        when(storageClient.download(path)).thenReturn(mockStream);
        Path result = adapter.downloadSbom(path);
        Files.deleteIfExists(result);
        verify(storageClient).download(path);
    }

    @Test
    void testExtractStatusCodeFrom404Error() {
        String path = "errors/not-found-404.json";
        int statusCode = NOT_FOUND.getStatusCode();
        Response mockResponse = mock(Response.class);
        Response.StatusType statusType = mock(Response.StatusType.class);
        when(statusType.getStatusCode()).thenReturn(statusCode);
        when(mockResponse.getStatus()).thenReturn(statusCode);
        when(mockResponse.getStatusInfo()).thenReturn(statusType);
        WebApplicationException exception = new WebApplicationException(mockResponse);
        when(storageClient.download(path)).thenThrow(exception);
        SBOMDownloadException thrown = assertThrows(SBOMDownloadException.class, () ->
            adapter.downloadSbom("/" + path)
        );
        assertEquals(String.valueOf(statusCode), thrown.getErrorCode());
        assertTrue(thrown.getMessage().contains(ERROR_MESSAGE));
        assertEquals(exception, thrown.getCause());
    }

    @Test
    void testExtractStatusCodeFrom500Error() {
        String path = "errors/server-error-500.json";
        int statusCode = INTERNAL_SERVER_ERROR.getStatusCode();
        Response mockResponse = mock(Response.class);
        Response.StatusType statusType = mock(Response.StatusType.class);
        when(statusType.getStatusCode()).thenReturn(statusCode);
        when(mockResponse.getStatus()).thenReturn(statusCode);
        when(mockResponse.getStatusInfo()).thenReturn(statusType);
        WebApplicationException exception = new WebApplicationException(mockResponse);
        when(storageClient.download(path)).thenThrow(exception);
        SBOMDownloadException thrown = assertThrows(SBOMDownloadException.class, () ->
            adapter.downloadSbom("/" + path)
        );
        assertEquals(String.valueOf(statusCode), thrown.getErrorCode());
        assertTrue(thrown.getMessage().contains(ERROR_MESSAGE));
        assertEquals(exception, thrown.getCause());
    }

    @Test
    void testHandleNetworkErrors() {
        String path = "errors/network-timeout.json";
        String causeMessage = "Connection timeout";
        RuntimeException cause = new RuntimeException(causeMessage);
        when(storageClient.download(path)).thenThrow(cause);
        SBOMDownloadException thrown = assertThrows(SBOMDownloadException.class, () ->
            adapter.downloadSbom("/" + path)
        );
        assertNull(thrown.getErrorCode());
        assertTrue(thrown.getMessage().contains(ERROR_MESSAGE));
        assertTrue(thrown.getMessage().contains(causeMessage));
        assertEquals(cause, thrown.getCause());
    }

    @Test
    void testRethrowSBOMDownloadException() {
        String path = "errors/rethrow-exception.json";
        String errorCode = "bar";
        String originalMessage = "Original exception";
        SBOMDownloadException original = new SBOMDownloadException(errorCode, originalMessage);
        when(storageClient.download(path)).thenThrow(original);
        SBOMDownloadException thrown = assertThrows(SBOMDownloadException.class, () ->
            adapter.downloadSbom("/" + path)
        );
        assertEquals(errorCode, thrown.getErrorCode());
        assertEquals(originalMessage, thrown.getMessage());
        assertEquals(original, thrown);
    }

    @Test
    void testCleanupTempFileOnError() {
        String path = "errors/cleanup-temp.json";
        String causeMessage = "Download failed";
        RuntimeException cause = new RuntimeException(causeMessage);
        when(storageClient.download(path)).thenThrow(cause);
        SBOMDownloadException thrown = assertThrows(SBOMDownloadException.class, () ->
            adapter.downloadSbom("/" + path)
        );
        assertNull(thrown.getErrorCode());
        assertTrue(thrown.getMessage().contains(ERROR_MESSAGE));
        assertTrue(thrown.getMessage().contains(causeMessage));
        assertEquals(cause, thrown.getCause());
        verify(storageClient).download(path);
    }

    @Test
    void testHandlePathWithSpecialCharacters() throws Exception {
        String pathWithSpecialChars = "api/v1/storage/content/generations/abc-123/files/bom-linux-amd64.json";
        InputStream mockStream = new ByteArrayInputStream("foo".getBytes());
        when(storageClient.download(pathWithSpecialChars)).thenReturn(mockStream);
        Path result = adapter.downloadSbom("/" + pathWithSpecialChars);
        Files.deleteIfExists(result);
        verify(storageClient).download(pathWithSpecialChars);
    }

    @Test
    void testHandleIOExceptionDuringStreamCopy() {
        String path = "errors/io-stream-failure.json";
        String causeMessage = "Stream read failure";
        IOException cause = new IOException(causeMessage);
        InputStream faultyStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw cause;
            }
        };
        when(storageClient.download(path)).thenReturn(faultyStream);
        SBOMDownloadException thrown = assertThrows(SBOMDownloadException.class, () ->
            adapter.downloadSbom("/" + path)
        );
        assertNull(thrown.getErrorCode());
        assertTrue(thrown.getMessage().contains(ERROR_MESSAGE));
        assertTrue(thrown.getMessage().contains(causeMessage));
        assertEquals(cause, thrown.getCause());
    }
}