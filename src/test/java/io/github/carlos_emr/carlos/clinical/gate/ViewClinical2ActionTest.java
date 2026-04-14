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
package io.github.carlos_emr.carlos.clinical.gate;

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
 * Privilege and HTTP-method contract tests for the shared
 * {@link ViewClinical2Action}. This single class is the authorization gate for
 * 129 action mappings in {@code struts-clinical.xml} (+ a few in
 * {@code struts-encounter.xml}) covering 137 relocated encounter/casemgmt
 * JSPs. A regression here would either (a) expose every gated JSP to
 * authenticated users without {@code _eChart r}, or (b) accept POST mutations
 * against a JSP that was moved behind a read-only gate.
 *
 * <p>Mirrors the structure of {@code TicklerGateActionTest} (PR #1670) and
 * flips the base-class permissive {@code hasPrivilege} stub to deny-all so
 * allow-tests must explicitly grant the specific privilege.
 *
 * @since 2026-04-14
 */
@DisplayName("ViewClinical2Action privilege + method tests")
@Tag("integration")
@Tag("clinical")
class ViewClinical2ActionTest extends CarlosWebTestBase {

    @BeforeEach
    void setUpGate() {
        // Deny-all default so each allow-test must stub the specific privilege
        // it relies on. Without this, the base class's permissive default would
        // let a bugged gate that checks the wrong privilege pass.
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
        mockRequest.setMethod("GET");
    }

    @Test
    @DisplayName("should deny when _eChart r is missing")
    void shouldDeny_whenEChartReadMissing() {
        assertThatThrownBy(() -> executeAction(new ViewClinical2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eChart");
    }

    @Test
    @DisplayName("should return success when _eChart r is granted")
    void shouldAllow_whenEChartReadGranted() throws Exception {
        allowPrivilege("_eChart", "r");
        assertThat(executeAction(new ViewClinical2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_eChart", "r");
    }

    /**
     * A future refactor that drops the explicit {@code loggedInInfo == null}
     * guard could NPE inside {@code SecurityInfoManager#hasPrivilege} and
     * swallow the {@code SecurityException} contract global exception mapping
     * relies on. This test pins the behavior.
     */
    @Test
    @DisplayName("should throw SecurityException when session has no LoggedInInfo")
    void shouldDeny_whenSessionEmpty() {
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, null);

        assertThatThrownBy(() -> executeAction(new ViewClinical2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eChart");
    }

    /**
     * {@code _eChart r} alone is insufficient if another refactor widens the
     * gate. Grant an unrelated privilege ({@code _admin r}) and verify the
     * gate still denies — proves the gate reads the literal {@code "_eChart"}
     * and doesn't pass through any non-null LoggedInInfo.
     */
    @Test
    @DisplayName("should deny when an unrelated privilege is granted")
    void shouldDeny_whenOnlyUnrelatedPrivilegeGranted() {
        allowPrivilege("_admin", "r");

        assertThatThrownBy(() -> executeAction(new ViewClinical2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eChart");
    }

    @Test
    @DisplayName("should return 405 and not check privilege on POST")
    void shouldReturn405_onPost() throws Exception {
        allowPrivilege("_eChart", "r");
        mockRequest.setMethod("POST");

        String result = executeAction(new ViewClinical2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).contains("GET");
    }

    @Test
    @DisplayName("should return 405 on PUT")
    void shouldReturn405_onPut() throws Exception {
        allowPrivilege("_eChart", "r");
        mockRequest.setMethod("PUT");

        String result = executeAction(new ViewClinical2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("should return 405 on DELETE")
    void shouldReturn405_onDelete() throws Exception {
        allowPrivilege("_eChart", "r");
        mockRequest.setMethod("DELETE");

        String result = executeAction(new ViewClinical2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("should allow HEAD just like GET")
    void shouldAllow_onHead() throws Exception {
        allowPrivilege("_eChart", "r");
        mockRequest.setMethod("HEAD");

        assertThat(executeAction(new ViewClinical2Action())).isEqualTo(ActionSupport.SUCCESS);
    }
}
