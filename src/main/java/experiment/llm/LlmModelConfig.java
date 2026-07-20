package experiment.llm;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents configuration for one LLM model.
 *
 * This object stores:
 * - Provider name
 * - Model name
 * - API base URL
 * - Environment variable name for the API key
 * - Optional manually supplied API key
 */
public final class LlmModelConfig {

    private final LlmProvider provider;
    private final String displayName;
    private final String modelName;
    private final String baseUrl;
    private final String baseUrlEnvironmentVariable;
    private final String apiKeyEnvironmentVariable;
    private final String apiKey;

    private LlmModelConfig(Builder builder) {
        this.provider = Objects.requireNonNull(builder.provider, "Provider cannot be null.");
        this.displayName = requireNonBlank(builder.displayName, "Display name");
        this.modelName = requireNonBlank(builder.modelName, "Model name");
        this.baseUrl = normalizeOptional(builder.baseUrl);
        this.baseUrlEnvironmentVariable = normalizeOptional(builder.baseUrlEnvironmentVariable);
        if (baseUrl == null && baseUrlEnvironmentVariable == null) {
            throw new IllegalArgumentException("A base URL or base URL environment variable is required.");
        }
        this.apiKeyEnvironmentVariable = normalizeOptional(builder.apiKeyEnvironmentVariable);
        this.apiKey = builder.apiKey;
    }

    public LlmProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getModelName() {
        return modelName;
    }

    public String getBaseUrl() {
        return findBaseUrl().orElseThrow(() -> new IllegalStateException(
                "Missing API endpoint for " + displayName + ". Set environment variable "
                        + baseUrlEnvironmentVariable + "."
        ));
    }

    public Optional<String> findBaseUrl() {
        if (baseUrlEnvironmentVariable != null) {
            String environmentValue = System.getenv(baseUrlEnvironmentVariable);
            if (environmentValue != null && !environmentValue.isBlank()) {
                return Optional.of(environmentValue.trim());
            }
        }
        return Optional.ofNullable(baseUrl);
    }

    public Optional<String> getBaseUrlEnvironmentVariable() {
        return Optional.ofNullable(baseUrlEnvironmentVariable);
    }

    public String getApiKeyEnvironmentVariable() {
        return apiKeyEnvironmentVariable;
    }

    /**
     * Gets the API key.
     *
     * Priority:
     * 1. Manually supplied API key
     * 2. Environment variable
     *
     * @return API key if available
     */
    public Optional<String> getApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return Optional.of(apiKey);
        }

        if (apiKeyEnvironmentVariable == null) {
            return Optional.empty();
        }
        String environmentValue = System.getenv(apiKeyEnvironmentVariable);

        if (environmentValue == null || environmentValue.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(environmentValue);
    }

    /**
     * Returns the API key or throws a clear error if missing.
     */
    public String requireApiKey() {
        return getApiKey().orElseThrow(() ->
                new IllegalStateException(
                        "Missing API key for " + displayName
                                + (apiKeyEnvironmentVariable == null
                                ? ". Configure an API key for this model."
                                : ". Set environment variable " + apiKeyEnvironmentVariable
                                + " or provide the key manually.")
                )
        );
    }

    public Builder toBuilder() {
        return new Builder()
                .provider(provider)
                .displayName(displayName)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .baseUrlEnvironmentVariable(baseUrlEnvironmentVariable)
                .apiKeyEnvironmentVariable(apiKeyEnvironmentVariable)
                .apiKey(apiKey);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }

        return value;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public String toString() {
        return "LlmModelConfig{" +
                "provider=" + provider +
                ", displayName='" + displayName + '\'' +
                ", modelName='" + modelName + '\'' +
                ", baseUrlConfigured=" + findBaseUrl().isPresent() +
                ", apiKeyEnvironmentVariable='" + apiKeyEnvironmentVariable + '\'' +
                ", apiKeyPresent=" + getApiKey().isPresent() +
                '}';
    }

    public static final class Builder {

        private LlmProvider provider;
        private String displayName;
        private String modelName;
        private String baseUrl;
        private String baseUrlEnvironmentVariable;
        private String apiKeyEnvironmentVariable;
        private String apiKey;

        private Builder() {
        }

        public Builder provider(LlmProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder baseUrlEnvironmentVariable(String baseUrlEnvironmentVariable) {
            this.baseUrlEnvironmentVariable = baseUrlEnvironmentVariable;
            return this;
        }

        public Builder apiKeyEnvironmentVariable(String apiKeyEnvironmentVariable) {
            this.apiKeyEnvironmentVariable = apiKeyEnvironmentVariable;
            return this;
        }

        /**
         * Optional manual API key input.
         *
         * Prefer environment variables for real projects.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public LlmModelConfig build() {
            return new LlmModelConfig(this);
        }
    }
}
