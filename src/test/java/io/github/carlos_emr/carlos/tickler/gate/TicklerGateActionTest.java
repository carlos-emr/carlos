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
package io.github.carlos_emr.carlos.tickler.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

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
 * Integration tests for the 6 tickler view-gate actions. Each gate must throw
 * {@link SecurityException} when its required {@code _tickler} privilege is
 * missing and return {@code success} when granted. A regression here would let
 * an authenticated provider without tickler rights render a tickler JSP
 * directly. The default privilege stub is flipped to deny-all so allow-tests
 * prove the specific grant drove success rather than coasting on the base-class
 * permissive stub.
 *
 * @since 2026-04-13
 */
@DisplayName("Tickler gate 2Action privilege tests")
@Tag("integration")
@Tag("tickler")
class TicklerGateActionTest extends CarlosWebTestBase {

    @BeforeEach
    void setUpGate() {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        // Flip base-class default (allow-all) to deny-all so allow-tests must
        // explicitly stub the privilege they rely on.
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);
    }

    @Test
    @DisplayName("ViewIndex should deny when _tickler r missing")
    void shouldDenyViewIndex_whenReadMissing() {
        assertThatThrownBy(() -> executeAction(new ViewIndex2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewIndex should return success when _tickler r granted")
    void shouldAllowViewIndex_whenReadGranted() throws Exception {
        allowPrivilege("_tickler", "r");
        assertThat(executeAction(new ViewIndex2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "r");
    }

    @Test
    @DisplayName("ViewTicklerMain should deny when _tickler r missing")
    void shouldDenyViewTicklerMain_whenReadMissing() {
        assertThatThrownBy(() -> executeAction(new ViewTicklerMain2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewTicklerMain should return success when _tickler r granted")
    void shouldAllowViewTicklerMain_whenReadGranted() throws Exception {
        allowPrivilege("_tickler", "r");
        assertThat(executeAction(new ViewTicklerMain2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "r");
    }

    @Test
    @DisplayName("ViewTicklerDemoMain should deny when _tickler w missing")
    void shouldDenyViewTicklerDemoMain_whenWriteMissing() {
        assertThatThrownBy(() -> executeAction(new ViewTicklerDemoMain2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewTicklerDemoMain should return success when _tickler w granted")
    void shouldAllowViewTicklerDemoMain_whenWriteGranted() throws Exception {
        allowPrivilege("_tickler", "w");
        assertThat(executeAction(new ViewTicklerDemoMain2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "w");
    }

    @Test
    @DisplayName("ViewTicklerEdit should deny when _tickler w missing")
    void shouldDenyViewTicklerEdit_whenWriteMissing() {
        assertThatThrownBy(() -> executeAction(new ViewTicklerEdit2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewTicklerEdit should return success when _tickler w granted")
    void shouldAllowViewTicklerEdit_whenWriteGranted() throws Exception {
        allowPrivilege("_tickler", "w");
        assertThat(executeAction(new ViewTicklerEdit2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "w");
    }

    @Test
    @DisplayName("ViewTicklerSuggestedText should deny when _tickler w missing")
    void shouldDenyViewTicklerSuggestedText_whenWriteMissing() {
        assertThatThrownBy(() -> executeAction(new ViewTicklerSuggestedText2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewTicklerSuggestedText should return success when _tickler w granted")
    void shouldAllowViewTicklerSuggestedText_whenWriteGranted() throws Exception {
        allowPrivilege("_tickler", "w");
        assertThat(executeAction(new ViewTicklerSuggestedText2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "w");
    }

    @Test
    @DisplayName("ViewAddTickler should deny when _tickler w missing")
    void shouldDenyViewAddTickler_whenWriteMissing() {
        assertThatThrownBy(() -> executeAction(new ViewAddTickler2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewAddTickler should return success when _tickler w granted")
    void shouldAllowViewAddTickler_whenWriteGranted() throws Exception {
        allowPrivilege("_tickler", "w");
        assertThat(executeAction(new ViewAddTickler2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "w");
    }

    /**
     * With the deny-all default stub, a cleared {@code LoggedInInfo} session
     * attribute causes {@code hasPrivilege(null, ...)} to return false, so the
     * gate still throws {@link SecurityException} rather than a raw
     * {@link NullPointerException}. One read-family and one write-family gate
     * cover the shared code path; the other 4 gates are identical at this seam.
     */
    @Test
    @DisplayName("ViewTicklerMain should throw SecurityException when session is empty")
    void shouldDenyViewTicklerMain_whenSessionEmpty() {
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, null);

        assertThatThrownBy(() -> executeAction(new ViewTicklerMain2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewAddTickler should throw SecurityException when session is empty")
    void shouldDenyViewAddTickler_whenSessionEmpty() {
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, null);

        assertThatThrownBy(() -> executeAction(new ViewAddTickler2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }
}
