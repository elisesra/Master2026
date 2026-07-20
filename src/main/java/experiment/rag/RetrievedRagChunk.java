package experiment.rag;

public record RetrievedRagChunk(
        int rank,
        String source,
        int chunkIndex,
        double similarity,
        String text
) {
}
