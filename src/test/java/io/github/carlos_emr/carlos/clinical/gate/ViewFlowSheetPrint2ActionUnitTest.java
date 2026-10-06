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

class ViewFlowSheetPrint2ActionUnitTest extends CarlosWebTestBase {
    @BeforeEach
    void denyByDefault() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
        mockRequest.setMethod("POST");
        mockRequest.setParameter("demographic_no", "123");
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(123)))
                .thenReturn(true);
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
        verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_eChart"), eq("r"), eq("123"));
        verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_flowsheet"), eq("r"), eq("123"));
        verify(mockSecurityInfoManager).isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(123));
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
    @ValueSource(strings = {"GET", "HEAD", "POST"})
    void shouldRefuseLockedPatient_evenWithGeneralReadPrivileges(String method) {
        mockRequest.setMethod(method);
        allowPrivilege("_eChart", "r");
        allowPrivilege("_flowsheet", "r");
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(123)))
                .thenReturn(false);
        assertThatThrownBy(() -> executeAction(new ViewFlowSheetPrint2Action()))
                .isInstanceOf(SecurityException.class).hasMessageContaining("patient record");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_eChart", "_flowsheet"})
    void shouldRefusePatientSpecificPrivilegeDenial_evenWithGeneralReadPrivileges(String object) {
        allowPrivilege("_eChart", "r");
        allowPrivilege("_flowsheet", "r");
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq(object), eq("r"), eq("123")))
                .thenReturn(false);
        assertThatThrownBy(() -> executeAction(new ViewFlowSheetPrint2Action()))
                .isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"", "0", "-1", "abc", "2147483648", "00123"})
    void shouldRejectInvalidPatient_withoutCheckingPatientAccess(String patient) throws Exception {
        allowPrivilege("_eChart", "r");
        if (patient == null) mockRequest.removeParameter("demographic_no");
        else mockRequest.setParameter("demographic_no", patient);
        assertThat(executeAction(new ViewFlowSheetPrint2Action())).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verify(mockSecurityInfoManager, never()).isAllowedAccessToPatientRecord(any(), any());
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
