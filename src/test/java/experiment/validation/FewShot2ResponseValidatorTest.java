package experiment.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FewShot2ResponseValidatorTest {

    @Test
    void acceptsOnlyTheTargetAllocation() {
        FewShot2ResponseValidator validator = new FewShot2ResponseValidator();

        assertDoesNotThrow(() -> validator.validate(responseFor("CAS"), "CAS"));
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(responseFor("SS"), "CAS")
        );
        assertEquals(
                "fs2 response contains allocation SS but the target allocation is CAS.",
                exception.getMessage()
        );
    }

    private static String responseFor(String allocation) {
        return "{\"low_level_requirements\":[{\"allocation\":\"" + allocation
                + "\",\"requirement\":\"The subsystem shall process observations.\"}]}";
    }
}
