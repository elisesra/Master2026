package experiment.rag;

public record RagIndexSummary(
        int documents,
        int addedOrChangedDocuments,
        int removedDocuments,
        int embeddedChunks,
        int totalChunks,
        int embeddingDimensions,
        String embeddingModel
) {
}
