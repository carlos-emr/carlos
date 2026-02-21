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


package io.github.carlos_emr.carlos.login;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.CarlosProperties;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.model.security.LdapSecurity;
import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;

/**
 * Bean that validates user credentials and enforces authentication security policies for CARLOS EMR.
 *
 * <p>This bean performs the actual credential validation after {@link LoginCheckLogin} has
 * determined that the login attempt is not blocked by brute force protection.
 *
 * <p>Authentication checks performed:
 * <ul>
 *   <li>Username existence in security table</li>
 *   <li>Password validation (plain-text legacy or BCrypt hashed)</li>
 *   <li>PIN validation (local and remote access policies)</li>
 *   <li>Account expiration checking</li>
 *   <li>Password expiration warning (10 days before expiration)</li>
 *   <li>Legacy password hash migration to BCrypt</li>
 * </ul>
 *
 * <p>PIN policy enforcement:
 * <ul>
 *   <li>PIN required for WAN (remote) access when bRemotelockset == 1</li>
 *   <li>PIN required for LAN (local) access when bLocallockset == 1</li>
 *   <li>PIN can be encrypted based on CarlosProperties.isPINEncripted()</li>
 *   <li>PIN check disabled for users with MFA enabled</li>
 *   <li>PIN check disabled if global legacy PIN setting is off</li>
 * </ul>
 *
 * <p>Password hash migration:
 * <ul>
 *   <li>Legacy passwords (< 20 chars) use plain-text comparison</li>
 *   <li>Modern passwords (>= 20 chars) use BCrypt validation</li>
 *   <li>Successful legacy password login triggers automatic BCrypt migration</li>
 *   <li>Failed migration is logged but does not prevent login</li>
 * </ul>
 *
 * <p>LDAP integration:
 * <ul>
 *   <li>If LDAP authentication is enabled, Security object is wrapped in {@link LdapSecurity}</li>
 *   <li>LDAP delegates password validation to LDAP server while maintaining local Security record</li>
 *   <li>LDAP configuration via {@link CarlosProperties#isLdapAuthenticationEnabled()}</li>
 * </ul>
 *
 * <p>Usage pattern:
 * <pre>
 * LoginCheckLoginBean bean = new LoginCheckLoginBean();
 * bean.ini(username, password, pin, ipAddress);
 * String[] result = bean.authenticate();
 * if (result != null && result.length > 1) {
 *     // Success - result contains provider info
 *     Security security = bean.getSecurity();
 * } else if (result != null && result[0].equals("expired")) {
 *     // Password expired
 * } else {
 *     // Authentication failed
 * }
 * </pre>
 *
 * @see LoginCheckLogin for brute force protection and authentication coordination
 * @see SecurityManager for password hashing and validation
 * @see MfaManager for multi-factor authentication operations
 * @see LdapSecurity for LDAP authentication integration
 * @since 2026-02-10
 */
public final class LoginCheckLoginBean {
    /** Logger instance for authentication events and errors */
    private static final Logger logger = MiscUtils.getLogger();

    /** Log message prefix for authentication-related log entries */
    private static final String LOG_PRE = "Login!@#$: ";

    /** Security manager for password encoding, validation, and hash migration */
    private final SecurityManager securityManager = SpringUtils.getBean(SecurityManager.class);

    /** Username being authenticated */
    private String username = "";

    /** Plain-text password provided by user (cleared after authentication) */
    private String password = "";

    /** Provider PIN for local/remote access control */
    private String pin;

    /** Client IP address for LAN/WAN detection and audit logging */
    private String ip = "";

    /** Password hash from database for comparison */
    private String userpassword;

    /** Provider first name (populated from provider table) */
    private String firstname;

    /** Provider last name (populated from provider table) */
    private String lastname;

    /** Provider type/profession (populated from provider table) */
    private String profession;

    /** Comma-separated list of role names (populated from sec_user_role table) */
    private String rolename;

    /** Provider email address (populated from provider table) */
    private String email;

    /** Security object for authenticated user (contains password hash, PIN, expiration, etc.) */
    private Security security = null;

