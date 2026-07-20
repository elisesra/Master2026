package experiment.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class ChatCompletionsClient implements LlmClient {

    private final LlmModelConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    ChatCompletionsClient(LlmModelConfig config) {
        this(config, HttpJsonTransport.defaultClient(), new ObjectMapper());
    }

    ChatCompletionsClient(LlmModelConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        if (config.getProvider() != LlmProvider.MISTRAL
                && config.getProvider() != LlmProvider.AZURE_OPENAI
                && config.getProvider() != LlmProvider.COHERE
                && config.getProvider() != LlmProvider.OPENAI_COMPATIBLE) {
            throw new IllegalArgumentException(
                    "ChatCompletionsClient requires Mistral, Azure OpenAI, Cohere, or an OpenAI-compatible endpoint."
            );
        }
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(String prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModelName());
        ObjectNode message = body.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", prompt);

        if (config.getProvider() == LlmProvider.MISTRAL
                || config.getProvider() == LlmProvider.COHERE) {
            body.putObject("response_format").put("type", "json_object");
        }

        JsonNode response = HttpJsonTransport.post(
                httpClient,
                objectMapper,
                endpoint(),
                body,
                headers()
        );
        String text = response.path("choices").path(0).path("message").path("content").asText();
        if (text.isBlank()) {
            throw new IllegalStateException("Chat completion response did not contain message content.");
        }
        return text;
    }

    private URI endpoint() {
        String baseUrl = HttpJsonTransport.baseUrl(config);
        if (config.getProvider() == LlmProvider.MISTRAL
                || config.getProvider() == LlmProvider.COHERE
                || config.getProvider() == LlmProvider.OPENAI_COMPATIBLE) {
            return URI.create(baseUrl + "/chat/completions");
        }
        String deployment = URLEncoder.encode(config.getModelName(), StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create(baseUrl + "/openai/deployments/" + deployment
                + "/chat/completions?api-version=2024-10-21");
    }

    private Map<String, String> headers() {
        if (config.getProvider() == LlmProvider.AZURE_OPENAI) {
            return Map.of("api-key", config.requireApiKey());
        }
        return config.getApiKey()
                .<Map<String, String>>map(key -> Map.of("Authorization", "Bearer " + key))
                .orElseGet(Map::of);
    }
}
