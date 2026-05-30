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
package io.github.carlos_emr.carlos.integration.mcedt;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("McedtSecurity Tests")
@Tag("unit")
@Tag("security")
class McedtSecurityTest extends CarlosUnitTestBase {

    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        loggedInInfo = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
    }

    @Test
    @DisplayName("should require read privilege, not write")
    void shouldRequireReadPrivilege_notWrite() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq(McedtSecurity.PRIVILEGE), eq("r"), isNull()))
                .thenReturn(true);

        McedtSecurity.requireRead(request);

        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq(McedtSecurity.PRIVILEGE), eq("r"), isNull());
        verify(securityInfoManager, never()).hasPrivilege(eq(loggedInInfo), eq(McedtSecurity.PRIVILEGE), eq("w"), isNull());
    }

    @Test
    @DisplayName("should require write privilege for mutations")
    void shouldRequireWritePrivilege_forMutations() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq(McedtSecurity.PRIVILEGE), eq("w"), isNull()))
                .thenReturn(true);

        McedtSecurity.requireWrite(request);

        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq(McedtSecurity.PRIVILEGE), eq("w"), isNull());
    }
}
