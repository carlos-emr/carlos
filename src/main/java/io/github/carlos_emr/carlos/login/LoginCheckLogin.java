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

import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.Vector;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.OscarProperties;

/**
 * Coordinates authentication validation and brute force protection for CARLOS EMR login.
 *
 * <p>This class serves as a facade for the authentication subsystem, coordinating:
 * <ul>
 *   <li>User credential validation via {@link LoginCheckLoginBean}</li>
 *   <li>IP-based brute force attack mitigation via {@link LoginList}</li>
 *   <li>Username-based account lockout for enhanced security</li>
 *   <li>Local network (LAN) vs. wide area network (WAN) detection</li>
 * </ul>
 *
 * <p>Security features:
 * <ul>
 *   <li><b>IP Blocking:</b> Blocks login attempts from IPs with repeated failures</li>
 *   <li><b>Username Lockout:</b> Blocks accounts after repeated failed login attempts (when login_lock=true)</li>
 *   <li><b>LAN Exemption:</b> Exempts local network IPs from brute force protection</li>
 *   <li><b>Timeout Management:</b> Automatically removes expired block entries</li>
 *   <li><b>Configurable Thresholds:</b> Uses login_max_failed_times and login_max_duration properties</li>
 * </ul>
 *
 * <p>Configuration properties (from {@link OscarProperties}):
 * <ul>
 *   <li>login_local_ip - Comma-separated list of local IP prefixes to exempt from blocking</li>
 *   <li>login_lock - "true" to enable username-based locking, otherwise IP-based only</li>
 *   <li>login_max_failed_times - Maximum failed attempts before blocking</li>
 *   <li>login_max_duration - Duration (minutes) before removing expired block entries</li>
 * </ul>
 *
 * <p>Usage pattern:
 * <pre>
 * LoginCheckLogin cl = new LoginCheckLogin();
 * if (cl.isBlock(ip, userName)) {
 *     // Account is locked due to failed attempts
 * } else {
 *     String[] result = cl.auth(userName, password, pin, ip);
 *     if (result != null &amp;&amp; result.length > 1) {
 *         // Authentication successful
 *         Security security = cl.getSecurity();
 *     } else {
 *         // Authentication failed
 *         cl.updateLoginList(ip, userName);
 *     }
 * }
 * </pre>
 *
 * @see LoginCheckLoginBean for credential validation logic
 * @see LoginList for singleton login attempt tracking
 * @see LoginInfoBean for individual login attempt tracking
 * @since 2026-02-10
 */
public final class LoginCheckLogin {
    /** Flag indicating if client is on WAN (true) or LAN (false); LAN clients bypass brute force protection */
    boolean bWAN = true;

    /** Bean responsible for actual credential validation */
    LoginCheckLoginBean lb = null;

    /** Login info bean for tracking current login attempt */
    LoginInfoBean linfo = null;

    /** Singleton registry of blocked IPs and usernames with failed login attempts */
    LoginList llist = null;

    /**
     * Constructs a new LoginCheckLogin instance for authentication coordination.
     *
     * <p>Initializes with WAN mode enabled (brute force protection active).
     * Call {@link #auth} to validate credentials and {@link #isBlock} to check
     * if IP or username is currently blocked.
     */
    public LoginCheckLogin() {
    }

