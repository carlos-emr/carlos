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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.login.jaas;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * Factory for creating JAAS {@link LoginContext} instances configured with the appropriate
 * login module for CARLOS EMR authentication.
 *
 * <p>Initializes LDAP configuration from CARLOS properties and creates login contexts
 * using {@link LdapLoginModule}. Configuration is performed lazily on first use
 * and cached for subsequent calls.
 *
 * @see LdapLoginModule
 * @see OscarConfiguration
 * @since 2026-03-17
 */
public class LoginModuleFactory {

    public static final String OPTION_ATN_ENABLED = "ldap.authorization";
    public static final String OPTION_LDAP_URL = "ldap.url";
    public static final String OPTION_BASE_DN = "ldap.baseDn";
    public static final String OPTION_LDAP_ENABLED = "ldap.enabled";
    public static final String OPTION_LDAP_AUTH_METHOD = "ldap.authMethod";

    private static final String DEFAULT_BASE_DN = "CN=Roles,CN=Providers,DC=OSCAR,DC=ca";

    private static boolean initialized;

    /**
     * Initializes this factory with the information from OSCAR properties file.
     */
    private static synchronized void init() {
        if (initialized) return;

        boolean isLdapEnabled = CarlosProperties.isLdapAuthenticationEnabled();
        if (!isLdapEnabled) throw new IllegalStateException("LDAP is not enabled in carlos.properties");

        CarlosProperties props = CarlosProperties.getInstance();
        String baseDn = props.getProperty(OPTION_BASE_DN);
        if (baseDn == null || baseDn.isEmpty()) {
            baseDn = DEFAULT_BASE_DN;
        }

        String ldapUrl = props.getProperty(OPTION_LDAP_URL);
        if (ldapUrl == null || ldapUrl.isEmpty())
            throw new IllegalStateException("LDAP URL is not specified in carlos.properties");

        MiscUtils.getLogger().info("Configuring LDAP settings with: \n" + "LDAP URL: " + ldapUrl + "\n" + "BASE  DN:" + baseDn);

        Map<String, Object> config = new HashMap<String, Object>();
        // this is the partition where AD LDS keeps all the users by default
        config.put(LdapLoginModule.OPTION_BASE_DN, baseDn);
        // this is the location of AD LDS
        config.put(LdapLoginModule.OPTION_LDAP_URL, ldapUrl);
        // check if role info needs to be populated
        config.put(LdapLoginModule.OPTION_ATN_ENABLED, props.get(OPTION_LDAP_ENABLED));
        // authentication method - specify only if it's there
        Object authMethod = props.get(OPTION_LDAP_AUTH_METHOD);
        if (authMethod != null) {
            config.put(LdapLoginModule.OPTION_AUTH_METHOD, authMethod);
        }
        // enable login module configuration without additional property files
        Configuration.setConfiguration(new OscarConfiguration("oscar", LdapLoginModule.class.getName(), config));

        initialized = true;
    }

    private LoginModuleFactory() {
    }

    /**
     * Creates a new JAAS LoginContext configured with the LDAP login module.
     *
     * @param handler CallbackHandler the callback handler for supplying credentials
     * @return LoginContext a configured login context ready for authentication
     * @throws LoginException if the context cannot be created or LDAP is not properly configured
     */
    public static final LoginContext createContext(CallbackHandler handler) throws LoginException {
        init();

        return new LoginContext("oscar", handler);
    }

}