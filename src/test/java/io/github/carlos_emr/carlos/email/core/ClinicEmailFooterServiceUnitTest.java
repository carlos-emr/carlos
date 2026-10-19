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
package io.github.carlos_emr.carlos.email.core;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ClinicEmailFooterServiceUnitTest {
    private final UserPropertyDAO properties = mock(UserPropertyDAO.class);
    private final EmailFooterLogoService logos = mock(EmailFooterLogoService.class);
    private final ClinicEmailFooterService service = new ClinicEmailFooterService(properties, logos);

    @Test
    void shouldUseExistingClinicSettingKey_andSanitizeItsText() {
        UserProperty row = new UserProperty();
        row.setName("email_footer_clinic_default");
        row.setValue("<b>FAKE Clinic</b><script>bad()</script>");
        when(properties.findClinicEmailFooter()).thenReturn(List.of(row));
        assertThat(service.snapshot().html()).isEqualTo("<b>FAKE Clinic</b>");
    }

    @Test
    void shouldLeaveMissingSettingVisible_andAvoidLogoLookup() {
        when(properties.findClinicEmailFooter()).thenReturn(List.of());
        assertThat(service.snapshot().html()).isEmpty();
        verifyNoInteractions(logos);
    }

    @Test
    void shouldSayTheFooterIsMissing_onlyWhenNoClinicFooterIsSet() {
        when(properties.findClinicEmailFooter()).thenReturn(List.of());
        assertThat(service.requiredFooterMissing()).isTrue();

        UserProperty row = new UserProperty();
        row.setName("email_footer_clinic_default");
        row.setValue("FAKE Clinic");
        when(properties.findClinicEmailFooter()).thenReturn(List.of(row));
        assertThat(service.requiredFooterMissing()).isFalse();
        verifyNoInteractions(logos);
    }

    private static UserProperty row(String name, String value) {
        UserProperty row = new UserProperty();
        row.setName(name);
        row.setValue(value);
        return row;
    }

    @Test
    void shouldBeRequired_untilAnAdministratorTurnsTheFooterOff() {
        assertThat(service.mode()).isEqualTo(ClinicEmailFooterService.Mode.REQUIRED);
        when(properties.findClinicProperty("email_footer_clinic_mode")).thenReturn(row("email_footer_clinic_mode", "sometimes"));
        assertThat(service.mode()).isEqualTo(ClinicEmailFooterService.Mode.REQUIRED);
        when(properties.findClinicProperty("email_footer_clinic_mode")).thenReturn(row("email_footer_clinic_mode", "off"));
        assertThat(service.mode()).isEqualTo(ClinicEmailFooterService.Mode.OFF);
    }

    @Test
    void shouldGiveCarlosEmailsNothing_butThePortalTheSavedFooter_whenOff() {
        when(properties.findClinicProperty("email_footer_clinic_mode")).thenReturn(row("email_footer_clinic_mode", "off"));
        when(properties.findClinicEmailFooter()).thenReturn(List.of(row("email_footer_clinic_default", "FAKE Clinic")));

        assertThat(service.snapshot()).isEqualTo(ClinicEmailFooterSnapshot.OFF);
        assertThat(service.requiredFooterMissing()).isFalse();
        assertThat(service.portalSnapshot().html()).isEqualTo("FAKE Clinic");
        assertThat(service.portalSnapshot().clinicFooterOff()).isFalse();
    }

    @Test
    void shouldStoreTheMode_underTheFooterLock_onlyWhenItChanges() {
        assertThat(service.saveMode(ClinicEmailFooterService.Mode.REQUIRED)).isFalse();
        verify(properties, never()).saveProp(any(UserProperty.class));

        assertThat(service.saveMode(ClinicEmailFooterService.Mode.OFF)).isTrue();
        var order = inOrder(properties);
        order.verify(properties).lockClinicEmailFooterSettings();
        order.verify(properties).findClinicProperty("email_footer_clinic_mode");
        order.verify(properties).saveProp(argThat((UserProperty saved) ->
                "email_footer_clinic_mode".equals(saved.getName()) && "off".equals(saved.getValue())));
    }

    @Test
    void shouldTurnTheFooterOff_withBlankText_leavingTheSavedFooter() {
        var saved = service.save("<b> </b>", ClinicEmailFooterService.fingerprint("FAKE Clinic"),
                ClinicEmailFooterService.Mode.OFF, false);

        assertThat(saved).isEqualTo(new ClinicEmailFooterService.Saved(ClinicEmailFooterService.SaveResult.UNCHANGED, true, true));
        verify(properties, never()).updateClinicEmailFooter(any(), any());
        verify(properties, never()).findClinicEmailFooterForUpdate();
    }

    @Test
    void shouldLeaveTheMode_whenTheTextSaveIsStale() {
        when(properties.findClinicEmailFooterForUpdate()).thenReturn(List.of(row("email_footer_clinic_default", "FAKE newer")));

        var saved = service.save("FAKE typed", ClinicEmailFooterService.fingerprint("FAKE older"),
                ClinicEmailFooterService.Mode.OFF, false);

        assertThat(saved.footer()).isEqualTo(ClinicEmailFooterService.SaveResult.STALE);
        assertThat(saved.modeChanged()).isFalse();
        assertThat(saved.logoChanged()).isFalse();
        verify(properties, never()).findClinicProperty(anyString());
    }

    @Test
    void shouldCarryTheLogo_untilAnAdministratorTurnsItOff() {
        var logo = new EmailInlineImage("clinic-logo-0123456789abcdef@carlos-emr", "image/png", new byte[] {1});
        when(logos.inlineLogo()).thenReturn(logo);
        when(properties.findClinicEmailFooter()).thenReturn(List.of(row("email_footer_clinic_default", "FAKE Clinic")));

        assertThat(service.logoShown()).isTrue();
        assertThat(service.snapshot().logo()).isNotNull();
        assertThat(service.emailLogo()).isNotNull();

        when(properties.findClinicProperty("email_footer_logo_shown")).thenReturn(row("email_footer_logo_shown", "false"));
        assertThat(service.logoShown()).isFalse();
        assertThat(service.snapshot().logo()).isNull();
        assertThat(service.portalSnapshot().logo()).isNull();
        assertThat(service.emailLogo()).isNull();
    }

    @Test
    void shouldStoreTheLogoSwitch_underTheFooterLock_onlyWhenItChanges() {
        assertThat(service.saveLogoShown(true)).isFalse();
        verify(properties, never()).saveProp(any(UserProperty.class));

        assertThat(service.saveLogoShown(false)).isTrue();
        var order = inOrder(properties);
        order.verify(properties).lockClinicEmailFooterSettings();
        order.verify(properties).findClinicProperty("email_footer_logo_shown");
        order.verify(properties).saveProp(argThat((UserProperty saved) ->
                "email_footer_logo_shown".equals(saved.getName()) && "false".equals(saved.getValue())));
    }

    @Test
    void shouldRefuseBlankSave_withoutChangingConfiguredClinic() {
        assertThatThrownBy(() -> service.save("<b> </b>", ClinicEmailFooterService.fingerprint("Clinic")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(properties, logos);
    }

    @Test
    void shouldRefuseStaleAdminSave_withoutWriting() {
        UserProperty current = new UserProperty();
        current.setValue("New Clinic");
        when(properties.findClinicEmailFooterForUpdate()).thenReturn(List.of(current));
        assertThat(service.save("Other Clinic", ClinicEmailFooterService.fingerprint("Old Clinic")))
                .isEqualTo(ClinicEmailFooterService.SaveResult.STALE);
        verify(properties).lockClinicEmailFooterSettings();
        verify(properties, never()).saveProp(any(UserProperty.class));
    }

    @Test
    void shouldSerializeFirstSaveBeforeReading_andKeepProviderNull() {
        when(properties.findClinicEmailFooterForUpdate()).thenReturn(List.of());
        assertThat(service.save("<i>Clinic</i>", ClinicEmailFooterService.fingerprint("")))
                .isEqualTo(ClinicEmailFooterService.SaveResult.SAVED);
        var order = inOrder(properties);
        order.verify(properties).lockClinicEmailFooterSettings();
        order.verify(properties).findClinicEmailFooterForUpdate();
        var saved = org.mockito.ArgumentCaptor.forClass(UserProperty.class);
        order.verify(properties).saveProp(saved.capture());
        assertThat(saved.getValue().getProviderNo()).isNull();
        assertThat(saved.getValue().getName()).isEqualTo("email_footer_clinic_default");
    }
}
