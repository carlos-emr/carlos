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
    private static final String ERROR_RESULT = "error";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String URL_PATH_SEPARATOR = "/";

    // Pattern to validate task parameter - allows alphanumeric, underscore, hyphen, and forward slash
    private static final Pattern VALID_TASK_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-/]+$");
    // Maximum length for task parameter to prevent excessive input
    private static final int MAX_TASK_LENGTH = 100;

    private final CarlosProperties oscarProperties = CarlosProperties.getInstance();
    private final String frontendEconsultUrl = oscarProperties.getProperty(FRONTEND_ECONSULT_URL_PROPERTY);
    private final String backendEconsultUrl = oscarProperties.getProperty(BACKEND_ECONSULT_URL_PROPERTY);

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

        //Gets the request URL
        StringBuffer oscarUrl = request.getRequestURL();

        //Determines the initial length by subtracting the length of the servlet path from the full url's length
        Integer urlLength = oscarUrl.length() - request.getServletPath().length();

        //Sets the length of the URL, found by subtracting the length of the servlet path from the length of the full URL, that way it only gets up to the context path
        oscarUrl.setLength(urlLength);

        oscarUrl.append("/econsultSSOLogin");

        try {
            String redirectUrl = loginRedirectUrl(
                    backendEconsultUrl,
                    oscarUrl.toString(),
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

    private static String normalizedBaseUrl(URI configuredBase) {
        String base = configuredBase.toASCIIString();
        return base.endsWith(URL_PATH_SEPARATOR) ? base : base + URL_PATH_SEPARATOR;
    }

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
        return value instanceof String ? (String) value : null;
    }

    // FindSecBugs UNVALIDATED_REDIRECT: eConsult redirects intentionally leave CARLOS for a deployment-configured eConsult service. The configured base is parsed as http(s), rejects userinfo/query/fragment, request data is URL-encoded, and the final redirect must remain under the configured origin/path.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "eConsult redirects are built from validated deployment-configured http(s) base URLs; request data is URL-encoded and the final target must remain under the configured origin/path")
    private void sendConfiguredEconsultRedirect(HttpServletResponse response, String redirectUrl,
            String configuredBaseUrl, String propertyName) throws IOException {
        URI configuredBase = validatedConfiguredRedirectBase(propertyName, configuredBaseUrl);
        response.sendRedirect(validatedRedirectUnderConfiguredBase(configuredBase, redirectUrl, propertyName));
    }

}
