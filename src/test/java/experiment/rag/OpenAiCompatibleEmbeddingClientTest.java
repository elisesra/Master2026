package experiment.rag;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleEmbeddingClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/embeddings", this::respond);
        server.start();
        baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsBatchedOpenAiCompatibleRequestsAndRestoresIndexOrder() {
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
                baseUrl,
                "embedding-test",
                "secret",
                3,
                2,
                HttpClient.newHttpClient(),
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        List<double[]> vectors = client.embed(List.of("first", "second"));

        assertEquals(2, vectors.size());
        assertArrayEquals(new double[]{1, 0, 0}, vectors.get(0));
        assertArrayEquals(new double[]{0, 1, 0}, vectors.get(1));
        assertEquals("Bearer secret", authorization.get());
        assertTrue(requestBody.get().contains("\"model\":\"embedding-test\""));
        assertTrue(requestBody.get().contains("\"dimensions\":3"));
        assertTrue(requestBody.get().contains("\"input\":[\"first\",\"second\"]"));
    }

    private void respond(HttpExchange exchange) throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        String response = """
                {"data":[
                  {"index":1,"embedding":[0,1,0]},
                  {"index":0,"embedding":[1,0,0]}
                ]}
                """;
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
