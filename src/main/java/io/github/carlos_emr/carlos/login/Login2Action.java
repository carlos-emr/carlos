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

import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.utility.*;
import org.apache.struts2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.apache.commons.codec.binary.Base32;
import javax.crypto.spec.SecretKeySpec;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.PMmodule.web.utils.UserRoleUtils;
import io.github.carlos_emr.carlos.decisionSupport.service.DSService;
import io.github.carlos_emr.carlos.managers.AppManager;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.managers.UserSessionManager;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.util.AlertTimer;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

/**
 * Struts2 action class that handles user authentication and login flow for CARLOS EMR.
 *
 * <p>This action manages the complete authentication lifecycle including:
 * <ul>
 *   <li>Standard username/password/PIN authentication</li>
 *   <li>Multi-factor authentication (MFA) validation and registration</li>
 *   <li>Forced password reset flows</li>
 *   <li>Session management and invalidation</li>
 *   <li>Provider preference initialization</li>
 *   <li>Facility selection and assignment</li>
 *   <li>Mobile device detection and optimization</li>
 *   <li>AJAX response handling</li>
 *   <li>IP-based account lockout for failed login attempts</li>
 * </ul>
 *
 * <p>Security features include:
 * <ul>
 *   <li>Input validation for username, password, and PIN formats</li>
 *   <li>Protection against brute force attacks via IP blocking</li>
 *   <li>Secure session regeneration after successful authentication</li>
 *   <li>Server-side password policy enforcement for forced password resets</li>
 *   <li>Short-lived opaque credential-cache tokens for multi-request login state</li>
 *   <li>OWASP encoding for all user-provided output</li>
 *   <li>TOTP-based MFA support (RFC 6238)</li>
 *   <li>PHI-compliant audit logging</li>
 * </ul>
 *
 * <p>The authentication flow varies based on the request type:
 * <ol>
 *   <li>Standard login: validates credentials via {@link LoginCheckLogin#auth}</li>
 *   <li>MFA validation: verifies TOTP code against stored secret</li>
 *   <li>Forced password reset: validates old password and persists new password</li>
 *   <li>Facility selection: allows multi-facility providers to choose working context</li>
 * </ol>
 *
 * <p>CRITICAL: This action ONLY accepts POST requests for security reasons.
 * GET requests are rejected to prevent credential exposure in URL parameters or server logs.
 * Forced password reset uses a dedicated POST endpoint that remains behind CSRFGuard even though
 * it is exempt from the authenticated-session filter; do not move password updates back onto a
 * public GET/JSP route.
 *
 * <p>This action integrates with Spring-managed services for:
 * <ul>
 *   <li>Provider management ({@link ProviderManager})</li>
 *   <li>Security operations ({@link SecurityManager})</li>
 *   <li>MFA operations ({@link MfaManager})</li>
 *   <li>Session tracking ({@link UserSessionManager})</li>
 *   <li>Decision support ({@link DSService})</li>
 * </ul>
 *
 * @see LoginCheckLogin for authentication business logic
 * @see LoginCheckLoginBean for authentication validation
 * @see MfaManager for multi-factor authentication operations
 * @see SecurityManager for password encoding and validation
 * @since 2026-02-10
 */
public final class Login2Action extends ActionSupport {
    /** Servlet request from Struts2 context */
    HttpServletRequest request = ServletActionContext.getRequest();

    /** Servlet response from Struts2 context */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Query string parameter key for facility ID selection.
     *
     * <p>This constant is used when a provider belongs to multiple facilities
     * and must select which facility context to work in. The selected facility ID
     * is passed as a request parameter using this key.
     *
     * @see #execute() for facility selection logic
     */
    public static final String SELECTED_FACILITY_ID = "selectedFacilityId";

    /** Logger instance for authentication events and errors */
    private static final Logger logger = MiscUtils.getLogger();

    /** Log message prefix for authentication-related log entries */
    private static final String LOG_PRE = "Login!@#$: ";
    private static final int DEFAULT_PASSWORD_MIN_LENGTH = 8;
    private static final int DEFAULT_PASSWORD_MIN_GROUPS = 3;
    private static final String DEFAULT_PASSWORD_LOWER_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final String DEFAULT_PASSWORD_UPPER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DEFAULT_PASSWORD_DIGIT_CHARS = "0123456789";
    private static final String DEFAULT_PASSWORD_SPECIAL_CHARS = "! @#$%^&*()_+|~-=`{}[]\\:\";'<>?,./";

    /**
     * Session attribute name holding an opaque random token that references credential
     * material stashed in {@link LoginCredentialCache}. The token itself contains no
     * credential information; credential hashes and PINs are never placed in the session.
     */
    public static final String LOGIN_CREDENTIALS_TOKEN_ATTR = "loginCredentialsToken";

    /**
     * Session-scoped, one-request error message used by the forced-reset retry redirect.
     *
     * <p>Validation failures intentionally redirect back to the GET-only reset page instead of
     * forwarding after POST. That keeps browser refresh/back behavior safe while preserving the
     * still-valid credential-cache token for retryable mistakes such as a bad old password.</p>
     */
    public static final String FORCE_PASSWORD_RESET_ERROR_ATTR = "forcePasswordResetError";

    /**
     * Session marker for the short-lived MFA challenge state.
     *
     * <p>The normal {@code user} session attribute is intentionally not set until the
     * MFA code has been validated. LoginFilter treats {@code user} as an authenticated
     * session, so MFA state must use distinct attributes that grant no application access.</p>
     */
    public static final String PENDING_MFA_AUTH_ATTR = "pendingMfaAuthentication";
    private static final String PENDING_MFA_LOGIN_CHECK_ATTR = "pendingMfaLoginCheck";
    private static final String PENDING_MFA_AUTH_RESULT_ATTR = "pendingMfaAuthResult";

    /** Spring-managed service for provider data access and management */
    private final ProviderManager providerManager = SpringUtils.getBean(ProviderManager.class);

    /** Spring-managed service for application-level operations */
    private final AppManager appManager = SpringUtils.getBean(AppManager.class);

    /** DAO for facility data access */
    private final FacilityDao facilityDao = SpringUtils.getBean(FacilityDao.class);

    /** DAO for provider preference data access */
    private final ProviderPreferenceDao providerPreferenceDao = SpringUtils.getBean(ProviderPreferenceDao.class);

    /** DAO for provider data access */
    private final ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    /** DAO for user property data access */
    private final UserPropertyDAO propDao = SpringUtils.getBean(UserPropertyDAO.class);

    /** Decision support service for clinical decision support features */
    private final DSService dsService = SpringUtils.getBean(DSService.class);

    /** DAO for OAuth service request token management */
    private final ServiceRequestTokenDao serviceRequestTokenDao = SpringUtils.getBean(ServiceRequestTokenDao.class);

    /** Security manager for password encoding and validation */
    private final SecurityManager securityManager = SpringUtils.getBean(SecurityManager.class);

