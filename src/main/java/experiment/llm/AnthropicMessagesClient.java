package experiment.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;

public final class AnthropicMessagesClient implements LlmClient {

    private final LlmModelConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnthropicMessagesClient(LlmModelConfig config) {
        this(config, HttpJsonTransport.defaultClient(), new ObjectMapper());
    }

    AnthropicMessagesClient(LlmModelConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        if (config.getProvider() != LlmProvider.ANTHROPIC) {
            throw new IllegalArgumentException("AnthropicMessagesClient requires an Anthropic configuration.");
        }
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(String prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModelName());
        body.put("max_tokens", 4096);
        ObjectNode message = body.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", prompt);

        JsonNode response = HttpJsonTransport.post(
                httpClient,
                objectMapper,
                URI.create(HttpJsonTransport.baseUrl(config) + "/messages"),
                body,
                Map.of(
                        "x-api-key", config.requireApiKey(),
                        "anthropic-version", "2023-06-01"
                )
        );
        for (JsonNode content : response.path("content")) {
            if ("text".equals(content.path("type").asText()) && !content.path("text").asText().isBlank()) {
                return content.path("text").asText();
            }
        }
        throw new IllegalStateException("Anthropic response did not contain text content.");
    }
}
