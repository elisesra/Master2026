package experiment.runners;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import experiment.core.RequirementInput;
import experiment.llm.LlmClient;
import experiment.llm.LlmClientFactory;
import experiment.llm.LlmModelConfig;
import experiment.llm.LlmProvider;
import experiment.prompts.ReAct1PromptTemplate;
import experiment.prompts.ReAct2PromptTemplate;
import experiment.validation.FewShot2ResponseValidator;
import experiment.validation.FewShotResponseValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReActProviderCompatibilityTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger requests = new AtomicInteger();

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/responses", this::respondOpenAi);
        server.createContext("/v1/messages", this::respondAnthropic);
        server.createContext("/v1beta/models/gemini-test:generateContent", this::respondGemini);
        server.createContext("/v1/chat/completions", this::respondChat);
        server.createContext("/openai/deployments/deepseek-test/chat/completions", this::respondChat);
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
    void reactRunnersUseACompatibleJsonShapeForEveryImplementedProvider() {
        RequirementInput requirement = new RequirementInput(
                "The system shall process observations.",
                List.of("CAS", "QChS")
        );

        assertProvider(LlmProvider.OPENAI, "openai-test", baseUrl + "/v1", requirement);
        assertProvider(LlmProvider.ANTHROPIC, "claude-test", baseUrl + "/v1", requirement);
        assertProvider(LlmProvider.GOOGLE_GEMINI, "gemini-test", baseUrl + "/v1beta", requirement);
        assertProvider(LlmProvider.MISTRAL, "mistral-test", baseUrl + "/v1", requirement);
        assertProvider(LlmProvider.AZURE_OPENAI, "deepseek-test", baseUrl, requirement);
        assertProvider(LlmProvider.OPENAI_COMPATIBLE, "local-test", baseUrl + "/v1", requirement);

        assertEquals(36, requests.get());
    }

    private void assertProvider(
            LlmProvider provider,
            String model,
            String endpoint,
            RequirementInput requirement
    ) {
        LlmModelConfig.Builder builder = LlmModelConfig.builder()
                .provider(provider)
                .displayName(provider.name())
                .modelName(model)
                .baseUrl(endpoint);
        if (provider != LlmProvider.OPENAI_COMPATIBLE) {
            builder.apiKeyEnvironmentVariable("UNUSED_TEST_KEY").apiKey("test-key");
        }

        LlmClient client = LlmClientFactory.create(builder.build());
        ReAct1PromptTemplate react1Template = new ReAct1PromptTemplate();
        FewShotResponseValidator react1Validator = new FewShotResponseValidator();
        var react1 = new ReActRunner(react1Template.getPromptStyle(), SimpleRunner.Scope.HLR,
                react1Template::buildPrompt, react1Template::buildAssessmentPrompt, null,
                react1Template::isoCriteria, (raw, target) -> react1Validator.validate(raw), client)
                .run(List.of(requirement), temporaryDirectory.resolve(provider + "-react1.json"));

        ReAct2PromptTemplate react2Template = new ReAct2PromptTemplate();
        FewShot2ResponseValidator react2Validator = new FewShot2ResponseValidator();
        var react2 = new ReActRunner(react2Template.getPromptStyle(), SimpleRunner.Scope.ALLOCATION,
                react2Template::buildPrompt, react2Template::buildAssessmentPrompt,
                react2Template::allocationDescription, react2Template::isoCriteria,
                react2Validator::validate, client)
                .run(List.of(requirement), temporaryDirectory.resolve(provider + "-react2.json"));

        assertEquals(1, react1.size());
        assertEquals(2, react2.size());
        assertEquals("CAS", react2.get(0).path("response")
                .path("low_level_requirements").get(0).path("allocation").asText());
        assertEquals("QChS", react2.get(1).path("response")
                .path("low_level_requirements").get(0).path("allocation").asText());
    }

    private void respondOpenAi(HttpExchange exchange) throws IOException {
        respond(exchange, "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":"
                + quote(modelJson(read(exchange))) + "}]}]}");
    }

    private void respondAnthropic(HttpExchange exchange) throws IOException {
        respond(exchange, "{\"content\":[{\"type\":\"text\",\"text\":"
                + quote(modelJson(read(exchange))) + "}]}");
    }

    private void respondGemini(HttpExchange exchange) throws IOException {
        respond(exchange, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + quote(modelJson(read(exchange))) + "}]}}]}");
    }

    private void respondChat(HttpExchange exchange) throws IOException {
        respond(exchange, "{\"choices\":[{\"message\":{\"content\":"
                + quote(modelJson(read(exchange))) + "}}]}");
    }

    private String read(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String modelJson(String requestBody) {
        String allocation = targetAllocationFrom(requestBody);
        return "{\"low_level_requirements\":[{\"allocation\":\"" + allocation
                + "\",\"requirement\":\"The " + allocation + " shall process observations.\"}]}";
    }

    private static String targetAllocationFrom(String text) {
        String marker = "Target allocation code: ";
        int start = text.lastIndexOf(marker);
        if (start < 0) {
            return "CAS";
        }
        start += marker.length();
        int end = text.indexOf("\\n", start);
        if (end < 0) {
            end = text.indexOf('\n', start);
        }
        if (end < 0) {
            end = text.indexOf('"', start);
        }
        return text.substring(start, end).trim();
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
