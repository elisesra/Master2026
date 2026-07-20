package experiment.rag;

public record RagIndexInfo(
        String embeddingModel,
        int embeddingDimensions,
        String chunker,
        String corpusFingerprint
) {
}