    /**
     * Initializes the bean with authentication credentials.
     *
     * <p>This method must be called before {@link #authenticate()}. It sets the
     * username, password, PIN, and IP address fields via their respective setters,
     * which perform validation and whitespace normalization.
     *
     * @param user_name String the username to authenticate
     * @param password String the plain-text password
     * @param pin1 String the 4-digit provider PIN (may be null if PIN not required)
     * @param ip1 String the client IP address for LAN/WAN detection
     * @see #setUsername for username storage
     * @see #setPassword for password whitespace normalization
     * @see #setPin for PIN whitespace normalization
     * @see #setIp for IP address storage
     */
    public void ini(String user_name, String password, String pin1, String ip1) {
        setUsername(user_name);
        setPassword(password);
        setPin(pin1);
        setIp(ip1);
    }

    /**
     * Performs complete authentication validation of user credentials.
     *
     * <p>This method executes the full authentication flow:
     * <ol>
     *   <li>Retrieve Security record from database via {@link #getUserID()}</li>
     *   <li>Validate PIN requirements (local vs. remote access policies)</li>
     *   <li>Check account expiration date</li>
     *   <li>Calculate password expiration warning (10 days before expiry)</li>
     *   <li>Validate password (BCrypt for modern hashes, plain-text for legacy)</li>
     *   <li>Migrate legacy plain-text passwords to BCrypt on successful auth</li>
     *   <li>Return provider information array on success</li>
     * </ol>
     *
     * <p>PIN validation rules:
     * <ul>
     *   <li>PIN required if legacy PIN enabled AND user not using MFA</li>
     *   <li>Remote (WAN) access: PIN required if bRemotelockset == 1</li>
     *   <li>Local (LAN) access: PIN required if bLocallockset == 1</li>
     *   <li>PIN must be at least 3 characters</li>
     *   <li>PIN encrypted if CarlosProperties.isPINEncripted() returns true</li>
     * </ul>
     *
     * <p>Password validation:
     * <ul>
     *   <li>Legacy passwords (< 20 chars): plain-text comparison</li>
     *   <li>Modern passwords (>= 20 chars): BCrypt validation via {@link SecurityManager}</li>
     *   <li>Successful legacy password login triggers automatic BCrypt migration</li>
     * </ul>
     *
     * <p>Return value formats:
     * <ul>
     *   <li>Success (length 7): [providerNo, firstName, lastName, profession, roles, expiredDays, email]</li>
     *   <li>Expired (length 1): ["expired"]</li>
     *   <li>Failed (null): Authentication failed or user not found</li>
     * </ul>
     *
     * @return String[] authentication result array (see description for format)
     * @see #getUserID() for Security record retrieval
     * @see #isWAN() for LAN/WAN detection
     * @see #isPinCheckEnabled() for PIN policy checking
     * @see SecurityManager#validatePassword for BCrypt password validation
     * @see SecurityManager#upgradeSavePasswordHash for legacy password migration
     */
    public String[] authenticate() {
        // Retrieve Security record and populate provider info (firstname, lastname, etc.)
        security = getUserID();

        // Fail authentication if user not found in security table
        if (security == null) {
            return cleanNullObj(LOG_PRE + "No Such User: " + username);
        }

        // Encrypt PIN if encryption is enabled in configuration
        String sPin = pin;
        if (sPin != null && CarlosProperties.getInstance().isPINEncripted()) sPin = Misc.encryptPIN(sPin);

        // Validate PIN for remote (WAN) access
        if (this.isPinCheckEnabled() && isWAN() && security.getBRemotelockset() != null && security.getBRemotelockset().intValue() == 1 && (!sPin.equals(security.getPin()) || pin.length() < 3)) {
            return cleanNullObj(LOG_PRE + "Pin-remote needed: " + username);
        }
        // Validate PIN for local (LAN) access
        else if (this.isPinCheckEnabled() && !isWAN() && security.getBLocallockset() != null && security.getBLocallockset().intValue() == 1 && (!sPin.equals(security.getPin()) || pin.length() < 3)) {
            return cleanNullObj(LOG_PRE + "Pin-local needed: " + username);
        }

        // Check if account has expired (expiration date in the past)
        if (security.getBExpireset() != null && security.getBExpireset().intValue() == 1 && (security.getDateExpiredate() == null || security.getDateExpiredate().before(new Date()))) {
            return cleanNullObjExpire(LOG_PRE + "Expired: " + username);
        }

        // Calculate days until password expiration for warning message
        String expired_days = "";
        if (security.getBExpireset() != null && security.getBExpireset().intValue() == 1) {
            // Warn user if password will expire within 10 days
            long date_expireDate = security.getDateExpiredate().getTime();
            long date_now = new Date().getTime();
            long date_diff = (date_expireDate - date_now) / (24 * 3600 * 1000);

            if (security.getBExpireset().intValue() == 1 && date_diff < 11) {
                expired_days = String.valueOf(date_diff);
            }
        }

        boolean auth = false;

        userpassword = security.getPassword();
        // Legacy password (< 20 chars): plain-text comparison
        if (userpassword.length() < 20) {
            auth = password.equals(userpassword);
            // Migrate legacy password to BCrypt on successful authentication
            if (auth) {
                boolean isPasswordUpgraded = this.securityManager.upgradeSavePasswordHash(this.password, this.security);
                if (!isPasswordUpgraded)
                    logger.error("Error while upgrading password hash");
            }
        }
        // Modern password (>= 20 chars): BCrypt validation
        else {
            auth = this.securityManager.validatePassword(this.password, this.security);
        }

        // Return provider information array on successful authentication
        if (auth) {
            String[] strAuth = new String[7];
            strAuth[0] = security.getProviderNo();
            strAuth[1] = firstname;
            strAuth[2] = lastname;
            strAuth[3] = profession;
            strAuth[4] = rolename;
            strAuth[5] = expired_days;
            strAuth[6] = email;
            return strAuth;
        }
        // Return null on failed authentication
        else {
            return cleanNullObj(LOG_PRE + "password failed: " + username);
        }
    }

