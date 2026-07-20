package experiment.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagChunkerTest {

    @Test
    void keepsParagraphsAndCreatesDeterministicOverlap() {
        RagDocument document = new RagDocument(
                "guide.txt",
                "hash",
                "First paragraph has useful context.\n\n"
                        + "Second paragraph contains additional subsystem details.\n\n"
                        + "Third paragraph provides the final operational rule."
        );

        List<RagTextChunk> chunks = new RagChunker(200, 60).chunk(document);

        assertEquals(1, chunks.size());
        assertEquals("guide.txt", chunks.get(0).source());
        assertTrue(chunks.get(0).text().contains("Second paragraph"));
    }

    @Test
    void splitsOversizedMaterialWithoutDroppingText() {
        String text = "word ".repeat(150);
        List<RagTextChunk> chunks = new RagChunker(200, 30)
                .chunk(new RagDocument("large.txt", "hash", text));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> !chunk.text().isBlank()));
        assertEquals(0, chunks.get(0).chunkIndex());
        assertEquals(chunks.size() - 1, chunks.get(chunks.size() - 1).chunkIndex());
    }
}
