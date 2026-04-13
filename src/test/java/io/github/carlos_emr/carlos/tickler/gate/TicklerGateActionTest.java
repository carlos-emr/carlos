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
import org.mockito.MockitoAnnotations;

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
        MockitoAnnotations.openMocks(this);
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
    void viewIndex_deniesWhenReadMissing() {
        assertThatThrownBy(() -> executeAction(new ViewIndex2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewIndex should return success when _tickler r granted")
    void viewIndex_successWhenReadGranted() throws Exception {
        allowPrivilege("_tickler", "r");
        assertThat(executeAction(new ViewIndex2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "r");
    }

    @Test
    @DisplayName("ViewTicklerMain should deny when _tickler r missing")
    void viewTicklerMain_deniesWhenReadMissing() {
        assertThatThrownBy(() -> executeAction(new ViewTicklerMain2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewTicklerMain should return success when _tickler r granted")
    void viewTicklerMain_successWhenReadGranted() throws Exception {
        allowPrivilege("_tickler", "r");
        assertThat(executeAction(new ViewTicklerMain2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "r");
    }

    @Test
    @DisplayName("ViewTicklerDemoMain should deny when _tickler w missing")
    void viewTicklerDemoMain_deniesWhenWriteMissing() {
        assertThatThrownBy(() -> executeAction(new ViewTicklerDemoMain2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewTicklerDemoMain should return success when _tickler w granted")
    void viewTicklerDemoMain_successWhenWriteGranted() throws Exception {
        allowPrivilege("_tickler", "w");
        assertThat(executeAction(new ViewTicklerDemoMain2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "w");
    }

    @Test
    @DisplayName("ViewTicklerEdit should deny when _tickler w missing")
    void viewTicklerEdit_deniesWhenWriteMissing() {
        assertThatThrownBy(() -> executeAction(new ViewTicklerEdit2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewTicklerEdit should return success when _tickler w granted")
    void viewTicklerEdit_successWhenWriteGranted() throws Exception {
        allowPrivilege("_tickler", "w");
        assertThat(executeAction(new ViewTicklerEdit2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "w");
    }

    @Test
    @DisplayName("ViewTicklerSuggestedText should deny when _tickler w missing")
    void viewTicklerSuggestedText_deniesWhenWriteMissing() {
        assertThatThrownBy(() -> executeAction(new ViewTicklerSuggestedText2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewTicklerSuggestedText should return success when _tickler w granted")
    void viewTicklerSuggestedText_successWhenWriteGranted() throws Exception {
        allowPrivilege("_tickler", "w");
        assertThat(executeAction(new ViewTicklerSuggestedText2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "w");
    }

    @Test
    @DisplayName("ViewAddTickler should deny when _tickler w missing")
    void viewAddTickler_deniesWhenWriteMissing() {
        assertThatThrownBy(() -> executeAction(new ViewAddTickler2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_tickler");
    }

    @Test
    @DisplayName("ViewAddTickler should return success when _tickler w granted")
    void viewAddTickler_successWhenWriteGranted() throws Exception {
        allowPrivilege("_tickler", "w");
        assertThat(executeAction(new ViewAddTickler2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_tickler", "w");
    }
}
