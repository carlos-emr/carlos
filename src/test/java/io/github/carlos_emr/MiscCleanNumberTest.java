package io.github.carlos_emr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MiscCleanNumberTest {

    @Test
    public void testCleanNumber() {
        assertEquals("123456", Misc.cleanNumber("abc123def456!@#"));
        assertEquals("123", Misc.cleanNumber("123"));
        assertEquals("0", Misc.cleanNumber("abc"));
        assertEquals("0", Misc.cleanNumber(""));
        assertEquals("0", Misc.cleanNumber(null));
        assertEquals("123", Misc.cleanNumber(" 1 2 3 "));
    }
}
