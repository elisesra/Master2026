package experiment.llm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmClientFactoryTest {

    private static final String MODEL_JSON =
            "{\"low_level_requirements\":[{\"allocation\":\"CS\",\"requirement\":\"The CS shall collect data.\"}]}";

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/responses", exchange -> respond(exchange,
                "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":" + quote(MODEL_JSON) + "}]}]}"
        ));
        server.createContext("/v1/messages", exchange -> respond(exchange,
                "{\"content\":[{\"type\":\"text\",\"text\":" + quote(MODEL_JSON) + "}]}"
        ));
        server.createContext("/v1beta/models/gemini-test:generateContent", exchange -> respond(exchange,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + quote(MODEL_JSON) + "}]}}]}"
        ));
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, chatResponse()));
        server.createContext(
                "/openai/deployments/deepseek-test/chat/completions",
                exchange -> respond(exchange, chatResponse())
        );
        server.start();
        baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void allRequiredProvidersGenerateTextThroughTheirProtocol() {
        assertProvider(LlmProvider.OPENAI, "openai-test", baseUrl + "/v1");
        assertProvider(LlmProvider.ANTHROPIC, "claude-test", baseUrl + "/v1");
        assertProvider(LlmProvider.GOOGLE_GEMINI, "gemini-test", baseUrl + "/v1beta");
        assertProvider(LlmProvider.MISTRAL, "mistral-test", baseUrl + "/v1");
        assertProvider(LlmProvider.COHERE, "cohere-test", baseUrl + "/v1");
        assertProvider(LlmProvider.AZURE_OPENAI, "deepseek-test", baseUrl);
        assertLocalProviderWithoutApiKey();

        assertEquals(7, requests.get());
    }

    @Test
    void registryContainsAllThreeOpenWeightModels() {
        var displayNames = LlmModelRegistry.getDefaultModels().stream()
                .map(LlmModelConfig::getDisplayName)
                .toList();

        assertTrue(displayNames.contains("gpt-oss 20B"));
        assertTrue(displayNames.contains("gpt-oss 120B"));
        assertTrue(displayNames.contains("Qwen3 30B-A3B"));
        assertTrue(LlmModelRegistry.findByModelName("gpt-oss:20b").isPresent());
        assertTrue(LlmModelRegistry.findByModelName("gpt-oss:120b").isPresent());
        assertTrue(LlmModelRegistry.findByModelName("qwen3:30b-a3b").isPresent());
    }

    @Test
    void registryUsesExactMistralModelIdentifier() {
        assertTrue(LlmModelRegistry.findByDisplayName("Mistral Medium 3.5").isPresent());
        assertTrue(LlmModelRegistry.findByModelName("mistral-medium-3-5").isPresent());
    }

    @Test
    void registryContainsDirectDeepSeekV4Flash() {
        LlmModelConfig config = LlmModelRegistry.findByModelName("deepseek-v4-flash").orElseThrow();

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.getProvider());
        assertEquals("https://api.deepseek.com", config.getBaseUrl());
        assertEquals("DEEPSEEK_API_KEY", config.getApiKeyEnvironmentVariable());
    }

    @Test
    void registryContainsCurrentCohereCommandAPlus() {
        LlmModelConfig config = LlmModelRegistry.findByModelName("command-a-plus-05-2026").orElseThrow();

        assertEquals(LlmProvider.COHERE, config.getProvider());
        assertEquals("https://api.cohere.ai/compatibility/v1", config.getBaseUrl());
        assertEquals("COHERE_API_KEY", config.getApiKeyEnvironmentVariable());
    }

    private void assertProvider(LlmProvider provider, String model, String endpoint) {
        LlmModelConfig config = LlmModelConfig.builder()
                .provider(provider)
                .displayName(provider.name())
                .modelName(model)
                .baseUrl(endpoint)
                .apiKeyEnvironmentVariable("UNUSED_TEST_KEY")
                .apiKey("test-key")
                .build();

        assertEquals(MODEL_JSON, LlmClientFactory.create(config).generate("test prompt"));
    }

    private void assertLocalProviderWithoutApiKey() {
        LlmModelConfig config = LlmModelConfig.builder()
                .provider(LlmProvider.OPENAI_COMPATIBLE)
                .displayName("Local model")
                .modelName("local-test")
                .baseUrl(baseUrl + "/v1")
                .build();

        assertEquals(MODEL_JSON, LlmClientFactory.create(config).generate("test prompt"));
    }

    private void respond(HttpExchange exchange, String response) throws IOException {
        requests.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String chatResponse() {
        return "{\"choices\":[{\"message\":{\"content\":" + quote(MODEL_JSON) + "}}]}";
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
