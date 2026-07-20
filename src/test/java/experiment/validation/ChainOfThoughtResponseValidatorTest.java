package experiment.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChainOfThoughtResponseValidatorTest {

    @Test
    void validatesStructureAndCot2TargetAllocation() {
        ChainOfThoughtResponseValidator baseValidator = new ChainOfThoughtResponseValidator();
        ChainOfThought2ResponseValidator cot2Validator = new ChainOfThought2ResponseValidator();

        assertDoesNotThrow(() -> baseValidator.validate(responseFor("CAS")));
        assertThrows(IllegalArgumentException.class, () -> baseValidator.validate("not json"));
        assertThrows(IllegalArgumentException.class, () -> baseValidator.validate("Here is the JSON:\n"
                + responseFor("CAS")));
        assertDoesNotThrow(() -> cot2Validator.validate(responseFor("CAS"), "CAS"));
        assertDoesNotThrow(() -> cot2Validator.validate("""
                ```
                {"low_level_requirements":[{"allocation":"CAS","requirement":"The subsystem shall process observations."}]}
                ```
                """, "CAS"));
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cot2Validator.validate(responseFor("SS"), "CAS")
        );
        assertEquals(
                "cot2 response contains allocation SS but the target allocation is CAS.",
                exception.getMessage()
        );
    }

    private static String responseFor(String allocation) {
        return "{\"low_level_requirements\":[{\"allocation\":\"" + allocation
                + "\",\"requirement\":\"The subsystem shall process observations.\"}]}";
    }
}
