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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

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
 * HTTP method and privilege contract tests for measurement submission writes.
 *
 * @since 2026-05-05
 */
@DisplayName("EctMeasurements2Action - HTTP method and privilege contract tests")
@Tag("integration")
@Tag("clinical")
class EctMeasurements2ActionTest extends CarlosWebTestBase {

    @BeforeEach
    void setUpAction() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
        mockRequest.setMethod("POST");
        mockSession.setAttribute("user", "999998");
    }

    @Test
    @DisplayName("should deny when _measurement write privilege is missing")
    void shouldThrowException_whenMeasurementWriteMissing() {
        assertThatThrownBy(() -> executeAction(new EctMeasurements2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_measurement");
    }

    @Test
    @DisplayName("should return 405 on GET before reading measurement params")
    void shouldReturn405_onGet() throws Exception {
        allowPrivilege("_measurement", "w");
        mockRequest.setMethod("GET");

        String result = executeAction(new EctMeasurements2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    @DisplayName("should return 405 on HEAD before reading measurement params")
    void shouldReturn405_onHead() throws Exception {
        allowPrivilege("_measurement", "w");
        mockRequest.setMethod("HEAD");

        String result = executeAction(new EctMeasurements2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    @DisplayName("should allow minimal POST and clear encounter text session stash")
    void shouldAllowPost_whenMeasurementWriteGranted() throws Exception {
        allowPrivilege("_measurement", "w");
        mockSession.setAttribute("textOnEncounter", "stale");
        addRequestParameter("demographicNo", "1");
        addRequestParameter("numType", "0");
        addRequestParameter("skipCreateNote", "true");

        String result = executeAction(new EctMeasurements2Action());

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockSession.getAttribute("textOnEncounter")).isNull();
    }
}
