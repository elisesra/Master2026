package experiment.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRagIndexTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void indexingIsIdempotentAndOnlyReembedsChangedDocuments() throws Exception {
        Path documents = temporaryDirectory.resolve("documents");
        Files.createDirectories(documents);
        Files.writeString(documents.resolve("cas.txt"), "CAS configures operational rules.");
        Files.writeString(documents.resolve("qchs.txt"), "QChS checks observation quality.");
        Path index = temporaryDirectory.resolve("rag.db");
        CountingEmbeddingClient client = new CountingEmbeddingClient("test-embedding", 3);
        RagIndexer indexer = new RagIndexer(new RagChunker(300, 30), client);

        RagIndexSummary first = indexer.index(documents, index);
        int firstEmbeddedTexts = client.textsEmbedded.get();
        RagIndexSummary second = indexer.index(documents, index);
        Files.writeString(documents.resolve("cas.txt"), "CAS configures updated operational rules.");
        RagIndexSummary third = indexer.index(documents, index);

        assertEquals(2, first.addedOrChangedDocuments());
        assertEquals(0, second.addedOrChangedDocuments());
        assertEquals(0, second.embeddedChunks());
        assertEquals(1, third.addedOrChangedDocuments());
        assertTrue(client.textsEmbedded.get() > firstEmbeddedTexts);
        assertEquals(2, third.totalChunks());
    }

    @Test
    void removesMissingDocumentsAndRejectsAnIncompatibleQueryModel() throws Exception {
        Path documents = temporaryDirectory.resolve("documents");
        Files.createDirectories(documents);
        Files.writeString(documents.resolve("one.txt"), "first reference");
        Files.writeString(documents.resolve("two.txt"), "second reference");
        Path index = temporaryDirectory.resolve("rag.db");
        RagIndexer indexer = new RagIndexer(
                new RagChunker(300, 30),
                new CountingEmbeddingClient("model-a", 3)
        );
        indexer.index(documents, index);
        Files.delete(documents.resolve("two.txt"));

        RagIndexSummary summary = indexer.index(documents, index);

        assertEquals(1, summary.removedDocuments());
        assertEquals(1, summary.totalChunks());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RagRetriever(index, new CountingEmbeddingClient("model-b", 3))
        );
    }

    @Test
    void retrievesTheMostSimilarChunkFirst() throws Exception {
        Path documents = temporaryDirectory.resolve("documents");
        Files.createDirectories(documents);
        Files.writeString(documents.resolve("configuration.txt"), "CAS configuration administration");
        Files.writeString(documents.resolve("quality.txt"), "QChS quality checking observations");
        Path index = temporaryDirectory.resolve("rag.db");
        CountingEmbeddingClient client = new CountingEmbeddingClient("test-embedding", 3);
        new RagIndexer(new RagChunker(300, 30), client).index(documents, index);

        List<RetrievedRagChunk> results = new RagRetriever(index, client)
                .retrieve("quality checking", 1);

        assertEquals(1, results.size());
        assertEquals("quality.txt", results.get(0).source());
    }

    @Test
    void retrievesFromLegacyChunkEmbeddingDatabase() throws Exception {
        Path index = temporaryDirectory.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + index);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE chunks(input_file, text, vector)");
            statement.executeUpdate("""
                    INSERT INTO chunks(input_file, text, vector)
                    VALUES('/old/path/configuration.txt', 'CAS configuration administration', '[0.0,1.0,0.25]')
                    """);
            statement.executeUpdate("""
                    INSERT INTO chunks(input_file, text, vector)
                    VALUES('/old/path/quality.txt', 'QChS quality checking observations', '[1.0,0.0,0.25]')
                    """);
        }

        CountingEmbeddingClient client = new CountingEmbeddingClient("test-embedding", 3);
        RagRetriever retriever = new RagRetriever(index, client);

        List<RetrievedRagChunk> results = retriever.retrieve("quality checking", 1);

        assertEquals(1, results.size());
        assertEquals("quality.txt", results.get(0).source());
        assertEquals("test-embedding", retriever.indexInfo().embeddingModel());
        assertEquals("legacy-context-embedding", retriever.indexInfo().chunker());
    }

    static final class CountingEmbeddingClient implements EmbeddingClient {
        private final String model;
        private final int dimensions;
        private final AtomicInteger textsEmbedded = new AtomicInteger();

        CountingEmbeddingClient(String model, int dimensions) {
            this.model = model;
            this.dimensions = dimensions;
        }

        @Override
        public String modelName() {
            return model;
        }

        @Override
        public List<double[]> embed(List<String> texts) {
            textsEmbedded.addAndGet(texts.size());
            List<double[]> vectors = new ArrayList<>();
            for (String text : texts) {
                String lower = text.toLowerCase();
                double[] vector = new double[dimensions];
                vector[0] = lower.contains("quality") || lower.contains("qchs") ? 1 : 0;
                vector[1] = lower.contains("configuration") || lower.contains("cas") ? 1 : 0;
                vector[2] = 0.25;
                vectors.add(vector);
            }
            return vectors;
        }
    }
}
