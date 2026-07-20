import experiment.core.ExperimentHandler;
import experiment.crossvalidating.CrossValidationRunner;
import evaluation.DeterministicEvaluationRunner;
import evaluation.EvaluationExperimentRunner;
import evaluation.GeneralEvaluationRunner;
import evaluation.ExperimentDirectoryEvaluationRunner;
import experiment.prompts.PromptTemplate;
import experiment.prompts.PanaceaRun1PromptTemplate;
import experiment.prompts.PanaceaRun2PromptTemplate;
import experiment.prompts.Rag1PromptTemplate;
import experiment.prompts.Rag2PromptTemplate;
import experiment.rag.EmbeddingClient;
import experiment.rag.OpenAiCompatibleEmbeddingClient;
import experiment.rag.RagChunker;
import experiment.rag.RagIndexSummary;
import experiment.rag.RagIndexer;
import experiment.llm.LlmModelConfig;
import experiment.llm.LlmModelRegistry;
import experiment.llm.LlmClientFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromArgs(args);
        if (config.indexRag()) {
            indexRag(config);
            return;
        }
        if (config.deterministicEvaluation()) {
            runDeterministicEvaluation(config);
            return;
        }
        if (config.generalEvaluation()) {
            runGeneralEvaluation(config);
            return;
        }
        if (config.experimentDirectoryEvaluation()) {
            runExperimentDirectoryEvaluation(config);
            return;
        }
        if (config.crossValidation()) {
            runCrossValidation(config);
            return;
        }
        if (config.evaluateOutput().isPresent()) {
            runEvaluation(config);
            return;
        }

        ExperimentHandler experimentHandler = new ExperimentHandler();

        String promptName = config.prompt().orElseThrow(() ->
                new IllegalArgumentException(
                        "Provide --prompt=fs1, fs2, cot1, cot2, rag1, rag2, react1, react2, panacea1, panacea2, panacea_run1, or panacea_run2."
                )
        );
        PromptTemplate promptTemplate = experimentHandler.selectPrompt(promptName);
        System.out.println("Selected prompt: " + promptTemplate.getPromptStyle());

        String datasetName = config.dataset().orElseThrow(() ->
                new IllegalArgumentException("Experiment execution requires --dataset=<dataset-name>.")
        );
        String modelName = config.model().orElseThrow(() ->
                new IllegalArgumentException("Experiment execution requires --model=<model-name>.")
        );
        LlmModelConfig modelConfig = LlmModelRegistry.findByModelName(modelName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown model: " + modelName));

        Path outputFile = config.output().orElse(defaultExperimentOutputFile(
                modelConfig,
                promptTemplate,
                datasetName
        ));
        if (promptTemplate instanceof Rag1PromptTemplate || promptTemplate instanceof Rag2PromptTemplate) {
            experimentHandler.runRagExperimentDataset(
                    promptTemplate,
                    Path.of("datasets"),
                    datasetName,
                    outputFile,
                    config.ragIndex(),
                    embeddingClient(config),
                    LlmClientFactory.create(modelConfig),
                    config.topK()
            );
        } else if (promptTemplate instanceof PanaceaRun1PromptTemplate
                || promptTemplate instanceof PanaceaRun2PromptTemplate) {
            experimentHandler.runPromptExperimentDataset(
                    promptTemplate,
                    Path.of("datasets"),
                    datasetName,
                    outputFile,
                    LlmClientFactory.create(modelConfig),
                    panaceaPromptReport(config, promptTemplate)
            );
        } else {
            experimentHandler.runPromptExperimentDataset(
                    promptTemplate,
                    Path.of("datasets"),
                    datasetName,
                    outputFile,
                    LlmClientFactory.create(modelConfig)
            );
        }
        System.out.println("Experiment report written to: " + outputFile);
    }

    private static void runEvaluation(AppConfig config) {
        Path generatedOutputFile = config.evaluateOutput().orElseThrow();
        List<LlmModelConfig> judgeModels = config.judgeModels()
                .map(Main::judgeModelsFromArgument)
                .orElseGet(LlmModelRegistry::getDefaultModels);
        Path outputFile = config.output().orElse(Path.of(
                "results",
                "evaluation_report.json"
        ));

        new EvaluationExperimentRunner().run(
                generatedOutputFile,
                judgeModels,
                outputFile
        );
        System.out.println("Evaluation report written to: " + outputFile);
    }

    private static void runDeterministicEvaluation(AppConfig config) {
        Path generatedOutputFile = config.evaluateOutput().orElseThrow(() ->
                new IllegalArgumentException("Deterministic evaluation requires --evaluate-output=<generated-json>.")
        );
        String datasetName = config.dataset().orElseThrow(() ->
                new IllegalArgumentException("Deterministic evaluation requires --dataset=<dataset-name>.")
        );
        Path datasetFile = Path.of("datasets", datasetName, "allocation_requirements_cleaned.json");
        Path outputFile = config.output().orElse(Path.of(
                "results",
                "deterministic_evaluation_report.json"
        ));

        new DeterministicEvaluationRunner().run(
                generatedOutputFile,
                datasetFile,
                outputFile
        );
        System.out.println("Deterministic evaluation report written to: " + outputFile);
    }

    private static void runGeneralEvaluation(AppConfig config) {
        List<Path> generatedOutputFiles = config.evaluateOutputs()
                .map(Main::pathsFromArgument)
                .orElseThrow(() ->
                        new IllegalArgumentException("General evaluation requires --evaluate-outputs=<json-files>.")
                );
        String datasetName = config.dataset().orElseThrow(() ->
                new IllegalArgumentException("General evaluation requires --dataset=<dataset-name>.")
        );
        Path datasetFile = Path.of("datasets", datasetName, "allocation_requirements_cleaned.json");
        Path outputFile = config.output().orElse(Path.of(
                "results",
                "general_evaluation_report.json"
        ));

        new GeneralEvaluationRunner().run(
                generatedOutputFiles,
                datasetFile,
                outputFile
        );
        System.out.println("General evaluation report written to: " + outputFile);
    }

    private static void runExperimentDirectoryEvaluation(AppConfig config) {
        String datasetName = config.dataset().orElseThrow(() ->
                new IllegalArgumentException("Experiment evaluation requires --dataset=<dataset-name>.")
        );
        Path datasetFile = Path.of("datasets", datasetName, "allocation_requirements_cleaned.json");
        Path experimentDirectory = config.experimentDirectory()
                .orElse(Path.of("results", "experiment"));
        Path evaluationDirectory = config.evaluationDirectory()
                .orElse(Path.of("results", "evaluation"));
        List<ExperimentDirectoryEvaluationRunner.FileEvaluation> results =
                new ExperimentDirectoryEvaluationRunner().run(
                        experimentDirectory, datasetFile, datasetName, evaluationDirectory
                );
        System.out.println("Evaluated generation files: " + results.size());
        System.out.println("Evaluation directory: " + evaluationDirectory);
    }

    private static void runCrossValidation(AppConfig config) {
        String datasetName = config.dataset().orElseThrow(() ->
                new IllegalArgumentException("Cross-validation requires --dataset=<dataset-name>.")
        );
        List<LlmModelConfig> judges = config.judgeModels()
                .map(Main::crossValidationModelsFromArgument)
                .orElseGet(() -> List.of(
                        LlmModelRegistry.mistralMedium35(),
                        LlmModelRegistry.anthropicClaude(),
                        LlmModelRegistry.googleGemini(),
                        LlmModelRegistry.deepSeekV4Flash(),
                        LlmModelRegistry.cohereCommand()
                ));
        new CrossValidationRunner().run(
                config.experimentDirectory().orElse(Path.of("results", "experiment")),
                Path.of("datasets", datasetName, "allocation_requirements_cleaned.json"),
                datasetName,
                judges,
                config.crossValidationDirectory().orElse(Path.of("results", "crossvalidation")),
                config.crossValidationMaximumCases(),
                config.crossValidationPromptType().orElse("fs2"),
                config.crossValidationCasesPerFile()
        );
        System.out.println("Cross-validation complete for judge models: "
                + judges.stream().map(LlmModelConfig::getModelName).toList());
    }

    private static List<LlmModelConfig> crossValidationModelsFromArgument(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(model -> !model.isEmpty())
                .map(model -> model.equalsIgnoreCase("mistral-medium-3.5")
                        ? LlmModelRegistry.mistralMedium35()
                        : LlmModelRegistry.findByModelName(model)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown cross-validation judge: " + model)))
                .toList();
    }

    private static List<LlmModelConfig> judgeModelsFromArgument(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(model -> !model.isEmpty())
                .map(model -> LlmModelRegistry.findByModelName(model)
                        .or(() -> LlmModelRegistry.findByDisplayName(model))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown judge model: " + model)))
                .toList();
    }

    private static List<Path> pathsFromArgument(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .map(Path::of)
                .toList();
    }

    private static Path defaultExperimentOutputFile(
            LlmModelConfig modelConfig,
            PromptTemplate promptTemplate,
            String datasetName
    ) {
        String promptStyle = promptTemplate.getPromptStyle();
        if (promptTemplate instanceof PanaceaRun1PromptTemplate
                || promptTemplate instanceof PanaceaRun2PromptTemplate) {
            return Path.of("results", promptStyle + "_report.json");
        }

        String fileName = fileNamePart(modelConfig.getModelName())
                + "_"
                + fileNamePart(promptStyle)
                + "_"
                + fileNamePart(datasetName)
                + "_result.json";
        return Path.of("results", "experiments", "llmresult", fileNamePart(promptStyle), fileName);
    }

    private static String fileNamePart(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Output file name part cannot be blank.");
        }
        String sanitized = value.trim()
                .replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Output file name part has no safe characters: " + value);
        }
        return sanitized;
    }

    private static void indexRag(AppConfig config) {
        RagIndexer indexer = new RagIndexer(
                new RagChunker(config.chunkCharacters(), config.chunkOverlap()),
                embeddingClient(config)
        );
        RagIndexSummary summary = indexer.index(config.documents(), config.ragIndex());
        System.out.println("RAG index written to: " + config.ragIndex());
        System.out.println("Documents: " + summary.documents());
        System.out.println("Added or changed documents: " + summary.addedOrChangedDocuments());
        System.out.println("Removed documents: " + summary.removedDocuments());
        System.out.println("Newly embedded chunks: " + summary.embeddedChunks());
        System.out.println("Total indexed chunks: " + summary.totalChunks());
        System.out.println("Embedding model: " + summary.embeddingModel());
        System.out.println("Embedding dimensions: " + summary.embeddingDimensions());
    }

    private static EmbeddingClient embeddingClient(AppConfig config) {
        return new OpenAiCompatibleEmbeddingClient(
                config.embeddingBaseUrl(),
                config.embeddingModel(),
                config.embeddingApiKey(),
                config.embeddingDimensions().orElse(null),
                config.embeddingBatchSize()
        );
    }

    private static Path panaceaPromptReport(AppConfig config, PromptTemplate promptTemplate) {
        return config.panaceaPromptReport().orElseGet(() -> {
            if (promptTemplate instanceof PanaceaRun2PromptTemplate) {
                return Path.of("results", "mistral_panacea2_report.json");
            }
            return Path.of("results", "mistral_panacea1_report.json");
        });
    }

    private record AppConfig(
            boolean indexRag,
            boolean deterministicEvaluation,
            boolean generalEvaluation,
            boolean experimentDirectoryEvaluation,
            boolean crossValidation,
            Optional<String> prompt,
            Optional<String> dataset,
            Optional<String> model,
            Optional<Path> evaluateOutput,
            Optional<String> evaluateOutputs,
            Optional<Path> experimentDirectory,
            Optional<Path> evaluationDirectory,
            Optional<Path> crossValidationDirectory,
            Optional<String> crossValidationPromptType,
            Optional<String> judgeModels,
            Optional<Path> output,
            Optional<Path> panaceaPromptReport,
            Path documents,
            Path ragIndex,
            String embeddingBaseUrl,
            String embeddingModel,
            String embeddingApiKey,
            Optional<Integer> embeddingDimensions,
            int embeddingBatchSize,
            int chunkCharacters,
            int chunkOverlap,
            int topK,
            int crossValidationMaximumCases,
            int crossValidationCasesPerFile
    ) {
        static AppConfig fromArgs(String[] args) {
            return new AppConfig(
                    hasFlag(args, "--index-rag"),
                    hasFlag(args, "--deterministic-evaluation"),
                    hasFlag(args, "--eval-general"),
                    hasFlag(args, "--eval-experiment"),
                    hasFlag(args, "--cross-validate"),
                    findArgument(args, "--prompt="),
                    findArgument(args, "--dataset="),
                    findArgument(args, "--model="),
                    findArgument(args, "--evaluate-output=").map(Path::of),
                    findArgument(args, "--evaluate-outputs="),
                    findArgument(args, "--experiment-dir=").map(Path::of),
                    findArgument(args, "--evaluation-dir=").map(Path::of),
                    findArgument(args, "--crossvalidation-dir=").map(Path::of),
                    findArgument(args, "--crossvalidation-prompt="),
                    findArgument(args, "--judge-models="),
                    findArgument(args, "--output=").map(Path::of),
                    findArgument(args, "--panacea-prompt=").map(Path::of),
                    findArgument(args, "--documents=")
                            .map(Path::of)
                            .orElse(Path.of("storage", "embeddings", "paraphrase")),
                    findArgument(args, "--rag-index=")
                            .map(Path::of)
                            .orElse(Path.of(
                                    "storage",
                                    "embeddings",
                                    "db_embeddings",
                                    "db_embedded_chunk_context.db"
                            )),
                    findArgument(args, "--embedding-base-url=")
                            .orElseGet(() -> environmentOrDefault(
                                    "EMBEDDING_BASE_URL",
                                    "https://api.openai.com/v1"
                            )),
                    findArgument(args, "--embedding-model=")
                            .orElseGet(() -> environmentOrDefault(
                                    "EMBEDDING_MODEL",
                                    "text-embedding-3-small"
                            )),
                    firstNonBlankEnvironment("EMBEDDING_API_KEY", "OPENAI_API_KEY"),
                    findArgument(args, "--embedding-dimensions=").map(value ->
                            positiveInteger("--embedding-dimensions", value)
                    ),
                    findArgument(args, "--embedding-batch-size=")
                            .map(value -> positiveInteger("--embedding-batch-size", value))
                            .orElse(64),
                    findArgument(args, "--chunk-characters=")
                            .map(value -> positiveInteger("--chunk-characters", value))
                            .orElse(1800),
                    findArgument(args, "--chunk-overlap=")
                            .map(value -> nonNegativeInteger("--chunk-overlap", value))
                            .orElse(250),
                    findArgument(args, "--top-k=")
                            .map(value -> positiveInteger("--top-k", value))
                            .orElse(6),
                    findArgument(args, "--max-crossvalidation-cases=")
                            .map(value -> positiveInteger("--max-crossvalidation-cases", value))
                            .orElse(0),
                    findArgument(args, "--crossvalidation-cases-per-file=")
                            .map(value -> positiveInteger("--crossvalidation-cases-per-file", value))
                            .orElse(10)
            );
        }
        private static Optional<String> findArgument(String[] args, String prefix) {
            for (String arg : args) {
                if (arg.startsWith(prefix)) {
                    String value = arg.substring(prefix.length()).trim();
                    if (value.isEmpty()) {
                        throw new IllegalArgumentException("Argument " + prefix + " must not be empty.");
                    }
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }

        private static boolean hasFlag(String[] args, String flag) {
            for (String arg : args) {
                if (arg.equals(flag)) {
                    return true;
                }
            }
            return false;
        }

        private static int positiveInteger(String name, String value) {
            int parsed = nonNegativeInteger(name, value);
            if (parsed == 0) {
                throw new IllegalArgumentException(name + " must be positive.");
            }
            return parsed;
        }

        private static int nonNegativeInteger(String name, String value) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 0) {
                    throw new IllegalArgumentException(name + " cannot be negative.");
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(name + " must be an integer.", exception);
            }
        }

        private static String environmentOrDefault(String name, String defaultValue) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : value.trim();
        }

        private static String firstNonBlankEnvironment(String... names) {
            for (String name : names) {
                String value = System.getenv(name);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return null;
        }
    }
}
