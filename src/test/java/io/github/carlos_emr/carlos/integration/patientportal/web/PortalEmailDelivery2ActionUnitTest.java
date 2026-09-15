/** Copyright (c) 2026 CARLOS Contributors. Published under the GPL GNU General Public License. */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.integration.patientportal.PortalEmailDelivery;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("security")
class PortalEmailDelivery2ActionUnitTest extends CarlosUnitTestBase {
    @Test void shouldNeverMutateOnGetEvenWithRecoveryParameters() throws Exception { check("GET", true, false, 200); }
    @Test void shouldDispatchPostToRecovery() throws Exception { check("POST", true, false, 200); }
    @Test void shouldRejectMissingEmailReadPrivilege() throws Exception { check("POST", false, false, 403); }
    @Test void shouldRejectPatientAccessDeniedDuringRecovery() throws Exception { check("POST", true, true, 403); }
    @Test void shouldRejectUnsupportedMethod() throws Exception { check("DELETE", true, false, 405); }

    private void check(String method, boolean allowed, boolean patientDenied, int expectedStatus) throws Exception {
        var request = new MockHttpServletRequest(method, "/email/portalDelivery");
        request.setParameter("emailLogId", "45"); request.setParameter("operation", "confirmSent");
        request.setParameter("confirmed", "true");
        var response = new MockHttpServletResponse();
        var security = mock(SecurityInfoManager.class); var delivery = mock(PortalEmailDelivery.class);
        mockedBeans.put(SecurityInfoManager.class, security); mockedBeans.put(PortalEmailDelivery.class, delivery);
        var user = new LoggedInInfo();
        when(security.hasPrivilege(user, "_email", SecurityInfoManager.READ, null)).thenReturn(allowed);
        when(delivery.findForRecovery(user, 45)).thenReturn(new EmailLog());
        if (patientDenied) when(delivery.recover(user,45,"confirmSent",true)).thenThrow(new SecurityException("denied"));
        try (var servlet = mockStatic(ServletActionContext.class); var sessions = mockStatic(LoggedInInfo.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
            new PortalEmailDelivery2Action().execute();
        }
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        if ("GET".equals(method) || !allowed || "DELETE".equals(method)) {
            verify(delivery, never()).recover(any(), anyInt(), any(), anyBoolean());
        } else { verify(delivery).recover(user,45,"confirmSent",true); }
    }
}