    /**
     * Cleans sensitive data and logs failed authentication attempt.
     *
     * <p>This method is called when authentication fails for reasons other than
     * password expiration (e.g., invalid credentials, missing PIN, user not found).
     *
     * <p>Security measures:
     * <ul>
     *   <li>Clears userpassword and password fields to prevent memory-based attacks</li>
     *   <li>Logs failed attempt with OWASP-encoded username for PHI protection</li>
     *   <li>Returns null to indicate authentication failure</li>
     * </ul>
     *
     * @param errorMsg String the error message to log (not exposed to user for security)
     * @return null to indicate authentication failure
     * @see #cleanNullObjExpire for expired password cleanup
     */
    private String[] cleanNullObj(String errorMsg) {
        logger.warn(errorMsg);
        // SECURITY: OWASP encode username for HTML context to prevent injection in logs
        LogAction.addLogSynchronous("", "failed", LogConst.CON_LOGIN, Encode.forHtmlContent(username), ip);
        // Clear sensitive data from memory
        userpassword = null;
        password = null;
        return null;
    }

    /**
     * Cleans sensitive data and logs expired password authentication attempt.
     *
     * <p>This method is called when authentication fails due to account expiration.
     * Unlike {@link #cleanNullObj}, this returns a special "expired" indicator
     * that allows the caller to distinguish between expired accounts and invalid credentials.
     *
     * <p>Security measures:
     * <ul>
     *   <li>Clears userpassword and password fields to prevent memory-based attacks</li>
     *   <li>Logs expiration event with OWASP-encoded username for PHI protection</li>
     *   <li>Returns ["expired"] array to indicate account expiration</li>
     * </ul>
     *
     * @param errorMsg String the error message to log (not exposed to user for security)
     * @return String[] array containing single "expired" element
     * @see #cleanNullObj for general authentication failure cleanup
     */
    private String[] cleanNullObjExpire(String errorMsg) {
        logger.warn(errorMsg);
        // SECURITY: OWASP encode username for HTML context to prevent injection in logs
        LogAction.addLogSynchronous("", "expired", LogConst.CON_LOGIN, Encode.forHtmlContent(username), ip);
        // Clear sensitive data from memory
        userpassword = null;
        password = null;
        return new String[]{"expired"};
    }

