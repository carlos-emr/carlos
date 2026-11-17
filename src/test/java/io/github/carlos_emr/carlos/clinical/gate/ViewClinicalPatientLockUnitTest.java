/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("security")
class ViewClinicalPatientLockUnitTest extends CarlosUnitTestBase {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo user;

    @BeforeEach void prepare() {
        registerMock(SecurityInfoManager.class, security);
        request = new MockHttpServletRequest(); request.setMethod("GET");
        response = new MockHttpServletResponse(); user = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        when(security.hasPrivilege(user, "_eChart", "r", (String) null)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 10)).thenReturn(true);
    }

    private String execute() throws Exception {
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            return new ViewClinical2Action().execute();
        }
    }

    @ParameterizedTest @ValueSource(strings={"demographic_no", "demographicNo", "demographicId", "demographicID", "demoNo"})
    void requestedOpenPatientRetainsView(String alias) throws Exception {
        request.setParameter(alias, "10");
        assertThat(execute()).isEqualTo("success");
        verify(security).isAllowedAccessToPatientRecord(user, 10);
    }

    @ParameterizedTest @ValueSource(strings={"GET", "HEAD"})
    void lockedPatientCannotPassSharedReadGate(String method) {
        request.setMethod(method); request.setParameter("demographic_no", "11");
        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);
    }

    @Test void unboundClinicalShellRemainsAvailable() throws Exception {
        assertThat(execute()).isEqualTo("success");
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test void conflictingPatientAliasesCannotChooseTheOpenPatient() {
        request.setParameter("demographicNo", "10"); request.setParameter("demographic_no", "11");
        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test void conflictingRepeatedPatientAliasCannotChooseTheOpenPatient() {
        request.setParameter("demographic_no", "10", "11");
        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);
    }

    @Test void equalNormalizedAliasesRemainAvailable() throws Exception {
        request.setParameter("demographicNo", "010"); request.setParameter("demographic_no", "10");
        assertThat(execute()).isEqualTo("success");
    }

    @Test void fullPositiveIntegerBoundaryIsAccepted() throws Exception {
        request.setParameter("demographic_no", "2147483647");
        when(security.isAllowedAccessToPatientRecord(user, Integer.MAX_VALUE)).thenReturn(true);
        assertThat(execute()).isEqualTo("success");
    }

    @ParameterizedTest @ValueSource(strings={"", "0", "-1", "2147483648", "10 OR 1=1", " 10"})
    void malformedPatientDoesNotBecomeAnUnboundShell(String value) {
        request.setParameter("demographic_no", value);
        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest @ValueSource(strings={"POST", "PUT", "DELETE"})
    void nonReadMethodsStillFailBeforePatientChecks(String method) throws Exception {
        request.setMethod(method); request.setParameter("demographic_no", "10");
        assertThat(execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }
}
