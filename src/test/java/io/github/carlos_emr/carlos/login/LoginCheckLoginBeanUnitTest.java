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
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for username-enumeration timing controls in the legacy login bean.
 *
 * @since 2026-05-20
 */
@Tag("unit")
@Tag("security")
@DisplayName("LoginCheckLoginBean")
class LoginCheckLoginBeanUnitTest extends CarlosUnitTestBase {

    private static final String EXPECTED_MISSING_USER_DUMMY_PASSWORD_HASH =
            "{bcrypt}$2b$10$YzOXP.2axkRiYS07sVHWkuyvQjcuwR.bGeZd5WHQVJ23py57UES8C";

    private SecurityDao securityDao;
    private SecurityManager securityManager;

    @BeforeEach
    void setUp() {
        securityDao = mock(SecurityDao.class);
        securityManager = mock(SecurityManager.class);
        registerMock(SecurityDao.class, securityDao);
        registerMock(SecurityManager.class, securityManager);
    }

    @Test
    @DisplayName("should validate dummy password hash when user is missing")
    void shouldValidateDummyPasswordHash_whenUserIsMissing() {
        String username = "nonexistentUser";
        String password = "WRONGPASS";
        when(securityDao.findByUserName(username)).thenReturn(Collections.emptyList());

        LoginCheckLoginBean bean = new LoginCheckLoginBean();
        bean.ini(username, password, "", "127.0.0.1");

        String[] result = bean.authenticate();

        assertThat(result).isNull();
        verify(securityManager).validatePassword(
                password,
                argThat(security -> security != null
                        && EXPECTED_MISSING_USER_DUMMY_PASSWORD_HASH.equals(security.getPassword())));
    }
}