    /**
     * Retrieves Security record and populates provider information fields.
     *
     * <p>This method performs multiple database queries to collect all user information:
     * <ol>
     *   <li>Query security table for username to get Security record</li>
     *   <li>Wrap with LdapSecurity if LDAP authentication is enabled</li>
     *   <li>Query provider table for first name, last name, profession, email</li>
     *   <li>Query sec_user_role table for comma-separated role list</li>
     * </ol>
     *
     * <p>Side effects - populates instance fields:
     * <ul>
     *   <li>firstname - Provider's first name</li>
     *   <li>lastname - Provider's last name</li>
     *   <li>profession - Provider type/specialty</li>
     *   <li>email - Provider email address</li>
     *   <li>rolename - Comma-separated list of role names (e.g., "doctor,admin")</li>
     * </ul>
     *
     * <p>LDAP integration:
     * <ul>
     *   <li>If LDAP enabled, Security is wrapped in {@link LdapSecurity}</li>
     *   <li>LdapSecurity delegates password validation to LDAP server</li>
     *   <li>Local Security record maintained for session management</li>
     * </ul>
     *
     * @return Security the user's security record (wrapped in LdapSecurity if LDAP enabled), or null if user not found
     * @see SecurityDao#findByUserName for database lookup
     * @see LdapSecurity for LDAP authentication wrapper
     */
    private Security getUserID() {

        SecurityDao securityDao = (SecurityDao) SpringUtils.getBean(SecurityDao.class);
        List<Security> results = securityDao.findByUserName(username);
        Security security = null;
        if (results.size() > 0) security = results.get(0);

        if (security == null) {
            return null;
        }
        // Wrap with LDAP authentication adapter if LDAP is enabled
        else if (CarlosProperties.isLdapAuthenticationEnabled()) {
            security = new LdapSecurity(security);
        }

        // Populate provider information from provider table
        ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
        Provider provider = providerDao.getProvider(security.getProviderNo());

        if (provider != null) {
            firstname = provider.getFirstName();
            lastname = provider.getLastName();
            profession = provider.getProviderType();
            email = provider.getEmail();
        }

        // Build comma-separated role list from sec_user_role table
        SecUserRoleDao secUserRoleDao = (SecUserRoleDao) SpringUtils.getBean(SecUserRoleDao.class);
        List<SecUserRole> roles = secUserRoleDao.getUserRoles(security.getProviderNo());
        for (SecUserRole role : roles) {
            if (rolename == null) {
                rolename = role.getRoleName();
            } else {
                rolename += "," + role.getRoleName();
            }
        }

        return security;
    }

    /**
     * Determines if client is on wide area network (WAN) vs. local area network (LAN).
     *
     * <p>This method checks if the client IP matches any configured local network prefixes.
     * LAN clients are exempt from certain security restrictions like PIN requirements
     * and brute force protection.
     *
     * @return boolean true if client is on WAN (remote access), false if on LAN (local access)
     * @see LoginCheckLogin#ipFound for IP prefix matching logic
     */
    public boolean isWAN() {
        boolean bWAN = true;
        // Check if IP matches any configured local network prefix
        if (LoginCheckLogin.ipFound(ip)) bWAN = false;
        return bWAN;
    }

    /**
     * Sets the username for authentication.
     *
     * @param user_name String the username to authenticate
     */
    public void setUsername(String user_name) {
        this.username = user_name;
    }

    /**
     * Sets the password for authentication, removing whitespace.
     *
     * <p>This method replaces all space characters with backspace to prevent
     * accidental whitespace in passwords. This is a security measure to ensure
     * password consistency.
     *
     * @param password String the plain-text password (whitespace will be removed)
     */
    public void setPassword(String password) {
        // Remove whitespace from password for security consistency
        this.password = password.replace(' ', '\b');
    }

    /**
     * Sets the provider PIN for local/remote access control, removing whitespace.
     *
     * <p>This method replaces all space characters with backspace to prevent
     * accidental whitespace in PINs.
     *
     * @param pin1 String the 4-digit provider PIN (whitespace will be removed, may be null)
     */
    public void setPin(String pin1) {
        if (pin1 != null) {
            // Remove whitespace from PIN for security consistency
            this.pin = pin1.replace(' ', '\b');
        }
    }

    /**
     * Sets the client IP address for LAN/WAN detection and audit logging.
     *
     * @param ip1 String the client IP address
     */
    public void setIp(String ip1) {
        this.ip = ip1;
    }

    /**
     * Retrieves the Security object for the authenticated user.
     *
     * <p>This method should be called after {@link #authenticate()} returns
     * a successful result. The Security object contains the user's credentials,
     * PIN, MFA settings, and expiration configuration.
     *
     * @return Security the authenticated user's security record, or null if not yet authenticated
     * @see #authenticate() for authentication method that must be called first
     */
    public Security getSecurity() {
        return (security);
    }

	/**
	 * Checks if PIN check is enabled globally and for the current user.
	 *
	 * @return true if PIN check is enabled and the user is not using MFA, false otherwise.
	 */
	private boolean isPinCheckEnabled() {
		return MfaManager.isOscarLegacyPinEnabled() && !security.isUsingMfa();
	}

}
