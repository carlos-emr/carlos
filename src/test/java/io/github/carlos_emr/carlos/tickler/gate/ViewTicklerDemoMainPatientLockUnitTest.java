/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.tickler.gate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.demographic.PatientReadAccess;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises {@code GET /tickler/ViewTicklerDemoMain?demoview=<id>}: a patient-specific record
 * lock must stop the per-patient tickler list before the view renders, while unlocked patients
 * and the page's existing "no patient" selections (absent, empty, {@code all}, {@code 0}) behave
 * exactly as before.
 */
@Tag("unit")
@Tag("security")
@Tag("tickler")
class ViewTicklerDemoMainPatientLockUnitTest extends CarlosUnitTestBase {
    private SecurityInfoManager security;
    private TicklerManager ticklers;
    private DemographicDao demographics;
    private LoggedInInfo user;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;

    @BeforeEach
    void setUpGate() {
        security = mock(SecurityInfoManager.class);
        ticklers = mock(TicklerManager.class);
        demographics = mock(DemographicDao.class);
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(user), anyString(), anyString(), (String) isNull())).thenReturn(true);
        when(security.hasPrivilege(eq(user), anyString(), anyString(), anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(user), anyInt())).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        registerMock(TicklerManager.class, ticklers);
        registerMock(DemographicDao.class, demographics);
        request = new MockHttpServletRequest("GET", "/tickler/ViewTicklerDemoMain");
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
    }

    @AfterEach
    void closeStatics() {
        sessions.close();
        servlet.close();
    }

    private String execute() throws Exception {
        return new ViewTicklerDemoMain2Action().execute();
    }

    private void lockPatientSeven() {
        when(security.isAllowedAccessToPatientRecord(user, 7)).thenReturn(false);
        when(security.getSecurityObjects(user)).thenReturn(
                List.of(new Secobjprivilege("doctor", "_eChart$7", "|o|", 0, "999998")));
    }

    private void assertNothingRendered() {
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(request.getAttribute(PatientReadAccess.AUTHORIZED_PATIENT_ATTRIBUTE)).isNull();
        verifyNoInteractions(ticklers, demographics);
    }

    @Test
    void shouldForwardWithAuthorizedPatient_whenDemoviewPatientIsUnlocked() throws Exception {
        request.setParameter("demoview", "8");

        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute(PatientReadAccess.AUTHORIZED_PATIENT_ATTRIBUTE)).isEqualTo("8");
        verify(security).hasPrivilege(user, "_tickler", "w", null);
        verify(security).isAllowedAccessToPatientRecord(user, 8);
        verify(security).hasPrivilege(user, "_tickler", SecurityInfoManager.READ, "8");
    }

    @Test
    void shouldForwardCanonicalPatient_whenDemoviewHasLeadingZeros() throws Exception {
        request.setParameter("demoview", "0008");

        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute(PatientReadAccess.AUTHORIZED_PATIENT_ATTRIBUTE)).isEqualTo("8");
        verify(security).isAllowedAccessToPatientRecord(user, 8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseTicklerList_whenDemoviewPatientIsLocked(String method) {
        request.setMethod(method);
        request.setParameter("demoview", "7");
        lockPatientSeven();

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);

        assertNothingRendered();
    }

    @Test
    void shouldRefuseTicklerList_whenPatientScopedTicklerReadIsDenied() {
        request.setParameter("demoview", "7");
        when(security.hasPrivilege(user, "_tickler", SecurityInfoManager.READ, "7")).thenReturn(false);

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);

        assertNothingRendered();
    }

    @Test
    void shouldForwardWithoutPatient_whenDemoviewIsAbsent() throws Exception {
        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute(PatientReadAccess.AUTHORIZED_PATIENT_ATTRIBUTE)).isNull();
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "all", "0", "00"})
    void shouldForwardWithoutPatient_whenDemoviewCarriesNoPatientContext(String demoview) throws Exception {
        request.setParameter("demoview", demoview);
        lockPatientSeven();

        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute(PatientReadAccess.AUTHORIZED_PATIENT_ATTRIBUTE)).isNull();
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-7", "+7", " 7", "7.0", "abc", "ALL", "null", "2147483648", "99999999999999999999"})
    void shouldReturnBadRequest_whenDemoviewIsMalformed(String demoview) throws Exception {
        request.setParameter("demoview", demoview);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertNothingRendered();
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test
    void shouldReturnBadRequest_whenRepeatedDemoviewValuesConflict() throws Exception {
        request.setParameter("demoview", "8", "7");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertNothingRendered();
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test
    void shouldReturnBadRequest_whenNoPatientSelectionIsPairedWithAPatient() throws Exception {
        request.setParameter("demoview", "all", "7");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test
    void shouldCheckGlobalTicklerWriteFirst_whenDemoviewIsMalformed() {
        request.setParameter("demoview", "not-a-patient");
        when(security.hasPrivilege(user, "_tickler", "w", null)).thenReturn(false);

        assertThatThrownBy(this::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_tickler)");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test
    void shouldQueryOnlyTheGateAuthorizedPatient_forTicklerDemoMainJsp() throws Exception {
        String jsp = Files.readString(Path.of(System.getProperty("user.dir"),
                "src/main/webapp/WEB-INF/jsp/tickler/ticklerDemoMain.jsp"));
        int authorized = jsp.indexOf("request.getAttribute(PatientReadAccess.AUTHORIZED_PATIENT_ATTRIBUTE)");
        int search = jsp.indexOf("ticklerManager.search_tickler_bydemo(loggedInInfo, demoViewDemographicNo,");
        int addTickler = jsp.indexOf("demographicDao.getDemographicById(demoViewDemographicNo)");

        assertThat(authorized).isPositive();
        assertThat(search).isGreaterThan(authorized);
        assertThat(addTickler).isGreaterThan(authorized);
        assertThat(jsp).doesNotContain("Integer.parseInt(demoViewParam)");
    }
}
