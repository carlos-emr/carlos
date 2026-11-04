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
package io.github.carlos_emr.carlos.managers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

/**
 * Preparing an eForm email's attachments, and reading the patient's email consent, need their read
 * privileges. A missing one is refused as an access denial, which the action layer sends to the
 * access-denied page, rather than as a failure the compose window would offer to retry.
 *
 * @since 2026-10-14
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("email")
@DisplayName("EmailComposeManager privileges")
class EmailComposeManagerPrivilegeUnitTest {

    @Mock
    private SecurityInfoManager securityInfoManager;

    @InjectMocks
    private EmailComposeManager emailComposeManager;

    @Test
    @DisplayName("should refuse as an access denial when an attachment or consent read privilege is missing")
    void shouldRefuseAsAccessDenial_whenReadPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(false);

        assertThatThrownBy(() -> emailComposeManager.prepareEFormAttachments(null, "1", new String[0]))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_eform)");
        assertThatThrownBy(() -> emailComposeManager.prepareEDocAttachments(null, new String[0]))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_edoc)");
        assertThatThrownBy(() -> emailComposeManager.prepareLabAttachments(null, new String[0]))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_lab)");
        assertThatThrownBy(() -> emailComposeManager.prepareFormAttachments(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new String[0], 1))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_form)");
        assertThatThrownBy(() -> emailComposeManager.getEmailConsentStatus(null, 1))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_email)");
    }
}
