package experiment.rag;

import java.util.ArrayList;
import java.util.List;

public final class RagChunker {

    public static final String VERSION = "paragraph-v1";

    private final int targetCharacters;
    private final int overlapCharacters;

    public RagChunker(int targetCharacters, int overlapCharacters) {
        if (targetCharacters < 200) {
            throw new IllegalArgumentException("Chunk size must be at least 200 characters.");
        }
        if (overlapCharacters < 0 || overlapCharacters >= targetCharacters) {
            throw new IllegalArgumentException("Chunk overlap must be non-negative and smaller than chunk size.");
        }
        this.targetCharacters = targetCharacters;
        this.overlapCharacters = overlapCharacters;
    }

    public List<RagTextChunk> chunk(RagDocument document) {
        List<String> units = paragraphUnits(document.text());
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentLength = 0;

        for (String unit : units) {
            int separator = current.isEmpty() ? 0 : 2;
            if (!current.isEmpty() && currentLength + separator + unit.length() > targetCharacters) {
                chunks.add(String.join("\n\n", current));
                current = overlapTail(current);
                currentLength = joinedLength(current);
            }
            current.add(unit);
            currentLength += (current.size() == 1 ? 0 : 2) + unit.length();
        }
        if (!current.isEmpty()) {
            String finalChunk = String.join("\n\n", current);
            if (chunks.isEmpty() || !chunks.get(chunks.size() - 1).equals(finalChunk)) {
                chunks.add(finalChunk);
            }
        }

        List<RagTextChunk> result = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            result.add(new RagTextChunk(
                    document.source(),
                    document.contentHash(),
                    index,
                    chunks.get(index)
            ));
        }
        return List.copyOf(result);
    }

    public String configurationId() {
        return VERSION + ":" + targetCharacters + ":" + overlapCharacters;
    }

    private List<String> paragraphUnits(String text) {
        List<String> units = new ArrayList<>();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String normalized = paragraph.replaceAll("[\\t ]+", " ").strip();
            if (normalized.isEmpty()) {
                continue;
            }
            units.addAll(splitOversized(normalized));
        }
        return units;
    }

    private List<String> splitOversized(String value) {
        if (value.length() <= targetCharacters) {
            return List.of(value);
        }
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (String word : value.split("\\s+")) {
            if (word.length() > targetCharacters) {
                if (!piece.isEmpty()) {
                    pieces.add(piece.toString());
                    piece.setLength(0);
                }
                for (int start = 0; start < word.length(); start += targetCharacters) {
                    pieces.add(word.substring(start, Math.min(start + targetCharacters, word.length())));
                }
                continue;
            }
            if (!piece.isEmpty() && piece.length() + 1 + word.length() > targetCharacters) {
                pieces.add(piece.toString());
                piece.setLength(0);
            }
            if (!piece.isEmpty()) {
                piece.append(' ');
            }
            piece.append(word);
        }
        if (!piece.isEmpty()) {
            pieces.add(piece.toString());
        }
        return pieces;
    }

    private List<String> overlapTail(List<String> completedChunk) {
        if (overlapCharacters == 0) {
            return new ArrayList<>();
        }
        List<String> tail = new ArrayList<>();
        int length = 0;
        for (int index = completedChunk.size() - 1; index >= 0; index--) {
            String unit = completedChunk.get(index);
            if (tail.isEmpty() && unit.length() > overlapCharacters) {
                tail.add(overlapSuffix(unit));
                break;
            }
            if (!tail.isEmpty() && length + 2 + unit.length() > overlapCharacters) {
                break;
            }
            tail.add(0, unit);
            length += (tail.size() == 1 ? 0 : 2) + unit.length();
            if (length >= overlapCharacters) {
                break;
            }
        }
        return tail;
    }

    private String overlapSuffix(String unit) {
        int start = unit.length() - overlapCharacters;
        int whitespace = unit.indexOf(' ', start);
        return unit.substring(whitespace >= 0 ? whitespace + 1 : start);
    }

    private static int joinedLength(List<String> values) {
        return values.stream().mapToInt(String::length).sum() + Math.max(0, values.size() - 1) * 2;
    }
}
