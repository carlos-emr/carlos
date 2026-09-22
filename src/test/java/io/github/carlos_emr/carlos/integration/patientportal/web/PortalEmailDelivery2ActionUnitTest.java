/** Copyright (c) 2026 CARLOS Contributors. Published under the GPL GNU General Public License. */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.integration.patientportal.PortalEmailDelivery;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Date;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("security")
class PortalEmailDelivery2ActionUnitTest extends CarlosUnitTestBase {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final PortalEmailDelivery delivery = mock(PortalEmailDelivery.class);
    private final LoggedInInfo user = new LoggedInInfo();
    private final EmailLog stored = new EmailLog();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockedBeans.put(SecurityInfoManager.class, security);
        mockedBeans.put(PortalEmailDelivery.class, delivery);
        when(security.hasPrivilege(user, "_email", SecurityInfoManager.READ, null)).thenReturn(true);
        when(delivery.findForRecovery(user, 45)).thenReturn(stored);
    }

    @Test void shouldShowStateWithoutRecovering_forGet() throws Exception {
        execute("GET", "45");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("emailLog")).isSameAs(stored);
        assertThat(request.getAttribute("portalRecoveryTooRecent")).isEqualTo(false);
        verify(delivery, never()).recover(any(), anyInt(), any(), anyBoolean());
    }

    @Test void shouldFlagRecentPendingSend_forGet() throws Exception {
        stored.setStatus(EmailLog.EmailStatus.PENDING);
        stored.setTimestamp(new Date());
        execute("GET", "45");
        assertThat(request.getAttribute("portalRecoveryTooRecent")).isEqualTo(true);
    }

    @Test void shouldDispatchToRecovery_forPost() throws Exception {
        execute("POST", "45");
        assertThat(response.getStatus()).isEqualTo(200);
        verify(delivery).recover(user, 45, "confirmSent", true);
    }

    @Test void shouldRejectRequest_whenEmailReadPrivilegeIsMissing() throws Exception {
        when(security.hasPrivilege(user, "_email", SecurityInfoManager.READ, null)).thenReturn(false);
        execute("POST", "45");
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(delivery);
    }

    @Test void shouldReturnForbidden_whenRecoveryDeniesPatientAccess() throws Exception {
        when(delivery.recover(user, 45, "confirmSent", true)).thenThrow(new SecurityException("denied"));
        execute("POST", "45");
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test void shouldAdvertiseAllowedMethods_whenMethodIsUnsupported() throws Exception {
        execute("DELETE", "45");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, POST");
        verifyNoInteractions(delivery);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "0", "-3"})
    void shouldReturnBadRequest_forInvalidEmailLogId(String id) throws Exception {
        execute("POST", id);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(delivery);
    }

    @Test void shouldReturnBadRequest_whenEmailLogIdIsMissing() throws Exception {
        execute("POST", null);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(delivery);
    }

    @Test void shouldReturnBadRequest_whenRecoveryRejectsTheOperation() throws Exception {
        when(delivery.recover(user, 45, "confirmSent", true)).thenThrow(new IllegalArgumentException("not yet"));
        execute("POST", "45");
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test void shouldReportUnavailableWithoutDetail_whenPortalFails() throws Exception {
        when(delivery.recover(user, 45, "confirmSent", true)).thenThrow(new IllegalStateException("portal outage secret=x"));
        execute("POST", "45");
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(request.getAttribute("portalRecoveryError")).isEqualTo(true);
        assertThat(response.getContentAsString()).doesNotContain("secret");
    }

    private void execute(String method, String emailLogId) throws Exception {
        request = new MockHttpServletRequest(method, "/email/portalDelivery");
        if (emailLogId != null) request.setParameter("emailLogId", emailLogId);
        request.setParameter("operation", "confirmSent");
        request.setParameter("confirmed", "true");
        response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class); var sessions = mockStatic(LoggedInInfo.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
            new PortalEmailDelivery2Action().execute();
        }
    }
}
