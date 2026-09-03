/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Copyright (c) 2017-2024. Juno EMR. All Rights Reserved.
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Portions contributed by Juno EMR.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.fax.admin;

import org.apache.struts2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.fax.provider.SRFaxProviderClient;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.form.JSONUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import com.fasterxml.jackson.databind.node.ArrayNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.fax.core.FaxImporter;
import java.io.IOException;

/**
 * Admin action for fax configuration and scheduler controls.
 *
 * <p>This action is intentionally backed by established Struts endpoints used by
 * the pre-existing fax admin JSP UX. Configuration writes are restricted to
 * `_admin.fax` write privilege.</p>
 */
public class ConfigureFax2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    /** Sentinel value sent by the UI to indicate a stored password should not be overwritten. */
    public static final String PASSWORD_MASK_SENTINEL = "**********";
    private static final String DEFAULT_ERROR_MESSAGE = "There was a problem saving your configuration. Check the logs for details.";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Dispatches request methods for configure/scheduler endpoints.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of the literal HTTP method name (GET/HEAD) for the method-verb gate; not a security or authorization decision on user identity.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal HTTP method name (GET/HEAD) for the method-verb gate; not a security or authorization decision on user identity")
    public String execute() {
        String method = request.getParameter("method");

        // configure() rewrites fax_config rows (credentials included) and restartFaxScheduler()
        // restarts the polling scheduler — both are mutations and must never ride a GET/HEAD.
        // testConnection() is not a persistence mutation, but it forwards submitted credentials
        // to the provider, so it is held to the same rule. These three are POST-only: any other
        // verb (GET/HEAD, and also PUT/PATCH/DELETE, which carry a body just like POST) is
        // refused with 405 + Allow: POST before dispatch.
        // getFaxSchedularStatus/getPendingIncomingFaxes are read-only and stay verb-open.
        // configureFax.jsp issues all of these calls via POST, so no UI change is required.
        boolean mutator = "configure".equals(method) || "restartFaxScheduler".equals(method)
                || "testConnection".equals(method);
        String httpMethod = request.getMethod();
        if (mutator && !"POST".equalsIgnoreCase(httpMethod)) {
            response.setHeader("Allow", "POST");
            sendErrorQuietly(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method not allowed");
            // Direct-response contract: NONE stops Struts result resolution after the
            // error response has been written.
            return NONE;
        }

        if ("getFaxSchedularStatus".equals(method)) {
            getFaxSchedularStatus();
            return null;
        } else if ("restartFaxScheduler".equals(method)) {
            restartFaxScheduler();
            return null;
        } else if ("getPendingIncomingFaxes".equals(method)) {
            getPendingIncomingFaxes();
            return null;
        } else if ("configure".equals(method)) {
            return configure();
        } else if ("testConnection".equals(method)) {
            return testConnection();
        }

        // Default case: action called without a method parameter
        // Since the JSP is accessed directly, this should probably never happen
        // But just in case, we can return back to the page and log a warning
        MiscUtils.getLogger().warn("ConfigureFax2Action called without a method parameter.");
        return null;
    }

    /**
     * Persists fax server and account configuration rows from admin form submission.
     *
     * <p>Provider selection is parsed per account and defaults to middleware when
     * invalid or unspecified, preserving backward compatibility.</p>
     */
    public String configure() {
        ObjectNode jsonObject;

        // Fax configuration is admin-fax write protected.
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("No valid session found");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.fax", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.fax)");
        }

        try {
            FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
            List<FaxConfig> savedFaxConfigList = faxConfigDao.findAll(null, null);
            List<FaxConfig> faxConfigList = new ArrayList<FaxConfig>();

            String faxUrl = request.getParameter("faxUrl");
            String siteUser = request.getParameter("siteUser");
            String sitePasswd = request.getParameter("sitePasswd");

            String[] faxConfigIds = request.getParameterValues("id");
            String[] faxUsers = request.getParameterValues("faxUser");
            String[] faxPasswds = request.getParameterValues("faxPassword");
            String[] inboxQueues = request.getParameterValues("inboxQueue");
            String[] activeState = request.getParameterValues("activeState");
            String[] faxNumbers = request.getParameterValues("faxNumber");
            String[] senderEmails = request.getParameterValues("senderEmail");
            String[] accountNames = request.getParameterValues("accountName");
            String[] downloadState = request.getParameterValues("downloadState");
            String[] providerTypes = request.getParameterValues("providerType");

            Integer id;
            int savedidx;
            FaxConfig faxConfig;
            FaxConfig savedFaxConfig;

            if (faxConfigIds == null) {
                for (FaxConfig sfaxConfig : savedFaxConfigList) {
                    faxConfigDao.remove(sfaxConfig.getId());
                }
            } else {
                // Validate all required arrays have consistent lengths
                int expectedLength = faxConfigIds.length;
                if (faxUsers == null || faxUsers.length < expectedLength
                        || faxNumbers == null || faxNumbers.length < expectedLength
                        || senderEmails == null || senderEmails.length < expectedLength
                        || accountNames == null || accountNames.length < expectedLength
                        || inboxQueues == null || inboxQueues.length < expectedLength
                        || activeState == null || activeState.length < expectedLength
                        || downloadState == null || downloadState.length < expectedLength) {
                    throw new IllegalArgumentException(
                            "Form submission is incomplete — some account fields are missing. "
                            + "Please reload the page and try again.");
                }

                for (int idx = 0; idx < faxConfigIds.length; ++idx) {
                    if (StringUtils.trimToNull(faxConfigIds[idx]) == null) {
                        continue;
                    }
                    try {
                        id = Integer.parseInt(faxConfigIds[idx]);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid configuration ID for account row " + (idx + 1) + ".");
                    }
                    FaxConfig.ProviderType providerType = resolveProviderType(providerTypes, idx, id);
                    validateConfigRow(providerType, faxUrl, siteUser, sitePasswd, faxUsers, faxPasswds, faxNumbers, senderEmails, inboxQueues, idx, id);

                    // SRFax always uses the fixed API URL; middleware uses the user-provided URL
                    String resolvedFaxUrl = providerType == FaxConfig.ProviderType.SRFAX
                            ? SRFaxProviderClient.DEFAULT_SRFAX_API_URL
                            : faxUrl;

                    faxConfig = new FaxConfig();
                    faxConfig.setId(id);

                    savedidx = savedFaxConfigList.indexOf(faxConfig);
                    if (savedidx > -1) {
                        savedFaxConfig = savedFaxConfigList.get(savedidx);
                        savedFaxConfig.setUrl(resolvedFaxUrl);
                        savedFaxConfig.setSiteUser(siteUser);

                        if (sitePasswd != null && !isPasswordUnchanged(sitePasswd)) {
                            savedFaxConfig.setPasswd(sitePasswd.trim());
                        }

                        savedFaxConfig.setFaxUser(faxUsers[idx]);

                        if (faxPasswds != null && idx < faxPasswds.length && faxPasswds[idx] != null && !isPasswordUnchanged(faxPasswds[idx])) {
                            savedFaxConfig.setFaxPasswd(faxPasswds[idx].trim());
                        }
                        // Clear per-row fax password after use (covers both updated and sentinel cases)
                        if (faxPasswds != null && idx < faxPasswds.length) {
                            faxPasswds[idx] = null;
                        }

                        savedFaxConfig.setFaxNumber(normalizeFaxNumber(faxNumbers[idx], idx + 1));
                        savedFaxConfig.setSenderEmail(senderEmails[idx]);
                        savedFaxConfig.setQueue(Integer.parseInt(inboxQueues[idx]));
                        savedFaxConfig.setAccountName(accountNames[idx]);
                        savedFaxConfig.setActive(Boolean.parseBoolean(activeState[idx]));
                        savedFaxConfig.setDownload(Boolean.parseBoolean(downloadState[idx]));
                        savedFaxConfig.setProviderType(providerType);
                        faxConfigList.add(savedFaxConfig);
                    } else {
                        faxConfig.setId(null);
                        faxConfig.setSiteUser(siteUser);

                        if (sitePasswd != null && !isPasswordUnchanged(sitePasswd)) {
                            faxConfig.setPasswd(sitePasswd.trim());
                        }

                        faxConfig.setUrl(resolvedFaxUrl);
                        faxConfig.setFaxUser(faxUsers[idx]);

                        if (faxPasswds != null && idx < faxPasswds.length && faxPasswds[idx] != null && !isPasswordUnchanged(faxPasswds[idx])) {
                            faxConfig.setFaxPasswd(faxPasswds[idx].trim());
                        }
                        // Clear per-row fax password after use (covers both updated and sentinel cases)
                        if (faxPasswds != null && idx < faxPasswds.length) {
                            faxPasswds[idx] = null;
                        }

                        faxConfig.setFaxNumber(normalizeFaxNumber(faxNumbers[idx], idx + 1));
                        faxConfig.setSenderEmail(senderEmails[idx]);
                        faxConfig.setQueue(Integer.parseInt(inboxQueues[idx]));
                        faxConfig.setAccountName(accountNames[idx]);
                        faxConfig.setActive(Boolean.parseBoolean(activeState[idx]));
                        faxConfig.setDownload(Boolean.parseBoolean(downloadState[idx]));
                        faxConfig.setProviderType(providerType);
                        faxConfigList.add(faxConfig);
                    }
                }

                for (FaxConfig faxConfig1 : faxConfigList) {
                    faxConfigDao.saveEntity(faxConfig1);
                }

                for (FaxConfig faxConfig2 : savedFaxConfigList) {
                    if (!faxConfigList.contains(faxConfig2)) {
                        faxConfigDao.remove(faxConfig2.getId());
                    }
                }
            }

            /*
             * Ensure that the fax server information remains intact
             * whenever all the gateway accounts are wiped out.
             */
            int auditList = faxConfigDao.getCountAll();
            if (auditList == 0) {
                faxConfig = new FaxConfig();
                faxConfig.setUrl(faxUrl);
                faxConfig.setSiteUser(siteUser);

                if (sitePasswd != null && !isPasswordUnchanged(sitePasswd)) {
                    faxConfig.setPasswd(sitePasswd.trim());
                }
                // SRFax is the supported provider for new configurations; middleware remains
                // only for grandfathered rows that already carry providerType=MIDDLEWARE.
                faxConfig.setProviderType(FaxConfig.ProviderType.SRFAX);
                faxConfigDao.saveEntity(faxConfig);
            }

            // Clear site password from memory after all configuration rows are processed
            sitePasswd = null;

            jsonObject = objectMapper.createObjectNode();
            jsonObject.put("success", true);
            jsonObject.put("message", "Configuration saved!");

            // Auto-start scheduler if any active config exists and scheduler isn't running
            try {
                boolean hasActive = faxConfigList.stream().anyMatch(FaxConfig::isActive);
                if (hasActive) {
                    faxManager.startFaxSchedulerIfNotRunning(loggedInInfo);
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Failed to auto-start fax scheduler after config save", e);
                jsonObject.put("message", "Configuration saved, but fax scheduler failed to start. "
                        + "Use the Restart button to start it manually.");
            }
        } catch (IllegalArgumentException ex) {
            // Validation errors - safe to expose message
            jsonObject = objectMapper.createObjectNode();
            jsonObject.put("success", false);
            jsonObject.put("message", ex.getMessage() == null ? DEFAULT_ERROR_MESSAGE : ex.getMessage());
            MiscUtils.getLogger().error("Fax configuration validation failed: {}", ex.getMessage(), ex);
        } catch (jakarta.persistence.PersistenceException ex) {
            // Database errors - do not leak details
            jsonObject = objectMapper.createObjectNode();
            jsonObject.put("success", false);
            jsonObject.put("message", DEFAULT_ERROR_MESSAGE);
            MiscUtils.getLogger().error("Database error saving fax configuration", ex);
        } catch (Exception ex) {
            // System errors - do not leak details
            jsonObject = objectMapper.createObjectNode();
            jsonObject.put("success", false);
            jsonObject.put("message", DEFAULT_ERROR_MESSAGE);
            MiscUtils.getLogger().error("COULD NOT SAVE FAX CONFIGURATION", ex);
        }

        MiscUtils.getLogger().debug("Fax configuration response: success={}", jsonObject.get("success"));
        JSONUtil.jsonResponse(response, jsonObject);
        return null;
    }

    /**
     * Verifies the submitted provider credentials with a read-only provider probe, without
     * saving anything.
     *
     * <p>Backs the "Test SRFax connection" button on Configure Fax. The form values are used
     * as submitted so an admin can check a new account number/password before saving. When the
     * password field still carries the {@link #PASSWORD_MASK_SENTINEL} (the admin did not retype
     * it), the stored credential for the submitted config id is used instead. The response is
     * {@code {success, message}} and never carries the credentials; provider failure messages
     * are status strings (for example an SRFax "Invalid Access Code / Password" result).</p>
     *
     * @return {@link #NONE}: the JSON response has been written and Struts result resolution
     *         must not run (the sibling JSON methods in this class still return {@code null}
     *         and are left as-is)
     * @throws SecurityException when the session lacks {@code _admin.fax} write rights
     */
    public String testConnection() {
        requireLoggedInWithPrivilege("_admin.fax", "w");

        try {
            String faxUser = StringUtils.trimToEmpty(request.getParameter("faxUser"));
            String faxPassword = request.getParameter("faxPassword");
            String[] providerTypes = request.getParameterValues("providerType");
            Integer configId = parseConfigId(request.getParameter("id"));

            if (faxUser.isEmpty()) {
                sendJsonError(text("admin.configureFax.test.missingAccountNumber",
                        "Enter the SRFax account number to test the connection."));
                return NONE;
            }

            FaxConfig probe = new FaxConfig();
            probe.setProviderType(resolveProviderType(providerTypes, 0, configId));
            probe.setFaxUser(faxUser);

            // The classic mistake is the login email in the account-number field: say so
            // immediately instead of forwarding it to SRFax for a bare HTTP 403.
            if (probe.getProviderType() == FaxConfig.ProviderType.SRFAX && !isSrfaxAccountNumber(faxUser)) {
                sendJsonError(text("admin.configureFax.test.accountNumberNotNumeric", ACCOUNT_NUMBER_NOT_NUMERIC_DEFAULT));
                return NONE;
            }

            if (faxPassword == null || StringUtils.isBlank(faxPassword)) {
                sendJsonError(text("admin.configureFax.test.missingPassword",
                        "Enter the SRFax password to test the connection."));
                return NONE;
            }
            if (isPasswordUnchanged(faxPassword)) {
                // The mask sentinel means "keep what is stored": test with the stored credential.
                FaxConfig stored = configId != null
                        ? SpringUtils.getBean(FaxConfigDao.class).find(configId.intValue())
                        : null;
                if (stored == null || StringUtils.isBlank(stored.getFaxPasswd())) {
                    sendJsonError(text("admin.configureFax.test.missingPassword",
                            "Enter the SRFax password to test the connection."));
                    return NONE;
                }
                probe.setFaxPasswd(stored.getFaxPasswd());
            } else {
                probe.setFaxPasswd(faxPassword.trim());
            }
            // Drop the request copy of the credential as soon as the probe carries it.
            faxPassword = null;

            FaxProviderClient client = SpringUtils.getBean(FaxProviderClientFactory.class).getClient(probe);
            client.verifyConnection(probe);
            sendJsonSuccess(text("admin.configureFax.test.success",
                    "Connection successful. SRFax accepted the account number and password."));
        } catch (FaxProviderException | IllegalArgumentException e) {
            // Provider status text / validation text only — never the submitted values.
            MiscUtils.getLogger().warn("Fax connection test failed: {}", e.getMessage());
            sendJsonError(text("admin.configureFax.test.failed", "Connection failed: {0}",
                    e.getMessage() == null ? "provider error" : e.getMessage()));
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("Fax connection test failed unexpectedly", e);
            sendJsonError(text("admin.configureFax.test.unexpected",
                    "Connection test failed unexpectedly. Check the logs for details."));
        }
        // Direct-response contract: the JSON body is written; stop Struts result resolution.
        return NONE;
    }

    /** English fallback for the digits-only rule; the localized text lives under the same key in the bundles. */
    private static final String ACCOUNT_NUMBER_NOT_NUMERIC_DEFAULT =
            "SRFax account number must contain digits only. It is the numeric account number from the SRFax portal, not your login email.";

    /**
     * SRFax {@code access_id} values are numeric account numbers. Enforced server-side on save
     * and on the connection test because the AJAX submit bypasses native input validation.
     */
    static boolean isSrfaxAccountNumber(String value) {
        return value != null && value.trim().matches("\\d+");
    }

    /**
     * Resolves a user-facing message from {@code oscarResources} for the request locale (the
     * same bundle the Configure Fax page renders with), formatting {@code {n}} placeholders with
     * {@link MessageFormat}. Falls back to the English default when the key is missing so a
     * bundle gap can never blank out a response.
     */
    private String text(String key, String defaultText, Object... args) {
        String pattern = defaultText;
        try {
            Locale locale = request.getLocale() == null ? Locale.ENGLISH : request.getLocale();
            pattern = ResourceBundle.getBundle("oscarResources", locale).getString(key);
        } catch (MissingResourceException e) {
            MiscUtils.getLogger().debug("Missing oscarResources key {}; using default text", key);
        }
        return args.length == 0 ? pattern : new MessageFormat(pattern).format(args);
    }

    /**
     * Parses the hidden config id posted by the form. Blank, malformed, and non-positive values
     * ({@code -1} is the form's "no stored row yet" marker; ids are 1-based) all resolve to
     * {@code null}, so a caller can never mistake them for a persisted row.
     */
    private static Integer parseConfigId(String rawId) {
        if (StringUtils.isBlank(rawId)) {
            return null;
        }
        try {
            int id = Integer.parseInt(rawId.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Normalizes an admin-entered fax number to the 10-digit form stored in fax_config.
     *
     * <p>Strips formatting characters and drops a leading North American country code from an
     * 11-digit entry. Rejects anything that does not normalize to exactly 10 digits — the
     * column is varchar(10), so an unvalidated longer value would be silently truncated and
     * then fail to match {@code faxes.fax_line} in the sender/status-updater joins.</p>
     *
     * @param rawFaxNumber admin-entered fax number (may carry punctuation/spaces)
     * @param rowNumber 1-based account row index for the error message
     * @return exactly 10 digits
     * @throws IllegalArgumentException when the value cannot be normalized to 10 digits
     */
    static String normalizeFaxNumber(String rawFaxNumber, int rowNumber) {
        String digits = rawFaxNumber == null ? "" : rawFaxNumber.trim().replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("1")) {
            digits = digits.substring(1);
        }
        if (digits.length() != 10) {
            throw new IllegalArgumentException(
                    "Fax number must be a 10-digit North American number for account row " + rowNumber + ".");
        }
        return digits;
    }

    /**
     * Sends an HTTP error response, quietly logging (rather than propagating) any IO failure.
     */
    private void sendErrorQuietly(int statusCode, String message) {
        try {
            response.sendError(statusCode, message);
        } catch (IOException ex) {
            MiscUtils.getLogger().error("Error sending error response", ex);
        }
    }

    /**
     * Returns {@code true} when the submitted password value is the UI mask sentinel,
     * indicating that the admin left the password field unchanged and the stored
     * credential should be preserved as-is.
     *
     * @param password the submitted password string from the request
     * @return true if the value is the placeholder mask, false if it is a real credential update
     */
    private boolean isPasswordUnchanged(String password) {
        return PASSWORD_MASK_SENTINEL.equals(password);
    }

    /**
     * Returns {@link #PASSWORD_MASK_SENTINEL} when the stored password is non-empty,
     * or an empty string when no password has been stored yet.
     *
     * <p>Use this helper in view templates to populate password input fields without
     * revealing the actual credential to the browser.</p>
     *
     * @param storedPassword the stored (encrypted) password value read from the database
     * @return mask sentinel string or empty string
     */
    public static String maskPasswordForDisplay(String storedPassword) {
        return (storedPassword != null && !storedPassword.isEmpty()) ? PASSWORD_MASK_SENTINEL : "";
    }

    /**
     * Resolves provider type selection from request arrays with safe middleware fallback.
     *
     * <p><strong>Backward Compatibility:</strong> Middleware is the legacy default provider.
     * This method falls back to MIDDLEWARE when providerTypes array is missing or the value
     * at the given index is null. Throws {@link IllegalArgumentException} for invalid provider
     * type names. This ensures existing fax configurations continue working after the provider
     * abstraction refactor without requiring manual updates.</p>
     *
     * <p>The fallback prevents configuration errors when:
     * <ul>
     *   <li>Legacy configurations are loaded (no providerType field)</li>
     *   <li>UI form submissions omit provider type selection</li>
     * </ul>
     * </p>
     *
     * @param providerTypes provider type request values (may be null for legacy configs)
     * @param idx row index currently being processed
     * @param faxConfigId persisted identifier for logging context
     * @return resolved provider type, defaulting to {@link FaxConfig.ProviderType#SRFAX} when absent
     * @throws IllegalArgumentException if the provider type value is present but not a valid enum constant
     */
    private FaxConfig.ProviderType resolveProviderType(String[] providerTypes, int idx, Integer faxConfigId) {
        // Default to SRFAX when provider type is not specified: SRFax is the supported provider;
        // existing MIDDLEWARE rows always post their explicit value from the (grandfathered) select.
        if (providerTypes == null || idx >= providerTypes.length || providerTypes[idx] == null) {
            MiscUtils.getLogger().info("Provider type not specified for fax config id {}. Using default SRFAX.", faxConfigId);
            return FaxConfig.ProviderType.SRFAX;
        }

        // Validate and parse provider type - throw exception for invalid values to notify user
        try {
            return FaxConfig.ProviderType.valueOf(providerTypes[idx]);
        } catch (IllegalArgumentException ex) {
            // Sanitize user input before including in error message to prevent XSS
            String sanitizedInput = providerTypes[idx].replaceAll("[^a-zA-Z0-9_]", "");
            String errorMsg = String.format("Invalid provider type '%s' for fax config id %d. Valid values are: MIDDLEWARE, SRFAX",
                    sanitizedInput, faxConfigId);
            MiscUtils.getLogger().error("Invalid provider type for fax config id {}: {}", faxConfigId, providerTypes[idx], ex);
            throw new IllegalArgumentException(errorMsg);
        }
    }

    /**
     * Validates a single fax account row before persistence.
     *
     * @param providerType provider selected for the account row
     * @param faxUrl shared fax endpoint URL
     * @param siteUser shared fax endpoint username
     * @param sitePasswd shared fax endpoint password (already retrieved from request by caller)
     * @param faxUsers per-row fax usernames
     * @param faxPasswds per-row fax passwords
     * @param faxNumbers per-row sender fax numbers
     * @param senderEmails per-row sender emails
     * @param inboxQueues per-row inbox queue identifiers
     * @param idx row index currently being processed
     * @param faxConfigId persisted identifier used to distinguish new vs existing rows
     * @throws IllegalArgumentException when required values are missing or malformed
     */
    private void validateConfigRow(FaxConfig.ProviderType providerType, String faxUrl, String siteUser, String sitePasswd,
                                   String[] faxUsers, String[] faxPasswds, String[] faxNumbers, String[] senderEmails,
                                   String[] inboxQueues, int idx, Integer faxConfigId) {
        // Middleware mode requires URL and credentials; SRFax mode can use default URL
        if (providerType == FaxConfig.ProviderType.MIDDLEWARE) {
            if (StringUtils.isBlank(faxUrl)) {
                throw new IllegalArgumentException("Middleware relay URL is required for Middleware mode.");
            }
            if (StringUtils.isBlank(siteUser)) {
                throw new IllegalArgumentException("Middleware server username is required for Middleware mode.");
            }
            // For new middleware configs, site password is required for Basic auth
            boolean isNewConfig = faxConfigId == null || faxConfigId <= 0;
            if (isNewConfig && StringUtils.isBlank(sitePasswd)) {
                throw new IllegalArgumentException("Middleware site password is required for new Middleware accounts.");
            }
        }
        if (faxUsers == null || idx >= faxUsers.length || StringUtils.isBlank(faxUsers[idx])) {
            throw new IllegalArgumentException("SRFax account number is required.");
        }
        if (providerType == FaxConfig.ProviderType.SRFAX && !isSrfaxAccountNumber(faxUsers[idx])) {
            throw new IllegalArgumentException(ACCOUNT_NUMBER_NOT_NUMERIC_DEFAULT);
        }
        if (faxNumbers == null || idx >= faxNumbers.length || StringUtils.isBlank(faxNumbers[idx])) {
            throw new IllegalArgumentException("Your SRFax fax number is required.");
        }
        if (senderEmails == null || idx >= senderEmails.length || StringUtils.isBlank(senderEmails[idx])) {
            throw new IllegalArgumentException("Sender email is required.");
        }
        if (inboxQueues == null || idx >= inboxQueues.length || StringUtils.isBlank(inboxQueues[idx])) {
            throw new IllegalArgumentException("Inbox queue is required.");
        }

        try {
            Integer.parseInt(inboxQueues[idx]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Inbox queue must be a numeric value.");
        }

        // Basic format check to give immediate, actionable feedback in admin UX.
        if (!senderEmails[idx].contains("@")) {
            throw new IllegalArgumentException("Sender email must be a valid email address (for example, you@clinic.example).");
        }

        if (providerType == FaxConfig.ProviderType.SRFAX) {
            boolean missingPassword = faxPasswds == null || idx >= faxPasswds.length || StringUtils.isBlank(faxPasswds[idx]);
            boolean isNewConfigRow = faxConfigId == null || faxConfigId <= 0;
            if (isNewConfigRow && missingPassword) {
                throw new IllegalArgumentException("SRFax password is required for a new SRFax account.");
            }
        }
    }

    /**
     * Restarts fax scheduler thread/task via manager layer.
     */
    public void restartFaxScheduler() {
        try {
            LoggedInInfo loggedInInfo = requireLoggedInWithPrivilege("_admin.fax.restart", "w");
            faxManager.restartFaxScheduler(loggedInInfo);
            sendJsonSuccess(null);
        } catch (SecurityException e) {
            MiscUtils.getLogger().warn("Fax scheduler restart denied: {}", e.getMessage());
            sendJsonError("Insufficient privileges to restart fax scheduler.");
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("Fax scheduler restart failed: {}", e.getMessage(), e);
            sendJsonError("Fax scheduler restart failed unexpectedly.");
        }
    }

    /**
     * Returns a JSON list of fax files in the incoming directory that have not been imported yet.
     * Provides admin visibility into pending/failed fax imports.
     */
    public void getPendingIncomingFaxes() {
        try {
            requireLoggedInWithPrivilege("_admin.fax", "r");

            FaxImporter faxImporter = SpringUtils.getBean(FaxImporter.class);
            List<Map<String, Object>> pendingFaxes = faxImporter.listPendingIncomingFaxes();

            ObjectNode jsonObject = objectMapper.createObjectNode();
            jsonObject.put("success", true);
            jsonObject.put("count", pendingFaxes.size());
            ArrayNode faxArray = objectMapper.valueToTree(pendingFaxes);
            jsonObject.set("faxes", faxArray);
            JSONUtil.jsonResponse(response, jsonObject);
        } catch (SecurityException e) {
            MiscUtils.getLogger().warn("Pending faxes check denied: {}", e.getMessage());
            sendJsonError("Insufficient privileges.");
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("Failed to list pending incoming faxes: {}", e.getMessage(), e);
            sendJsonError("Failed to list pending faxes.");
        }
    }

    /**
     * Returns scheduler health/status payload for admin UI polling.
     */
    public void getFaxSchedularStatus() {
        try {
            LoggedInInfo loggedInInfo = requireLoggedInWithPrivilege("_admin.fax.restart", "r");
            JSONUtil.jsonResponse(response, faxManager.getFaxSchedularStatus(loggedInInfo));
        } catch (SecurityException e) {
            MiscUtils.getLogger().warn("Fax scheduler status check denied: {}", e.getMessage());
            sendJsonError("Insufficient privileges to view fax scheduler status.");
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("Fax scheduler status check failed: {}", e.getMessage(), e);
            sendJsonError("Fax scheduler status check failed unexpectedly.");
        }
    }

    /**
     * Validates session and privilege, returning the logged-in info.
     *
     * @param secObject security object name to check
     * @param accessLevel access level ("r" for read, "w" for write)
     * @return LoggedInInfo the authenticated session info
     * @throws SecurityException if session is missing or privilege check fails
     */
    private LoggedInInfo requireLoggedInWithPrivilege(String secObject, String accessLevel) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("No valid session found");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, secObject, accessLevel, null)) {
            throw new SecurityException("missing required sec object (" + secObject + ")");
        }
        return loggedInInfo;
    }

    /**
     * Sends a JSON success response, optionally with a message.
     *
     * @param message optional message to include (null for no message field)
     */
    private void sendJsonSuccess(String message) {
        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("success", true);
        if (message != null) {
            jsonObject.put("message", message);
        }
        JSONUtil.jsonResponse(response, jsonObject);
    }

    /**
     * Sends a JSON error response with the given user-facing message.
     *
     * @param message error message safe to display to admin users
     */
    private void sendJsonError(String message) {
        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("success", false);
        jsonObject.put("message", message);
        JSONUtil.jsonResponse(response, jsonObject);
    }

}
