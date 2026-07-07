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
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.managers.UserSessionManager;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.util.AlertTimer;
import io.github.carlos_emr.carlos.webserv.oauth.OAuthAuthorizationSessionState;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
     * @see io.github.carlos_emr.carlos.login.gate.SelectFacility2Action for the CSRF-protected
     *      facility selection POST flow
     */
    public static final String SELECTED_FACILITY_ID = "selectedFacilityId";

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Legacy authentication grep anchor mirroring the same literal in {@link LoginCheckLoginBean}.
     *
     * <p>Operational log searches still use this distinctive prefix to separate credential
     * decisions from general login controller flow. Keep the token stable unless log dashboards
     * and runbooks are migrated at the same time.</p>
     */
    private static final String LOG_PRE = "Login!@#$: ";
    /** Default for {@code password_min_length} when the property is absent or malformed. */
    private static final int DEFAULT_POLICY_MIN_LENGTH = 8;
    /** Default for {@code password_min_groups} when the property is absent or malformed. */
    private static final int DEFAULT_POLICY_MIN_GROUPS = 3;
    /** Default for {@code password_group_lower_chars} when the property is absent. */
    private static final String DEFAULT_POLICY_LOWER_CHARS = "abcdefghijklmnopqrstuvwxyz";
    /** Default for {@code password_group_upper_chars} when the property is absent. */
    private static final String DEFAULT_POLICY_UPPER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /** Default for {@code password_group_digits} when the property is absent. */
    private static final String DEFAULT_POLICY_DIGIT_CHARS = "0123456789";
    /** Default for {@code password_group_special} when the property is absent. */
    private static final String DEFAULT_POLICY_SPECIAL_CHARS = "! @#$%^&*()_+|~-=`{}[]\\:\";'<>?,./";
    private static final Pattern OAUTH_TOKEN_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9._~\\-]{1,200}");

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

    private static final String FORCE_PASSWORD_RESET_OLD_PASSWORD_ATTEMPTS_ATTR =
            "forcePasswordResetOldPasswordAttempts";

    private static final int MAX_FORCE_PASSWORD_RESET_OLD_PASSWORD_ATTEMPTS = 5;

    /**
     * Session marker for the short-lived MFA challenge state.
     *
     * <p>The normal {@code user} session attribute is intentionally not set until the
     * MFA code has been validated. LoginFilter treats {@code user} as an authenticated
     * session, so MFA state must use distinct attributes that grant no application access.</p>
     */
    public static final String PENDING_MFA_AUTH_ATTR = PendingMfaChallenges.AUTH_ATTR;

    /** Session key for counting retryable MFA code failures inside one pending challenge. */
    private static final String PENDING_MFA_ATTEMPTS_ATTR = PendingMfaChallenges.ATTEMPTS_ATTR;

    /** Maximum invalid OTP submissions allowed before pending MFA state is cleared. */
    private static final int MAX_PENDING_MFA_ATTEMPTS = 5;

    /**
     * Session key for the provider number associated with the pending MFA challenge.
     *
     * <p>This is useful for audit context when a cache entry expires. It is not sufficient to
     * complete login; the opaque cache token must still resolve to server-side pending state.</p>
     */
    private static final String PENDING_MFA_PROVIDER_NO_ATTR = PendingMfaChallenges.PROVIDER_NO_ATTR;

    /**
     * Session key for the opaque pending-MFA cache token.
     *
     * <p>The token references {@link PendingMfaChallengeCache}; the session must never store the
     * full {@link Security} entity, the {@link LoginCheckLogin#auth} result, or an MFA registration
     * secret while OTP validation is still pending.</p>
     */
    private static final String PENDING_MFA_TOKEN_ATTR = PendingMfaChallenges.TOKEN_ATTR;

    /** Spring-managed service for provider data access and management */
    private final ProviderManager providerManager = SpringUtils.getBean(ProviderManager.class);

    /** DAO for facility data access */
    private final FacilityDao facilityDao = SpringUtils.getBean(FacilityDao.class);

    /** DAO for provider preference data access */
    private final ProviderPreferenceDao providerPreferenceDao = SpringUtils.getBean(ProviderPreferenceDao.class);

    /** DAO for provider data access */
    private final ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    /** DAO for user property data access */
    private final UserPropertyDAO propDao = SpringUtils.getBean(UserPropertyDAO.class);

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
     *   <li>"error" - Authentication cannot continue safely and a login failure page should render</li>
     *   <li>NONE - Processing complete (redirect already sent)</li>
     *   <li>null - AJAX response sent (no forward needed)</li>
     * </ul>
     *
     * @return String Struts2 result name indicating next page to display
     * @throws ServletException if servlet-level error occurs
     * @throws IOException if I/O error occurs during redirect or response writing
     * @see LoginCheckLogin#auth for authentication logic
     * @see MfaManager#getQRCodeImageData for MFA QR code generation
     * @see #validateMfaAndCompleteLogin for MFA continuation logic
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = {"XSS_SERVLET", "IMPROPER_UNICODE", "UNVALIDATED_REDIRECT"}, justification = "XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink. case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String execute() throws ServletException, IOException {

        if (!"POST".equals(request.getMethod())) {
            MiscUtils.getLogger().info("Rejected non-POST login request: method={}, remote={}, uri={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(request.getMethod()), LogSafe.sanitize(request.getRemoteAddr()),
                    LogSafe.sanitizeUri(request.getRequestURI()));
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

        cl = new LoginCheckLogin();
        String userName = "";
        String password = "";
        String pin = "";
        String nextPage = "";
        boolean forcedpasswordchange = true;
        boolean forcedPasswordChangeRequest = request.getParameter("forcedpasswordchange") != null
                && request.getParameter("forcedpasswordchange").equalsIgnoreCase("true")
                && (this.oldPassword != null || this.newPassword != null || this.confirmPassword != null);
        if (forcedPasswordChangeRequest && !isForcedPasswordResetSubmitPath(request)) {
            logger.warn("Rejected forced password reset payload on non-reset route: uri={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitizeUri(request.getRequestURI()), LogSafe.sanitize(ip));
            LogAction.addLog("", LogConst.LOGIN, LogConst.CON_LOGIN,
                    "forced_password_reset_wrong_route", ip);
            response.sendRedirect(loginFailedRedirectUrl(message("login.errorUnableToProcess")));
            return NONE;
        }

        if (!forcedPasswordChangeRequest && this.code != null && !this.code.isEmpty()) {
            return validateMfaAndCompleteLogin(ip, isMobileOptimized, submitType, ajaxResponse);
        }

        // Forced password reset submit path.
        if (forcedPasswordChangeRequest) {
            // Credentials remain server-side; the session carries only the opaque cache token.
            HttpSession pendingResetSession = request.getSession(false);
            Object credsTokenAttr = pendingResetSession == null
                    ? null
                    : pendingResetSession.getAttribute(LOGIN_CREDENTIALS_TOKEN_ATTR);
            String credsToken = credsTokenAttr instanceof String ? (String) credsTokenAttr : null;
            LoginCredentialCache.LoginCredentials cached = LoginCredentialCache.getInstance().peek(credsToken);
            if (cached == null) {
                // Expired, missing, or consumed token means no staged credentials remain.
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
            // Validate cached nextPage as defense in depth against open redirects.
            if (!RedirectValidationUtils.isValidRelativeRedirect(nextPage)) {
                if (nextPage != null) {
                    logger.warn("Rejected invalid nextPage from credential cache: {}", LogSafe.sanitize(nextPage)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                }
                nextPage = null;
            }

            String newPassword = this.getNewPassword();
            String confirmPassword = this.getConfirmPassword();
            String oldPassword = this.getOldPassword();

            boolean oldPasswordMatched = oldPasswordMatches(oldPassword, password);
            if (!oldPasswordMatched) {
                int failedAttempts = incrementForcedResetOldPasswordAttempts();
                if (failedAttempts >= MAX_FORCE_PASSWORD_RESET_OLD_PASSWORD_ATTEMPTS) {
                    auditForcedPasswordResetFailure(userName, "old_password_mismatch_limit");
                    LoginCredentialCache.getInstance().invalidate(credsToken);
                    removeAttributesFromSession(request);
                    response.sendRedirect(loginFailedRedirectUrl(message(
                            "provider.providerchangepassword.errorSessionExpired")));
                    return NONE;
                }
            } else {
                clearForcedResetOldPasswordAttempts();
            }

            String errorStr = errorHandling(userName, newPassword, confirmPassword, oldPassword,
                    oldPasswordMatched);

            // Retryable validation failures keep the token; terminal submits consume it.
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

            try {
                persistNewPassword(userName, newPassword);
            } catch (RuntimeException e) {
                logger.error("Forced password reset failed before password persistence completed", e);
                try {
                    auditForcedPasswordResetFailure(userName, "persistence_failure");
                    removeAttributesFromSession(request);
                } catch (RuntimeException secondary) {
                    logger.error("Unable to cleanly report forced password reset persistence failure: user={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                            LogSafe.sanitize(userName), secondary);
                }
                response.sendRedirect(loginFailedRedirectUrl(message("login.errorResetPersistence")));
                return NONE;
            }

            password = newPassword;

            try {
                removeAttributesFromSession(request);
            } catch (IllegalStateException | UnsupportedOperationException cleanupFailure) {
                logger.warn("Forced password reset persisted, but session cleanup failed with {}; ending current login flow",
                        cleanupFailure.getClass().getName(), cleanupFailure);
                // The password is already changed; re-authenticating in a damaged session can mislead
                // users into retrying the old password and tripping lockout counters.
                try {
                    auditForcedPasswordResetCompletion(userName, "cleanup_failure_relogin");
                } catch (RuntimeException auditFailure) {
                    logger.error("Unable to audit forced password reset cleanup failure: user={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                            LogSafe.sanitize(userName), auditFailure);
                }
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.sendRedirect(loginFailedRedirectUrl(message("login.passwordUpdatedLoginAgain")));
                return NONE;
            }

            // make sure this checking doesn't happen again
            forcedpasswordchange = false;
            // Re-authentication below intentionally uses the cached username/PIN plus the new
            // password; the reset form never accepts fresh username/PIN request parameters.

        } else {
            // Standard login attempt.
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

            logger.debug("nextPage: {}", LogSafe.sanitize(nextPage)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
            // Empty hidden nextPage fields are absent; facility choices post to /select_facility.
            if (nextPage != null && !nextPage.isEmpty()) {
                logger.warn("Rejected facility selection on CSRF-exempt login route: user={}, nextPage={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        LogSafe.sanitize(userName), LogSafe.sanitize(nextPage));
                LogAction.addLog(userName, LogConst.LOGIN, LogConst.CON_LOGIN,
                        "facility_selection_on_login_rejected", ip);
                response.sendRedirect(loginFailedRedirectUrl(message("login.errorUnableToProcess")));
                return NONE;
            }

            if (cl.isBlock(ip, userName)) {
                logger.info("{} Blocked: {}", LOG_PRE, LogSafe.sanitize(userName)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
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

            logger.debug("ip was not blocked: {}", LogSafe.sanitize(ip)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
        }

        // Credential provider authentication boundary.
        String[] strAuth;
        try {
            strAuth = cl.auth(userName, password, pin, ip);
        } catch (Exception e) {
            logger.error("Authentication provider failed during login: user={}, remote={}, ajax={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(userName), LogSafe.sanitize(ip), ajaxResponse, e);
            recordAuthenticationExceptionFailure(cl, ip, userName);
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
        
        logger.debug("strAuth : {}", LogSafe.sanitize(Arrays.toString(strAuth))); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
        
        // Successful login handling.
        if (strAuth != null && strAuth.length != 1) { // login successfully

            // is the providers record inactive?
            Provider p = providerDao.getProvider(strAuth[0]);
            if (p == null || (p.getStatus() != null && p.getStatus().equals("0"))) {
                logger.info("{} Inactive: {}", LOG_PRE, LogSafe.sanitize(userName)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
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
                logger.error("Authenticated user has no security record: {}", LogSafe.sanitize(userName)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
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
                    String newURL = loginFailedRedirectUrl(message("login.errorResetStaging"));
                    removeAttributesFromSession(request);
                    response.sendRedirect(newURL);
                    return NONE;
                }

                response.sendRedirect(request.getContextPath() + "/forcepasswordreset");
                return NONE;
            }

            if (MfaManager.isOscarMfaEnabled() && security.isUsingMfa()) {
                return beginPendingMfaChallenge(strAuth, security, ip);
            }

            return completeAuthenticatedLogin(security, strAuth, ip, isMobileOptimized, submitType, ajaxResponse);

        }
        // Authentication failure handling.
        // expired password
        else if (strAuth != null && strAuth.length == 1 && strAuth[0].equals("expired")) {
            logger.warn("Expired password: user={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(userName), LogSafe.sanitize(ip));
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
                json.put("error", message("login.errorInvalidCredentials"));
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
     * <p>The HTTP session receives only a marker, provider number, retry counter, and opaque cache
     * token. The canonical {@code user} session attribute is set by
     * {@link #completeAuthenticatedLogin} after the OTP validates.</p>
     *
     * @param strAuth authentication result fields returned by {@link LoginCheckLogin#auth}
     * @param security security row for the authenticated provider
     * @param ip remote address used for audit logging
     * @return {@code mfaHandler} when the challenge view is ready, or {@code error} when
     *         registration setup cannot safely continue
     */
    private String beginPendingMfaChallenge(String[] strAuth, Security security, String ip) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        session = request.getSession();
        session.setMaxInactiveInterval(300);
        String registrationSecret = null;
        try {
            if (this.mfaManager.isMfaRegistrationRequired(security.getId())) {
                registrationSecret = MfaManager.generateMfaSecret();
                request.setAttribute("mfaRegistrationRequired", true);
                request.setAttribute("qrData", this.mfaManager.getQRCodeImageData(security.getId(), registrationSecret));
            }
        } catch (RuntimeException e) {
            session.invalidate();
            logger.error("Unable to prepare MFA registration: providerNo={}, securityId={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(security.getProviderNo()),
                    LogSafe.sanitize(String.valueOf(security.getSecurityNo())),
                    LogSafe.sanitize(ip),
                    e);
            LogAction.addLog(security.getProviderNo(), "login", "mfa_failed", "mfa_setup_failure", ip);
            return loginFailureResult(message("login.errorUnableToProcess"));
        }

        PendingMfaChallengeCache.PendingMfaChallenge challenge =
                new PendingMfaChallengeCache.PendingMfaChallenge(
                        security.getSecurityNo(), security.getProviderNo(), strAuth, registrationSecret);
        String challengeToken = PendingMfaChallengeCache.getInstance().store(challenge);
        PendingMfaChallenges.stage(session, security.getProviderNo(), challengeToken, 0);
        request.setAttribute("securityId", String.valueOf(security.getSecurityNo()));
        return "mfaHandler";
    }

    /**
     * Validates a submitted MFA code, then completes the normal authenticated login flow.
     *
     * <p>The pending-MFA session is the boundary between password/PIN success and an authenticated
     * application session. Fatal validation failures clear that pending state so the staged
     * opaque cache token cannot survive after a corrupted secret, missing security record, or
     * broken TOTP key. Retryable user-code failures intentionally keep the pending state so the
     * user can enter a new OTP.</p>
     *
     * @param ip remote address used for logging and final login audit
     * @param isMobileOptimized whether mobile session flags should be applied after success
     * @param submitType mobile/full-site submit mode from the original request
     * @param ajaxResponse whether the final response should be JSON instead of a Struts result
     * @return Struts result name, {@link #NONE}, or null for direct AJAX responses
     * @throws IOException if redirecting or writing the final response fails
     */
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    private String validateMfaAndCompleteLogin(String ip, boolean isMobileOptimized, String submitType,
                                               boolean ajaxResponse) throws IOException {
        HttpSession session = request.getSession(false);
        if (!hasPendingMfaSession(session)) {
            logger.info("Rejected MFA verification without valid pending challenge: remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(ip));
            if (session != null) {
                clearPendingMfaSession(session);
            }
            response.sendRedirect(loginFailedRedirectUrl(message("provider.providerchangepassword.errorSessionExpired")));
            return NONE;
        }

        String challengeToken = PendingMfaChallenges.getToken(session);
        String pendingProviderNo = (String) session.getAttribute(PENDING_MFA_PROVIDER_NO_ATTR);
        PendingMfaChallengeCache.PendingMfaChallenge challenge =
                PendingMfaChallengeCache.getInstance().peek(challengeToken);
        if (challenge == null) {
            logger.info("Rejected MFA verification because pending challenge token was missing or expired: providerNo={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(pendingProviderNo), LogSafe.sanitize(ip));
            clearPendingMfaSession(session);
            response.sendRedirect(loginFailedRedirectUrl(message("provider.providerchangepassword.errorSessionExpired")));
            return NONE;
        }

        String[] strAuth = challenge.authResult();
        Security security;
        try {
            security = securityDao.find(challenge.securityNo());
        } catch (RuntimeException e) {
            logger.error("Unable to load security record for pending MFA challenge: providerNo={}, securityId={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(challenge.providerNo()),
                    LogSafe.sanitize(String.valueOf(challenge.securityNo())),
                    LogSafe.sanitize(ip),
                    e);
            clearPendingMfaSession(session);
            return loginFailureResult(message("login.errorUnableToProcess"));
        }
        if (security == null) {
            logger.error("Rejected MFA verification because pending challenge has no security record: providerNo={}, securityId={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(challenge.providerNo()),
                    LogSafe.sanitize(String.valueOf(challenge.securityNo())),
                    LogSafe.sanitize(ip));
            clearPendingMfaSession(session);
            response.sendRedirect(loginFailedRedirectUrl(message("login.errorSecurityRecordMissing")));
            return NONE;
        }

        boolean registrationChallenge = challenge.registrationSecret() != null;
        String mfaSecret;
        if (registrationChallenge) {
            mfaSecret = challenge.registrationSecret();
            if (mfaSecret == null || mfaSecret.isEmpty()) {
                logger.warn("Rejected MFA registration submit without a staged secret: providerNo={}, securityId={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        LogSafe.sanitize(security.getProviderNo()),
                        LogSafe.sanitize(String.valueOf(security.getSecurityNo())),
                        LogSafe.sanitize(ip));
                clearPendingMfaSession(session);
                response.sendRedirect(loginFailedRedirectUrl(message("provider.providerchangepassword.errorSessionExpired")));
                return NONE;
            }
        } else {
            try {
                mfaSecret = this.mfaManager.getMfaSecret(security);
            } catch (Exception e) {
                logger.error("Unable to retrieve MFA secret: providerNo={}, securityId={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        LogSafe.sanitize(security.getProviderNo()),
                        LogSafe.sanitize(String.valueOf(security.getSecurityNo())),
                        LogSafe.sanitize(ip),
                        e);
                clearPendingMfaSession(session);
                return loginFailureResult(message("login.errorUnableToProcess"));
            }
        }

        boolean validCode;
        try {
            validCode = isValidTotpCode(mfaSecret, this.code);
        } catch (InvalidKeyException e) {
            logger.error("Unable to validate MFA code: providerNo={}, securityId={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(security.getProviderNo()),
                    LogSafe.sanitize(String.valueOf(security.getSecurityNo())),
                    LogSafe.sanitize(ip),
                    e);
            clearPendingMfaSession(session);
            return loginFailureResult(message("login.errorUnableToProcess"));
        }

        if (!validCode) {
            LogAction.addLog(security.getProviderNo(), "login", "mfa_failed", "mfa", ip);
            int failedAttempts = incrementPendingMfaAttempts(session);
            if (failedAttempts >= MAX_PENDING_MFA_ATTEMPTS) {
                LogAction.addLog(security.getProviderNo(), "login", "mfa_locked", "mfa", ip);
                logger.warn("MFA challenge exhausted retry limit: providerNo={}, securityId={}, remote={}, attempts={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        LogSafe.sanitize(security.getProviderNo()),
                        LogSafe.sanitize(String.valueOf(security.getSecurityNo())),
                        LogSafe.sanitize(ip),
                        failedAttempts);
                clearPendingMfaSession(session);
                return loginFailureResult(message("login.errorUnableToProcess"));
            }
            if (registrationChallenge) {
                request.setAttribute("mfaRegistrationRequired", true);
                request.setAttribute("qrData", this.mfaManager.getQRCodeImageData(security.getId(), mfaSecret));
            }
            request.setAttribute("mfaValidateCodeErr", "Invalid MFA Code");
            request.setAttribute("securityId", String.valueOf(security.getSecurityNo()));
            return "mfaHandler";
        }

        PendingMfaChallengeCache.PendingMfaChallenge terminalChallenge =
                PendingMfaChallengeCache.getInstance().consume(challengeToken);
        if (terminalChallenge == null) {
            logger.info("Rejected MFA verification because pending challenge token was already consumed: providerNo={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(pendingProviderNo), LogSafe.sanitize(ip));
            clearPendingMfaSession(session);
            response.sendRedirect(loginFailedRedirectUrl(message("provider.providerchangepassword.errorSessionExpired")));
            return NONE;
        }
        strAuth = terminalChallenge.authResult();
        registrationChallenge = terminalChallenge.registrationSecret() != null;
        if (registrationChallenge) {
            mfaSecret = terminalChallenge.registrationSecret();
        }

        if (registrationChallenge) {
            try {
                this.mfaManager.saveMfaSecret(buildLoggedInInfoForPendingMfa(session, strAuth, security), security, mfaSecret);
            } catch (Exception e) {
                logger.error("Unable to persist MFA registration secret: providerNo={}, securityId={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        LogSafe.sanitize(security.getProviderNo()),
                        LogSafe.sanitize(String.valueOf(security.getSecurityNo())),
                        LogSafe.sanitize(ip),
                        e);
                LogAction.addLog(security.getProviderNo(), "login", "mfa_failed", "mfa_registration_persist", ip);
                clearPendingMfaSession(session);
                return loginFailureResult(message("login.errorMfaRegistrationPersistence"));
            }
        }

        // Success audit follows registration persistence so operators do not see a false success row
        // when the OTP was correct but the new secret could not be stored.
        LogAction.addLog(security.getProviderNo(), "login", "mfa_success", "mfa", ip);
        return completeAuthenticatedLogin(security, strAuth, ip, isMobileOptimized, submitType, ajaxResponse);
    }

    /**
     * Returns whether the session contains all state required to validate a pending MFA challenge.
     *
     * <p>Every attribute is required and type-checked. Missing or wrong-typed state is treated as an
     * expired challenge rather than reconstructing login state from request parameters. The method
     * deliberately checks only session markers; the cache token is resolved separately so the
     * caller can log a clearer expired-token reason.</p>
     *
     * @param session candidate HTTP session
     * @return true when the session contains the pending-MFA marker and opaque token
     */
    private boolean hasPendingMfaSession(HttpSession session) {
        return session != null
                && Boolean.TRUE.equals(session.getAttribute(PENDING_MFA_AUTH_ATTR))
                && session.getAttribute(PENDING_MFA_PROVIDER_NO_ATTR) instanceof String
                && session.getAttribute(PENDING_MFA_TOKEN_ATTR) instanceof String;
    }

    /**
     * Validates an RFC 6238 TOTP code with one time-step of clock skew tolerance.
     *
     * <p>Authenticator apps and server clocks can differ briefly. Accepting the current, previous,
     * or next time step preserves the usual +/- one-step tolerance without accepting an unbounded
     * replay window.</p>
     *
     * @param mfaSecret Base32-encoded MFA secret
     * @param submittedCode user-submitted TOTP code; length validation is performed by the TOTP
     *        comparison rather than this helper
     * @return true when the submitted code matches the current, previous, or next time step
     * @throws InvalidKeyException when the Base32 secret is null, empty, malformed, or cannot
     *         create a valid TOTP key
     */
    private boolean isValidTotpCode(String mfaSecret, String submittedCode) throws InvalidKeyException {
        TimeBasedOneTimePasswordGenerator totpGenerator = new TimeBasedOneTimePasswordGenerator();
        SecretKeySpec key;
        try {
            byte[] decodedKey = new Base32().decode(mfaSecret);
            if (decodedKey == null || decodedKey.length == 0) {
                throw new IllegalArgumentException("empty decoded MFA secret");
            }
            key = new SecretKeySpec(decodedKey, totpGenerator.getAlgorithm());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidKeyException("malformed MFA secret", e);
        }
        java.time.Instant now = java.time.Instant.now();
        java.time.Duration timeStep = totpGenerator.getTimeStep();

        return constantTimeEquals(totpGenerator.generateOneTimePasswordString(key, now), submittedCode)
                || constantTimeEquals(totpGenerator.generateOneTimePasswordString(key, now.minus(timeStep)), submittedCode)
                || constantTimeEquals(totpGenerator.generateOneTimePasswordString(key, now.plus(timeStep)), submittedCode);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Removes all pre-authentication MFA state from the session.
     *
     * <p>The pending-MFA cache payload represents a successful password/PIN check and must be
     * invalidated before any fatal MFA failure returns control to Struts. During MFA registration
     * this also removes the cached registration secret, which is sensitive material and must not
     * survive a failed or abandoned challenge.</p>
     *
     * @param session session containing pending-MFA state
     */
    private void clearPendingMfaSession(HttpSession session) {
        PendingMfaChallenges.clearFromSession(session);
    }

    /**
     * Increments the server-side failed-code counter for the current pending MFA challenge.
     *
     * <p>The counter deliberately lives in the pending-MFA session rather than relying only on the
     * WAF rate limiter, which may run in detect-only mode in development or legacy deployments.</p>
     */
    private int incrementPendingMfaAttempts(HttpSession session) {
        synchronized (session) {
            Object attemptsAttr = session.getAttribute(PENDING_MFA_ATTEMPTS_ATTR);
            int attempts = attemptsAttr instanceof Number ? ((Number) attemptsAttr).intValue() : 0;
            attempts++;
            session.setAttribute(PENDING_MFA_ATTEMPTS_ATTR, attempts);
            return attempts;
        }
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
     * Returns the servlet-context CAISI user list, tolerating a missing startup attribute.
     *
     * <p>Older startup paths are expected to seed {@code CaseMgmtUsers}, but login must not fail if
     * that context attribute is absent after a partial startup or test container bootstrap. Mixed
     * legacy lists are copied defensively: only provider-number {@link String} entries are retained,
     * and only the class name for non-String entries is logged at WARN, never the value itself.
     * A non-list context value is logged at WARN because it means shared CAISI state was seeded with
     * an incompatible type and will be rebuilt.</p>
     *
     * @return mutable provider-number list safe to store back into servlet context/session
     */
    private List<String> caseManagementUsers() {
        Object caseMgmtUsersAttr = request.getSession().getServletContext().getAttribute("CaseMgmtUsers");
        List<String> caseMgmtUsers = new ArrayList<String>();
        if (caseMgmtUsersAttr instanceof List<?>) {
            for (Object providerNo : (List<?>) caseMgmtUsersAttr) {
                if (providerNo instanceof String) {
                    caseMgmtUsers.add((String) providerNo);
                } else {
                    logger.warn("Ignoring non-String CaseMgmtUsers entry during login session setup: type={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                            providerNo == null ? "null" : LogSafe.sanitize(providerNo.getClass().getName()));
                }
            }
        } else if (caseMgmtUsersAttr != null) {
            logger.warn("CaseMgmtUsers context attribute is not a List: type={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(caseMgmtUsersAttr.getClass().getName()));
        }
        return caseMgmtUsers;
    }

    /**
     * Starts the BC alert timer when configured without blocking successful login.
     *
     * <p>BC alert polling is a post-login convenience subsystem. Malformed polling frequency or
     * missing alert-code configuration should be operator-visible in logs, but should not prevent
     * a user who has already authenticated from reaching the application.</p>
     *
     * @param properties CARLOS runtime properties
     */
    private void startBcAlertTimerIfConfigured(Properties properties) {
        if (!"BC".equals(properties.getProperty("billregion"))) {
            return;
        }

        String alertFreq = properties.getProperty("ALERT_POLL_FREQUENCY");
        if (alertFreq == null || alertFreq.trim().isEmpty()) {
            logger.info("Skipping BC alert timer setup because ALERT_POLL_FREQUENCY is not configured");
            return;
        }

        String configuredAlerts = CarlosProperties.getInstance().getProperty("CDM_ALERTS");
        if (configuredAlerts == null || configuredAlerts.trim().isEmpty()) {
            logger.warn("Skipping BC alert timer setup because CDM_ALERTS is not configured");
            return;
        }

        try {
            Long longFreq = Long.valueOf(alertFreq);
            AlertTimer.getInstance(configuredAlerts.split(","), longFreq.longValue());
        } catch (NumberFormatException e) {
            logger.warn("Skipping BC alert timer setup because ALERT_POLL_FREQUENCY is invalid: value={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(alertFreq), e);
        } catch (RuntimeException e) {
            // The alert timer is post-login convenience work; startup defects must be visible but
            // cannot strand an otherwise authenticated BC user at the login boundary.
            logger.warn("Skipping BC alert timer setup because AlertTimer startup failed", e);
        }
    }

    /**
     * Completes session setup after password/PIN authentication and any required MFA have succeeded.
     *
     * <p>This is the only path that creates the canonical authenticated session marker
     * ({@code user}). Callers must complete forced password reset and MFA checks before invoking it,
     * because {@link io.github.carlos_emr.carlos.sec.LoginFilter} treats that marker as logged-in
     * state. If the provider row cannot be loaded after authentication, the new session is
     * invalidated and the method returns {@code error} rather than leaving a partial session.</p>
     *
     * @param security authenticated security row
     * @param strAuth authentication result fields returned by {@link LoginCheckLogin#auth}
     * @param ip remote address used for audit logging
     * @param isMobileOptimized whether mobile session flags should be applied
     * @param submitType mobile/full-site submit mode
     * @param ajaxResponse whether to write the final provider JSON response directly
     * @return Struts result name, {@link #NONE} after redirect, {@code error}, or null for AJAX
     * @throws IOException if redirecting or writing the response fails
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "UNVALIDATED_REDIRECT"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    private String completeAuthenticatedLogin(Security security, String[] strAuth, String ip,
                                              boolean isMobileOptimized, String submitType,
                                              boolean ajaxResponse) throws IOException {
        HttpSession session = request.getSession(false);
        Map<String, String> oauthAuthorizationNonces =
                OAuthAuthorizationSessionState.snapshotNonces(session);
        if (session != null) {
            session.invalidate();
        }
        session = request.getSession();
        OAuthAuthorizationSessionState.restoreNonces(session, oauthAuthorizationNonces);
        session.setMaxInactiveInterval(7200);

        if (security != null) {
            this.userSessionManager.registerUserSession(security.getSecurityNo(), session);
        }

        logger.debug("Assigned new session for: {} : {} : {}", LogSafe.sanitize(strAuth[0]), LogSafe.sanitize(strAuth[3]), LogSafe.sanitize(strAuth[4])); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
        LogAction.addLog(strAuth[0], LogConst.LOGIN, LogConst.CON_LOGIN, "", ip);

        Properties pvar = CarlosProperties.getInstance();

        String providerNo = strAuth[0] != null ? strAuth[0].trim() : "";
        // Session values stay as raw domain data. JSP, JSON, and redirect sinks must apply the
        // correct context-specific encoding when these values are rendered.
        session.setAttribute("user", providerNo); // nosemgrep: tainted-session-from-http-request -- provider number is the authenticated LoginCheckLogin result
        session.setAttribute("userfirstname", strAuth[1] != null ? strAuth[1].trim() : ""); // nosemgrep: tainted-session-from-http-request -- display name comes from authenticated LoginCheckLogin result
        session.setAttribute("userlastname", strAuth[2] != null ? strAuth[2].trim() : ""); // nosemgrep: tainted-session-from-http-request -- display name comes from authenticated LoginCheckLogin result
        session.setAttribute("userrole", strAuth[4] != null ? strAuth[4].trim() : ""); // nosemgrep: tainted-session-from-http-request -- role comes from authenticated LoginCheckLogin result
        session.setAttribute("oscar_context_path", request.getContextPath()); // nosemgrep: tainted-session-from-http-request -- servlet context path is container-provided routing state
        session.setAttribute("expired_days", strAuth[5] != null ? strAuth[5].trim() : ""); // nosemgrep: tainted-session-from-http-request -- password-expiry metadata comes from authenticated LoginCheckLogin result
        if (isMobileOptimized) {
            if ("Full".equalsIgnoreCase(submitType)) {
                session.setAttribute("fullSite", "true"); // nosemgrep: tainted-session-from-http-request -- constant session mode marker selected by validated mobile flow
            } else {
                session.setAttribute("mobileOptimized", "true"); // nosemgrep: tainted-session-from-http-request -- constant session mode marker selected by validated mobile flow
            }
        }

        String default_pmm = null;
        ProviderPreference providerPreference = providerPreferenceDao.find(providerNo);

        if (providerPreference == null) {
            providerPreference = new ProviderPreference();
        }

        session.setAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE, providerPreference); // nosemgrep: tainted-session-from-http-request -- provider preferences are DAO-loaded server-side state

        if (IsPropertiesOn.isCaisiEnable()) {
            String tklerProviderNo;
            UserProperty prop = propDao.getProp(providerNo, UserProperty.PROVIDER_FOR_TICKLER_WARNING);
            if (prop == null) {
                tklerProviderNo = providerNo;
            } else {
                tklerProviderNo = prop.getValue();
            }
            session.setAttribute("tklerProviderNo", tklerProviderNo); // nosemgrep: tainted-session-from-http-request -- tickler provider comes from DAO-loaded provider preference

            session.setAttribute("newticklerwarningwindow", providerPreference.getNewTicklerWarningWindow()); // nosemgrep: tainted-session-from-http-request -- CAISI value comes from DAO-loaded provider preference
            session.setAttribute("default_pmm", providerPreference.getDefaultCaisiPmm()); // nosemgrep: tainted-session-from-http-request -- CAISI value comes from DAO-loaded provider preference
            session.setAttribute("caisiBillingPreferenceNotDelete", // nosemgrep: tainted-session-from-http-request -- CAISI billing preference is DAO-loaded server-side state
                    String.valueOf(providerPreference.getDefaultDoNotDeleteBilling()));

            default_pmm = providerPreference.getDefaultCaisiPmm();
            if ("enabled".equals(providerPreference.getDefaultNewOscarCme())) {
                List<String> sessionCaseMgmtUsers;
                // CaseMgmtUsers is servlet-context state shared by every CAISI session. Hold the
                // context lock across read-modify-write so concurrent first logins do not lose a
                // provider number, then store a session snapshot so later context changes cannot
                // mutate this user's view.
                synchronized (request.getSession().getServletContext()) {
                    List<String> contextCaseMgmtUsers = caseManagementUsers();
                    if (!contextCaseMgmtUsers.contains(providerNo)) {
                        contextCaseMgmtUsers.add(providerNo);
                    }
                    List<String> contextSnapshot = new ArrayList<String>(contextCaseMgmtUsers);
                    request.getSession().getServletContext().setAttribute("CaseMgmtUsers", contextSnapshot); // nosemgrep: tainted-session-from-http-request -- context snapshot contains DAO/auth-derived provider numbers, not raw request values
                    sessionCaseMgmtUsers = new ArrayList<String>(contextSnapshot);
                }
                session.setAttribute("CaseMgmtUsers", sessionCaseMgmtUsers); // nosemgrep: tainted-session-from-http-request -- defensive copy of servlet-context provider-number list
            }
        }
        session.setAttribute("starthour", providerPreference.getStartHour().toString()); // nosemgrep: tainted-session-from-http-request -- schedule preference is DAO-loaded server-side state
        session.setAttribute("endhour", providerPreference.getEndHour().toString()); // nosemgrep: tainted-session-from-http-request -- schedule preference is DAO-loaded server-side state
        session.setAttribute("everymin", providerPreference.getEveryMin().toString()); // nosemgrep: tainted-session-from-http-request -- schedule preference is DAO-loaded server-side state
        session.setAttribute("groupno", providerPreference.getMyGroupNo()); // nosemgrep: tainted-session-from-http-request -- group preference is DAO-loaded server-side state

        String where = "provider";

        if (where.equals("provider") && default_pmm != null && "enabled".equals(default_pmm)) {
            where = "caisiPMM";
        }

        if (where.equals("provider")
                && CarlosProperties.getInstance().getProperty("useProgramLocation", "false").equals("true")) {
            where = "programLocation";
        }

        startBcAlertTimerIfConfigured(pvar);

        Provider provider = providerManager.getProvider(providerNo);
        if (provider == null) {
            logger.error("Authenticated login could not load provider record: providerNo={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(providerNo), LogSafe.sanitize(ip));
            session.invalidate();
            return loginFailureResult(message("login.errorUnableToProcess"));
        }
        session.setAttribute("provider", provider); // nosemgrep: tainted-session-from-http-request -- provider entity is DAO-loaded after successful authentication
        session.setAttribute(SessionConstants.LOGGED_IN_PROVIDER, provider); // nosemgrep: tainted-session-from-http-request -- provider entity is DAO-loaded after successful authentication
        session.setAttribute(SessionConstants.LOGGED_IN_SECURITY, security); // nosemgrep: tainted-session-from-http-request -- security entity is DAO-loaded after successful authentication

        List<Integer> facilityIds = providerDao.getFacilityIds(provider.getProviderNo());
        if (facilityIds.size() > 1) {
            session.setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);
            String facilityPath = "/select_facility?nextPage=";
            String newURL = request.getContextPath() + facilityPath + SafeEncode.forUriComponent(where);

            response.sendRedirect(newURL);
            return NONE;
        } else if (facilityIds.size() == 1) {
            Facility facility = facilityDao.find(facilityIds.get(0));
            request.getSession().setAttribute("currentFacility", facility); // nosemgrep: tainted-session-from-http-request -- facility entity is DAO-loaded by authorized facility id
            session.removeAttribute(SessionConstants.PENDING_FACILITY_SELECTION);
            LogAction.addLog(strAuth[0], LogConst.LOGIN, LogConst.CON_LOGIN, "facilityId=" + facilityIds.get(0),
                    ip);
        } else {
            List<Facility> facilities = facilityDao.findAll(true);
            if (facilities != null && facilities.size() >= 1) {
                Facility fac = facilities.get(0);
                int first_id = fac.getId();
                providerDao.addProviderToFacility(providerNo, first_id);
                Facility facility = facilityDao.find(first_id);
                request.getSession().setAttribute("currentFacility", facility); // nosemgrep: tainted-session-from-http-request -- fallback facility entity is DAO-loaded from active facility list
                session.removeAttribute(SessionConstants.PENDING_FACILITY_SELECTION);
                LogAction.addLog(strAuth[0], LogConst.LOGIN, LogConst.CON_LOGIN, "facilityId=" + first_id, ip);
            }
        }

        LoggedInInfo loggedInInfo = LoggedInUserFilter.generateLoggedInInfoFromSession(request);
        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);

        String oauthBindingResult = bindOauthTokenForAuthenticatedSession(provider, ajaxResponse, where, providerNo, ip);
        if (oauthBindingResult != null) {
            return oauthBindingResult;
        }

        if (UserRoleUtils.hasRole(request, "Patient Intake")) {
            return "patientIntake";
        }

        if ("provider".equals(where)) {
            response.sendRedirect(buildDefaultProviderSchedulePath());
            return NONE;
        }

        return buildPostAuthenticationResponse(provider, ajaxResponse, where);
    }

    private String bindOauthTokenForAuthenticatedSession(Provider provider, boolean ajaxResponse,
                                                        String where, String providerNo, String ip)
            throws IOException {
        String oauthToken = request.getParameter("oauth_token");
        if (oauthToken == null) {
            return null;
        }
        if (!isValidOauthTokenId(oauthToken)) {
            logger.warn("Rejected malformed oauth_token during login completion: providerNo={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(providerNo), LogSafe.sanitize(ip));
            LogAction.addLog(providerNo, LogConst.LOGIN, LogConst.CON_LOGIN, "invalid_oauth_token", ip);
            return buildPostAuthenticationResponse(provider, ajaxResponse, where);
        }
        logger.debug("checking oauth_token");
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(oauthToken);
        if (srt != null) {
            srt.setProviderNo(providerNo);
            serviceRequestTokenDao.merge(srt);
        }
        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    private String buildPostAuthenticationResponse(Provider provider, boolean ajaxResponse, String where)
            throws IOException {

        if (ajaxResponse) {
            logger.debug("rendering ajax response");
            ObjectNode json = objectMapper.createObjectNode();
            json.put("success", true);
            json.put("providerName", SafeEncode.forJavaScript(provider.getFormattedName()));
            json.put("providerNo", provider.getProviderNo());
            response.setContentType("application/json");
            response.getWriter().write(json.toString());
            return null;
        }

        logger.debug("rendering standard response : {}", where);
        return where;
    }

    private static boolean isValidOauthTokenId(String token) {
        return token != null && OAUTH_TOKEN_ID_PATTERN.matcher(token).matches();
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
     * @return true when {@code mandatory_password_reset} is missing, set to true/yes/on/1, or
     *         contains an unrecognized value; false only for explicit false/no/off/0
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private boolean isMandatoryPasswordResetEnabled() {
        String value = CarlosProperties.getInstance().getProperty("mandatory_password_reset", "true");
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "yes".equals(normalized)
                || "on".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "no".equals(normalized)
                || "off".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        logger.warn("Unrecognized mandatory_password_reset value {}; defaulting to enabled", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                LogSafe.sanitize(value));
        return true;
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
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
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
     * using the current request locale, then falling back to English, then to a hardcoded generic
     * message if even the English bundle is missing the requested key.
     *
     * <p>A null request is tolerated for static helper call sites and uses English directly.</p>
     *
     * @param request servlet request carrying the active locale
     * @param key resource bundle key to resolve
     * @return localized resource bundle message
     */
    public static String message(HttpServletRequest request, String key) {
        Locale locale = request == null ? Locale.ENGLISH : request.getLocale();
        try {
            return ResourceBundle.getBundle("oscarResources", locale).getString(key);
        } catch (MissingResourceException e) {
            logger.warn("Missing localized message: bundle=oscarResources locale={} key={}", locale, key);
            try {
                return ResourceBundle.getBundle("oscarResources", Locale.ENGLISH).getString(key);
            } catch (MissingResourceException fallbackException) {
                logger.error("Missing default message: bundle=oscarResources locale={} key={}",
                        Locale.ENGLISH, key, fallbackException);
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

    /**
     * Routes fatal in-action login failures to the login failure view with a request-scoped message.
     *
     * <p>Do not use the {@code failure} result for these paths: the Struts mapping sends that result
     * to {@code /WEB-INF/jsp/login/logout.jsp}, which intentionally does not render login error
     * details. The {@code error} result maps to {@code /WEB-INF/jsp/login/loginfailed.jsp}.</p>
     *
     * @param errorMessage localized, display-safe error message
     * @return {@code error} so Struts renders {@code loginfailed.jsp}
     */
    private String loginFailureResult(String errorMessage) {
        request.setAttribute("errormsg", errorMessage);
        return "error";
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
     * The retry-display attribute {@link #FORCE_PASSWORD_RESET_ERROR_ATTR} is cleared at the same
     * time so stale retry messages cannot influence a later attempt.
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
    }

    /**
     * Stores user authentication information in a short-lived server-side cache for the
     * forced password reset and MFA flows, and records only an opaque cache token in
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
        String validatedNextPage = nextPage;
        if (!RedirectValidationUtils.isValidRelativeRedirect(validatedNextPage)) {
            if (validatedNextPage != null) {
                logger.warn("Rejected invalid nextPage before credential cache: {}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        LogSafe.sanitize(validatedNextPage));
            }
            validatedNextPage = null;
        }

        // SECURITY: Do NOT place credential material (password hash, PIN) in the HTTP session.
        // Sessions can be serialized to disk, replicated across nodes, dumped for debugging,
        // or read by any session-aware code. Instead, stash credentials in a short-lived
        // server-side cache keyed by a cryptographically random opaque token, and store
        // only the opaque token in the session. See LoginCredentialCache for details.
        LoginCredentialCache.LoginCredentials credentials = new LoginCredentialCache.LoginCredentials(
                userName, securityManager.encodePassword(password), pin, validatedNextPage);

        // Invalidate any previously issued token on this session before minting a new one,
        // so that stale cache entries do not outlive the current login attempt.
        HttpSession session = request.getSession(false);
        Object existingToken = session == null ? null : session.getAttribute(LOGIN_CREDENTIALS_TOKEN_ATTR);
        if (existingToken instanceof String) {
            LoginCredentialCache.getInstance().invalidate((String) existingToken);
        }
        if (session != null) {
            session.invalidate();
        }
        session = request.getSession();
        session.setMaxInactiveInterval(300);

        String token = LoginCredentialCache.getInstance().store(credentials);
        // Use an opaque random token, not user-controlled; no credential material in session
        session.setAttribute(LOGIN_CREDENTIALS_TOKEN_ATTR, token); // nosemgrep: tainted-session-from-http-request -- opaque server-generated cache token, not raw credentials
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
     * <p>The method returns a display-safe error message if validation fails, or an empty string if
     * all validations pass. Each rejection emits a PHI-safe audit reason so direct POST probing is
     * visible to operators without recording password material.
     *
     * @param userName provider login name used only for PHI-safe audit context
     * @param newPassword String the new password entered by the user
     * @param confirmPassword String the confirmation of the new password
     * @param oldPassword String the old password entered by the user for verification
     * @param oldPasswordMatched true when the staged old-password check already passed
     * @return String empty string if validation passes, or an error message if validation fails
     * @see SecurityManager#matchesPassword for password comparison logic
     */
    private String errorHandling(String userName, String newPassword, String confirmPassword,
                                 String oldPassword, boolean oldPasswordMatched) {

        // Verify old password matches the password from the staged successful login.
        if (!oldPasswordMatched) {
            return rejectForcedPasswordReset(userName, "old_password_mismatch",
                    "provider.providerchangepassword.errorOldPasswordMismatch");
        }
        // Verify new password and confirmation match
        else if (newPassword == null || confirmPassword == null || !Objects.equals(newPassword, confirmPassword)) {
            return rejectForcedPasswordReset(userName, "confirm_password_mismatch",
                    "provider.providerchangepassword.errorConfirmPasswordMismatch");
        }
        // Verify new password is different from old password (unless requirement is disabled)
        else if (!Boolean.parseBoolean(CarlosProperties.getInstance().getProperty("IGNORE_PASSWORD_REQUIREMENTS"))
                && Objects.equals(newPassword, oldPassword)) {
            return rejectForcedPasswordReset(userName, "new_password_same_as_old",
                    "provider.providerchangepassword.errorNewPasswordSameAsOld");
        }

        PasswordPolicyResult policyResult = validatePasswordPolicy(newPassword);
        if (!policyResult.isValid()) {
            auditForcedPasswordResetFailure(userName, policyResult.auditReason());
        }
        return policyResult.errorMessage();
    }

    /**
     * Builds the localized forced-reset rejection message while recording the shared audit event.
     *
     * <p>Keep password material out of {@code auditReason}; callers pass stable symbolic reasons so
     * operators can distinguish probing patterns without exposing submitted credentials.</p>
     *
     * @param userName provider login name used only for PHI-safe audit context
     * @param auditReason stable symbolic rejection reason
     * @param messageKey resource-bundle key for the user-facing rejection message
     * @return localized display-safe rejection message
     */
    private String rejectForcedPasswordReset(String userName, String auditReason, String messageKey) {
        auditForcedPasswordResetFailure(userName, auditReason);
        return message(messageKey);
    }

    private boolean oldPasswordMatches(String oldPassword, String oldEncodedPassword) {
        return oldPassword != null
                && oldEncodedPassword != null
                && this.securityManager.matchesPassword(oldPassword, oldEncodedPassword);
    }

    private static boolean isForcedPasswordResetSubmitPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        String path = contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
        return "/forcepasswordresetSubmit".equals(path);
    }

    private int incrementForcedResetOldPasswordAttempts() {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return MAX_FORCE_PASSWORD_RESET_OLD_PASSWORD_ATTEMPTS;
        }
        synchronized (session) {
            Object attemptsAttr = session.getAttribute(FORCE_PASSWORD_RESET_OLD_PASSWORD_ATTEMPTS_ATTR);
            int attempts = attemptsAttr instanceof Number ? ((Number) attemptsAttr).intValue() : 0;
            attempts++;
            session.setAttribute(FORCE_PASSWORD_RESET_OLD_PASSWORD_ATTEMPTS_ATTR, attempts);
            return attempts;
        }
    }

    private void clearForcedResetOldPasswordAttempts() {
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.removeAttribute(FORCE_PASSWORD_RESET_OLD_PASSWORD_ATTEMPTS_ATTR);
            } catch (IllegalStateException | UnsupportedOperationException e) {
                logger.warn("Unable to clear forced-reset old-password retry counter", e);
            }
        }
    }

    /**
     * Records forced password-reset validation failures through both the application log and audit log.
     *
     * <p>The audit event intentionally stores only the provider login name and a symbolic reason.
     * Old/new password values and password-policy details beyond the failure class must never be
     * logged.</p>
     *
     * @param userName provider login name associated with the staged reset attempt
     * @param auditReason stable symbolic rejection reason
     */
    private void auditForcedPasswordResetFailure(String userName, String auditReason) {
        logger.info("Forced password reset rejected: user={}, reason={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                LogSafe.sanitize(userName), auditReason);
        LogAction.addLog(userName, "login", "forced_password_reset_failed", auditReason);
    }

    /**
     * Records forced password-reset completion events that need a forensic marker.
     *
     * @param userName provider login name associated with the staged reset attempt
     * @param auditReason stable symbolic completion condition
     */
    private void auditForcedPasswordResetCompletion(String userName, String auditReason) {
        logger.info("Forced password reset completed with follow-up action: user={}, reason={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                LogSafe.sanitize(userName), auditReason);
        LogAction.addLog(userName, "login", "forced_password_reset_completed", auditReason);
    }

    /**
     * Counts authentication-provider exceptions as failed attempts so exception-triggering probes do
     * not bypass the same lockout path as ordinary credential failures.
     */
    private void recordAuthenticationExceptionFailure(LoginCheckLogin cl, String ip, String userName) {
        try {
            cl.updateLoginList(ip, userName);
        } catch (RuntimeException updateFailure) {
            logger.warn("Unable to update login-failure counter after authentication exception: user={}, remote={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(userName), LogSafe.sanitize(ip), updateFailure);
        }
    }

    /**
     * Applies the server-side forced-reset password complexity policy.
     *
     * <p>The JSP performs the same checks for immediate user feedback, but browser JavaScript is
     * advisory. This method is the authoritative policy gate for direct POSTs and scripted clients.
     * It mirrors the configurable length/group settings used by the legacy password-change UI.</p>
     *
     * @param newPassword candidate password submitted from the forced reset form
     * @return validation result containing the display message and audit reason when rejected
     */
    private PasswordPolicyResult validatePasswordPolicy(String newPassword) {
        CarlosProperties properties = CarlosProperties.getInstance();
        if (Boolean.parseBoolean(properties.getProperty("IGNORE_PASSWORD_REQUIREMENTS"))) {
            return PasswordPolicyResult.valid();
        }

        int minLength = intProperty(properties, "password_min_length", DEFAULT_POLICY_MIN_LENGTH);
        if (newPassword == null || newPassword.length() < minLength) {
            return PasswordPolicyResult.invalid(message("password.policy.violation.msgPasswordLengthError") + " "
                    + minLength + " " + message("password.policy.violation.msgSymbols"),
                    "password_policy_min_length");
        }

        int minGroups = intProperty(properties, "password_min_groups", DEFAULT_POLICY_MIN_GROUPS);
        int groupsUsed = countPasswordGroups(newPassword,
                properties.getProperty("password_group_lower_chars", DEFAULT_POLICY_LOWER_CHARS),
                properties.getProperty("password_group_upper_chars", DEFAULT_POLICY_UPPER_CHARS),
                properties.getProperty("password_group_digits", DEFAULT_POLICY_DIGIT_CHARS),
                properties.getProperty("password_group_special", DEFAULT_POLICY_SPECIAL_CHARS));
        if (groupsUsed < minGroups) {
            return PasswordPolicyResult.invalid(message("password.policy.violation.msgPasswordStrengthError") + " "
                    + minGroups + " " + message("password.policy.violation.msgPasswordGroups"),
                    "password_policy_min_groups");
        }

        return PasswordPolicyResult.valid();
    }

    /**
     * Carries both halves of a password-policy decision.
     *
     * <p>The display message stays localized for the reset page while {@code auditReason} remains a
     * stable, non-sensitive token for audit logging. Valid results use an empty message and no audit
     * reason.</p>
     */
    private record PasswordPolicyResult(String errorMessage, String auditReason) {
        private static PasswordPolicyResult valid() {
            return new PasswordPolicyResult("", null);
        }

        private static PasswordPolicyResult invalid(String errorMessage, String auditReason) {
            return new PasswordPolicyResult(errorMessage, auditReason);
        }

        private boolean isValid() {
            return (errorMessage == null || errorMessage.isEmpty()) && auditReason == null;
        }
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
        if (lower) {
            groups++;
        }
        if (upper) {
            groups++;
        }
        if (digit) {
            groups++;
        }
        if (special) {
            groups++;
        }
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
            logger.warn("Invalid integer property {}={}, using default {}", key, LogSafe.sanitize(value), // NOSONAR javasecurity:S5145 - sanitized with LogSafe
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
        if (results.size() > 0) {
            security = results.get(0);
        }

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
     * Returns the request's MFA registration hint.
     *
     * @return request hint only; server-side challenge state decides registration behavior
     */
    public boolean isMfaRegistrationFlow() {
        return mfaRegistrationFlow;
    }

    /**
     * Sets the request's MFA registration hint.
     *
     * <p>Struts2 populates this from the form, but validation derives registration mode from the
     * server-side pending challenge so clients cannot choose the security branch.</p>
     *
     * @param mfaRegistrationFlow boolean request hint from the MFA form
     */
    @StrutsParameter
    public void setMfaRegistrationFlow(boolean mfaRegistrationFlow) {
        this.mfaRegistrationFlow = mfaRegistrationFlow;
    }
}
