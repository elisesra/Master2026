package evaluation;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Token-level, unscaled BERTScore using contextual embeddings from an actual BERT model. */
public final class BertScoreEvaluator implements BertScorer {

    private static final int MAX_TOKENS = 64;

    public static final String MODEL_NAME = "sentence-transformers/bert-base-nli-mean-tokens";
    public static final Path DEFAULT_MODEL = Path.of(
            "storage", "models", "bert-base-nli-mean-tokens", "model.onnx"
    );
    public static final Path DEFAULT_TOKENIZER = Path.of(
            "storage", "models", "bert-base-nli-mean-tokens", "tokenizer.json"
    );

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Path modelPath;
    private final HuggingFaceTokenizer tokenizer;
    private final Map<String, float[][]> embeddingCache = new HashMap<>();

    public BertScoreEvaluator() {
        this(DEFAULT_MODEL, DEFAULT_TOKENIZER);
    }

    public BertScoreEvaluator(Path modelPath, Path tokenizerPath) {
        if (!Files.isRegularFile(modelPath) || !Files.isRegularFile(tokenizerPath)) {
            throw new IllegalStateException(
                    "BERT model assets are missing. Expected " + modelPath + " and " + tokenizerPath
            );
        }
        this.modelPath = modelPath;
        try {
            environment = OrtEnvironment.getEnvironment();
            session = createSession();
            tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
        } catch (OrtException | IOException exception) {
            throw new IllegalStateException("Failed to load BERT model assets.", exception);
        }
    }

    @Override
    public BertScoreResult score(String candidate, List<String> references) {
        if (candidate == null || candidate.isBlank() || references == null || references.isEmpty()) {
            return BertScoreResult.zero();
        }
        // Candidates are generally unique, so retaining them would grow memory with the corpus.
        // Ground-truth references repeat heavily and are the only embeddings worth caching.
        float[][] candidateEmbeddings = infer(candidate);
        BertScoreResult best = BertScoreResult.zero();
        for (String reference : references) {
            if (reference == null || reference.isBlank()) {
                continue;
            }
            BertScoreResult current = calculate(candidateEmbeddings, referenceEmbeddings(reference));
            if (current.f1() > best.f1()) {
                best = current;
            }
        }
        return best;
    }

    private float[][] referenceEmbeddings(String text) {
        return embeddingCache.computeIfAbsent(text, this::infer);
    }

    private float[][] infer(String text) {
        Encoding encoding = tokenizer.encode(text);
        long[] ids = fixedLength(encoding.getIds(), 0);
        long[] attention = fixedLength(encoding.getAttentionMask(), 0);
        long[] typeIds = fixedLength(encoding.getTypeIds(), 0);
        long[] specialTokens = fixedLength(encoding.getSpecialTokenMask(), 1);
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            if (session.getInputNames().contains("input_ids")) {
                inputs.put("input_ids", OnnxTensor.createTensor(environment, new long[][]{ids}));
            }
            if (session.getInputNames().contains("attention_mask")) {
                inputs.put("attention_mask", OnnxTensor.createTensor(environment, new long[][]{attention}));
            }
            if (session.getInputNames().contains("token_type_ids")) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(environment, new long[][]{typeIds}));
            }
            try (OrtSession.Result result = session.run(inputs)) {
                Object value = result.get(0).getValue();
                if (!(value instanceof float[][][] hiddenStates)) {
                    throw new IllegalStateException("BERT model did not return token-level hidden states.");
                }
                return normalizeAndRemoveSpecialTokens(hiddenStates[0], specialTokens, attention);
            }
        } catch (OrtException exception) {
            throw new IllegalStateException("BERT inference failed.", exception);
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    private OrtSession createSession() throws OrtException {
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setMemoryPatternOptimization(false);
            options.setCPUArenaAllocator(false);
            options.setIntraOpNumThreads(2);
            return environment.createSession(modelPath.toString(), options);
        }
    }

    private static long[] fixedLength(long[] source, long paddingValue) {
        long[] result = new long[MAX_TOKENS];
        if (paddingValue != 0) {
            java.util.Arrays.fill(result, paddingValue);
        }
        System.arraycopy(source, 0, result, 0, Math.min(source.length, MAX_TOKENS));
        return result;
    }

    private static float[][] normalizeAndRemoveSpecialTokens(
            float[][] embeddings,
            long[] specialTokenMask,
            long[] attentionMask
    ) {
        int included = 0;
        for (int index = 0; index < embeddings.length; index++) {
            if (attentionMask[index] == 1 && specialTokenMask[index] == 0) {
                included++;
            }
        }
        float[][] result = new float[included][];
        int target = 0;
        for (int index = 0; index < embeddings.length; index++) {
            if (attentionMask[index] != 1 || specialTokenMask[index] != 0) {
                continue;
            }
            float[] vector = embeddings[index].clone();
            double norm = 0;
            for (float component : vector) {
                norm += component * component;
            }
            double divisor = Math.sqrt(norm);
            if (divisor > 0) {
                for (int component = 0; component < vector.length; component++) {
                    vector[component] /= (float) divisor;
                }
            }
            result[target++] = vector;
        }
        return result;
    }

    static BertScoreResult calculate(float[][] candidate, float[][] reference) {
        if (candidate.length == 0 || reference.length == 0) {
            return BertScoreResult.zero();
        }
        double precision = averageMaximumSimilarity(candidate, reference);
        double recall = averageMaximumSimilarity(reference, candidate);
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        return new BertScoreResult(round4(precision), round4(recall), round4(f1));
    }

    private static double averageMaximumSimilarity(float[][] source, float[][] target) {
        double total = 0;
        for (float[] sourceToken : source) {
            double maximum = -1;
            for (float[] targetToken : target) {
                maximum = Math.max(maximum, dot(sourceToken, targetToken));
            }
            total += maximum;
        }
        return total / source.length;
    }

    private static double dot(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("BERT embedding dimensions do not match.");
        }
        double result = 0;
        for (int index = 0; index < left.length; index++) {
            result += left[index] * right[index];
        }
        return result;
    }

    private static double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    @Override
    public void close() {
        tokenizer.close();
        try {
            session.close();
        } catch (OrtException exception) {
            throw new IllegalStateException("Failed to close BERT session.", exception);
        }
        embeddingCache.clear();
    }
}
