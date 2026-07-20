package experiment.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

final class HttpJsonTransport {

    private HttpJsonTransport() {
    }

    static JsonNode post(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI uri,
            JsonNode requestBody,
            Map<String, String> headers
    ) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json");
        headers.forEach(request::header);

        try {
            HttpResponse<String> response = httpClient.send(
                    request.POST(HttpRequest.BodyPublishers.ofString(requestBody.toString())).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "LLM API returned HTTP " + response.statusCode() + ": " + response.body()
                );
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the LLM response.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call the LLM API.", exception);
        }
    }

    static HttpClient defaultClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    static String baseUrl(LlmModelConfig config) {
        return config.getBaseUrl().replaceAll("/+$", "");
    }
}