    /** DAO for security data access (user accounts and credentials) */
    private final SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);

    /** Session manager for tracking active user sessions */
    private final UserSessionManager userSessionManager = SpringUtils.getBean(UserSessionManager.class);

    /** MFA manager for multi-factor authentication operations */
    private final MfaManager mfaManager = SpringUtils.getBean(MfaManager.class);

    /** Jackson ObjectMapper for JSON response serialization */
    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Main execution method that handles user authentication and login flow.
     *
     * <p>This method processes various authentication scenarios:
     * <ul>
     *   <li>Standard login with username, password, and PIN</li>
     *   <li>MFA code validation after initial authentication</li>
     *   <li>MFA registration for first-time MFA users</li>
     *   <li>Forced password reset after administrator-mandated password change</li>
     *   <li>Facility selection for multi-facility providers</li>
     *   <li>OAuth token association for integrated services</li>
     * </ul>
     *
     * <p>Security validations performed:
     * <ul>
     *   <li>POST-only request method enforcement</li>
     *   <li>Username format validation (alphanumeric, max 10 characters)</li>
     *   <li>PIN format validation (4 digits)</li>
     *   <li>IP-based brute force protection</li>
     *   <li>Provider inactive status checking</li>
     *   <li>Password expiration checking</li>
     *   <li>Absolute URL redirect prevention</li>
     * </ul>
     *
     * <p>Session management:
     * <ul>
     *   <li>Existing session invalidation before creating new session</li>
     *   <li>Session timeout set to 2 hours (7200 seconds)</li>
     *   <li>Provider preferences loaded into session</li>
     *   <li>Facility context established</li>
     *   <li>CAISI program management settings initialized (if enabled)</li>
     * </ul>
     *
     * <p>Audit logging:
     * <ul>
     *   <li>Successful login events logged with IP address</li>
     *   <li>Failed login attempts tracked for IP blocking</li>
     *   <li>Inactive account access attempts logged</li>
     *   <li>Password expiration events logged</li>
     * </ul>
     *
     * <p>Return values:
     * <ul>
     *   <li>"provider" - Standard provider interface</li>
     *   <li>"caisiPMM" - CAISI program management module interface</li>
     *   <li>"programLocation" - Program location selection interface</li>
     *   <li>"patientIntake" - Patient intake role interface</li>
     *   <li>"mfaHandler" - MFA validation/registration interface</li>
     *   <li>"failure" - Authentication cannot continue safely</li>
     *   <li>NONE - Processing complete (redirect already sent)</li>
     *   <li>null - AJAX response sent (no forward needed)</li>
     * </ul>
     *
     * @return String Struts2 result name indicating next page to display
     * @throws ServletException if servlet-level error occurs
     * @throws IOException if I/O error occurs during redirect or response writing
     * @see LoginCheckLogin#auth for authentication logic
     * @see MfaManager#getQRCodeImageData for MFA QR code generation
     * @see #completeAuthenticatedLogin for MFA continuation logic
     */
    public String execute() throws ServletException, IOException {

        // >> 1. Initial Checks and Mobile Detection
        // SECURITY: Reject GET requests to prevent credential exposure in URLs/logs
        if (!"POST".equals(request.getMethod())) {
            MiscUtils.getLogger().error("Someone is trying to login with a GET request.", new Exception());
            String newURL = loginFailedRedirectUrl(message("login.errorApplicationError"));
            response.sendRedirect(newURL);
            return NONE;
        }

        // Determine if client expects JSON response (for AJAX login forms)
        boolean ajaxResponse = request.getParameter("ajaxResponse") != null ? Boolean.valueOf(request.getParameter("ajaxResponse")) : false;
        boolean isMobileOptimized = false;

        // Extract client information for audit logging and device detection
        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("user-agent");
        String accept = request.getHeader("Accept");

        // Detect mobile devices to serve optimized interface
        UAgentInfo userAgentInfo = new UAgentInfo(userAgent, accept);
        isMobileOptimized = userAgentInfo.detectMobileQuick();

        // Allow user to override mobile detection and request full desktop site
        String submitType = request.getParameter("submit");

        if (submitType != null && "full".equalsIgnoreCase(submitType)) {
            isMobileOptimized = false;
        }

        LoginCheckLogin cl;

        // Determine if this is MFA code validation flow vs. initial authentication
        boolean isMfaVerifyFlow = (this.code != null && !this.code.isEmpty());
        
        if (isMfaVerifyFlow) {
            return validateMfaAndCompleteLogin(ip, isMobileOptimized, submitType, ajaxResponse);
        }
        
        cl = new LoginCheckLogin();
        String userName = "";
        String password = "";
        String pin = "";
        String nextPage = "";
        boolean forcedpasswordchange = true;

        // >> 2. Forced Password Change Handling
        if (request.getParameter("forcedpasswordchange") != null
                && request.getParameter("forcedpasswordchange").equalsIgnoreCase("true")) {
            // Coming back from force password change. Credentials are held in the
            // server-side LoginCredentialCache, referenced by an opaque token in the
            // session (the credentials themselves are NEVER placed in the session).
            // Retryable validation failures use peek() so the user can correct the
            // form. Once all validation passes, consume() below makes the terminal
            // submit single-use before the password is persisted.
            HttpSession pendingResetSession = request.getSession(false);
            Object credsTokenAttr = pendingResetSession == null
                    ? null
                    : pendingResetSession.getAttribute(LOGIN_CREDENTIALS_TOKEN_ATTR);
            String credsToken = credsTokenAttr instanceof String ? (String) credsTokenAttr : null;
            LoginCredentialCache.LoginCredentials cached = LoginCredentialCache.getInstance().peek(credsToken);
            if (cached == null) {
                // Token missing or expired (>5 min, or already consumed). Treat as a
                // session-timeout and send the user back to the login screen rather than
                // proceeding with empty credentials.
                logger.info("Forced password reset submitted without valid credential-cache token; redirecting to login");
                removeAttributesFromSession(request);
                response.sendRedirect(loginFailedRedirectUrl(message("provider.providerchangepassword.errorSessionExpired")));
                return NONE;
            }

            userName = cached.getUserName();

            // Username is only letters and numbers
            if (userName == null || !Pattern.matches("[a-zA-Z0-9]{1,10}", userName)) {
                userName = "Invalid Username";
            }

            password = cached.getEncodedPassword();

            pin = cached.getPin();

            // pins are integers only
            if (pin == null || !Pattern.matches("[0-9]{4}", pin)) {
                pin = "";
            }
            nextPage = cached.getNextPage();
            // Validate nextPage retrieved from cache to prevent open redirect (CWE-601 defense in depth)
            if (!RedirectValidationUtils.isValidRelativeRedirect(nextPage)) {
                if (nextPage != null) {
                    logger.warn("Rejected invalid nextPage from credential cache: {}", LogSanitizer.sanitize(nextPage));
                }
                nextPage = null;
            }

            String newPassword = this.getNewPassword();
            String confirmPassword = this.getConfirmPassword();
            String oldPassword = this.getOldPassword();

            try {
                String errorStr = errorHandling(userName, password, newPassword, confirmPassword, oldPassword);

                // Keep the token alive only for retryable validation failures. This preserves
                // normal form correction while still allowing the success path to consume the
                // token atomically before persistence.
                if (errorStr != null && !errorStr.isEmpty()) {
                    return redirectForcePasswordResetRetry(errorStr);
                }

                LoginCredentialCache.LoginCredentials terminalCredentials =
                        LoginCredentialCache.getInstance().consume(credsToken);
                if (terminalCredentials == null) {
                    logger.info("Forced password reset credential-cache token was replayed or expired before persistence");
                    removeAttributesFromSession(request);
                    response.sendRedirect(loginFailedRedirectUrl(message("provider.providerchangepassword.errorSessionExpired")));
                    return NONE;
                }
                userName = terminalCredentials.getUserName();

                persistNewPassword(userName, newPassword);

                password = newPassword;

                // Remove the attributes from session
                removeAttributesFromSession(request);
            } catch (Exception e) {
                logger.error("Forced password reset failed during terminal persistence or session cleanup", e);
                String newURL = loginFailedRedirectUrl(message("provider.providerchangepassword.errorSessionSetup"));
                removeAttributesFromSession(request);

                response.sendRedirect(newURL);
                return NONE;
            }

            // make sure this checking doesn't happen again
            forcedpasswordchange = false;

        } else {
            // >> 3. Standard Login Attempt
            userName = this.getUsername();

            // Username is only letters and numbers
            if (userName == null || !Pattern.matches("[a-zA-Z0-9]{1,10}", userName)) {
                userName = "Invalid Username";
            }
            password = this.getPassword();
            pin = this.getPin();

            // pins are integers only
            if (pin == null || !Pattern.matches("[0-9]{4}", pin)) {
                pin = "";
            }
            nextPage = request.getParameter("nextPage");

            logger.debug("nextPage: {}", LogSanitizer.sanitize(nextPage));
            if (nextPage != null) {
                if (!RedirectValidationUtils.isValidRelativeRedirect(nextPage)) {
                    logger.warn("Rejected redirect URL: {}", LogSanitizer.sanitize(nextPage));
                    response.sendRedirect(request.getContextPath() + "/loginfailed");
                    return NONE;
                } else {
                    // set current facility - validate format and verify the authenticated user has access
                    String facilityIdString = request.getParameter(SELECTED_FACILITY_ID);
                    // Validate format: must be non-null positive integer (max 9 digits to stay within Integer range)
                    if (facilityIdString == null || !facilityIdString.matches("\\d{1,9}")) {
                        logger.warn("Invalid or missing facility ID in facility selection request");
                        response.sendRedirect(request.getContextPath() + "/loginfailed");
                        return NONE;
                    }
                    int facilityId = Integer.parseInt(facilityIdString);
                    String username = (String) request.getSession().getAttribute("user");
                    // Authorization check: verify the authenticated provider is permitted to access this facility (CWE-501)
                    List<Integer> allowedFacilityIds = providerDao.getFacilityIds(username);
                    if (!allowedFacilityIds.contains(facilityId)) {
                        logger.warn("Provider {} attempted unauthorized facility selection: {}", LogSanitizer.sanitize(username), facilityId);
                        response.sendRedirect(request.getContextPath() + "/loginfailed");
                        return NONE;
                    }
                    Facility facility = facilityDao.find(facilityId);
                    if (facility == null) {
                        logger.warn("Selected facility not found: {}", facilityId);
                        response.sendRedirect(request.getContextPath() + "/loginfailed");
                        return NONE;
                    }
                    // facilityId validated via Integer.parseInt() and facilityDao.find() above
                    request.getSession().setAttribute(SessionConstants.CURRENT_FACILITY, facility); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
                    LogAction.addLog(username, LogConst.LOGIN, LogConst.CON_LOGIN, "facilityId=" + facilityId, ip);
                    // FP for open-redirect scanners (CodeQL java/unvalidated-url-redirection #5909):
                    // nextPage is validated by RedirectValidationUtils.isValidRelativeRedirect()
                    // immediately above before entering this branch; it rejects absolute URIs,
                    // protocol-relative URLs, backslash bypasses, control characters, and
                    // path-traversal sequences — only safe relative paths reach here.
                    response.sendRedirect(nextPage); // nosemgrep: javasecurity.S5146, java.lang.security.audit.servlets.unvalidated-redirect.unvalidated-redirect-java -- gated by RedirectValidationUtils.isValidRelativeRedirect() before entering this branch // lgtm[java/unvalidated-url-redirection]
                    return NONE;
                }
            }

            if (cl.isBlock(ip, userName)) {
                logger.info("{} Blocked: {}", LOG_PRE, LogSanitizer.sanitize(userName));
                // return mapping.findForward(where); //go to block page
                // change to block page
                String lockedMessage = message("login.errorAccountLocked");
                String newURL = loginFailedRedirectUrl(lockedMessage);

                if (ajaxResponse) {
                    ObjectNode json = objectMapper.createObjectNode();
                    json.put("success", false);
                    json.put("error", lockedMessage);
                    response.setContentType("application/json");
                    response.getWriter().write(json.toString());
                    return null;
                }

                response.sendRedirect(newURL);
                return NONE;
            }

            logger.debug("ip was not blocked: {}", LogSanitizer.sanitize(ip));
        }

        // >> 4. Authentication
        /*
         * THIS IS THE GATEWAY.
         */
        String[] strAuth;
        try {
            strAuth = cl.auth(userName, password, pin, ip);
        } catch (Exception e) {
            logger.error("Authentication provider failed during login: user={}, remote={}, ajax={}",
                    LogSanitizer.sanitize(userName), LogSanitizer.sanitize(ip), ajaxResponse, e);
            String unableToProcessMessage = message("login.errorUnableToProcess");
            String newURL = loginFailedRedirectUrl(unableToProcessMessage);

            if (ajaxResponse) {
                ObjectNode json = objectMapper.createObjectNode();
                json.put("success", false);
                json.put("error", unableToProcessMessage);
                response.setContentType("application/json");
                response.getWriter().write(json.toString());
                return null;
            }

            response.sendRedirect(newURL);
            return NONE;
        }
        
        logger.debug("strAuth : {}", LogSanitizer.sanitize(Arrays.toString(strAuth)));
        
        // >> 5. Successful Login Handling
        if (strAuth != null && strAuth.length != 1) { // login successfully

            // is the providers record inactive?
            Provider p = providerDao.getProvider(strAuth[0]);
            if (p == null || (p.getStatus() != null && p.getStatus().equals("0"))) {
                logger.info("{} Inactive: {}", LOG_PRE, LogSanitizer.sanitize(userName));
                LogAction.addLog(strAuth[0], "login", "failed", "inactive");

                String newURL = loginFailedRedirectUrl(message("login.errorAccountInactive"));

                response.sendRedirect(newURL);
                return NONE;
            }

            /*
             * This section is added for forcing the initial password change.
             */
            Security security = getSecurity(userName);
            if (security == null) {
                logger.error("Authenticated user has no security record: {}", LogSanitizer.sanitize(userName));
                response.sendRedirect(loginFailedRedirectUrl(message("login.errorSecurityRecordMissing")));
                return NONE;
            }
            // Default mandatory password-reset enforcement to enabled. Older deployments that did
            // not define the property still need server-side forced-reset handling when the
            // Security row is flagged, otherwise a missing config key silently bypasses a security
            // control.
            if (isMandatoryPasswordResetEnabled() &&
                    security.isForcePasswordReset() != null && security.isForcePasswordReset()
                    && forcedpasswordchange) {

                try {
                    setUserInfoToSession(request, userName, password, pin, nextPage);
                } catch (Exception e) {
                    logger.error("Unable to stage forced password reset credentials", e);
                    String newURL = loginFailedRedirectUrl(message("provider.providerchangepassword.errorSessionSetup"));
                    removeAttributesFromSession(request);
                    response.sendRedirect(newURL);
                    return NONE;
                }

                response.sendRedirect(request.getContextPath() + "/forcepasswordreset");
                return NONE;
            }

            if (MfaManager.isOscarMfaEnabled() && security.isUsingMfa()) {
                return beginPendingMfaChallenge(cl, strAuth, security, ip);
            }

            return completeAuthenticatedLogin(cl, strAuth, ip, isMobileOptimized, submitType, ajaxResponse);

        }
        // >> 6. Authentication Failure Handling
        // expired password
        else if (strAuth != null && strAuth.length == 1 && strAuth[0].equals("expired")) {
            logger.warn("Expired password");
            cl.updateLoginList(ip, userName);
            String expiredMessage = message("login.errorAccountExpired");
            String newURL = loginFailedRedirectUrl(expiredMessage);

            if (ajaxResponse) {
                ObjectNode json = objectMapper.createObjectNode();
                json.put("success", false);
                json.put("error", expiredMessage);
                response.setContentType("application/json");
                response.getWriter().write(json.toString());
                return null;
            }

            response.sendRedirect(newURL);
            return NONE;
        } else {
            logger.debug("go to normal directory");

            cl.updateLoginList(ip, userName);

            if (ajaxResponse) {
                ObjectNode json = objectMapper.createObjectNode();
                json.put("success", false);
                response.setContentType("application/json");
                json.put("error", "Invalid Credentials");
                response.getWriter().write(json.toString());
                return null;
            }

            String oneIdKey = request.getParameter("nameId");
            String newURL = request.getContextPath() + "/index?login=failed";
            if (oneIdKey != null && !oneIdKey.equals("")) {
                newURL += "&nameId=" + SafeEncode.forUriComponent(oneIdKey);
            }
            response.sendRedirect(newURL);
            return NONE;
        }

    }

    /**
     * Starts an MFA challenge without granting an authenticated application session.
     *
     * <p>Only pending-MFA attributes are stored here. The canonical {@code user} session
     * attribute is set by {@link #completeAuthenticatedLogin} after the OTP validates.</p>
     *
     * @param cl authenticated password/PIN login object to resume after MFA
     * @param strAuth authentication result fields returned by {@link LoginCheckLogin#auth}
     * @param security security row for the authenticated provider
     * @param ip remote address used for audit logging
     * @return {@code mfaHandler} when the challenge view is ready, or {@code failure} when
     *         registration setup cannot safely continue
     */
    private String beginPendingMfaChallenge(LoginCheckLogin cl, String[] strAuth, Security security, String ip) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        session = request.getSession();
        session.setMaxInactiveInterval(300);
        session.setAttribute(PENDING_MFA_AUTH_ATTR, Boolean.TRUE); // nosemgrep: tainted-session-from-http-request
        session.setAttribute(PENDING_MFA_LOGIN_CHECK_ATTR, cl); // nosemgrep: tainted-session-from-http-request
        session.setAttribute(PENDING_MFA_AUTH_RESULT_ATTR, strAuth); // nosemgrep: tainted-session-from-http-request

        try {
            if (this.mfaManager.isMfaRegistrationRequired(security.getId())) {
                Object mfaSecret = session.getAttribute("mfaSecret");
                if (mfaSecret == null) {
                    mfaSecret = MfaManager.generateMfaSecret();
                    session.setAttribute("mfaSecret", mfaSecret); // nosemgrep: tainted-session-from-http-request
                }
                request.setAttribute("mfaRegistrationRequired", true);
                request.setAttribute("qrData", this.mfaManager.getQRCodeImageData(security.getId(), mfaSecret.toString()));
            }
        } catch (IllegalStateException e) {
            logger.warn("Unable to prepare MFA registration: providerNo={}, securityId={}, remote={}",
                    LogSanitizer.sanitize(security.getProviderNo()),
                    LogSanitizer.sanitize(String.valueOf(security.getSecurityNo())),
                    LogSanitizer.sanitize(ip),
                    e);
            session.invalidate();
            request.setAttribute("errMsg", message("login.errorUnableToProcess"));
            return "failure";
        }

        request.setAttribute("securityId", String.valueOf(security.getSecurityNo()));
        return "mfaHandler";
    }

    /**
     * Validates a submitted MFA code, then completes the normal authenticated login flow.
     *
     * <p>The pending-MFA session is the boundary between password/PIN success and an authenticated
     * application session. Fatal validation failures clear that pending state so the staged
     * {@link LoginCheckLogin} and authentication result cannot survive after a corrupted secret,
     * missing security record, or broken TOTP key. Retryable user-code failures intentionally keep
     * the pending state so the user can enter a new OTP.</p>
     *
     * @param ip remote address used for logging and final login audit
     * @param isMobileOptimized whether mobile session flags should be applied after success
     * @param submitType mobile/full-site submit mode from the original request
     * @param ajaxResponse whether the final response should be JSON instead of a Struts result
     * @return Struts result name, {@link #NONE}, or null for direct AJAX responses
     * @throws IOException if redirecting or writing the final response fails
     */
    private String validateMfaAndCompleteLogin(String ip, boolean isMobileOptimized, String submitType,
                                               boolean ajaxResponse) throws IOException {
        HttpSession session = request.getSession(false);
        if (!hasPendingMfaSession(session)) {
            logger.info("Rejected MFA verification without valid pending challenge: remote={}",
                    LogSanitizer.sanitize(ip));
            response.sendRedirect(loginFailedRedirectUrl(message("provider.providerchangepassword.errorSessionExpired")));
            return NONE;
        }

        LoginCheckLogin cl = (LoginCheckLogin) session.getAttribute(PENDING_MFA_LOGIN_CHECK_ATTR);
        String[] strAuth = (String[]) session.getAttribute(PENDING_MFA_AUTH_RESULT_ATTR);
        Security security = cl.getSecurity();
        if (security == null) {
            logger.error("Rejected MFA verification because pending challenge has no security record: remote={}",
                    LogSanitizer.sanitize(ip));
            clearPendingMfaSession(session);
            response.sendRedirect(loginFailedRedirectUrl(message("login.errorSecurityRecordMissing")));
            return NONE;
        }

        String mfaSecret;
        if (this.mfaRegistrationFlow) {
            Object mfaSecretAttr = session.getAttribute("mfaSecret");
            if (!(mfaSecretAttr instanceof String)) {
                logger.warn("Rejected MFA registration submit without a staged secret: providerNo={}, securityId={}, remote={}",
                        LogSanitizer.sanitize(security.getProviderNo()),
                        LogSanitizer.sanitize(String.valueOf(security.getSecurityNo())),
                        LogSanitizer.sanitize(ip));
                clearPendingMfaSession(session);
                response.sendRedirect(loginFailedRedirectUrl(message("provider.providerchangepassword.errorSessionExpired")));
                return NONE;
            }
            mfaSecret = (String) mfaSecretAttr;
        } else {
            try {
                mfaSecret = this.mfaManager.getMfaSecret(security);
            } catch (Exception e) {
                logger.error("Unable to retrieve MFA secret: providerNo={}, securityId={}, remote={}",
                        LogSanitizer.sanitize(security.getProviderNo()),
                        LogSanitizer.sanitize(String.valueOf(security.getSecurityNo())),
                        LogSanitizer.sanitize(ip),
                        e);
                clearPendingMfaSession(session);
                request.setAttribute("errMsg", message("login.errorUnableToProcess"));
                return "failure";
            }
        }

        boolean validCode;
        try {
            validCode = isValidTotpCode(mfaSecret, this.code);
        } catch (java.security.InvalidKeyException e) {
            logger.error("Unable to validate MFA code: providerNo={}, securityId={}, remote={}",
                    LogSanitizer.sanitize(security.getProviderNo()),
                    LogSanitizer.sanitize(String.valueOf(security.getSecurityNo())),
                    LogSanitizer.sanitize(ip),
                    e);
            clearPendingMfaSession(session);
            request.setAttribute("errMsg", message("login.errorUnableToProcess"));
            return "failure";
        }

        if (!validCode) {
            LogAction.addLog(security.getProviderNo(), "login", "mfa_failed", "mfa", ip);
            if (this.mfaRegistrationFlow) {
                request.setAttribute("mfaRegistrationRequired", true);
                request.setAttribute("qrData", this.mfaManager.getQRCodeImageData(security.getId(), mfaSecret));
            }
            request.setAttribute("mfaValidateCodeErr", "Invalid MFA Code");
            request.setAttribute("securityId", String.valueOf(security.getSecurityNo()));
            return "mfaHandler";
        }

        LogAction.addLog(security.getProviderNo(), "login", "mfa_success", "mfa", ip);
        if (this.mfaRegistrationFlow) {
            try {
                this.mfaManager.saveMfaSecret(buildLoggedInInfoForPendingMfa(session, strAuth, security), security, mfaSecret);
            } catch (Exception e) {
                logger.error("Unable to persist MFA registration secret: providerNo={}, securityId={}, remote={}",
                        LogSanitizer.sanitize(security.getProviderNo()),
                        LogSanitizer.sanitize(String.valueOf(security.getSecurityNo())),
                        LogSanitizer.sanitize(ip),
                        e);
                clearPendingMfaSession(session);
                request.setAttribute("errMsg", message("login.errorUnableToProcess"));
                return "failure";
            }
        }

        clearPendingMfaSession(session);
        return completeAuthenticatedLogin(cl, strAuth, ip, isMobileOptimized, submitType, ajaxResponse);
    }

    /**
     * Returns whether the session contains all state required to validate a pending MFA challenge.
     *
     * <p>Every attribute is required and type-checked. Missing or wrong-typed state is treated as an
     * expired challenge rather than reconstructing login state from request parameters.</p>
     *
     * @param session candidate HTTP session
     * @return true when the session contains the pending-MFA marker, login object, and auth result
     */
    private boolean hasPendingMfaSession(HttpSession session) {
        return session != null
                && Boolean.TRUE.equals(session.getAttribute(PENDING_MFA_AUTH_ATTR))
                && session.getAttribute(PENDING_MFA_LOGIN_CHECK_ATTR) instanceof LoginCheckLogin
                && session.getAttribute(PENDING_MFA_AUTH_RESULT_ATTR) instanceof String[];
    }

    /**
     * Validates an RFC 6238 TOTP code with one time-step of clock skew tolerance.
     *
     * <p>Authenticator apps and server clocks can differ briefly. Accepting the current, previous,
     * or next time step preserves the usual +/- one-step tolerance without accepting an unbounded
     * replay window.</p>
     *
     * @param mfaSecret Base32-encoded MFA secret
     * @param submittedCode user-submitted six-digit TOTP code
     * @return true when the submitted code matches the current, previous, or next time step
     * @throws java.security.InvalidKeyException when the decoded secret cannot create a valid TOTP key
     */
    private boolean isValidTotpCode(String mfaSecret, String submittedCode) throws java.security.InvalidKeyException {
        TimeBasedOneTimePasswordGenerator totpGenerator = new TimeBasedOneTimePasswordGenerator();
        byte[] decodedKey = new Base32().decode(mfaSecret);
        SecretKeySpec key = new SecretKeySpec(decodedKey, totpGenerator.getAlgorithm());
        java.time.Instant now = java.time.Instant.now();
        java.time.Duration timeStep = totpGenerator.getTimeStep();

        return totpGenerator.generateOneTimePasswordString(key, now).equals(submittedCode)
                || totpGenerator.generateOneTimePasswordString(key, now.minus(timeStep)).equals(submittedCode)
                || totpGenerator.generateOneTimePasswordString(key, now.plus(timeStep)).equals(submittedCode);
    }

    /**
     * Removes all pre-authentication MFA state from the session.
     *
     * <p>The staged login object and authentication result represent a successful password/PIN check
     * and must be cleared before any fatal MFA failure returns control to Struts.</p>
     *
     * @param session session containing pending-MFA state
     */
    private void clearPendingMfaSession(HttpSession session) {
        session.removeAttribute(PENDING_MFA_AUTH_ATTR);
        session.removeAttribute(PENDING_MFA_LOGIN_CHECK_ATTR);
        session.removeAttribute(PENDING_MFA_AUTH_RESULT_ATTR);
        session.removeAttribute("mfaSecret");
    }

    /**
     * Builds the minimal authenticated context needed to persist a newly registered MFA secret.
     *
     * <p>MFA registration happens before the normal {@code user}-authenticated session is created,
     * so this context is derived from pending-MFA state rather than the canonical logged-in session
     * attributes.</p>
     *
     * @param session pending-MFA session that will become the authenticated session on success
     * @param strAuth authentication result fields returned by {@link LoginCheckLogin#auth}
     * @param security security row for the authenticated provider
     * @return minimal {@link LoggedInInfo} context required by {@link MfaManager#saveMfaSecret}
     */
    private LoggedInInfo buildLoggedInInfoForPendingMfa(HttpSession session, String[] strAuth, Security security) {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setSession(session);
        loggedInInfo.setLoggedInProvider(providerDao.getProvider(strAuth[0]));
        loggedInInfo.setLoggedInSecurity(security);
        loggedInInfo.setLocale(request.getLocale());
        loggedInInfo.setIp(request.getRemoteAddr());
        loggedInInfo.setInitiatingCode(request.getRequestURI());
        return loggedInInfo;
    }

    /**
     * Completes session setup after password/PIN authentication and any required MFA have succeeded.
     *
     * <p>This is the only path that creates the canonical authenticated session marker
     * ({@code user}). Callers must complete forced password reset and MFA checks before invoking it,
     * because {@link io.github.carlos_emr.carlos.sec.LoginFilter} treats that marker as logged-in
     * state. If the provider row cannot be loaded after authentication, the new session is
     * invalidated and the method returns {@code failure} rather than leaving a partial session.</p>
     *
     * @param cl authenticated login object carrying the security row
     * @param strAuth authentication result fields returned by {@link LoginCheckLogin#auth}
     * @param ip remote address used for audit logging
     * @param isMobileOptimized whether mobile session flags should be applied
     * @param submitType mobile/full-site submit mode
     * @param ajaxResponse whether to write the final provider JSON response directly
     * @return Struts result name, {@link #NONE} after redirect, {@code failure}, or null for AJAX
     * @throws IOException if redirecting or writing the response fails
     */
    private String completeAuthenticatedLogin(LoginCheckLogin cl, String[] strAuth, String ip,
                                              boolean isMobileOptimized, String submitType,
                                              boolean ajaxResponse) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            if (request.getParameter("invalidate_session") != null
                    && request.getParameter("invalidate_session").equals("false")) {
                // don't invalidate in this case it messes up authenticity of OAUTH
            } else {
                session.invalidate();
            }
        }
        session = request.getSession();
        session.setMaxInactiveInterval(7200);

        if (cl.getSecurity() != null) {
            this.userSessionManager.registerUserSession(cl.getSecurity().getSecurityNo(), session);
        }

        logger.debug("Assigned new session for: {} : {} : {}", LogSanitizer.sanitize(strAuth[0]), LogSanitizer.sanitize(strAuth[3]), LogSanitizer.sanitize(strAuth[4]));
        LogAction.addLog(strAuth[0], LogConst.LOGIN, LogConst.CON_LOGIN, "", ip);

        Properties pvar = CarlosProperties.getInstance();

        String providerNo = strAuth[0] != null ? strAuth[0].trim() : "";
        session.setAttribute("user", providerNo); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("userfirstname", strAuth[1] != null ? strAuth[1].trim() : ""); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("userlastname", strAuth[2] != null ? strAuth[2].trim() : ""); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("userrole", strAuth[4] != null ? strAuth[4].trim() : ""); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("oscar_context_path", request.getContextPath()); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("expired_days", strAuth[5] != null ? strAuth[5].trim() : ""); // nosemgrep: tainted-session-from-http-request
        if (isMobileOptimized) {
            if ("Full".equalsIgnoreCase(submitType)) {
                session.setAttribute("fullSite", "true"); // nosemgrep: tainted-session-from-http-request
            } else {
                session.setAttribute("mobileOptimized", "true"); // nosemgrep: tainted-session-from-http-request
            }
        }

        String default_pmm = null;
        ProviderPreference providerPreference = providerPreferenceDao.find(providerNo);

        if (providerPreference == null) {
            providerPreference = new ProviderPreference();
        }

        session.setAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE, providerPreference); // nosemgrep: tainted-session-from-http-request

        if (IsPropertiesOn.isCaisiEnable()) {
            String tklerProviderNo;
            UserProperty prop = propDao.getProp(providerNo, UserProperty.PROVIDER_FOR_TICKLER_WARNING);
            if (prop == null) {
                tklerProviderNo = providerNo;
            } else {
                tklerProviderNo = prop.getValue();
            }
            session.setAttribute("tklerProviderNo", tklerProviderNo); // nosemgrep: tainted-session-from-http-request

            session.setAttribute("newticklerwarningwindow", providerPreference.getNewTicklerWarningWindow()); // nosemgrep: tainted-session-from-http-request
            session.setAttribute("default_pmm", providerPreference.getDefaultCaisiPmm()); // nosemgrep: tainted-session-from-http-request
            session.setAttribute("caisiBillingPreferenceNotDelete", // nosemgrep: tainted-session-from-http-request
                    String.valueOf(providerPreference.getDefaultDoNotDeleteBilling()));

            default_pmm = providerPreference.getDefaultCaisiPmm();
            @SuppressWarnings("unchecked")
            ArrayList<String> newDocArr = (ArrayList<String>) request.getSession().getServletContext()
                    .getAttribute("CaseMgmtUsers");
            if ("enabled".equals(providerPreference.getDefaultNewOscarCme())) {
                newDocArr.add(providerNo);
                session.setAttribute("CaseMgmtUsers", newDocArr); // nosemgrep: tainted-session-from-http-request
            }
        }
        session.setAttribute("starthour", providerPreference.getStartHour().toString()); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("endhour", providerPreference.getEndHour().toString()); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("everymin", providerPreference.getEveryMin().toString()); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("groupno", providerPreference.getMyGroupNo()); // nosemgrep: tainted-session-from-http-request

        String where = "provider";

        if (where.equals("provider") && default_pmm != null && "enabled".equals(default_pmm)) {
            where = "caisiPMM";
        }

        if (where.equals("provider")
                && CarlosProperties.getInstance().getProperty("useProgramLocation", "false").equals("true")) {
            where = "programLocation";
        }

        if (pvar.getProperty("billregion").equals("BC")) {
            String alertFreq = pvar.getProperty("ALERT_POLL_FREQUENCY");
            if (alertFreq != null) {
                Long longFreq = Long.valueOf(alertFreq);
                String[] alertCodes = CarlosProperties.getInstance().getProperty("CDM_ALERTS").split(",");
                AlertTimer.getInstance(alertCodes, longFreq.longValue());
            }
        }

        String username = (String) session.getAttribute("user");
        Provider provider = providerManager.getProvider(username);
        if (provider == null) {
            logger.error("Authenticated login could not load provider record: providerNo={}, remote={}",
                    LogSanitizer.sanitize(username), LogSanitizer.sanitize(ip));
            session.invalidate();
            request.setAttribute("errMsg", message("login.errorUnableToProcess"));
            return "failure";
        }
        session.setAttribute("provider", provider); // nosemgrep: tainted-session-from-http-request
        session.setAttribute(SessionConstants.LOGGED_IN_PROVIDER, provider); // nosemgrep: tainted-session-from-http-request
        session.setAttribute(SessionConstants.LOGGED_IN_SECURITY, cl.getSecurity()); // nosemgrep: tainted-session-from-http-request

        List<Integer> facilityIds = providerDao.getFacilityIds(provider.getProviderNo());
        if (facilityIds.size() > 1) {
            String facilityPath = "/select_facility?nextPage=";
            String newURL = request.getContextPath() + facilityPath + SafeEncode.forUriComponent(where);

            response.sendRedirect(newURL);
            return NONE;
        } else if (facilityIds.size() == 1) {
            Facility facility = facilityDao.find(facilityIds.get(0));
            request.getSession().setAttribute("currentFacility", facility); // nosemgrep: tainted-session-from-http-request
            LogAction.addLog(strAuth[0], LogConst.LOGIN, LogConst.CON_LOGIN, "facilityId=" + facilityIds.get(0),
                    ip);
        } else {
            List<Facility> facilities = facilityDao.findAll(true);
            if (facilities != null && facilities.size() >= 1) {
                Facility fac = facilities.get(0);
                int first_id = fac.getId();
                providerDao.addProviderToFacility(providerNo, first_id);
                Facility facility = facilityDao.find(first_id);
                request.getSession().setAttribute("currentFacility", facility); // nosemgrep: tainted-session-from-http-request
                LogAction.addLog(strAuth[0], LogConst.LOGIN, LogConst.CON_LOGIN, "facilityId=" + first_id, ip);
            }
        }

        LoggedInInfo loggedInInfo = LoggedInUserFilter.generateLoggedInInfoFromSession(request);
        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);

        if (UserRoleUtils.hasRole(request, "Patient Intake")) {
            return "patientIntake";
        }

        if ("provider".equals(where)) {
            response.sendRedirect(buildDefaultProviderSchedulePath());
            return NONE;
        }

        if (request.getParameter("oauth_token") != null) {
            logger.debug("checking oauth_token");
            String proNo = (String) request.getSession().getAttribute("user");
            ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(request.getParameter("oauth_token"));
            if (srt != null) {
                srt.setProviderNo(proNo);
                serviceRequestTokenDao.merge(srt);
            }
        }

        if (ajaxResponse) {
            logger.debug("rendering ajax response");
            Provider prov = providerDao.getProvider((String) request.getSession().getAttribute("user"));
            ObjectNode json = objectMapper.createObjectNode();
            json.put("success", true);
            json.put("providerName", SafeEncode.forJavaScript(prov.getFormattedName()));
            json.put("providerNo", prov.getProviderNo());
            response.setContentType("application/json");
            response.getWriter().write(json.toString());
            return null;
        }

        logger.debug("rendering standard response : {}", where);
        return where;
    }

    /**
     * Builds the canonical post-login schedule landing path.
     *
     * <p>This sends authenticated users to the provider-control router with
     * today's day-view parameters. Provider-control performs the active
     * in-page schedule checks before including the day-view JSP.</p>
     *
     * @return internal application path for the default provider schedule view
     */
    private String buildDefaultProviderSchedulePath() {
        GregorianCalendar now = new GregorianCalendar();
        String viewAll = "1";
        if (CarlosProperties.getInstance().getProperty("default_schedule_viewall", "").startsWith("false")) {
            viewAll = "0";
        }

        return request.getContextPath()
                + "/provider/providercontrol?year=" + now.get(Calendar.YEAR)
                + "&month=" + (now.get(Calendar.MONTH) + 1)
                + "&day=" + now.get(Calendar.DAY_OF_MONTH)
                + "&view=0&displaymode=day&dboperation=searchappointmentday&viewall=" + viewAll;
    }

    /**
     * Returns whether flagged accounts must be routed through forced password reset.
     *
     * <p>This setting is a security control, so an omitted key is treated as enabled. The shared
     * {@code getBooleanProperty(key, "true")} helper only checks whether the configured value is
     * active and therefore returns false for missing positive-valued keys.</p>
     *
     * @return true when {@code mandatory_password_reset} is missing or set to true/yes/on
     */
    private boolean isMandatoryPasswordResetEnabled() {
        String value = CarlosProperties.getInstance().getProperty("mandatory_password_reset", "true");
        return value != null && ("true".equalsIgnoreCase(value.trim())
                || "yes".equalsIgnoreCase(value.trim())
                || "on".equalsIgnoreCase(value.trim()));
    }

    /**
     * Redirects a retryable forced-reset validation failure back to the GET view.
     *
     * <p>The credential token remains live for this path by design: the user has not yet passed
     * validation, so there is no terminal password-change attempt to consume. The next view render
     * copies the session error to the request and removes it from the session.</p>
     *
     * @param errorMessage localized message to show on the forced reset form
     * @return {@link #NONE} because the response has already been redirected
     * @throws IOException if the servlet container cannot issue the redirect
     */
    private String redirectForcePasswordResetRetry(String errorMessage) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(FORCE_PASSWORD_RESET_ERROR_ATTR, errorMessage);
        }
        response.sendRedirect(request.getContextPath() + "/forcepasswordreset");
        return NONE;
    }

    /**
     * Builds the extensionless login failure redirect URL with an encoded error
     * message parameter.
     *
     * @param request servlet request used to resolve the application context path
     * @param errorMessage localized error message to include in the redirect
     * @return context-relative login failure URL including an encoded errormsg parameter
     */
    public static String loginFailedRedirectUrl(HttpServletRequest request, String errorMessage) {
        return request.getContextPath() + "/loginfailed?errormsg="
                + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
    }

    /**
     * Looks up a localized message from the CARLOS EMR oscarResources bundle
     * using the current request locale, falling back to English when a locale
     * bundle is missing the requested key.
     *
     * @param request servlet request carrying the active locale
     * @param key resource bundle key to resolve
     * @return localized resource bundle message
     */
    public static String message(HttpServletRequest request, String key) {
        try {
            return ResourceBundle.getBundle("oscarResources", request.getLocale()).getString(key);
        } catch (MissingResourceException e) {
            logger.warn("Missing localized message for key: {}", key);
            try {
                return ResourceBundle.getBundle("oscarResources", Locale.ENGLISH).getString(key);
            } catch (MissingResourceException fallbackException) {
                logger.warn("Missing default message for key: {}", key);
                return "Unable to process your request. Please try again.";
            }
        }
    }

    /**
     * Checks whether the request session holds a live credential-cache token for
     * a pending multi-step login flow.
     *
     * @param request HttpServletRequest carrying the candidate session
     * @return true when the session contains a token that still maps to cached credentials
     */
    public static boolean hasValidLoginCredentialsToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object tokenAttr = session.getAttribute(LOGIN_CREDENTIALS_TOKEN_ATTR);
        return tokenAttr instanceof String token
                && LoginCredentialCache.getInstance().peek(token) != null;
    }

    private String loginFailedRedirectUrl(String errorMessage) {
        return loginFailedRedirectUrl(request, errorMessage);
    }

    private String message(String key) {
        return message(request, key);
    }

    /**
     * Removes authentication-related attributes from the session and invalidates
     * any cached credential material.
     *
     * <p>This method is called when cleaning up after a forced password reset flow
     * or when login fails and sensitive data must be cleared from the session.
     *
     * <p>The session attribute removed is the opaque credential-cache token (see
     * {@link LoginCredentialCache}); if it is present the corresponding cache entry is
     * also invalidated so that credential material cannot outlive the login attempt.
     * The retry-display attribute {@link #FORCE_PASSWORD_RESET_ERROR_ATTR} and older flow
     * attributes such as {@code userName} and {@code nextPage} are also cleared if present so stale
     * state from pre-cache login paths cannot influence a later attempt.
     *
     * @param request HttpServletRequest containing the session to clean
     */
    private void removeAttributesFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        Object tokenAttr = session.getAttribute(LOGIN_CREDENTIALS_TOKEN_ATTR);
        if (tokenAttr instanceof String) {
            LoginCredentialCache.getInstance().invalidate((String) tokenAttr);
        }
        session.removeAttribute(LOGIN_CREDENTIALS_TOKEN_ATTR);
        session.removeAttribute(FORCE_PASSWORD_RESET_ERROR_ATTR);
        session.removeAttribute("userName");
        session.removeAttribute("nextPage");
    }

    /**
     * Stores user authentication information in a short-lived server-side cache for the
     * forced password reset and MFA flows, and records only an opaque one-time token in
     * the session.
     *
     * <p>During multi-step login (MFA verification or forced password reset), the user's
     * credentials are needed on a subsequent request. Rather than placing password hash
     * and PIN material in the HTTP session — where it could be serialised, replicated,
     * or exposed via debug dumps — this method stashes the credentials in
     * {@link LoginCredentialCache} (a Caffeine cache with a 5-minute TTL, opaque random
     * tokens, and explicit invalidation on terminal outcomes). Only the opaque token is
     * placed in the session. The companion retrieval path (see the forced-password-change
     * branch in {@link #execute()}) may use {@link LoginCredentialCache#peek(String)} for
     * retryable flows so the cached credentials remain available across validation retries;
     * terminal success or failure paths invalidate the cache entry via
     * {@link #removeAttributesFromSession(HttpServletRequest)}.
     *
     * <p>The {@code nextPage} parameter is validated against open redirect (CWE-601) before
     * being cached. Any value that is absolute, protocol-relative, or contains backslash
     * bypasses is rejected and stored as {@code null} (defense in depth).
     *
     * <p>If a prior token is already attached to the session, its cache entry is invalidated
     * before a new one is issued, ensuring stale credential material does not outlive the
     * current login attempt.
     *
     * <p>Session attributes set:
     * <ul>
     *   <li>{@value #LOGIN_CREDENTIALS_TOKEN_ATTR} — opaque random token referencing the
     *       cached credentials; NOT credential material</li>
     * </ul>
     *
     * @param request HttpServletRequest to access the session
     * @param userName String the username (must match [a-zA-Z0-9]{1,10} pattern)
     * @param password String the plain-text password (will be encoded before caching)
     * @param pin String the 4-digit PIN (must match [0-9]{4} pattern)
     * @param nextPage String the relative URL to redirect to after password reset (validated before caching)
     * @see SecurityManager#encodePassword for password encoding algorithm
     * @see #removeAttributesFromSession for cleanup after password reset
     * @see RedirectValidationUtils#isValidRelativeRedirect for redirect URL validation logic
     * @see LoginCredentialCache for the server-side credential store
     */
    private void setUserInfoToSession(HttpServletRequest request, String userName, String password, String pin,
                                      String nextPage) {
        // Validate nextPage before caching to prevent open redirect (CWE-601 defense in depth)
        if (!RedirectValidationUtils.isValidRelativeRedirect(nextPage)) {
            if (nextPage != null) {
                logger.warn("Rejected invalid nextPage before credential cache: {}", LogSanitizer.sanitize(nextPage));
            }
            nextPage = null;
        }

        // SECURITY: Do NOT place credential material (password hash, PIN) in the HTTP session.
        // Sessions can be serialized to disk, replicated across nodes, dumped for debugging,
        // or read by any session-aware code. Instead, stash credentials in a short-lived
        // server-side cache keyed by a cryptographically random one-time token, and store
        // only the opaque token in the session. See LoginCredentialCache for details.
        LoginCredentialCache.LoginCredentials credentials = new LoginCredentialCache.LoginCredentials(
                userName, securityManager.encodePassword(password), pin, nextPage);

        // Invalidate any previously issued token on this session before minting a new one,
        // so that stale cache entries do not outlive the current login attempt.
        HttpSession session = request.getSession();
        Object existingToken = session.getAttribute(LOGIN_CREDENTIALS_TOKEN_ATTR);
        if (existingToken instanceof String) {
            LoginCredentialCache.getInstance().invalidate((String) existingToken);
        }

        String token = LoginCredentialCache.getInstance().store(credentials);
        // Use an opaque random token, not user-controlled; no credential material in session
        session.setAttribute(LOGIN_CREDENTIALS_TOKEN_ATTR, token); // nosemgrep: tainted-session-from-http-request
    }

    /**
     * Validates password change requirements during forced password reset flow.
     *
     * <p>This method performs four validation checks:
     * <ol>
     *   <li>Old password matches the password from the staged successful login</li>
     *   <li>New password and confirmation password match each other</li>
     *   <li>New password is different from old password (unless IGNORE_PASSWORD_REQUIREMENTS is true)</li>
     *   <li>New password satisfies the configured password complexity policy</li>
     * </ol>
     *
     * <p>The method returns a display-safe error message if validation fails, or an empty
     * string if all validations pass.
     *
     * @param userName provider login name used only for PHI-safe audit context
     * @param oldEncodedPassword String password hash staged from the successful login
     * @param newPassword String the new password entered by the user
     * @param confirmPassword String the confirmation of the new password
     * @param oldPassword String the old password entered by the user for verification
     * @return String empty string if validation passes, or an error message if validation fails
     * @see SecurityManager#matchesPassword for password comparison logic
     */
    private String errorHandling(String userName, String oldEncodedPassword, String newPassword, String confirmPassword,
                                 String oldPassword) {

        // Verify old password matches the password from the staged successful login.
        if (oldPassword == null || oldEncodedPassword == null
                || !this.securityManager.matchesPassword(oldPassword, oldEncodedPassword)) {
            logger.info("Forced password reset rejected because old password did not match: user={}",
                    LogSanitizer.sanitize(userName));
            LogAction.addLog(userName, "login", "forced_password_reset_failed", "old_password_mismatch");
            return message("provider.providerchangepassword.errorOldPasswordMismatch");
        }
        // Verify new password and confirmation match
        else if (newPassword == null || confirmPassword == null || !Objects.equals(newPassword, confirmPassword)) {
            return message("provider.providerchangepassword.errorConfirmPasswordMismatch");
        }
        // Verify new password is different from old password (unless requirement is disabled)
        else if (!Boolean.parseBoolean(CarlosProperties.getInstance().getProperty("IGNORE_PASSWORD_REQUIREMENTS"))
                && Objects.equals(newPassword, oldPassword)) {
            return message("provider.providerchangepassword.errorNewPasswordSameAsOld");
        }

        return validatePasswordPolicy(newPassword);
    }

    /**
     * Applies the server-side forced-reset password complexity policy.
     *
     * <p>The JSP performs the same checks for immediate user feedback, but browser JavaScript is
     * advisory. This method is the authoritative policy gate for direct POSTs and scripted clients.
     * It mirrors the configurable length/group settings used by the legacy password-change UI.</p>
     *
     * @param newPassword candidate password submitted from the forced reset form
     * @return empty string when the password is acceptable, otherwise a localized error message
     */
    private String validatePasswordPolicy(String newPassword) {
        CarlosProperties properties = CarlosProperties.getInstance();
        if (Boolean.parseBoolean(properties.getProperty("IGNORE_PASSWORD_REQUIREMENTS"))) {
            return "";
        }

        int minLength = intProperty(properties, "password_min_length", DEFAULT_PASSWORD_MIN_LENGTH);
        if (newPassword == null || newPassword.length() < minLength) {
            return message("password.policy.violation.msgPasswordLengthError") + " "
                    + minLength + " " + message("password.policy.violation.msgSymbols");
        }

        int minGroups = intProperty(properties, "password_min_groups", DEFAULT_PASSWORD_MIN_GROUPS);
        int groupsUsed = countPasswordGroups(newPassword,
                properties.getProperty("password_group_lower_chars", DEFAULT_PASSWORD_LOWER_CHARS),
                properties.getProperty("password_group_upper_chars", DEFAULT_PASSWORD_UPPER_CHARS),
                properties.getProperty("password_group_digits", DEFAULT_PASSWORD_DIGIT_CHARS),
                properties.getProperty("password_group_special", DEFAULT_PASSWORD_SPECIAL_CHARS));
        if (groupsUsed < minGroups) {
            return message("password.policy.violation.msgPasswordStrengthError") + " "
                    + minGroups + " " + message("password.policy.violation.msgPasswordGroups");
        }

        return "";
    }

    /**
     * Counts how many configured character groups appear in a password candidate.
     *
     * <p>Package visibility exists for focused policy tests. Keep this helper deterministic and
     * side-effect free so tests can cover policy behavior without driving the whole login action.</p>
     */
    static int countPasswordGroups(String password, String lowerChars, String upperChars, String digitChars,
                                   String specialChars) {
        if (password == null || password.isEmpty()) {
            return 0;
        }

        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean special = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (!lower && containsChar(lowerChars, ch)) {
                lower = true;
            }
            if (!upper && containsChar(upperChars, ch)) {
                upper = true;
            }
            if (!digit && containsChar(digitChars, ch)) {
                digit = true;
            }
            if (!special && containsChar(specialChars, ch)) {
                special = true;
            }
        }

        int groups = 0;
        if (lower) groups++;
        if (upper) groups++;
        if (digit) groups++;
        if (special) groups++;
        return groups;
    }

    private static boolean containsChar(String chars, char ch) {
        return chars != null && chars.indexOf(ch) >= 0;
    }

    /**
     * Reads an integer password-policy property with a safe fallback.
     *
     * <p>Misconfigured policy values should not make password changes impossible. Invalid values
     * are logged for operators and the conservative application default remains in force.</p>
     */
    private static int intProperty(CarlosProperties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer property {}={}, using default {}", key, LogSanitizer.sanitize(value),
                    defaultValue);
            return defaultValue;
        }
    }

    /**
     * Retrieves the Security record for a given username.
     *
     * @param username String the username to look up (must match security.user_name column)
     * @return Security the user's security record, or null if no matching user found
     * @see SecurityDao#findByUserName for database lookup
     */
    private Security getSecurity(String username) {

        List<Security> results = securityDao.findByUserName(username);
        Security security = null;
        if (results.size() > 0)
            security = results.get(0);

        return security;
    }

    /**
     * Persists a new password for a user after forced password reset.
     *
     * <p>This method updates the user's password in the database and clears the
     * forcePasswordReset flag so the user won't be prompted again on next login. It assumes the
     * caller has already validated the old password, confirmation match, reuse rule, complexity
     * policy, CSRF token, and credential-cache token. Do not call this helper directly from a new
     * endpoint without preserving those gates.
     *
     * <p>Steps performed:
     * <ol>
     *   <li>Retrieve the user's Security record via {@link #getSecurity}</li>
     *   <li>Encode the new password using {@link SecurityManager#encodePassword}</li>
     *   <li>Update the password field in the Security record</li>
     *   <li>Clear the forcePasswordReset flag</li>
     *   <li>Persist changes to the database</li>
     * </ol>
     *
     * @param userName String the username of the account to update
     * @param newPassword String the new plain-text password (will be encoded before storage)
     * @throws IllegalStateException if the user's security record cannot be found
     * @see #getSecurity for retrieving the Security record
     * @see SecurityManager#encodePassword for password hashing
     * @see SecurityDao#saveEntity for database persistence
     */
    private void persistNewPassword(String userName, String newPassword) {

        Security security = getSecurity(userName);
        if (security == null) {
            throw new IllegalStateException("Security record not found for forced password reset user.");
        }
        security.setPassword(securityManager.encodePassword(newPassword));
        security.setForcePasswordReset(Boolean.FALSE);
        security.setPasswordUpdateDate(new Date());
        securityDao.saveEntity(security);

    }

    /**
     * Retrieves the Spring ApplicationContext from the servlet context.
     *
     * <p>This method provides access to the Spring application context for
     * programmatic bean lookup and dependency injection outside of normal
     * Spring-managed components.
     *
     * @return ApplicationContext the Spring application context for this web application
     */
    public ApplicationContext getAppContext() {
        return WebApplicationContextUtils.getWebApplicationContext(ServletActionContext.getServletContext());
    }

    // ===== Struts2 Action Properties (populated from form parameters) =====

    /** Username submitted from login form (alphanumeric, max 10 characters) */
    private String username;

    /** Password submitted from login form (plain-text, encoded before storage/comparison) */
    private String password;

    /** Provider PIN submitted from login form (4 digits) */
    private String pin;

    /** Property name for user property operations (legacy field) */
    private String propname;

    /** Old password submitted from forced password reset form */
    private String oldPassword;

    /** New password submitted from forced password reset form */
    private String newPassword;

    /** Confirmation of new password from forced password reset form */
    private String confirmPassword;

    /** MFA TOTP code submitted from MFA validation form (6 digits) */
    private String code;

    /** Flag indicating whether this is MFA registration flow vs. standard MFA validation */
    private boolean mfaRegistrationFlow;

    /**
     * Gets the username from the login form.
     *
     * @return String the username (validated to alphanumeric, max 10 characters)
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username from the login form parameter.
     *
     * <p>Struts2 automatically calls this method to populate the username field
     * from the "username" request parameter.
     *
     * @param username String the username submitted from the form
     */
    @StrutsParameter
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the password from the login form.
     *
     * @return String the plain-text password (never store in logs or session without encoding)
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password from the login form parameter.
     *
     * <p>Struts2 automatically calls this method to populate the password field
     * from the "password" request parameter.
     *
     * @param password String the plain-text password submitted from the form
     */
    @StrutsParameter
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Gets the provider PIN from the login form.
     *
     * @return String the 4-digit PIN code
     */
    public String getPin() {
        return pin;
    }

    /**
     * Sets the PIN from the login form parameter.
     *
     * <p>Struts2 automatically calls this method to populate the pin field
     * from the "pin" request parameter.
     *
     * @param pin String the 4-digit PIN submitted from the form (validated to [0-9]{4})
     */
    @StrutsParameter
    public void setPin(String pin) {
        this.pin = pin;
    }

    /**
     * Gets the property name for user property operations.
     *
     * @return String the property name (legacy field, usage unclear)
     */
    public String getPropname() {
        return propname;
    }

    /**
     * Sets the property name from request parameter.
     *
     * @param propname String the property name
     */
    @StrutsParameter
    public void setPropname(String propname) {
        this.propname = propname;
    }

    /**
     * Gets the old password from the forced password reset form.
     *
     * @return String the old password for verification during password change
     */
    public String getOldPassword() {
        return oldPassword;
    }

    /**
     * Sets the old password from forced password reset form parameter.
     *
     * <p>Struts2 automatically calls this method to populate the oldPassword field
     * from the "oldPassword" request parameter during forced password reset flow.
     *
     * @param oldPassword String the old password for verification
     */
    @StrutsParameter
    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    /**
     * Gets the new password from the forced password reset form.
     *
     * @return String the new password to be set
     */
    public String getNewPassword() {
        return newPassword;
    }

    /**
     * Sets the new password from forced password reset form parameter.
     *
     * <p>Struts2 automatically calls this method to populate the newPassword field
     * from the "newPassword" request parameter during forced password reset flow.
     *
     * @param newPassword String the new password to be set
     */
    @StrutsParameter
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    /**
     * Gets the confirmation password from the forced password reset form.
     *
     * @return String the confirmation of the new password
     */
    public String getConfirmPassword() {
        return confirmPassword;
    }

    /**
     * Sets the confirmation password from forced password reset form parameter.
     *
     * <p>Struts2 automatically calls this method to populate the confirmPassword field
     * from the "confirmPassword" request parameter during forced password reset flow.
     *
     * @param confirmPassword String the confirmation of the new password
     */
    @StrutsParameter
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    /**
     * Gets the MFA TOTP code from the MFA validation form.
     *
     * @return String the 6-digit TOTP code (RFC 6238)
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the MFA code from MFA validation form parameter.
     *
     * <p>Struts2 automatically calls this method to populate the code field
     * from the "code" request parameter during MFA validation flow.
     *
     * @param code String the 6-digit TOTP code generated by the user's authenticator app
     */
    @StrutsParameter
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Checks if this is an MFA registration flow.
     *
     * @return boolean true if registering MFA for first time, false if validating existing MFA
     */
    public boolean isMfaRegistrationFlow() {
        return mfaRegistrationFlow;
    }

    /**
     * Sets whether this is an MFA registration flow.
     *
     * <p>Struts2 automatically calls this method to populate the mfaRegistrationFlow field
     * from the "mfaRegistrationFlow" request parameter.
     *
     * @param mfaRegistrationFlow boolean true for MFA registration, false for standard MFA validation
     */
    @StrutsParameter
    public void setMfaRegistrationFlow(boolean mfaRegistrationFlow) {
        this.mfaRegistrationFlow = mfaRegistrationFlow;
    }
}
