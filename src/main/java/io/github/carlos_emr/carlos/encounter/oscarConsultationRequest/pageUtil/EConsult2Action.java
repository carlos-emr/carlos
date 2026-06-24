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


package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * Class for use with the Ontario MD / eHealth eConsult project
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class EConsult2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    // Pattern to validate task parameter - allows alphanumeric, underscore, hyphen, and forward slash
    private static final Pattern VALID_TASK_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-/]+$");
    // Maximum length for task parameter to prevent excessive input
    private static final int MAX_TASK_LENGTH = 100;

    // Fixed eConsult SSO return endpoint on this CARLOS instance.
    private static final String ECONSULT_SSO_LOGIN_PATH = "econsultSSOLogin";

    private final CarlosProperties oscarProperties = CarlosProperties.getInstance();
    private final String frontendEconsultUrl = oscarProperties.getProperty("frontendEconsultUrl");
    private final String backendEconsultUrl = oscarProperties.getProperty("backendEconsultUrl");
    // Trusted, configured public base URL of this CARLOS instance. Used to build the
    // eConsult SSO return URL so it never reflects a spoofable request Host header.
    private final String carlosBaseUrl = oscarProperties.getProperty("carlosBaseUrl");

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "w", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        if ("login".equals(request.getParameter("method"))) {
            return login();
        }
        return frontend();
    }

    /**
     * Builds a proper eConsult frontend redirect link
     * <p>
     * ie: /?{oneIdEmail}&amp;{delegateOneIdEmail}#!/{task}?{parameter}
     */
    public String frontend() {

        // no token, no fun.
        String oneIdToken = (String) request.getSession().getAttribute("oneid_token");
        if (oneIdToken == null || oneIdToken.isEmpty()) {
            return login();
        }

        String demographicNo = request.getParameter("demographicNo");
        String task = request.getParameter("task");
        
        // Validate task parameter to prevent URL injection
        if (!isValidTask(task)) {
            MiscUtils.getLogger().error("Invalid task parameter provided");
            return "error";
        }
        
        StringBuilder stringBuilder = new StringBuilder(frontendEconsultUrl);

        if (!frontendEconsultUrl.endsWith(File.separator)) {
            stringBuilder.append(File.separator);
        }

        setLoginId(stringBuilder, request);

        // Add the validated task
        if (task != null && !task.isEmpty()) {
            stringBuilder.append(task);
        }

        // method parameters.
        if (demographicNo != null && !demographicNo.isEmpty()) {
            // Validate demographicNo to ensure it's numeric
            if (!demographicNo.matches("^\\d+$")) {
                MiscUtils.getLogger().error("Invalid demographicNo parameter");
                return "error";
            }
            stringBuilder.append(String.format("?%1$s=%2$s", "patient_id", demographicNo));
        }

        try {
            response.sendRedirect(stringBuilder.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().error("There was a problem with the redirect of " + stringBuilder.toString(), e);
        }

        return null;
    }

    /**
     * Creates a proper login redirect.
     */
    public String login() {

        // The SSO return URL must NOT be derived from the client-controlled request Host
        // (Host / X-Forwarded-Host can be spoofed). Build it from the configured, trusted
        // public base URL of this CARLOS instance so the post-login return - and any token
        // material the eConsult side reflects back to it - cannot be steered to an attacker
        // origin.
        String oscarReturnUrl = buildSsoReturnUrl(carlosBaseUrl, request.getContextPath());
        if (oscarReturnUrl == null) {
            MiscUtils.getLogger().error("Cannot build eConsult SSO return URL: the 'carlosBaseUrl' property is missing or invalid; refusing to fall back to the request Host.");
            return "error";
        }

        StringBuilder stringBuilder = new StringBuilder(backendEconsultUrl);

        if (!backendEconsultUrl.endsWith(File.separator)) {
            stringBuilder.append(File.separator);
        }

        stringBuilder.append("SAML2/login");

        try {
            stringBuilder.append(String.format("?%1$s=%2$s", "oscarReturnURL", URLEncoder.encode(oscarReturnUrl, StandardCharsets.UTF_8.toString())));
            stringBuilder.append(String.format("&%1$s=%2$s", "loginStart", new Date().getTime() / 1000));
            response.sendRedirect(stringBuilder.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().error("There was a problem with the redirect of " + stringBuilder.toString(), e);
        }

        return null;
    }

    /**
     * Builds the eConsult SSO return URL from the configured public base URL of this
     * CARLOS instance plus this deployment's context path and the fixed
     * {@code /econsultSSOLogin} endpoint.
     * <p>
     * Returns {@code null} when no trusted base URL is configured, so the caller can fail
     * closed instead of emitting a return URL whose host reflects a spoofable request Host
     * header.
     *
     * @param configuredBaseUrl the configured {@code carlosBaseUrl} property value
     * @param contextPath       this deployment's servlet context path (server-derived, not
     *                          Host-derived); may be {@code null} or empty for the root context
     * @return the absolute return URL, or {@code null} when no trusted base URL is configured
     */
    static String buildSsoReturnUrl(String configuredBaseUrl, String contextPath) {
        String trustedOrigin = normalizedConfiguredOrigin(configuredBaseUrl);
        if (trustedOrigin == null) {
            return null;
        }

        String normalizedContextPath = (contextPath == null) ? "" : contextPath;

        return trustedOrigin + normalizedContextPath + "/" + ECONSULT_SSO_LOGIN_PATH;
    }

    /**
     * Validates a configured base URL and returns its origin ({@code scheme://host[:port]}),
     * or {@code null} when it is absent or not a safe absolute http(s) origin. Any path,
     * query, fragment, or embedded credentials cause the value to be ignored or rejected;
     * only the scheme, host, and port are trusted.
     *
     * @param configuredBaseUrl the configured base URL value to validate
     * @return the normalized origin, or {@code null} if it is missing or unsafe
     */
    private static String normalizedConfiguredOrigin(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.trim().isEmpty()) {
            return null;
        }

        try {
            URI uri = new URI(configuredBaseUrl.trim());

            // URI schemes are ASCII and case-insensitive (RFC 3986). Match with the
            // locale-independent equalsIgnoreCase and emit a literal lowercase scheme rather
            // than case-folding the input, which is sensitive to locale/Unicode mappings.
            String rawScheme = uri.getScheme();
            String scheme;
            if ("https".equalsIgnoreCase(rawScheme)) {
                scheme = "https";
            } else if ("http".equalsIgnoreCase(rawScheme)) {
                scheme = "http";
            } else {
                return null;
            }

            // The base must be a bare origin: reject query or fragment.
            if (uri.getQuery() != null || uri.getFragment() != null) {
                return null;
            }

            String authority = uri.getAuthority();
            if (authority == null || authority.isEmpty()) {
                return null;
            }
            // Reject embedded credentials. Checked on the authority directly because
            // URI#getUserInfo() returns null when the host is not RFC 2396-compliant
            // (e.g. contains an underscore), which would otherwise slip credentials through.
            if (authority.indexOf('@') >= 0) {
                return null;
            }

            String host = uri.getHost();
            int port = uri.getPort();

            // URI#getHost() returns null when the host contains characters that are invalid
            // per RFC 2396 (e.g. an underscore, common in internal/dev hostnames). Fall back
            // to the authority so such a configured base is still accepted; credentials were
            // already rejected above.
            if (host == null) {
                int portSeparator = authority.lastIndexOf(':');
                if (portSeparator >= 0) {
                    host = authority.substring(0, portSeparator);
                    port = parsePort(authority.substring(portSeparator + 1));
                    if (port == -1) {
                        return null;
                    }
                } else {
                    host = authority;
                    port = -1;
                }
                // The fallback host was split out of a non-RFC2396 authority by hand; reject a
                // leftover ':' from a malformed multi-colon authority (e.g. host:8080:9090).
                // This check stays inside the fallback so bracketed IPv6 hosts resolved by
                // URI#getHost() (which legitimately contain ':') are not rejected.
                if (host.indexOf(':') >= 0) {
                    return null;
                }
            }

            if (host.isEmpty()) {
                return null;
            }

            StringBuilder origin = new StringBuilder(scheme).append("://").append(host);
            if (port != -1) {
                origin.append(':').append(port);
            }

            return origin.toString();
        } catch (URISyntaxException e) {
            MiscUtils.getLogger().error("Invalid 'carlosBaseUrl' property configured for the eConsult SSO return URL", e);
            return null;
        }
    }

    /**
     * Parses a URI authority port component, accepting only a non-empty run of ASCII digits
     * within the valid 0-65535 range. Returns {@code -1} for anything else (empty, signed,
     * non-numeric, or out of range) so the caller can fail closed. The explicit digit check
     * is required because {@code Integer.parseInt} alone would tolerate a leading
     * {@code +}/{@code -} sign and so accept a malformed port.
     *
     * @param portValue the raw port text following the authority's {@code ':'} separator
     * @return the parsed port, or {@code -1} when the value is not a valid port
     */
    private static int parsePort(String portValue) {
        if (portValue.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < portValue.length(); i++) {
            char c = portValue.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
        }
        try {
            int port = Integer.parseInt(portValue);
            return (port >= 0 && port <= 65535) ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Adds the proper user name line for both the user and user delegate.
     * <p>
     * ie: ?{oneIdEmail}&{delegateOneIdEmail}#!/
     */
    private void setLoginId(StringBuilder stringBuilder, HttpServletRequest request) {

        String oneIdEmail = (String) request.getSession().getAttribute("oneIdEmail");
        String delegateOneIdEmail = (String) request.getSession().getAttribute("delegateOneIdEmail");

        try {
            stringBuilder.append(String.format("?%1$s=%2$s", "oneid_email", URLEncoder.encode(oneIdEmail, StandardCharsets.UTF_8.toString())));

            // Add if there is a delegate too.
            if (delegateOneIdEmail != null && !delegateOneIdEmail.isEmpty()) {
                stringBuilder.append(String.format("&%1$s=%2$s", "delegate_oneid_email", URLEncoder.encode(delegateOneIdEmail, StandardCharsets.UTF_8.toString())));
            }

            // Add the shebang
            stringBuilder.append(String.format("#!%s", File.separator));
        } catch (UnsupportedEncodingException e) {
            MiscUtils.getLogger().error("There was a problem with construction of the login ids " + stringBuilder.toString(), e);
        }
    }
    
    /**
     * Validates the task parameter to prevent URL injection attacks
     * 
     * @param task The task parameter from the request
     * @return true if the task is valid, false otherwise
     */
    private boolean isValidTask(String task) {
        // Allow null or empty tasks (will be handled later)
        if (task == null || task.isEmpty()) {
            return true;
        }
        
        // Check length constraint
        if (task.length() > MAX_TASK_LENGTH) {
            return false;
        }
        
        // Check if task matches the allowed pattern
        // This prevents URL injection by restricting to safe characters
        if (!VALID_TASK_PATTERN.matcher(task).matches()) {
            return false;
        }
        
        // Prevent path traversal attacks
        if (task.contains("..") || task.contains("//") || task.contains("\\")) {
            return false;
        }
        
        // Prevent protocol injection (http://, https://, javascript:, etc.)
        String lowerTask = task.toLowerCase();
        if (lowerTask.contains("://") || lowerTask.startsWith("javascript:") 
            || lowerTask.startsWith("data:") || lowerTask.startsWith("vbscript:")) {
            return false;
        }
        
        return true;
    }


}
