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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.security.auth.login.LoginException;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * JAAS login module that authenticates CARLOS EMR providers against an LDAP directory.
 *
 * <p>Extends {@link BaseLoginModule} to provide LDAP-based credential validation.
 * After successful LDAP bind, loads the provider's {@link OscarPrincipal} and roles
 * from the CARLOS EMR database.
 *
 * <p>Supports configurable authentication methods (simple or DIGEST-MD5) and
 * customizable LDAP URL and base DN via JAAS configuration options.
 *
 * @see BaseLoginModule
 * @see LoginModuleFactory for configuration and context creation
 * @since 2026-03-17
 */
public final class LdapLoginModule extends BaseLoginModule {

    public static final String OPTION_LDAP_URL = "ldapUrl";
    public static final String OPTION_BASE_DN = "baseDn";
    public static final String OPTION_AUTH_METHOD = "authMethod";

    private static final String AUTH_METHOD_SIMPLE = "simple";
    private static final String AUTH_METHOD_DIGEST = "DIGEST-MD5";

    private ProviderDao providerDao;
    private SecurityDao securityDao;
    private SecUserRoleDao secUserRoleDao;

    public LdapLoginModule() {
        setProviderDao((ProviderDao) SpringUtils.getBean(ProviderDao.class));
        setSecurityDao((SecurityDao) SpringUtils.getBean(SecurityDao.class));
        setSecUserRoleDao((SecUserRoleDao) SpringUtils.getBean(SecUserRoleDao.class));
    }

    /**
     * Authenticates login against LDAP.
     *
     * @see io.github.carlos_emr.carlos.login.jaas.BaseLoginModule#authenticate(java.lang.String, char[])
     */
    @Override
    protected final OscarPrincipal authenticate(String loginName, char[] password) throws Exception, LoginException {
        if (logger.isDebugEnabled()) {
            logger.debug("Starting authentication for " + loginName);
        }

        Map<String, String> env = new HashMap<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.SECURITY_PRINCIPAL, toDn(loginName));
        env.put(Context.SECURITY_CREDENTIALS, new String(password));
        env.put(Context.PROVIDER_URL, getLdapUrl());
        String authMethod = getAuthMethod();
        if (authMethod != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Using authentication method: " + authMethod);
            }

            env.put(Context.SECURITY_AUTHENTICATION, authMethod);
        }

        DirContext dirContext = null;
        try {
            dirContext = new InitialDirContext(new java.util.Hashtable<String, String>(env));
            OscarPrincipal principal = loadPrincipal(loginName);

            if (logger.isDebugEnabled()) {
                logger.debug("Completed authentication for " + loginName + " successfully");
            }

            return principal;
        } catch (NamingException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Authentication failed for " + loginName, e);
            }

            throw new LoginException("Authentication failed");
        } finally {
            if (dirContext != null) {
                dirContext.close();
            }
        }
    }

    /**
     * Generates DN from the specified login name
     *
     * @param loginName Login name to create DN for
     * @return Returns the DN
     */
    protected String toDn(String loginName) {
        return "CN=" + loginName + "," + getBaseDn();
    }

    /**
     * Loads principal after successful validation of login credentials
     *
     * @param loginName Login name to use for loading the principal
     * @return Returns the loaded principal
     * @throws LoginException LoginException is thrown in case there are inconsistent sec info for the specified user name or if providers doesn't exist
     */
    protected final OscarPrincipal loadPrincipal(String loginName) throws LoginException {
        // find unique sec record for the validated login
        List<Security> securities = getSecurityDao().findByUserName(loginName);
        if (securities.size() < 1) {
            throw new LoginException("CARLOS Security record is not found for " + loginName);
        }
        if (securities.size() > 1) {
            throw new LoginException("Multiple CARLOS Security records found for " + loginName);
        }

        Security security = securities.get(0);
        // now find providers info for the sec record
        Provider provider = getProviderDao().getProvider(security.getProviderNo());
        OscarPrincipal result = new OscarPrincipal(provider);
        result.setName(loginName);
        return result;
    }

    @Override
    protected List<OscarRole> getRoles(String loginName) {
        List<SecUserRole> roles = getSecUserRoleDao().getUserRoles(getPrincipal().getProviderNo());
        List<OscarRole> result = new ArrayList<OscarRole>();
        for (SecUserRole role : roles) {
            result.add(new OscarRole(role));
        }
        return result;
    }

    public ProviderDao getProviderDao() {
        return providerDao;
    }

    public void setProviderDao(ProviderDao providerDao) {
        this.providerDao = providerDao;
    }

    public SecurityDao getSecurityDao() {
        return securityDao;
    }

    public void setSecurityDao(SecurityDao securityDao) {
        this.securityDao = securityDao;
    }

    public String getLdapUrl() {
        return String.valueOf(getOptions().get(OPTION_LDAP_URL));
    }

    public String getBaseDn() {
        return String.valueOf(getOptions().get(OPTION_BASE_DN));
    }

    public String getAuthMethod() {
        Object authMethod = getOptions().get(OPTION_AUTH_METHOD);
        if (authMethod == null) {
            return null;
        }

        String authMethodString = authMethod.toString();
        for (String method : new String[]{AUTH_METHOD_SIMPLE, AUTH_METHOD_DIGEST}) {
            if (method.equalsIgnoreCase(authMethodString)) {
                return method;
            }
        }

        throw new IllegalStateException("Unsupported method: " + authMethod);
    }

    public SecUserRoleDao getSecUserRoleDao() {
        return secUserRoleDao;
    }

    public void setSecUserRoleDao(SecUserRoleDao secUserRoleDao) {
        this.secUserRoleDao = secUserRoleDao;
    }

}
