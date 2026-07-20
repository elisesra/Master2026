package experiment.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

public final class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final String baseUrl;
    private final String modelName;
    private final String apiKey;
    private final Integer requestedDimensions;
    private final int batchSize;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleEmbeddingClient(
            String baseUrl,
            String modelName,
            String apiKey,
            Integer requestedDimensions,
            int batchSize
    ) {
        this(
                baseUrl,
                modelName,
                apiKey,
                requestedDimensions,
                batchSize,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(),
                new ObjectMapper()
        );
    }

    OpenAiCompatibleEmbeddingClient(
            String baseUrl,
            String modelName,
            String apiKey,
            Integer requestedDimensions,
            int batchSize,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Embedding base URL cannot be blank.");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("Embedding model cannot be blank.");
        }
        if (requestedDimensions != null && requestedDimensions <= 0) {
            throw new IllegalArgumentException("Embedding dimensions must be positive.");
        }
        if (batchSize <= 0 || batchSize > 2048) {
            throw new IllegalArgumentException("Embedding batch size must be between 1 and 2048.");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.modelName = modelName.trim();
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
        this.requestedDimensions = requestedDimensions;
        this.batchSize = batchSize;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public OptionalInt requestedDimensions() {
        return requestedDimensions == null
                ? OptionalInt.empty()
                : OptionalInt.of(requestedDimensions);
    }

    @Override
    public List<double[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("At least one text is required for embedding.");
        }
        if (texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new IllegalArgumentException("Embedding input cannot contain blank text.");
        }

        List<double[]> embeddings = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            embeddings.addAll(embedBatch(texts.subList(start, end)));
        }
        return List.copyOf(embeddings);
    }

    private List<double[]> embedBatch(List<String> texts) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);
        body.put("encoding_format", "float");
        if (requestedDimensions != null) {
            body.put("dimensions", requestedDimensions);
        }

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/embeddings"))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json");
        if (apiKey != null) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    request.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Embedding API returned HTTP " + response.statusCode() + ": " + response.body()
                );
            }
            return parseEmbeddings(objectMapper.readTree(response.body()), texts.size());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for embeddings.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call the embedding API.", exception);
        }
    }

    private static List<double[]> parseEmbeddings(JsonNode root, int expectedCount) {
        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() != expectedCount) {
            throw new IllegalStateException(
                    "Embedding response contained " + data.size() + " vectors; expected " + expectedCount + "."
            );
        }

        List<JsonNode> ordered = new ArrayList<>();
        data.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(node -> node.path("index").asInt()));

        List<double[]> result = new ArrayList<>(expectedCount);
        int dimensions = -1;
        for (JsonNode item : ordered) {
            JsonNode vector = item.path("embedding");
            if (!vector.isArray() || vector.isEmpty()) {
                throw new IllegalStateException("Embedding response contained an empty vector.");
            }
            if (dimensions == -1) {
                dimensions = vector.size();
            } else if (dimensions != vector.size()) {
                throw new IllegalStateException("Embedding response contained inconsistent vector dimensions.");
            }
            double[] values = new double[vector.size()];
            for (int index = 0; index < vector.size(); index++) {
                values[index] = vector.get(index).asDouble();
            }
            result.add(values);
        }
        return List.copyOf(result);
    }
}
