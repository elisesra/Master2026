package experiment.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OpenAiResponsesClient implements LlmClient {

    private final LlmModelConfig modelConfig;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiResponsesClient(LlmModelConfig modelConfig) {
        this(modelConfig, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build(), new ObjectMapper());
    }

    OpenAiResponsesClient(
            LlmModelConfig modelConfig,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        if (modelConfig.getProvider() != LlmProvider.OPENAI) {
            throw new IllegalArgumentException("OpenAiResponsesClient requires an OpenAI model configuration.");
        }
        this.modelConfig = modelConfig;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be blank.");
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", modelConfig.getModelName());
        requestBody.put("input", prompt);
        requestBody.set("text", structuredOutputConfiguration());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(responsesUri())
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + modelConfig.requireApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "OpenAI Responses API returned HTTP " + response.statusCode() + ": " + response.body()
                );
            }
            return extractOutputText(objectMapper.readTree(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the OpenAI response.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call the OpenAI Responses API.", exception);
        }
    }

    private URI responsesUri() {
        String baseUrl = modelConfig.getBaseUrl().replaceAll("/+$", "");
        return URI.create(baseUrl + "/responses");
    }

    private ObjectNode structuredOutputConfiguration() {
        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "json_schema");
        format.put("name", "low_level_requirements");
        format.put("strict", true);
        format.set("schema", LowLevelRequirementSchema.create(objectMapper));

        ObjectNode text = objectMapper.createObjectNode();
        text.set("format", format);
        return text;
    }

    private static String extractOutputText(JsonNode root) {
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    if ("output_text".equals(part.path("type").asText())
                            && !part.path("text").asText().isBlank()) {
                        return part.path("text").asText();
                    }
                }
            }
        }
        throw new IllegalStateException("OpenAI response did not contain output text.");
    }
}
