package experiment.crossvalidating;

public enum CrossValidationMode {
    BLIND("blind"),
    GROUND_TRUTH("ground_truth");

    private final String filePart;

    CrossValidationMode(String filePart) {
        this.filePart = filePart;
    }

    public String filePart() {
        return filePart;
    }
}
