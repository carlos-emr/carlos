/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.email.admin;

import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class SaveClinicEmailFooter2ActionUnitTest {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final ClinicEmailFooterService footers = mock(ClinicEmailFooterService.class);
    private final SaveClinicEmailFooter2Action action = new SaveClinicEmailFooter2Action(security, footers);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/saveClinicEmailFooter");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoggedInInfo user = mock(LoggedInInfo.class);

    private void prepare(boolean administrator) {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        when(security.hasPrivilege(user, "_admin", SecurityInfoManager.WRITE, null)).thenReturn(administrator);
        request.setParameter("clinicFooter", "Clinic");
        request.setParameter("clinicFooterFingerprint", ClinicEmailFooterService.fingerprint("Clinic"));
    }

    @Test
    void shouldDenyNonAdminDirectPost_beforeAnyClinicReadOrSave() {
        prepare(false);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        }
        verifyNoInteractions(footers);
    }

    @Test
    void shouldRejectGet_andNotWrite() throws Exception {
        prepare(true);
        request.setMethod("GET");
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            action.execute();
        }
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(footers);
    }

    @Test
    void shouldRejectMissingField_insteadOfClearingClinic() throws Exception {
        prepare(true);
        request.removeParameter("clinicFooter");
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            action.execute();
        }
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(footers);
    }

    @Test
    void shouldReturnLocalizedConflictOutcome_whenDatabaseSnapshotHasChanged() throws Exception {
        prepare(true);
        when(footers.save("Clinic", ClinicEmailFooterService.fingerprint("Clinic")))
                .thenThrow(new jakarta.persistence.OptimisticLockException("Concurrent clinic edit"));
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            action.execute();
        }
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=conflict");
    }

    @Test
    void shouldReturnFixedStaleOutcome_forAuthorizedSave() throws Exception {
        prepare(true);
        when(footers.save("Clinic", ClinicEmailFooterService.fingerprint("Clinic")))
                .thenReturn(ClinicEmailFooterService.SaveResult.STALE);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            action.execute();
        }
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=stale");
    }
}
