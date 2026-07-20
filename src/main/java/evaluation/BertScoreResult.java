package evaluation;

public record BertScoreResult(double precision, double recall, double f1) {

    public static BertScoreResult zero() {
        return new BertScoreResult(0, 0, 0);
    }
}
