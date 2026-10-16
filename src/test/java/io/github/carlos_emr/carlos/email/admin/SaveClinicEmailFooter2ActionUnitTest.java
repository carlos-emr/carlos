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
        when(footers.save("Clinic", ClinicEmailFooterService.fingerprint("Clinic"), null, null))
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
        when(footers.save("Clinic", ClinicEmailFooterService.fingerprint("Clinic"), null, null))
                .thenReturn(new ClinicEmailFooterService.Saved(ClinicEmailFooterService.SaveResult.STALE, false, false));
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            action.execute();
        }
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=stale");
    }

    // Ben's demo (15 Oct): a refused save must not lose what the administrator typed.
    private ClinicFooterDraft refuseTypedFooter(RuntimeException refusal, ClinicEmailFooterService.SaveResult result)
            throws Exception {
        prepare(true);
        String shown = ClinicEmailFooterService.fingerprint("Clinic");
        request.setParameter("clinicFooter", "<b>FAKE typed footer</b>");
        var save = when(footers.save("<b>FAKE typed footer</b>", shown, null, null));
        if (refusal != null) {
            save.thenThrow(refusal);
        } else {
            save.thenReturn(new ClinicEmailFooterService.Saved(result, false, false));
        }
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            action.execute();
        }
        return ClinicFooterDraft.take(request);
    }

    @Test
    void shouldKeepTypedFooter_whenTheSaveIsInvalid() throws Exception {
        ClinicFooterDraft draft = refuseTypedFooter(new IllegalArgumentException("Clinic footer is too long"), null);

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=invalid");
        assertThat(draft).isEqualTo(new ClinicFooterDraft("<b>FAKE typed footer</b>",
                ClinicEmailFooterService.fingerprint("Clinic"), false));
    }

    @Test
    void shouldKeepTypedFooter_whenTheSaveConflicts() throws Exception {
        ClinicFooterDraft draft = refuseTypedFooter(new jakarta.persistence.OptimisticLockException("FAKE"), null);

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=conflict");
        assertThat(draft).isNotNull();
        assertThat(draft.stale()).isFalse();
    }

    @Test
    void shouldKeepTypedFooterBesideTheCurrentOne_whenAnotherAdministratorChangedIt() throws Exception {
        ClinicFooterDraft draft = refuseTypedFooter(null, ClinicEmailFooterService.SaveResult.STALE);

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=stale");
        assertThat(draft).isNotNull();
        assertThat(draft.stale()).isTrue();
    }

    @Test
    void shouldKeepNothing_whenTheFooterIsSaved() throws Exception {
        try (var audit = mockStatic(io.github.carlos_emr.carlos.log.LogAction.class)) {
            assertThat(refuseTypedFooter(null, ClinicEmailFooterService.SaveResult.SAVED)).isNull();
        }
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=saved");
    }

    // Ben's demo (15 Oct): the clinic footer can be required (the default) or off, and each change is audited.
    private void postMode(String mode, ClinicEmailFooterService.Saved saved) throws Exception {
        prepare(true);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        request.setParameter("clinicFooterMode", mode);
        ClinicEmailFooterService.Mode parsed = ClinicEmailFooterService.Mode.parse(mode);
        if (saved != null) {
            when(footers.save("Clinic", ClinicEmailFooterService.fingerprint("Clinic"), parsed, null)).thenReturn(saved);
        }
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            action.execute();
        }
    }

    @Test
    void shouldSaveAndAuditTheMode_whenOnlyTheModeChanged() throws Exception {
        try (var audit = mockStatic(io.github.carlos_emr.carlos.log.LogAction.class)) {
            postMode("off", new ClinicEmailFooterService.Saved(ClinicEmailFooterService.SaveResult.UNCHANGED, true, false));

            audit.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog("999998", "update",
                    "emailFooterClinicMode", "off", "127.0.0.1"));
            audit.verifyNoMoreInteractions();
        }
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=settingsSaved");
    }

    @Test
    void shouldAuditTextAndMode_whenBothChanged() throws Exception {
        try (var audit = mockStatic(io.github.carlos_emr.carlos.log.LogAction.class)) {
            postMode("required", new ClinicEmailFooterService.Saved(ClinicEmailFooterService.SaveResult.SAVED, true, false));

            audit.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog("999998", "update",
                    "emailFooterClinicDefault", "", "127.0.0.1"));
            audit.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog("999998", "update",
                    "emailFooterClinicMode", "required", "127.0.0.1"));
        }
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=saved");
    }

    @Test
    void shouldAuditNothing_whenNothingChanged() throws Exception {
        try (var audit = mockStatic(io.github.carlos_emr.carlos.log.LogAction.class)) {
            postMode("required", new ClinicEmailFooterService.Saved(ClinicEmailFooterService.SaveResult.UNCHANGED, false, false));

            audit.verifyNoInteractions();
        }
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=unchanged");
    }

    @Test
    void shouldRefuseAnUnknownMode_beforeSavingAnything() throws Exception {
        postMode("sometimes", null);

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(footers);
    }

    // Ben's demo (15 Oct): Save footer in the window also sends "Add a logo to emails" (a hidden "false",
    // and "true" when switched on), saved and audited with the rest.
    private void postLogo(String[] values, Boolean expected, ClinicEmailFooterService.Saved saved) throws Exception {
        prepare(true);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        if (values != null) {
            request.setParameter("logoShown", values);
        }
        when(footers.save("Clinic", ClinicEmailFooterService.fingerprint("Clinic"), null, expected)).thenReturn(saved);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            action.execute();
        }
    }

    @Test
    void shouldReadTheSwitch_asOnOffOrNotSent() throws Exception {
        var unchanged = new ClinicEmailFooterService.Saved(ClinicEmailFooterService.SaveResult.UNCHANGED, false, false);
        try (var audit = mockStatic(io.github.carlos_emr.carlos.log.LogAction.class)) {
            postLogo(new String[] {"false", "true"}, true, unchanged);
            verify(footers).save("Clinic", ClinicEmailFooterService.fingerprint("Clinic"), null, true);
        }
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=unchanged");
    }

    @Test
    void shouldSaveAndAuditTheLogoSwitch_whenItAloneChanged() throws Exception {
        try (var audit = mockStatic(io.github.carlos_emr.carlos.log.LogAction.class)) {
            postLogo(new String[] {"false"}, false,
                    new ClinicEmailFooterService.Saved(ClinicEmailFooterService.SaveResult.UNCHANGED, false, true));

            audit.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog("999998", "update",
                    "emailFooterLogoShown", "false", "127.0.0.1"));
            audit.verifyNoMoreInteractions();
        }
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=settingsSaved");
    }

    @Test
    void shouldLeaveTheSwitch_whenItIsNotSent() throws Exception {
        try (var audit = mockStatic(io.github.carlos_emr.carlos.log.LogAction.class)) {
            postLogo(null, null, new ClinicEmailFooterService.Saved(ClinicEmailFooterService.SaveResult.UNCHANGED, false, false));
            verify(footers).save("Clinic", ClinicEmailFooterService.fingerprint("Clinic"), null, null);
        }
    }

    @Test
    void shouldKeepTheChosenSettingsWithTheText_whenTheSaveIsRefused() throws Exception {
        prepare(true);
        String shown = ClinicEmailFooterService.fingerprint("Clinic");
        request.setParameter("clinicFooter", "<b>FAKE typed footer</b>");
        request.setParameter("clinicFooterMode", "off");
        request.setParameter("logoShown", new String[] {"false"});
        when(footers.save("<b>FAKE typed footer</b>", shown, ClinicEmailFooterService.Mode.OFF, false))
                .thenThrow(new IllegalArgumentException("Clinic footer is too long"));
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            action.execute();
        }

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/ViewConfigureEmail?clinicFooterOutcome=invalid");
        assertThat(ClinicFooterDraft.take(request))
                .isEqualTo(new ClinicFooterDraft("<b>FAKE typed footer</b>", shown, false, "off", false));
    }
}
