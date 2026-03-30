package org.jboss.sbomer.dtrack.publisher.adapter.out.download;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Test resource that provides a simple HTTP server for manifest storage integration tests.
 */
public class ManifestStorageTestResource implements QuarkusTestResourceLifecycleManager {

    public static final String TEST_CONTENT = "{\"foo\": \"bar\"}";
    private static final Set<String> requestedPaths = ConcurrentHashMap.newKeySet();

    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", this::handleRequest);
            server.setExecutor(null);
            server.start();
            int port = server.getAddress().getPort();
            String serverUrl = "http://localhost:" + port;
            return Map.of(
                "manifest-storage/mp-rest/url", serverUrl,
                "manifest-storage-broken/mp-rest/url", serverUrl,
                "dtrack-api/mp-rest/url", "http://localhost:9999",
                "dtrack.api.key", "test-key",
                "kafka.bootstrap.servers", "localhost:9092",
                "kafka.apicurio.registry.url", "http://localhost:8081"
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to start test HTTP server", e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        requestedPaths.clear();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        // Use getRawPath() to capture URL before decoding (e.g. %2F vs /)
        String rawPath = exchange.getRequestURI().getRawPath();
        requestedPaths.add(rawPath);
        byte[] response = TEST_CONTENT.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    public static String getSingleRequestedPath() {
        return requestedPaths.iterator().next();
    }

    public static void clearRequestedPaths() {
        requestedPaths.clear();
    }
}