/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Clinic code v1 (mycarlos#141): the shared vectors, which myCarlos and the portal test too. */
@Tag("unit")
class PortalMyCarlosClinicCodeUnitTest {
    private static final String KID = "FAKE-kid-clinic-A-0001";
    private static final String DEVICE = "FAKE-device-key-0001";
    private static final String REQUEST = "0f8fad5b-d9cb-469f-a165-70867728950e";

    private static JsonNode vectors() throws Exception {
        try (var input = PortalMyCarlosClinicCodeUnitTest.class
                .getResourceAsStream("/patientportal/clinic-code-v1-vectors.json")) {
            assertNotNull(input, "shared vectors on the test classpath");
            JsonNode root = new ObjectMapper().readTree(input);
            assertEquals("clinic-code-v1", root.path("version").textValue());
            assertEquals(PortalMyCarlosClinicCode.LABEL, root.path("label").textValue());
            return root.path("vectors");
        }
    }

    @Test
    void shouldMatchEverySharedVector_forCheckCodeAndDisplay() throws Exception {
        JsonNode vectors = vectors();
        assertEquals(5, vectors.size());
        for (JsonNode v : vectors) {
            String kid = v.path("clinic_kid").textValue();
            String device = v.path("device_key_id").textValue();
            String request = v.path("request_id").textValue();
            String codeR = v.path("code_r").textValue();
            String where = kid + " " + device + " " + request + " " + codeR;
            assertEquals(v.path("check_t").textValue(),
                    PortalMyCarlosClinicCode.checkT(kid, device, request, codeR), where);
            String code = PortalMyCarlosClinicCode.code(kid, device, request, codeR);
            assertEquals(v.path("code").textValue(), code, where);
            assertEquals(v.path("display").textValue(), PortalMyCarlosClinicCode.display(code), where);
        }
    }

    @Test
    void shouldMakeSixDigitCodes_withLeadingZerosKept() {
        assertEquals("000000", PortalMyCarlosClinicCode.newCodeR(fixed(0)));
        assertEquals("000042", PortalMyCarlosClinicCode.newCodeR(fixed(42)));
        assertEquals("999999", PortalMyCarlosClinicCode.newCodeR(fixed(999_999)));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String codeR = PortalMyCarlosClinicCode.newCodeR();
            assertTrue(codeR.matches("[0-9]{6}"), codeR);
            seen.add(codeR);
        }
        // 2000 draws from a million: a repeat or two is possible, a constant source is not.
        assertTrue(seen.size() > 1990);
    }

    @Test
    void shouldMatchOnlyTheSameSixDigits_whenComparingCodes() {
        assertTrue(PortalMyCarlosClinicCode.sameCodeR("482193", "482193"));
        assertFalse(PortalMyCarlosClinicCode.sameCodeR("482193", "482194"));
        assertFalse(PortalMyCarlosClinicCode.sameCodeR("482193", "582193"));
        for (String bad : new String[] {null, "", "48219", "4821930", "48219a", " 482193", "482 193", "４８２１９３"}) {
            assertFalse(PortalMyCarlosClinicCode.sameCodeR("482193", bad), String.valueOf(bad));
            assertFalse(PortalMyCarlosClinicCode.sameCodeR(bad, "482193"), String.valueOf(bad));
        }
    }

    @Test
    void shouldRefuseFieldsThatWouldChangeTheInput_forCheck() {
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosClinicCode.checkT(KID + "\n", DEVICE, REQUEST, "482193"));
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosClinicCode.checkT(KID, "", REQUEST, "482193"));
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosClinicCode.checkT(KID, DEVICE, REQUEST.toUpperCase(java.util.Locale.ROOT), "482193"));
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosClinicCode.checkT(KID, DEVICE, "not-a-uuid", "482193"));
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosClinicCode.checkT(KID, DEVICE, REQUEST, "48219"));
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosClinicCode.display("48219364"));
    }

    /** A source whose next draw is the given number, to see the padding. */
    private static SecureRandom fixed(int value) {
        return new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                assertEquals(1_000_000, bound);
                return value;
            }
        };
    }
}
