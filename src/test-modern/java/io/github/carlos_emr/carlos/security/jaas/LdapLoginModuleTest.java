/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.security.jaas;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.login.jaas.LdapLoginModule;
import io.github.carlos_emr.carlos.login.jaas.LoginModuleFactory;
import io.github.carlos_emr.carlos.login.jaas.OscarCallbackHandler;
import io.github.carlos_emr.carlos.login.jaas.OscarConfiguration;
import io.github.carlos_emr.carlos.login.jaas.OscarPrincipal;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Integration tests for {@link LdapLoginModule}, verifying direct LDAP login,
 * end-to-end authentication, and various authentication methods.
 *
 * <p>Migrated from legacy JUnit 4 {@code LdapLoginModuleTest}. These tests
 * only run when LDAP authentication is enabled in the environment.</p>
 *
 * @see LdapLoginModule
 * @see LoginModuleFactory
 * @see OscarConfiguration
 * @since 2012-01-01
 */
@Tag("integration")
@Tag("security")
@DisplayName("LdapLoginModule Integration Tests")
class LdapLoginModuleTest extends CarlosTestBase {

    @Test
    @DisplayName("should authenticate successfully with valid LDAP credentials")
    void shouldAuthenticateSuccessfully_withValidLdapCredentials() throws Exception {
        assumeThat(OscarProperties.isLdapAuthenticationEnabled())
            .as("LDAP authentication must be enabled")
            .isTrue();

        // test direct auth
        LdapLoginModule lm = new LdapLoginModule();
        Map<String, Object> config = new HashMap<String, Object>();
        config.put(LdapLoginModule.OPTION_BASE_DN, OscarProperties.getInstance().getProperty("ldap.baseDn"));
        config.put(LdapLoginModule.OPTION_LDAP_URL, OscarProperties.getInstance().getProperty("ldap.url"));

        lm.initialize(new Subject(), new OscarCallbackHandler("oscardoc", "mac2002"),
            new HashMap<String, Object>(), config);
        boolean isSuccess = lm.login();
        assertThat(isSuccess).isTrue();
    }

    @Test
    @DisplayName("should complete end-to-end login and logout cycle with principal validation")
    void shouldCompleteLoginLogoutCycle_withPrincipalValidation() throws Exception {
        assumeThat(OscarProperties.isLdapAuthenticationEnabled())
            .as("LDAP authentication must be enabled")
            .isTrue();

        // test the end-to-end login
        LoginContext loginContext = LoginModuleFactory.createContext(
            new OscarCallbackHandler("oscardoc", "mac2002"));

        loginContext.login();

        Subject subject = loginContext.getSubject();
        assertThat(subject.getPrincipals()).isNotEmpty();
        assertThat(subject.getPrincipals(OscarPrincipal.class)).isNotEmpty();

        OscarPrincipal provider = subject.getPrincipals(OscarPrincipal.class).iterator().next();
        assertThat(provider.getName()).isEqualTo("oscardoc");

        loginContext.logout();

        subject = loginContext.getSubject();
        assertThat(subject.getPrincipals()).isEmpty();
        assertThat(subject.getPrincipals(OscarPrincipal.class)).isEmpty();

        // test invalid credentials
        LoginContext invalidContext = LoginModuleFactory.createContext(
            new OscarCallbackHandler("non_existent_user_name", "password"));
        assertThatThrownBy(() -> invalidContext.login())
            .isInstanceOf(LoginException.class);
    }

    @Test
    @DisplayName("should authenticate with various auth methods including null, simple, and DIGEST-MD5")
    void shouldAuthenticate_withVariousAuthMethods() throws Exception {
        assumeThat(OscarProperties.isLdapAuthenticationEnabled())
            .as("LDAP authentication must be enabled")
            .isTrue();

        // null should be the same as "simple"
        for (String authMethod : new String[]{null, "simple", "DIGEST-MD5"}) {
            OscarConfiguration config = (OscarConfiguration) Configuration.getConfiguration();
            config.setOption(LdapLoginModule.OPTION_AUTH_METHOD, authMethod);

            // test the end-to-end login
            LoginContext loginContext = LoginModuleFactory.createContext(
                new OscarCallbackHandler("oscardoc", "mac2002"));
            loginContext.login();
            loginContext.logout();
        }
    }
}
