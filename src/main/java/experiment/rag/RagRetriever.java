package experiment.rag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RagRetriever {

    private final EmbeddingClient embeddingClient;
    private final List<EmbeddedRagChunk> chunks;
    private final RagIndexInfo indexInfo;

    public RagRetriever(Path indexFile, EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
        SqliteRagIndex index = new SqliteRagIndex(indexFile);
        this.chunks = index.loadChunks(embeddingClient.modelName());
        this.indexInfo = index.loadInfo(embeddingClient.modelName());
    }

    public RagIndexInfo indexInfo() {
        return indexInfo;
    }

    public List<RetrievedRagChunk> retrieve(String query, int topK) {
        return retrieveAll(List.of(query), topK).get(0);
    }

    public List<List<RetrievedRagChunk>> retrieveAll(List<String> queries, int topK) {
        if (queries == null || queries.isEmpty()) {
            throw new IllegalArgumentException("At least one RAG query is required.");
        }
        if (queries.stream().anyMatch(query -> query == null || query.isBlank())) {
            throw new IllegalArgumentException("RAG queries cannot contain blank text.");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("top-k must be positive.");
        }

        List<double[]> queryVectors = embeddingClient.embed(queries);
        if (queryVectors.size() != queries.size()) {
            throw new IllegalStateException("Embedding client returned the wrong number of query vectors.");
        }
        return queryVectors.stream().map(vector -> rank(vector, topK)).toList();
    }

    private List<RetrievedRagChunk> rank(double[] queryVector, int topK) {
        List<ScoredChunk> scored = chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, cosineSimilarity(queryVector, chunk.vector())))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(value -> value.chunk().source())
                        .thenComparingInt(value -> value.chunk().chunkIndex()))
                .toList();

        List<RetrievedRagChunk> result = new ArrayList<>();
        Set<String> seenText = new HashSet<>();
        for (ScoredChunk candidate : scored) {
            String textHash = RagDocumentLoader.sha256(candidate.chunk().text());
            if (!seenText.add(textHash)) {
                continue;
            }
            result.add(new RetrievedRagChunk(
                    result.size() + 1,
                    candidate.chunk().source(),
                    candidate.chunk().chunkIndex(),
                    candidate.score(),
                    candidate.chunk().text()
            ));
            if (result.size() == topK) {
                break;
            }
        }
        return List.copyOf(result);
    }

    static double cosineSimilarity(double[] left, double[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException(
                    "Query vector dimension " + left.length
                            + " does not match index dimension " + right.length + "."
            );
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record ScoredChunk(EmbeddedRagChunk chunk, double score) {
    }
}
