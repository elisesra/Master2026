package experiment.rag;

import java.util.List;
import java.util.OptionalInt;

public interface EmbeddingClient {

    String modelName();

    default OptionalInt requestedDimensions() {
        return OptionalInt.empty();
    }

    List<double[]> embed(List<String> texts);

    default double[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }
}
