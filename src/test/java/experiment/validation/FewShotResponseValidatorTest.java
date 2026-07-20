package experiment.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FewShotResponseValidatorTest {

    @Test
    void rejectsNonJsonAndIncompleteRequirements() {
        FewShotResponseValidator validator = new FewShotResponseValidator();

        assertThrows(IllegalArgumentException.class, () -> validator.validate("not json"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "{\"low_level_requirements\":[{\"allocation\":\"CS\"}]}"
        ));
    }

    @Test
    void acceptsJsonInsideMarkdownCodeFence() {
        FewShotResponseValidator validator = new FewShotResponseValidator();

        assertDoesNotThrow(() -> validator.validate("""
                ```json
                {"low_level_requirements":[{"allocation":"CS","requirement":"The CS shall process observations."}]}
                ```
                """));
    }
}
