package experiment.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class GeminiGenerateContentClient implements LlmClient {

    private final LlmModelConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiGenerateContentClient(LlmModelConfig config) {
        this(config, HttpJsonTransport.defaultClient(), new ObjectMapper());
    }

    GeminiGenerateContentClient(LlmModelConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        if (config.getProvider() != LlmProvider.GOOGLE_GEMINI) {
            throw new IllegalArgumentException("GeminiGenerateContentClient requires a Gemini configuration.");
        }
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(String prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseJsonSchema", LowLevelRequirementSchema.create(objectMapper));

        String model = URLEncoder.encode(config.getModelName(), StandardCharsets.UTF_8).replace("+", "%20");
        URI uri = URI.create(HttpJsonTransport.baseUrl(config) + "/models/" + model + ":generateContent");
        JsonNode response = HttpJsonTransport.post(
                httpClient,
                objectMapper,
                uri,
                body,
                Map.of("x-goog-api-key", config.requireApiKey())
        );
        String text = response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
        if (text.isBlank()) {
            throw new IllegalStateException("Gemini response did not contain candidate text.");
        }
        return text;
    }
}
