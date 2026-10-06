/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.gate;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ViewFlowSheetPrint2ActionTest extends CarlosWebTestBase {
    @BeforeEach
    void denyByDefault() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
        mockRequest.setMethod("POST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "POST"})
    void shouldAllowSelectionAndPreview_whenBothReadPrivilegesAreGranted(String method) throws Exception {
        mockRequest.setMethod(method);
        allowPrivilege("_eChart", "r");
        allowPrivilege("_flowsheet", "r");
        assertThat(executeAction(new ViewFlowSheetPrint2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_eChart", "r");
        verifySecurityCheck("_flowsheet", "r");
    }

    @Test
    void shouldRefusePreview_whenChartReadIsMissing() {
        allowPrivilege("_flowsheet", "r");
        assertThatThrownBy(() -> executeAction(new ViewFlowSheetPrint2Action()))
                .isInstanceOf(SecurityException.class).hasMessageContaining("_eChart");
    }

    @Test
    void shouldRefusePreview_whenFlowsheetReadIsMissing() {
        allowPrivilege("_eChart", "r");
        assertThatThrownBy(() -> executeAction(new ViewFlowSheetPrint2Action()))
                .isInstanceOf(SecurityException.class).hasMessageContaining("_flowsheet");
    }

    @Test
    void shouldRefusePreview_whenSessionIsAbsent() {
        setSessionAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", null);
        assertThatThrownBy(() -> executeAction(new ViewFlowSheetPrint2Action()))
                .isInstanceOf(SecurityException.class).hasMessageContaining("_eChart");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE", "PATCH"})
    void shouldRejectUnsupportedMethods_withoutRenderingTheJsp(String method) throws Exception {
        mockRequest.setMethod(method);
        assertThat(executeAction(new ViewFlowSheetPrint2Action())).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("GET, HEAD, POST");
    }
}
