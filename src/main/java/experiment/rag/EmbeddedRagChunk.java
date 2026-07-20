package experiment.rag;

record EmbeddedRagChunk(
        String id,
        String source,
        String documentHash,
        int chunkIndex,
        String text,
        double[] vector
) {
}
