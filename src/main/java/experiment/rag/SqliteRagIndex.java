package experiment.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SqliteRagIndex {

    private static final String LEGACY_CHUNKER = "legacy-context-embedding";

    private final Path databasePath;
    private final ObjectMapper objectMapper;

    public SqliteRagIndex(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("RAG index path cannot be null.");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
        if (this.databasePath.toString().contains("/datasets/")) {
            throw new IllegalArgumentException("RAG index must never be written under datasets/.");
        }
        this.objectMapper = new ObjectMapper();
    }

    public RagIndexSummary synchronize(
            List<RagDocument> documents,
            RagChunker chunker,
            EmbeddingClient embeddingClient
    ) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("At least one RAG document is required.");
        }

        createParentDirectory();
        try (Connection connection = open()) {
            if (isLegacyIndex(connection)) {
                throw new IllegalArgumentException(
                        "Cannot write a Java-format RAG index into the legacy chunk embedding database: "
                                + databasePath
                                + ". Use this file for retrieval, or choose a fresh --rag-index path for indexing."
                );
            }
            initializeSchema(connection);
            String currentModel = metadata(connection, "embedding_model");
            String currentChunker = metadata(connection, "chunker");
            int currentDimensions = parseDimensions(metadata(connection, "embedding_dimensions"));
            boolean rebuild = currentModel != null
                    && (!currentModel.equals(embeddingClient.modelName())
                    || !chunker.configurationId().equals(currentChunker)
                    || embeddingClient.requestedDimensions().stream()
                    .anyMatch(requested -> requested != currentDimensions));

            Map<String, String> existingDocuments = loadDocumentHashes(connection);
            Map<String, String> indexedDocuments = rebuild ? Map.of() : existingDocuments;
            Set<String> incomingSources = documents.stream()
                    .map(RagDocument::source)
                    .collect(java.util.stream.Collectors.toSet());
            int removed = (int) existingDocuments.keySet().stream()
                    .filter(source -> !incomingSources.contains(source))
                    .count();

            List<RagDocument> changed = documents.stream()
                    .filter(document -> !document.contentHash().equals(indexedDocuments.get(document.source())))
                    .toList();
            List<EmbeddedRagChunk> embedded = embedChanged(changed, chunker, embeddingClient);
            int dimensions = embedded.isEmpty()
                    ? parseDimensions(metadata(connection, "embedding_dimensions"))
                    : embedded.get(0).vector().length;

            connection.setAutoCommit(false);
            try {
                if (rebuild) {
                    clearIndex(connection);
                } else {
                    deleteMissingSources(connection, incomingSources);
                }
                replaceChangedDocuments(connection, changed, embedded, embeddingClient.modelName());
                putMetadata(connection, "embedding_model", embeddingClient.modelName());
                putMetadata(connection, "embedding_dimensions", Integer.toString(dimensions));
                putMetadata(connection, "chunker", chunker.configurationId());
                putMetadata(
                        connection,
                        "corpus_fingerprint",
                        corpusFingerprint(documents, embeddingClient.modelName(), chunker.configurationId())
                );
                putMetadata(connection, "updated_at", Instant.now().toString());
                connection.commit();
            } catch (RuntimeException | SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }

            return new RagIndexSummary(
                    documents.size(),
                    changed.size(),
                    removed,
                    embedded.size(),
                    countChunks(connection),
                    dimensions,
                    embeddingClient.modelName()
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update RAG index: " + databasePath, exception);
        }
    }

    public List<EmbeddedRagChunk> loadChunks(String expectedEmbeddingModel) {
        if (!Files.isRegularFile(databasePath)) {
            throw new IllegalArgumentException("RAG index does not exist: " + databasePath);
        }
        try (Connection connection = open()) {
            if (isLegacyIndex(connection)) {
                return loadLegacyChunks(connection);
            }
            initializeSchema(connection);
            String indexedModel = metadata(connection, "embedding_model");
            if (indexedModel == null) {
                throw new IllegalStateException("RAG index has no embedding model metadata.");
            }
            if (!indexedModel.equals(expectedEmbeddingModel)) {
                throw new IllegalArgumentException(
                        "RAG index uses embedding model " + indexedModel
                                + " but the query client uses " + expectedEmbeddingModel + "."
                );
            }

            List<EmbeddedRagChunk> chunks = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, source, document_hash, chunk_index, text, vector "
                            + "FROM chunks ORDER BY source, chunk_index"
            ); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    chunks.add(new EmbeddedRagChunk(
                            rows.getString("id"),
                            rows.getString("source"),
                            rows.getString("document_hash"),
                            rows.getInt("chunk_index"),
                            rows.getString("text"),
                            parseVector(rows.getString("vector"))
                    ));
                }
            }
            if (chunks.isEmpty()) {
                throw new IllegalStateException("RAG index contains no chunks: " + databasePath);
            }
            return List.copyOf(chunks);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load RAG index: " + databasePath, exception);
        }
    }

    public RagIndexInfo loadInfo(String expectedEmbeddingModel) {
        if (!Files.isRegularFile(databasePath)) {
            throw new IllegalArgumentException("RAG index does not exist: " + databasePath);
        }
        try (Connection connection = open()) {
            if (isLegacyIndex(connection)) {
                return loadLegacyInfo(connection, expectedEmbeddingModel);
            }
            initializeSchema(connection);
            String model = metadata(connection, "embedding_model");
            if (model == null || !model.equals(expectedEmbeddingModel)) {
                throw new IllegalArgumentException(
                        "RAG index embedding model does not match: " + expectedEmbeddingModel
                );
            }
            String chunker = metadata(connection, "chunker");
            String fingerprint = metadata(connection, "corpus_fingerprint");
            if (chunker == null || fingerprint == null) {
                throw new IllegalStateException("RAG index is missing reproducibility metadata.");
            }
            return new RagIndexInfo(
                    model,
                    parseDimensions(metadata(connection, "embedding_dimensions")),
                    chunker,
                    fingerprint
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load RAG index metadata.", exception);
        }
    }

    private List<EmbeddedRagChunk> loadLegacyChunks(Connection connection) throws SQLException {
        List<EmbeddedRagChunk> chunks = new ArrayList<>();
        Map<String, Integer> chunkIndexesBySource = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT rowid, input_file, text, vector FROM chunks ORDER BY input_file, rowid"
        ); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String source = legacySource(rows.getString("input_file"));
                int chunkIndex = chunkIndexesBySource.merge(source, 1, Integer::sum) - 1;
                String text = rows.getString("text");
                String documentHash = RagDocumentLoader.sha256(source);
                String id = RagDocumentLoader.sha256(
                        "legacy\n" + source + "\n" + rows.getLong("rowid") + "\n" + text
                );
                chunks.add(new EmbeddedRagChunk(
                        id,
                        source,
                        documentHash,
                        chunkIndex,
                        text,
                        parseVector(rows.getString("vector"))
                ));
            }
        }
        if (chunks.isEmpty()) {
            throw new IllegalStateException("RAG index contains no chunks: " + databasePath);
        }
        return List.copyOf(chunks);
    }

    private RagIndexInfo loadLegacyInfo(Connection connection, String expectedEmbeddingModel) throws SQLException {
        return new RagIndexInfo(
                expectedEmbeddingModel,
                legacyDimensions(connection),
                LEGACY_CHUNKER,
                legacyFingerprint(connection)
        );
    }

    private int legacyDimensions(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT vector FROM chunks ORDER BY input_file, rowid LIMIT 1"
        ); ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new IllegalStateException("RAG index contains no chunks: " + databasePath);
            }
            return parseVector(rows.getString("vector")).length;
        }
    }

    private String legacyFingerprint(Connection connection) throws SQLException {
        StringBuilder manifest = new StringBuilder();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT input_file, text, vector FROM chunks ORDER BY input_file, rowid"
        ); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                manifest.append(rows.getString("input_file"))
                        .append('\n')
                        .append(RagDocumentLoader.sha256(rows.getString("text")))
                        .append('\n')
                        .append(RagDocumentLoader.sha256(rows.getString("vector")))
                        .append('\n');
            }
        }
        return RagDocumentLoader.sha256(LEGACY_CHUNKER + "\n" + manifest);
    }

    private static String legacySource(String inputFile) {
        if (inputFile == null || inputFile.isBlank()) {
            return "legacy";
        }
        return Path.of(inputFile).getFileName().toString();
    }

    private List<EmbeddedRagChunk> embedChanged(
            List<RagDocument> changed,
            RagChunker chunker,
            EmbeddingClient embeddingClient
    ) {
        List<RagTextChunk> chunks = changed.stream().flatMap(document -> chunker.chunk(document).stream()).toList();
        if (chunks.isEmpty()) {
            return List.of();
        }
        List<double[]> vectors = embeddingClient.embed(chunks.stream().map(RagTextChunk::text).toList());
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("Embedding client returned the wrong number of vectors.");
        }

        int dimensions = vectors.get(0).length;
        List<EmbeddedRagChunk> result = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            RagTextChunk chunk = chunks.get(index);
            double[] vector = vectors.get(index);
            if (vector.length != dimensions) {
                throw new IllegalStateException("Embedding vectors have inconsistent dimensions.");
            }
            String id = RagDocumentLoader.sha256(
                    embeddingClient.modelName() + "\n" + chunk.source() + "\n"
                            + chunk.documentHash() + "\n" + chunk.chunkIndex() + "\n" + chunk.text()
            );
            result.add(new EmbeddedRagChunk(
                    id,
                    chunk.source(),
                    chunk.documentHash(),
                    chunk.chunkIndex(),
                    chunk.text(),
                    vector
            ));
        }
        return List.copyOf(result);
    }

    private void replaceChangedDocuments(
            Connection connection,
            List<RagDocument> changed,
            List<EmbeddedRagChunk> embedded,
            String embeddingModel
    ) throws SQLException {
        Set<String> changedSources = changed.stream()
                .map(RagDocument::source)
                .collect(java.util.stream.Collectors.toSet());
        try (PreparedStatement deleteChunks = connection.prepareStatement("DELETE FROM chunks WHERE source = ?");
             PreparedStatement deleteDocument = connection.prepareStatement("DELETE FROM documents WHERE source = ?");
             PreparedStatement insertDocument = connection.prepareStatement(
                     "INSERT INTO documents(source, content_hash, indexed_at) VALUES(?, ?, ?)"
             );
             PreparedStatement insertChunk = connection.prepareStatement(
                     "INSERT INTO chunks(id, source, document_hash, chunk_index, text, vector, embedding_model) "
                             + "VALUES(?, ?, ?, ?, ?, ?, ?)"
             )) {
            for (RagDocument document : changed) {
                deleteChunks.setString(1, document.source());
                deleteChunks.executeUpdate();
                deleteDocument.setString(1, document.source());
                deleteDocument.executeUpdate();
                insertDocument.setString(1, document.source());
                insertDocument.setString(2, document.contentHash());
                insertDocument.setString(3, Instant.now().toString());
                insertDocument.executeUpdate();
            }
            for (EmbeddedRagChunk chunk : embedded) {
                if (!changedSources.contains(chunk.source())) {
                    continue;
                }
                insertChunk.setString(1, chunk.id());
                insertChunk.setString(2, chunk.source());
                insertChunk.setString(3, chunk.documentHash());
                insertChunk.setInt(4, chunk.chunkIndex());
                insertChunk.setString(5, chunk.text());
                insertChunk.setString(6, serializeVector(chunk.vector()));
                insertChunk.setString(7, embeddingModel);
                insertChunk.executeUpdate();
            }
        }
    }

    private static void deleteMissingSources(Connection connection, Set<String> incomingSources)
            throws SQLException {
        Set<String> existing = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT source FROM documents")) {
            while (rows.next()) {
                existing.add(rows.getString(1));
            }
        }
        existing.removeAll(incomingSources);
        try (PreparedStatement deleteChunks = connection.prepareStatement("DELETE FROM chunks WHERE source = ?");
             PreparedStatement deleteDocument = connection.prepareStatement("DELETE FROM documents WHERE source = ?")) {
            for (String source : existing) {
                deleteChunks.setString(1, source);
                deleteChunks.executeUpdate();
                deleteDocument.setString(1, source);
                deleteDocument.executeUpdate();
            }
        }
    }

    private static Map<String, String> loadDocumentHashes(Connection connection) throws SQLException {
        Map<String, String> hashes = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT source, content_hash FROM documents")) {
            while (rows.next()) {
                hashes.put(rows.getString(1), rows.getString(2));
            }
        }
        return hashes;
    }

    private static void initializeSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS metadata("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS documents("
                    + "source TEXT PRIMARY KEY, content_hash TEXT NOT NULL, indexed_at TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS chunks("
                    + "id TEXT PRIMARY KEY, source TEXT NOT NULL, document_hash TEXT NOT NULL, "
                    + "chunk_index INTEGER NOT NULL, text TEXT NOT NULL, vector TEXT NOT NULL, "
                    + "embedding_model TEXT NOT NULL, "
                    + "FOREIGN KEY(source) REFERENCES documents(source))");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_chunks_source "
                    + "ON chunks(source, chunk_index)");
        }
    }

    private static boolean isLegacyIndex(Connection connection) throws SQLException {
        Set<String> columns = tableColumns(connection, "chunks");
        return columns.contains("input_file")
                && columns.contains("text")
                && columns.contains("vector")
                && !columns.contains("id")
                && !columns.contains("source");
    }

    private static Set<String> tableColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rows.next()) {
                columns.add(rows.getString("name"));
            }
        }
        return columns;
    }

    private static void clearIndex(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM chunks");
            statement.executeUpdate("DELETE FROM documents");
        }
    }

    private static String metadata(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM metadata WHERE key = ?"
        )) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private static void putMetadata(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO metadata(key, value) VALUES(?, ?) "
                        + "ON CONFLICT(key) DO UPDATE SET value = excluded.value"
        )) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private static int countChunks(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM chunks")) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private void createParentDirectory() {
        try {
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to create RAG index directory.", exception);
        }
    }

    private String serializeVector(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize embedding vector.", exception);
        }
    }

    private double[] parseVector(String value) {
        try {
            return objectMapper.readValue(value, double[].class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("RAG index contains an invalid vector.", exception);
        }
    }

    private static int parseDimensions(String value) {
        return value == null ? 0 : Integer.parseInt(value);
    }

    private static String corpusFingerprint(
            List<RagDocument> documents,
            String embeddingModel,
            String chunker
    ) {
        String manifest = documents.stream()
                .sorted(java.util.Comparator.comparing(RagDocument::source))
                .map(document -> document.source() + "=" + document.contentHash())
                .collect(java.util.stream.Collectors.joining("\n"));
        return RagDocumentLoader.sha256(
                "embedding_model=" + embeddingModel + "\nchunker=" + chunker + "\n" + manifest
        );
    }
}
