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
