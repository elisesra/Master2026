package experiment.rag;

import java.util.List;
import java.util.stream.Collectors;

public final class RagContextFormatter {

    private RagContextFormatter() {
    }

    public static String format(List<RetrievedRagChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("Retrieved context cannot be empty.");
        }
        return chunks.stream()
                .map(chunk -> """
                        [Context %d | source=%s | chunk=%d | similarity=%.6f]
                        %s
                        """.formatted(
                        chunk.rank(),
                        chunk.source(),
                        chunk.chunkIndex(),
                        chunk.similarity(),
                        chunk.text()
                ).strip())
                .collect(Collectors.joining("\n\n"));
    }
}
