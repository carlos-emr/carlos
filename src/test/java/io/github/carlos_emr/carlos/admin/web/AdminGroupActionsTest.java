/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression coverage for schedule provider-group creation gates.
 *
 * @since 2026-05-21
 */
@DisplayName("Admin group action security gates")
@Tag("unit")
@Tag("admin")
@Tag("security")
class AdminGroupActionsTest extends CarlosWebTestBase {

    private static final String SCHEDULE_GROUP_CREATE_OBJECT = "_admin.schedule.groupCreate";

    @Test
    @DisplayName("should return success when group create privilege is granted")
    void shouldReturnSuccess_whenGroupCreatePrivilegeGranted() throws Exception {
        assertThat(executeAction(new AdminNewGroup2Action())).isEqualTo(ActionSupport.SUCCESS);

        verifySecurityCheck(SCHEDULE_GROUP_CREATE_OBJECT, "w");
    }

    @Test
    @DisplayName("should reject view when group create privilege is missing")
    void shouldRejectView_whenGroupCreatePrivilegeMissing() {
        denyPrivilege(SCHEDULE_GROUP_CREATE_OBJECT, "w");

        assertThatThrownBy(() -> executeAction(new AdminNewGroup2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining(SCHEDULE_GROUP_CREATE_OBJECT);
    }

    @Test
    @DisplayName("should reject save when group create privilege is missing")
    void shouldRejectSave_whenGroupCreatePrivilegeMissing() {
        denyPrivilege(SCHEDULE_GROUP_CREATE_OBJECT, "w");
        mockRequest.setMethod("POST");

        assertThatThrownBy(() -> executeAction(new AdminSaveMyGroup2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining(SCHEDULE_GROUP_CREATE_OBJECT);
    }

    @Test
    @DisplayName("should return success when saving with POST and group create privilege")
    void shouldReturnSuccess_whenSavingWithPost() throws Exception {
        mockRequest.setMethod("POST");

        assertThat(executeAction(new AdminSaveMyGroup2Action())).isEqualTo(ActionSupport.SUCCESS);

        verifySecurityCheck(SCHEDULE_GROUP_CREATE_OBJECT, "w");
    }

    @Test
    @DisplayName("should return 405 when saving with GET")
    void shouldReturn405_whenSavingWithGet() throws Exception {
        mockRequest.setMethod("GET");

        assertThat(executeAction(new AdminSaveMyGroup2Action())).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);

        // Security is checked before method validation so unauthorized callers do not learn route behavior.
        verifySecurityCheck(SCHEDULE_GROUP_CREATE_OBJECT, "w");
    }
}
