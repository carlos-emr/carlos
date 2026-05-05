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
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * HTTP method and privilege contract tests for flowsheet customization writes.
 *
 * @since 2026-05-05
 */
@DisplayName("FlowSheetCustom2Action - HTTP method and privilege contract tests")
@Tag("integration")
@Tag("clinical")
class FlowSheetCustom2ActionTest extends CarlosWebTestBase {

    @BeforeEach
    void setUpAction() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
        mockRequest.setMethod("POST");
    }

    @Test
    @DisplayName("should deny when _flowsheet w is missing")
    void shouldDeny_whenFlowsheetWriteMissing() {
        assertThatThrownBy(() -> executeAction(new FlowSheetCustom2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_flowsheet");
    }

    @Test
    @DisplayName("should return 405 on GET before dispatching mutator methods")
    void shouldReturn405_onGet() throws Exception {
        allowPrivilege("_flowsheet", "w");
        mockRequest.setMethod("GET");
        addRequestParameter("method", "save");

        String result = executeAction(new FlowSheetCustom2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    @DisplayName("should allow POST when _flowsheet w is granted")
    void shouldAllowPost_whenFlowsheetWriteGranted() throws Exception {
        allowPrivilege("_flowsheet", "w");

        String result = executeAction(new FlowSheetCustom2Action());

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }
}
