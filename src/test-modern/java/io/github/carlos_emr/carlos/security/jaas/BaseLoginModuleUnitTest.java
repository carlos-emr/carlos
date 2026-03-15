/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 BaseLoginModuleTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.security.jaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import javax.security.auth.Subject;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.login.jaas.BaseLoginModule;
import io.github.carlos_emr.carlos.login.jaas.OscarCallbackHandler;
import io.github.carlos_emr.carlos.login.jaas.OscarConfiguration;
import io.github.carlos_emr.carlos.login.jaas.OscarPrincipal;

/**
 * Unit tests for {@link BaseLoginModule}.
 *
 * <p>Tests JAAS login/logout lifecycle with valid and invalid credentials.
 * Migrated from legacy JUnit 4 BaseLoginModuleTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("BaseLoginModule unit tests")
class BaseLoginModuleUnitTest {

    @Test
    @DisplayName("should authenticate with valid credentials and reject invalid ones")
    void shouldAuthenticate_withValidCredentialsAndRejectInvalid() throws Exception {
        Configuration.setConfiguration(new OscarConfiguration("dummyConfig", TestLoginModule.class.getName()));

        LoginContext loginContext = new LoginContext("dummyConfig", new OscarCallbackHandler("dummy", "pass"));
        loginContext.login();

        Subject subject = loginContext.getSubject();
        assertThat(subject.getPrincipals()).isNotEmpty();
        assertThat(subject.getPrincipals(OscarPrincipal.class)).isNotEmpty();

        loginContext.logout();

        LoginContext badContext = new LoginContext("dummyConfig", new OscarCallbackHandler("dummy2", "pass2"));
        assertThatThrownBy(badContext::login).isInstanceOf(LoginException.class);
        assertThat(badContext.getSubject()).isNull();
        assertThatThrownBy(badContext::logout).isInstanceOf(LoginException.class);
    }

    /**
     * Dummy login module that authenticates only one subject with hardcoded credentials.
     */
    public static final class TestLoginModule extends BaseLoginModule {
        @Override
        protected OscarPrincipal authenticate(String loginName, char[] password) {
            if ("dummy".equalsIgnoreCase(loginName) && Arrays.equals("pass".toCharArray(), password)) {
                return new OscarPrincipal();
            }
            return null;
        }
    }
}