    /**
     * Checks if an IP address matches any configured local network prefix.
     *
     * <p>Local network IPs are exempt from brute force protection. The login_local_ip
     * property contains a comma-separated list of IP prefixes (e.g., "192.168,10.0,172.16").
     *
     * <p>Example configuration:
     * <pre>login_local_ip=192.168,10.0,172.16</pre>
     * This would match: 192.168.1.100, 10.0.0.50, 172.16.254.1
     *
     * @param IPToCheck String the IP address to check
     * @return boolean true if IP starts with any configured local prefix, false otherwise
     */
    static boolean ipFound(String IPToCheck) {
        String prop = OscarProperties.getInstance().getProperty("login_local_ip");
        if (!StringUtils.isEmpty(prop)) {
            String[] props = prop.split(",");
            // Check each configured local IP prefix
            for (String p : props) {
                if (IPToCheck.startsWith(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if an IP address is currently blocked due to failed login attempts.
     *
     * <p>This method:
     * <ol>
     *   <li>Checks if IP is on local network (exempted from blocking)</li>
     *   <li>Removes expired block entries from the login list</li>
     *   <li>Checks if the IP has a block status (status == 0)</li>
     * </ol>
     *
     * <p>Local network (LAN) IPs are never blocked. Only WAN IPs are subject to
     * brute force protection.
     *
     * @param ip String the IP address to check
     * @return boolean true if IP is blocked, false if IP is allowed to attempt login
     * @see #ipFound for local network detection
     * @see LoginInfoBean#getTimeOutStatus for timeout checking
     */
    public boolean isBlock(String ip) {
        boolean bBlock = false;

        // Check if IP is on local network (LAN clients bypass brute force protection)
        if (ipFound(ip)) bWAN = false;

        GregorianCalendar now = new GregorianCalendar();
        // Wait for singleton LoginList to initialize
        while (llist == null) {
            llist = LoginList.getLoginListInstance();
        }
        String sTemp = null;

        // Clean up expired block entries and check block status for WAN clients only
        if (bWAN && !llist.isEmpty()) {
            // Remove timed-out block entries
            for (Enumeration e = llist.keys(); e.hasMoreElements(); ) {
                sTemp = (String) e.nextElement();
                linfo = (LoginInfoBean) llist.get(sTemp);
                if (linfo.getTimeOutStatus(now)) llist.remove(sTemp);
            }

            // Check if this IP is blocked (status == 0 means blocked)
            if (llist.get(ip) != null && ((LoginInfoBean) llist.get(ip)).getStatus() == 0) bBlock = true;
        }

        return bBlock;
    }

    /**
     * Checks if a username or IP is currently blocked due to failed login attempts.
     *
     * <p>This method provides username-based account lockout when the login_lock
     * property is set to "true". Otherwise, it falls back to IP-based blocking.
     *
     * <p>Username-based locking is more effective against distributed brute force
     * attacks where attackers use multiple IPs to target a single account.
     *
     * @param ip String the client IP address (used for LAN detection)
     * @param userName String the username attempting to log in
     * @return boolean true if username is blocked, false if allowed to attempt login
     * @see #isBlock(String) for IP-based blocking (fallback mode)
     */
    public boolean isBlock(String ip, String userName) {
        Properties p = OscarProperties.getInstance();
        // Check configuration to determine if username-based locking is enabled
        if (!p.getProperty("login_lock", "").trim().equals("true")) {
            return isBlock(ip);
        }

        boolean bBlock = false;
        // Check if IP is on local network (LAN clients bypass brute force protection)
        if (ipFound(ip)) bWAN = false;

        // Wait for singleton LoginList to initialize
        while (llist == null) {
            llist = LoginList.getLoginListInstance();
        }

        // Check if this username is blocked (status == 0 means blocked)
        if (llist.get(userName) != null && ((LoginInfoBean) llist.get(userName)).getStatus() == 0) bBlock = true;

        return bBlock;
    }

    /**
     * Authenticates user credentials (username, password, and PIN).
     *
     * <p>This method delegates to {@link LoginCheckLoginBean} for actual credential
     * validation. The authentication result is returned as a String array with different
     * meanings based on array length:
     *
     * <p>Success (length > 1): Array contains:
     * <pre>
     * [0] = provider number
     * [1] = first name
     * [2] = last name
     * [3] = username
     * [4] = user role
     * [5] = expired days (for password expiration warning)
     * [6] = ... (additional fields)
     * </pre>
     *
     * <p>Failure (length == 1):
     * <ul>
     *   <li>["expired"] - Password has expired</li>
     *   <li>[null] - Authentication failed (invalid credentials)</li>
     * </ul>
     *
     * @param user_name String the username (validated to alphanumeric format)
     * @param password String the plain-text password
     * @param pin String the 4-digit provider PIN
     * @param ip String the client IP address for audit logging
     * @return String[] authentication result array (see description for format)
     * @see LoginCheckLoginBean#authenticate for validation logic
     * @see #getSecurity for retrieving the Security object after successful authentication
     */
    public String[] auth(String user_name, String password, String pin, String ip) {
        lb = new LoginCheckLoginBean();
        lb.ini(user_name, password, pin, ip);
        return lb.authenticate();
    }

    /**
     * Retrieves the Security object for the authenticated user.
     *
     * <p>This method only works after calling {@link #auth} successfully.
     * If auth() has not been called or authentication failed, this will return null.
     *
     * <p>The Security object contains:
     * <ul>
     *   <li>Security number (primary key)</li>
     *   <li>Username</li>
     *   <li>Password hash</li>
     *   <li>Provider number</li>
     *   <li>PIN</li>
     *   <li>MFA configuration (if enabled)</li>
     *   <li>Force password reset flag</li>
     *   <li>Account expiration date</li>
     * </ul>
     *
     * @return Security the authenticated user's security record, or null if auth not yet called
     * @see #auth for authentication method that must be called first
     */
    public Security getSecurity() {
        return (lb.getSecurity());
    }

    /**
     * Records a failed login attempt for IP or username-based blocking.
     *
     * <p>This method delegates to either {@link #updateLoginList(String)} for IP-based
     * blocking or {@link #updateLockList(String)} for username-based blocking, depending
     * on the login_lock configuration property.
     *
     * <p>Call this method after {@link #auth} returns a failed authentication result
     * to track the failed attempt and potentially block the IP or username.
     *
     * @param ip String the client IP address
     * @param userName String the username that failed authentication
     * @see #isBlock(String, String) to check if IP or username is now blocked
     */
    public synchronized void updateLoginList(String ip, String userName) {
        Properties p = OscarProperties.getInstance();
        // Choose blocking strategy based on configuration
        if (!p.getProperty("login_lock", "").trim().equals("true")) {
            updateLoginList(ip);
        } else {
            updateLockList(userName);
        }
    }

    /**
     * Records a failed login attempt for IP-based blocking.
     *
     * <p>For WAN clients, this method:
     * <ol>
     *   <li>Creates a new LoginInfoBean if this is the first failed attempt from this IP</li>
     *   <li>Updates existing LoginInfoBean if IP already has failed attempts</li>
     *   <li>Blocks the IP if failed attempts exceed login_max_failed_times threshold</li>
     * </ol>
     *
     * <p>LAN clients (bWAN == false) are never tracked or blocked.
     *
     * @param ip String the client IP address that failed authentication
     * @see LoginInfoBean#updateLoginInfoBean for attempt tracking logic
     */
    public synchronized void updateLoginList(String ip) {
        Properties p = OscarProperties.getInstance();
        // Only track WAN clients (LAN clients are exempt from brute force protection)
        if (bWAN) {
            GregorianCalendar now = new GregorianCalendar();
            // Create new tracking entry if first failure from this IP
            if (llist.get(ip) == null) {
                linfo = new LoginInfoBean(now, Integer.parseInt(p.getProperty("login_max_failed_times")), Integer.parseInt(p.getProperty("login_max_duration")));
            }
            // Update existing tracking entry
            else {
                linfo = (LoginInfoBean) llist.get(ip);
                linfo.updateLoginInfoBean(now, 1);
            }
            llist.put(ip, linfo);
            MiscUtils.getLogger().debug(ip + "  status: " + ((LoginInfoBean) llist.get(ip)).getStatus() + " times: " + linfo.getTimes() + " time: ");
        }
    }

    /**
     * Records a failed login attempt for username-based blocking.
     *
     * <p>For WAN clients, this method:
     * <ol>
     *   <li>Creates a new LoginInfoBean if this is the first failed attempt for this username</li>
     *   <li>Updates existing LoginInfoBean if username already has failed attempts</li>
     *   <li>Blocks the username if failed attempts exceed login_max_failed_times threshold</li>
     * </ol>
     *
     * <p>Username-based blocking is more effective against distributed attacks where
     * attackers use multiple IPs to target a single account.
     *
     * <p>LAN clients (bWAN == false) are never tracked or blocked.
     *
     * @param userName String the username that failed authentication
     * @see LoginInfoBean#updateLoginInfoBean for attempt tracking logic
     */
    public synchronized void updateLockList(String userName) {
        Properties p = OscarProperties.getInstance();
        // Only track WAN clients (LAN clients are exempt from brute force protection)
        if (bWAN) {
            GregorianCalendar now = new GregorianCalendar();
            // Create new tracking entry if first failure for this username
            if (llist.get(userName) == null) {
                linfo = new LoginInfoBean(now, Integer.parseInt(p.getProperty("login_max_failed_times")), Integer.parseInt(p.getProperty("login_max_duration")));
            }
            // Update existing tracking entry
            else {
                linfo = (LoginInfoBean) llist.get(userName);
                linfo.updateLoginInfoBean(now, 1);
            }
            llist.put(userName, linfo);
            MiscUtils.getLogger().debug(userName + "  status: " + ((LoginInfoBean) llist.get(userName)).getStatus() + " times: " + linfo.getTimes() + " time: ");
        }
    }

    /**
     * Unlocks a blocked username, removing it from the login attempt tracking list.
     *
     * <p>This method is typically called by administrators to manually unblock
     * an account that was locked due to failed login attempts.
     *
     * @param userName String the username to unlock
     * @return boolean true if username was found and removed from block list, false if not found
     * @see #findLockList for retrieving all currently blocked usernames/IPs
     */
    public boolean unlock(String userName) {
        boolean bBlock = false;

        // Wait for singleton LoginList to initialize
        while (llist == null) {
            llist = LoginList.getLoginListInstance();
        }
        String sTemp = null;

        // Search for and remove the username from the block list
        if (!llist.isEmpty()) {
            for (Enumeration e = llist.keys(); e.hasMoreElements(); ) {
                sTemp = (String) e.nextElement();
                if (sTemp.equals(userName)) {
                    llist.remove(sTemp);
                    bBlock = true;
                }
            }
        }

        return bBlock;
    }

    /**
     * Retrieves the list of all currently blocked usernames or IPs.
     *
     * <p>This method returns all entries in the login tracking list, including:
     * <ul>
     *   <li>Blocked usernames (if username-based locking is enabled)</li>
     *   <li>Blocked IP addresses (if IP-based blocking is enabled)</li>
     *   <li>Entries with failed attempts that haven't yet reached block threshold</li>
     * </ul>
     *
     * <p>Administrators can use this to view currently locked accounts and
     * selectively unlock them using {@link #unlock}.
     *
     * @return Vector list of blocked usernames or IP addresses (String objects)
     * @deprecated Use List instead of Vector for thread-safe collections
     * @see #unlock for manually unlocking a blocked username/IP
     */
    public Vector findLockList() {
        Vector ret = new Vector();

        // Wait for singleton LoginList to initialize
        while (llist == null) {
            llist = LoginList.getLoginListInstance();
        }
        String sTemp = null;

        // Collect all keys (usernames or IPs) from the tracking list
        if (!llist.isEmpty()) {
            for (Enumeration e = llist.keys(); e.hasMoreElements(); ) {
                sTemp = (String) e.nextElement();
                ret.add(sTemp);
            }
        }

        return ret;
    }
}
