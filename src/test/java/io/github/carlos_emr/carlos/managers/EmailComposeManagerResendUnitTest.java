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

import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Opening a sent email for resending needs the email read privilege; without it the request is
 * refused as an access denial, which the action layer turns into the access-denied page.
 *
 * @since 2026-10-08
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("email")
@DisplayName("EmailComposeManager resend")
class EmailComposeManagerResendUnitTest {

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private EmailLogDaoImpl emailLogDao;

    @InjectMocks
    private EmailComposeManager emailComposeManager;

    @Test
    @DisplayName("should refuse as an access denial, without reading the email, when the email read privilege is missing")
    void shouldRefuseAsAccessDenial_whenEmailReadPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(false);

        assertThatThrownBy(() -> emailComposeManager.prepareEmailForResend(null, 5))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(emailLogDao);
    }
}
