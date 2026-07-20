package experiment.rag;

import java.nio.file.Path;
import java.util.List;

public final class RagIndexer {

    private final RagDocumentLoader documentLoader;
    private final RagChunker chunker;
    private final EmbeddingClient embeddingClient;

    public RagIndexer(RagChunker chunker, EmbeddingClient embeddingClient) {
        this(new RagDocumentLoader(), chunker, embeddingClient);
    }

    RagIndexer(
            RagDocumentLoader documentLoader,
            RagChunker chunker,
            EmbeddingClient embeddingClient
    ) {
        this.documentLoader = documentLoader;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
    }

    public RagIndexSummary index(Path documentDirectory, Path indexFile) {
        List<RagDocument> documents = documentLoader.load(documentDirectory);
        return new SqliteRagIndex(indexFile).synchronize(documents, chunker, embeddingClient);
    }
}
