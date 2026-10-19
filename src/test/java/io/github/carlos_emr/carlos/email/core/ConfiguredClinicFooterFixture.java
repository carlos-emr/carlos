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
import static org.mockito.Mockito.*;

/** Existing email workflow tests now require a configured clinic; use the real snapshot service. */
public final class ConfiguredClinicFooterFixture {
    private ConfiguredClinicFooterFixture() { }
    public static ClinicEmailFooterService service() {
        return service(mock(EmailFooterLogoService.class));
    }
    public static ClinicEmailFooterService service(EmailFooterLogoService logos) {
        UserPropertyDAO properties = mock(UserPropertyDAO.class);
        UserProperty row = new UserProperty();
        row.setName("email_footer_clinic_default");
        row.setValue("FAKE Mandatory Clinic");
        when(properties.findClinicEmailFooter()).thenReturn(List.of(row));
        return new ClinicEmailFooterService(properties, logos);
    }
}
