package experiment.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class RagDocumentLoader {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".txt", ".md", ".markdown", ".rst");

    public List<RagDocument> load(Path documentDirectory) {
        if (documentDirectory == null) {
            throw new IllegalArgumentException("Document directory cannot be null.");
        }
        Path root = documentDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Document directory does not exist: " + root);
        }
        if (root.endsWith("datasets") || root.toString().contains("/datasets/")) {
            throw new IllegalArgumentException(
                    "RAG documents must not be loaded from datasets/. Use a separate read-only corpus."
            );
        }

        try (Stream<Path> paths = Files.walk(root)) {
            List<RagDocument> documents = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isSupported)
                    .sorted()
                    .map(path -> read(root, path))
                    .filter(document -> !document.text().isBlank())
                    .toList();
            if (documents.isEmpty()) {
                throw new IllegalArgumentException(
                        "No supported text documents were found under: " + root
                );
            }
            return documents;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan RAG documents: " + root, exception);
        }
    }

    private boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static RagDocument read(Path root, Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .strip();
            String source = root.relativize(path.toAbsolutePath().normalize()).toString()
                    .replace('\\', '/');
            return new RagDocument(source, sha256(text), text);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read RAG document: " + path, exception);
        }
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
