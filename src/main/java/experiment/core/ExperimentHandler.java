package experiment.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import experiment.prompts.ChainOfThought1PromptTemplate;
import experiment.prompts.ChainOfThought2PromptTemplate;
import experiment.prompts.FewShot1PromptTemplate;
import experiment.prompts.FewShot2PromptTemplate;
import experiment.prompts.PromptTemplate;
import experiment.prompts.Panacea1PromptTemplate;
import experiment.prompts.Panacea2PromptTemplate;
import experiment.prompts.PanaceaRun1PromptTemplate;
import experiment.prompts.PanaceaRun2PromptTemplate;
import experiment.prompts.Rag1PromptTemplate;
import experiment.prompts.Rag2PromptTemplate;
import experiment.prompts.ReAct1PromptTemplate;
import experiment.prompts.ReAct2PromptTemplate;
import experiment.rag.EmbeddingClient;
import experiment.rag.RagRetriever;
import experiment.runners.PanaceaRunner;
import experiment.runners.RagRunner;
import experiment.runners.ReActRunner;
import experiment.runners.SimpleRunner;
import experiment.llm.LlmClient;
import experiment.validation.ChainOfThought2ResponseValidator;
import experiment.validation.ChainOfThoughtResponseValidator;
import experiment.validation.FewShot2ResponseValidator;
import experiment.validation.FewShotResponseValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ExperimentHandler {

    private final DataHandler dataHandler;

    public ExperimentHandler() {
        this(new DataHandler());
    }

    ExperimentHandler(DataHandler dataHandler) {
        this.dataHandler = dataHandler;
    }

    public PromptTemplate selectPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be empty.");
        }

        return switch (prompt.trim().toLowerCase()) {
            case "fs", "fs1", "few-shot", "few_shot" -> new FewShot1PromptTemplate();
            case "fs2" -> new FewShot2PromptTemplate();
            case "cot", "cot1" -> new ChainOfThought1PromptTemplate();
            case "cot2" -> new ChainOfThought2PromptTemplate();
            case "rag", "rag1" -> new Rag1PromptTemplate();
            case "rag2" -> new Rag2PromptTemplate();
            case "react", "react1" -> new ReAct1PromptTemplate();
            case "react2" -> new ReAct2PromptTemplate();
            case "panacea", "panacea1" -> new Panacea1PromptTemplate();
            case "panacea2" -> new Panacea2PromptTemplate();
            case "panacea_run1", "panacea-run1" -> new PanaceaRun1PromptTemplate();
            case "panacea_run2", "panacea-run2" -> new PanaceaRun2PromptTemplate();
            default -> throw new IllegalArgumentException("Unsupported prompt: " + prompt);
        };
    }

    public List<JsonNode> runPromptExperiment(
            PromptTemplate promptTemplate,
            Path requirementFile,
            Path outputFile,
            LlmClient llmClient
    ) {
        return runPromptExperiment(
                promptTemplate,
                requirementFile,
                outputFile,
                llmClient,
                Path.of("results", "mistral_panacea1_report.json")
        );
    }

    public List<JsonNode> runPromptExperiment(
            PromptTemplate promptTemplate,
            Path requirementFile,
            Path outputFile,
            LlmClient llmClient,
            Path panaceaPromptReport
    ) {
        List<RequirementInput> requirements = dataHandler.readRequirements(requirementFile);
        if (promptTemplate instanceof FewShot1PromptTemplate) {
            FewShot1PromptTemplate template = new FewShot1PromptTemplate();
            FewShotResponseValidator validator = new FewShotResponseValidator();
            return new SimpleRunner(template.getPromptStyle(), SimpleRunner.Scope.SOURCE,
                    template::buildPrompt, null, (raw, target) -> validator.validate(raw), null, llmClient)
                    .run(requirements, outputFile);
        }
        if (promptTemplate instanceof FewShot2PromptTemplate) {
            FewShot2PromptTemplate template = new FewShot2PromptTemplate();
            FewShot2ResponseValidator validator = new FewShot2ResponseValidator();
            return new SimpleRunner(template.getPromptStyle(), SimpleRunner.Scope.ALLOCATION,
                    template::buildPrompt, template::allocationDescription, validator::validate, null, llmClient)
                    .run(requirements, outputFile);
        }
        if (promptTemplate instanceof ChainOfThought1PromptTemplate) {
            ChainOfThought1PromptTemplate template = new ChainOfThought1PromptTemplate();
            ChainOfThoughtResponseValidator validator = new ChainOfThoughtResponseValidator();
            return new SimpleRunner(template.getPromptStyle(), SimpleRunner.Scope.HLR,
                    template::buildPrompt, null, (raw, target) -> validator.validate(raw), null, llmClient)
                    .run(requirements, outputFile);
        }
        if (promptTemplate instanceof ChainOfThought2PromptTemplate) {
            ChainOfThought2PromptTemplate template = new ChainOfThought2PromptTemplate();
            ChainOfThought2ResponseValidator validator = new ChainOfThought2ResponseValidator();
            return new SimpleRunner(template.getPromptStyle(), SimpleRunner.Scope.ALLOCATION,
                    template::buildPrompt, template::allocationDescription, validator::validate, null, llmClient)
                    .run(requirements, outputFile);
        }
        if (promptTemplate instanceof ReAct1PromptTemplate) {
            ReAct1PromptTemplate template = new ReAct1PromptTemplate();
            FewShotResponseValidator validator = new FewShotResponseValidator();
            return new ReActRunner(template.getPromptStyle(), SimpleRunner.Scope.HLR,
                    template::buildPrompt, template::buildAssessmentPrompt, null, template::isoCriteria,
                    (raw, target) -> validator.validate(raw), llmClient).run(requirements, outputFile);
        }
        if (promptTemplate instanceof ReAct2PromptTemplate) {
            ReAct2PromptTemplate template = new ReAct2PromptTemplate();
            FewShot2ResponseValidator validator = new FewShot2ResponseValidator();
            return new ReActRunner(template.getPromptStyle(), SimpleRunner.Scope.ALLOCATION,
                    template::buildPrompt, template::buildAssessmentPrompt, template::allocationDescription,
                    template::isoCriteria, validator::validate, llmClient).run(requirements, outputFile);
        }
        if (promptTemplate instanceof Panacea1PromptTemplate) {
            Panacea1PromptTemplate template = new Panacea1PromptTemplate();
            List<PanaceaRequirementInput> examples = dataHandler.readPanaceaRequirements(requirementFile);
            FewShotResponseValidator validator = new FewShotResponseValidator();
            return new PanaceaRunner(llmClient).runHlr(template.getPromptStyle(), template.initialPrompt(),
                    template::buildGenerationPrompt, (raw, target) -> validator.validate(raw), examples, outputFile);
        }
        if (promptTemplate instanceof Panacea2PromptTemplate) {
            Panacea2PromptTemplate template = new Panacea2PromptTemplate();
            List<PanaceaAllocationRequirementInput> examples =
                    dataHandler.readPanaceaAllocationRequirements(requirementFile);
            FewShot2ResponseValidator validator = new FewShot2ResponseValidator();
            return new PanaceaRunner(llmClient).runAllocation(template.getPromptStyle(), template.initialPrompt(),
                    template::buildGenerationPrompt, template::allocationDescription, validator::validate,
                    examples, outputFile);
        }
        if (promptTemplate instanceof PanaceaRun1PromptTemplate) {
            PanaceaRun1PromptTemplate template = new PanaceaRun1PromptTemplate();
            String optimizedPrompt = readFinalPrompt(panaceaPromptReport, "Panacea", List.of(PanaceaRun1PromptTemplate.HLR_PLACEHOLDER));
            FewShotResponseValidator validator = new FewShotResponseValidator();
            return new SimpleRunner(template.getPromptStyle(), SimpleRunner.Scope.HLR,
                    input -> template.buildPrompt(input, optimizedPrompt), null,
                    (raw, target) -> validator.validate(raw),
                    (result, c) -> result.put("optimized_prompt_source", panaceaPromptReport.toString()),
                    llmClient).run(requirements, outputFile);
        }
        if (promptTemplate instanceof PanaceaRun2PromptTemplate) {
            PanaceaRun2PromptTemplate template = new PanaceaRun2PromptTemplate();
            String optimizedPrompt = readFinalPrompt(panaceaPromptReport, "Panacea2", List.of(
                    PanaceaRun2PromptTemplate.HLR_PLACEHOLDER,
                    PanaceaRun2PromptTemplate.TARGET_ALLOCATION_PLACEHOLDER,
                    PanaceaRun2PromptTemplate.TARGET_SUBSYSTEM_PLACEHOLDER
            ));
            FewShot2ResponseValidator validator = new FewShot2ResponseValidator();
            return new SimpleRunner(template.getPromptStyle(), SimpleRunner.Scope.ALLOCATION,
                    input -> template.buildPrompt(input, optimizedPrompt), template::allocationDescription,
                    validator::validate,
                    (result, c) -> result.put("optimized_prompt_source", panaceaPromptReport.toString()),
                    llmClient).run(requirements, outputFile);
        }
        throw new IllegalArgumentException(
                "Prompt is not a supported experiment: " + promptTemplate.getPromptStyle()
        );
    }

    public List<JsonNode> runPromptExperimentDataset(
            PromptTemplate promptTemplate,
            Path datasetsDirectory,
            String datasetName,
            Path outputFile,
            LlmClient llmClient
    ) {
        return runPromptExperimentDataset(
                promptTemplate,
                datasetsDirectory,
                datasetName,
                outputFile,
                llmClient,
                Path.of("results", "mistral_panacea1_report.json")
        );
    }

    public List<JsonNode> runPromptExperimentDataset(
            PromptTemplate promptTemplate,
            Path datasetsDirectory,
            String datasetName,
            Path outputFile,
            LlmClient llmClient,
            Path panaceaPromptReport
    ) {
        Path requirementFile = dataHandler.resolveRequirementFile(datasetsDirectory, datasetName);
        return runPromptExperiment(promptTemplate, requirementFile, outputFile, llmClient, panaceaPromptReport);
    }

    public List<JsonNode> runRagExperimentDataset(
            PromptTemplate promptTemplate,
            Path datasetsDirectory,
            String datasetName,
            Path outputFile,
            Path indexFile,
            EmbeddingClient embeddingClient,
            LlmClient llmClient,
            int topK
    ) {
        Path requirementFile = dataHandler.resolveRequirementFile(datasetsDirectory, datasetName);
        List<RequirementInput> requirements = dataHandler.readRequirements(requirementFile);
        RagRetriever retriever = new RagRetriever(indexFile, embeddingClient);
        if (promptTemplate instanceof Rag1PromptTemplate) {
            Rag1PromptTemplate template = new Rag1PromptTemplate();
            FewShotResponseValidator validator = new FewShotResponseValidator();
            return new RagRunner(
                    template.getPromptStyle(),
                    SimpleRunner.Scope.HLR,
                    template::buildPrompt,
                    null,
                    (raw, target) -> validator.validate(raw),
                    retriever,
                    llmClient,
                    topK,
                    embeddingClient.modelName()
            ).run(requirements, outputFile);
        }
        if (promptTemplate instanceof Rag2PromptTemplate) {
            Rag2PromptTemplate template = new Rag2PromptTemplate();
            FewShot2ResponseValidator validator = new FewShot2ResponseValidator();
            return new RagRunner(
                    template.getPromptStyle(),
                    SimpleRunner.Scope.ALLOCATION,
                    template::buildPrompt,
                    template::allocationDescription,
                    validator::validate,
                    retriever,
                    llmClient,
                    topK,
                    embeddingClient.modelName()
            ).run(requirements, outputFile);
        }
        throw new IllegalArgumentException(
                "Prompt is not a supported RAG experiment: " + promptTemplate.getPromptStyle()
        );
    }

    private static String readFinalPrompt(Path reportFile, String label, List<String> requiredPlaceholders) {
        if (reportFile == null) {
            throw new IllegalArgumentException(label + " prompt report cannot be null.");
        }
        if (!Files.isRegularFile(reportFile)) {
            throw new IllegalArgumentException(label + " prompt report does not exist: " + reportFile);
        }
        try {
            String finalPrompt = new ObjectMapper().readTree(reportFile.toFile()).path("final_prompt").asText();
            if (finalPrompt.isBlank()) {
                throw new IllegalArgumentException(label + " prompt report is missing a non-blank final_prompt: " + reportFile);
            }
            for (String placeholder : requiredPlaceholders) {
                if (!finalPrompt.contains(placeholder)) {
                    throw new IllegalArgumentException(label + " final_prompt must contain " + placeholder + ".");
                }
            }
            return finalPrompt;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + label + " prompt report: " + reportFile, exception);
        }
    }
}
