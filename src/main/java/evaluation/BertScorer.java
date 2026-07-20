package evaluation;

import java.util.List;

public interface BertScorer extends AutoCloseable {

    BertScoreResult score(String candidate, List<String> references);

    @Override
    default void close() {
        // Most test or alternative implementations do not own resources.
    }
}
