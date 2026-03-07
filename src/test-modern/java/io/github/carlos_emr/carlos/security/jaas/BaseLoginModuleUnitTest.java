/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.security.jaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import javax.security.auth.Subject;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.login.jaas.BaseLoginModule;
import io.github.carlos_emr.carlos.login.jaas.OscarCallbackHandler;
import io.github.carlos_emr.carlos.login.jaas.OscarConfiguration;
import io.github.carlos_emr.carlos.login.jaas.OscarPrincipal;

/**
 * Unit tests for {@link BaseLoginModule}.
 *
 * <p>Tests the JAAS login module lifecycle (login, commit, logout, abort)
 * using a test subclass with hardcoded credentials.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("BaseLoginModule")
class BaseLoginModuleUnitTest {

    @BeforeEach
    void setUp() {
        Configuration.setConfiguration(
                new OscarConfiguration("testConfig", TestLoginModule.class.getName()));
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should authenticate with valid credentials")
        void shouldAuthenticate_withValidCredentials() throws Exception {
            LoginContext ctx = new LoginContext("testConfig",
                    new OscarCallbackHandler("dummy", "pass"));

            ctx.login();

            Subject subject = ctx.getSubject();
            assertThat(subject).isNotNull();
            assertThat(subject.getPrincipals()).isNotEmpty();
            assertThat(subject.getPrincipals(OscarPrincipal.class)).isNotEmpty();
        }

        @Test
        @DisplayName("should reject invalid credentials")
        void shouldReject_invalidCredentials() {
            LoginContext ctx;
            try {
                ctx = new LoginContext("testConfig",
                        new OscarCallbackHandler("wrong", "credentials"));
            } catch (LoginException e) {
                throw new RuntimeException(e);
            }

            assertThatThrownBy(ctx::login).isInstanceOf(LoginException.class);
        }

        @Test
        @DisplayName("should have null subject after failed login")
        void shouldHaveNullSubject_afterFailedLogin() throws Exception {
            LoginContext ctx = new LoginContext("testConfig",
                    new OscarCallbackHandler("wrong", "credentials"));

            try {
                ctx.login();
            } catch (LoginException e) {
                // expected
            }

            assertThat(ctx.getSubject()).isNull();
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("should succeed after successful login")
        void shouldSucceed_afterSuccessfulLogin() throws Exception {
            LoginContext ctx = new LoginContext("testConfig",
                    new OscarCallbackHandler("dummy", "pass"));
            ctx.login();

            ctx.logout();

            // no exception means success
        }

        @Test
        @DisplayName("should fail when no login occurred")
        void shouldFail_whenNoLoginOccurred() throws Exception {
            LoginContext ctx = new LoginContext("testConfig",
                    new OscarCallbackHandler("wrong", "credentials"));

            try {
                ctx.login();
            } catch (LoginException e) {
                // expected
            }

            assertThatThrownBy(ctx::logout).isInstanceOf(LoginException.class);
        }
    }

    /**
     * Test login module that authenticates only the hardcoded "dummy"/"pass" credentials.
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
