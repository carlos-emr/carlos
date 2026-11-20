/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The clinic's device approval policy setting (clinic code v1, mycarlos#141). */
@Tag("unit")
class PortalMyCarlosApprovalPolicyUnitTest {
    private static PortalMyCarlosApprovalPolicy policy(String value) {
        Map<String, String> properties = new HashMap<>();
        properties.put(PortalMyCarlosApprovalPolicy.PROPERTY, value);
        return PortalMyCarlosApprovalPolicy.from(properties::get);
    }

    @Test
    void shouldUseCode_whenSettingIsUnsetOrBlank() {
        assertEquals("mycarlos.approval.policy", PortalMyCarlosApprovalPolicy.PROPERTY);
        assertEquals(PortalMyCarlosApprovalPolicy.CODE, policy(null));
        assertEquals(PortalMyCarlosApprovalPolicy.CODE, policy(""));
        assertEquals(PortalMyCarlosApprovalPolicy.CODE, policy("  "));
    }

    @Test
    void shouldReadEachValue_withSurroundingSpace() {
        assertEquals(PortalMyCarlosApprovalPolicy.CODE, policy("code"));
        assertEquals(PortalMyCarlosApprovalPolicy.CODE_AND_STAFF, policy(" code_and_staff "));
        assertEquals(PortalMyCarlosApprovalPolicy.STAFF_ONLY, policy("staff_only"));
        assertTrue(PortalMyCarlosApprovalPolicy.CODE.byCode());
        assertTrue(PortalMyCarlosApprovalPolicy.CODE_AND_STAFF.byCode());
        assertFalse(PortalMyCarlosApprovalPolicy.STAFF_ONLY.byCode());
        assertEquals("code_and_staff", PortalMyCarlosApprovalPolicy.CODE_AND_STAFF.value());
    }

    @Test
    void shouldRefuseAnyOtherValue_forPolicy() {
        for (String bad : new String[] {"CODE", "staff", "email", "code,staff_only"}) {
            assertThrows(PatientPortalConfigurationException.class, () -> policy(bad), bad);
        }
    }
}
