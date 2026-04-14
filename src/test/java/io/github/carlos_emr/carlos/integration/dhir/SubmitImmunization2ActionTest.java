/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.dhir;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SubmitImmunization2Action}. Verifies the privilege
 * check + null-session guard that this action adds on top of the struts
 * {@code httpMethod} POST-only interceptor, gating the DHIR (Digital Health
 * Immunization Registry) submission flow that forwards to
 * {@code /WEB-INF/jsp/prevention/dhirSubmission.jsp}.
 *
 * @since 2026-04-14
 */
@DisplayName("SubmitImmunization2Action")
@Tag("unit")
@Tag("prevention")
class SubmitImmunization2ActionTest extends CarlosWebTestBase {

    @Test
    @DisplayName("should return SUCCESS when _prevention write privilege granted")
    void shouldReturnSuccess_whenWritePrivilegeGranted() throws Exception {
        allowPrivilege("_prevention", "w");
        assertThat(executeAction(new SubmitImmunization2Action()))
                .isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should throw SecurityException when _prevention write privilege denied")
    void shouldThrowSecurityException_whenWritePrivilegeDenied() {
        denyPrivilege("_prevention", "w");
        assertThatThrownBy(() -> executeAction(new SubmitImmunization2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_prevention");
    }

    @Test
    @DisplayName("should throw SecurityException when session has no LoggedInInfo")
    void shouldThrowSecurityException_whenSessionMissingLoggedInInfo() {
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        mockSession.removeAttribute(loggedInInfoKey);

        assertThatThrownBy(() -> executeAction(new SubmitImmunization2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("authenticated session");
    }
}
