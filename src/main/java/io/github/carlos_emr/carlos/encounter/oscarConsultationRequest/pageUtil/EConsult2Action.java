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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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

    private static final String FRONTEND_ECONSULT_URL_PROPERTY = "frontendEconsultUrl";
    private static final String BACKEND_ECONSULT_URL_PROPERTY = "backendEconsultUrl";
    private static final String CARLOS_BASE_URL_PROPERTY = "carlosBaseUrl";
    private static final String ERROR_RESULT = "error";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String URL_PATH_SEPARATOR = "/";

    // Pattern to validate task parameter - allows alphanumeric, underscore, hyphen, and forward slash
    private static final Pattern VALID_TASK_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-/]+$");
    // Maximum length for task parameter to prevent excessive input
    private static final int MAX_TASK_LENGTH = 100;

    // Fixed eConsult SSO return endpoint on this CARLOS instance.
    private static final String ECONSULT_SSO_LOGIN_PATH = "econsultSSOLogin";

    // Emit the "eConsult configured but carlosBaseUrl missing" warning at most once per JVM
    // (this Struts action is instantiated per request, so an instance flag would spam logs).
    private static final AtomicBoolean ECONSULT_BASE_URL_WARNING_EMITTED = new AtomicBoolean(false);

    private final CarlosProperties oscarProperties = CarlosProperties.getInstance();
    private final String frontendEconsultUrl = oscarProperties.getProperty(FRONTEND_ECONSULT_URL_PROPERTY);
    private final String backendEconsultUrl = oscarProperties.getProperty(BACKEND_ECONSULT_URL_PROPERTY);
    // Trusted, configured public base URL of this CARLOS instance. Used to build the
    // eConsult SSO return URL so it never reflects a spoofable request Host header.
    private final String carlosBaseUrl = oscarProperties.getProperty(CARLOS_BASE_URL_PROPERTY);

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "w", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        warnIfEconsultBaseUrlMissing();

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
            return ERROR_RESULT;
        }

        // Validate demographicNo to ensure it's numeric
        if (demographicNo != null && !demographicNo.isEmpty() && !demographicNo.matches("^\\d+$")) {
            MiscUtils.getLogger().error("Invalid demographicNo parameter");
            return ERROR_RESULT;
        }

        try {
            String redirectUrl = frontendRedirectUrl(
                    frontendEconsultUrl,
                    sessionString(request.getSession(), "oneIdEmail"),
                    sessionString(request.getSession(), "delegateOneIdEmail"),
                    task,
                    demographicNo);
            sendConfiguredEconsultRedirect(response, redirectUrl, frontendEconsultUrl, FRONTEND_ECONSULT_URL_PROPERTY);
        } catch (IOException e) {
            MiscUtils.getLogger().error("There was a problem with the eConsult frontend redirect", e);
        } catch (IllegalArgumentException e) {
            MiscUtils.getLogger().error("Invalid eConsult frontend redirect configuration", e);
            return ERROR_RESULT;
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
            return ERROR_RESULT;
        }

        try {
            String redirectUrl = loginRedirectUrl(
                    backendEconsultUrl,
                    oscarReturnUrl,
                    new Date().getTime() / 1000);
            sendConfiguredEconsultRedirect(response, redirectUrl, backendEconsultUrl, BACKEND_ECONSULT_URL_PROPERTY);
        } catch (IOException e) {
            MiscUtils.getLogger().error("There was a problem with the eConsult login redirect", e);
        } catch (IllegalArgumentException e) {
            MiscUtils.getLogger().error("Invalid eConsult backend redirect configuration", e);
            return ERROR_RESULT;
        }

        return null;
    }

    /**
     * Logs a one-time warning when eConsult is configured (a backend eConsult URL is set) but
     * the trusted {@code carlosBaseUrl} is missing/blank, so an operator discovers the new
     * requirement from the logs rather than only when a user's SSO login fails closed.
     */
    private void warnIfEconsultBaseUrlMissing() {
        if (econsultBaseUrlMisconfigured(backendEconsultUrl, carlosBaseUrl)
                && ECONSULT_BASE_URL_WARNING_EMITTED.compareAndSet(false, true)) {
            MiscUtils.getLogger().warn("eConsult is configured (backendEconsultUrl set) but 'carlosBaseUrl' is missing or blank; "
                    + "eConsult SSO login will fail closed until carlosBaseUrl is set to this instance's public base URL (scheme://host[:port]).");
        }
    }

    /**
     * Returns whether eConsult is in use (a backend eConsult URL is configured) while no
     * trusted public base URL is configured for building the SSO return URL.
     *
     * @param backendEconsultUrl the configured {@code backendEconsultUrl} property value
     * @param carlosBaseUrl      the configured {@code carlosBaseUrl} property value
     * @return {@code true} when eConsult is configured but {@code carlosBaseUrl} is missing or blank
     */
    static boolean econsultBaseUrlMisconfigured(String backendEconsultUrl, String carlosBaseUrl) {
        return !isBlank(backendEconsultUrl) && isBlank(carlosBaseUrl);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
            // locale-independent ASCII compare and emit a literal lowercase scheme rather
            // than case-folding the input, which is sensitive to locale/Unicode mappings.
            String rawScheme = uri.getScheme();
            String scheme;
            if (asciiEqualsIgnoreCase(HTTPS_SCHEME, rawScheme)) {
                scheme = HTTPS_SCHEME;
            } else if (asciiEqualsIgnoreCase(HTTP_SCHEME, rawScheme)) {
                scheme = HTTP_SCHEME;
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

            // URI#getHost()'s companion getPort() does not range-check, so reject an
            // out-of-range port (e.g. :99999) to keep the fail-closed contract consistent
            // with the underscore-host fallback. -1 means "no port".
            if (port != -1 && (port < 0 || port > 65535)) {
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
     * Validates the task parameter to prevent URL injection attacks
     * 
     * @param task The task parameter from the request
     * @return true if the task is valid, false otherwise
     */
    static boolean isValidTask(String task) {
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

    static String frontendRedirectUrl(String frontendEconsultUrl, String oneIdEmail,
            String delegateOneIdEmail, String task, String demographicNo) {
        URI configuredBase = validatedConfiguredRedirectBase(FRONTEND_ECONSULT_URL_PROPERTY, frontendEconsultUrl);
        StringBuilder redirect = new StringBuilder(normalizedBaseUrl(configuredBase));
        redirect.append("?oneid_email=").append(urlParam(oneIdEmail));

        if (delegateOneIdEmail != null && !delegateOneIdEmail.isEmpty()) {
            redirect.append("&delegate_oneid_email=").append(urlParam(delegateOneIdEmail));
        }

        redirect.append("#!/");
        if (task != null && !task.isEmpty()) {
            if (!isValidTask(task)) {
                throw new IllegalArgumentException("Invalid eConsult task");
            }
            redirect.append(task);
        }
        if (demographicNo != null && !demographicNo.isEmpty()) {
            if (!demographicNo.matches("^\\d+$")) {
                throw new IllegalArgumentException("Invalid eConsult demographic number");
            }
            redirect.append("?patient_id=").append(urlParam(demographicNo));
        }

        return validatedRedirectUnderConfiguredBase(configuredBase, redirect.toString(), FRONTEND_ECONSULT_URL_PROPERTY);
    }

    static String loginRedirectUrl(String backendEconsultUrl, String oscarReturnUrl, long loginStartEpochSeconds) {
        URI configuredBase = validatedConfiguredRedirectBase(BACKEND_ECONSULT_URL_PROPERTY, backendEconsultUrl);
        String redirect = normalizedBaseUrl(configuredBase)
                + "SAML2/login?oscarReturnURL=" + urlParam(oscarReturnUrl)
                + "&loginStart=" + loginStartEpochSeconds;

        return validatedRedirectUnderConfiguredBase(configuredBase, redirect, BACKEND_ECONSULT_URL_PROPERTY);
    }

    private static URI validatedConfiguredRedirectBase(String propertyName, String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException(propertyName + " is blank");
        }
        try {
            URI uri = new URI(configuredBaseUrl.trim());
            String scheme = uri.getScheme();
            if (!isHttpScheme(scheme)) {
                throw new IllegalArgumentException(propertyName + " must use http or https");
            }
            if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
                throw new IllegalArgumentException(propertyName + " must include a host");
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalArgumentException(propertyName + " must not include userinfo");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException(propertyName + " must not include query or fragment");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(propertyName + " is not a valid URI", e);
        }
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "URI.toASCIIString intentionally emits ASCII-safe configured eConsult redirect URLs; no locale-sensitive case mapping is used")
    private static String normalizedBaseUrl(URI configuredBase) {
        String base = configuredBase.toASCIIString();
        return base.endsWith(URL_PATH_SEPARATOR) ? base : base + URL_PATH_SEPARATOR;
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "URI.toASCIIString intentionally emits ASCII-safe validated eConsult redirect URLs; no locale-sensitive case mapping is used")
    private static String validatedRedirectUnderConfiguredBase(URI configuredBase, String redirectUrl, String propertyName) {
        try {
            URI redirect = new URI(redirectUrl);
            if (!sameOrigin(configuredBase, redirect)) {
                throw new IllegalArgumentException(propertyName + " redirect changed origin");
            }
            String basePath = configuredBase.getRawPath();
            if (basePath == null || basePath.isEmpty()) {
                basePath = URL_PATH_SEPARATOR;
            } else if (!basePath.endsWith(URL_PATH_SEPARATOR)) {
                basePath = basePath + URL_PATH_SEPARATOR;
            }
            String redirectPath = redirect.getRawPath();
            if (redirectPath == null || !redirectPath.startsWith(basePath)) {
                throw new IllegalArgumentException(propertyName + " redirect left configured path");
            }
            return redirect.toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(propertyName + " redirect is not a valid URI", e);
        }
    }

    private static boolean sameOrigin(URI configuredBase, URI redirect) {
        return asciiEqualsIgnoreCase(configuredBase.getScheme(), redirect.getScheme())
                && asciiEqualsIgnoreCase(configuredBase.getHost(), redirect.getHost())
                && effectivePort(configuredBase) == effectivePort(redirect);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return asciiEqualsIgnoreCase(HTTPS_SCHEME, uri.getScheme()) ? 443 : 80;
    }

    private static String urlParam(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static boolean isHttpScheme(String scheme) {
        return asciiEqualsIgnoreCase(HTTPS_SCHEME, scheme) || asciiEqualsIgnoreCase(HTTP_SCHEME, scheme);
    }

    private static boolean asciiEqualsIgnoreCase(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        for (int i = 0; i < left.length(); i++) {
            if (asciiLower(left.charAt(i)) != asciiLower(right.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static char asciiLower(char value) {
        if (value >= 'A' && value <= 'Z') {
            return (char) (value + ('a' - 'A'));
        }
        return value;
    }

    private static String sessionString(HttpSession session, String attributeName) {
        Object value = session.getAttribute(attributeName); // nosemgrep: java.servlets.security.tainted-session-from-http-request.tainted-session-from-http-request -- authenticated session identity value is URL-encoded before being added to the configured eConsult redirect
        return value instanceof String string ? string : null;
    }

    // FindSecBugs UNVALIDATED_REDIRECT: eConsult redirects intentionally leave CARLOS for a deployment-configured eConsult service. The configured base is parsed as http(s), rejects userinfo/query/fragment, request data is URL-encoded, and the final redirect must remain under the configured origin/path.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "eConsult redirects are built from validated deployment-configured http(s) base URLs; request data is URL-encoded and the final target must remain under the configured origin/path")
    private void sendConfiguredEconsultRedirect(HttpServletResponse response, String redirectUrl,
            String configuredBaseUrl, String propertyName) throws IOException {
        URI configuredBase = validatedConfiguredRedirectBase(propertyName, configuredBaseUrl);
        response.sendRedirect(validatedRedirectUnderConfiguredBase(configuredBase, redirectUrl, propertyName));
    }

}
