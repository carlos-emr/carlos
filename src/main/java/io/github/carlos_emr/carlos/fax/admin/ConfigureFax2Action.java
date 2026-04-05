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
import io.github.carlos_emr.carlos.fax.provider.SRFaxProviderClient;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.form.JSONUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.carlos_emr.carlos.fax.core.FaxImporter;

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
    private static final String MASKED_CREDENTIAL = "**********";
    private static final String DEFAULT_ERROR_MESSAGE = "There was a problem saving your configuration. Check the logs for details.";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Dispatches request methods for configure/scheduler endpoints.
     */
    public String execute() {
        String method = request.getParameter("method");

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
                    validateConfigRow(providerType, faxUrl, siteUser, faxUsers, faxPasswds, faxNumbers, senderEmails, inboxQueues, idx, id);

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

                        if (sitePasswd != null && !MASKED_CREDENTIAL.equals(sitePasswd)) {
                            savedFaxConfig.setPasswd(sitePasswd.trim());
                        }

                        savedFaxConfig.setFaxUser(faxUsers[idx]);

                        if (faxPasswds != null && idx < faxPasswds.length && faxPasswds[idx] != null && !MASKED_CREDENTIAL.equals(faxPasswds[idx])) {
                            savedFaxConfig.setFaxPasswd(faxPasswds[idx].trim());
                        }

                        String faxNumber = faxNumbers[idx];
                        if (faxNumber != null) {
                            faxNumber = faxNumber.trim().replaceAll("\\D", "");
                        }
                        savedFaxConfig.setFaxNumber(faxNumber);
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

                        if (sitePasswd != null && !MASKED_CREDENTIAL.equals(sitePasswd)) {
                            faxConfig.setPasswd(sitePasswd.trim());
                        }

                        faxConfig.setUrl(resolvedFaxUrl);
                        faxConfig.setFaxUser(faxUsers[idx]);

                        if (faxPasswds != null && idx < faxPasswds.length && faxPasswds[idx] != null && !MASKED_CREDENTIAL.equals(faxPasswds[idx])) {
                            faxConfig.setFaxPasswd(faxPasswds[idx].trim());
                        }

                        String newFaxNumber = faxNumbers[idx];
                        if (newFaxNumber != null) {
                            newFaxNumber = newFaxNumber.trim().replaceAll("\\D", "");
                        }
                        faxConfig.setFaxNumber(newFaxNumber);
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

                if (sitePasswd != null && !MASKED_CREDENTIAL.equals(sitePasswd)) {
                    faxConfig.setPasswd(sitePasswd.trim());
                }
                faxConfig.setProviderType(FaxConfig.ProviderType.MIDDLEWARE);
                faxConfigDao.saveEntity(faxConfig);
            }

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
     * @return resolved provider type, defaulting to {@link FaxConfig.ProviderType#MIDDLEWARE} when absent
     * @throws IllegalArgumentException if the provider type value is present but not a valid enum constant
     */
    private FaxConfig.ProviderType resolveProviderType(String[] providerTypes, int idx, Integer faxConfigId) {
        // Default to MIDDLEWARE only if provider type is not specified (null or missing)
        if (providerTypes == null || idx >= providerTypes.length || providerTypes[idx] == null) {
            MiscUtils.getLogger().info("Provider type not specified for fax config id {}. Using default MIDDLEWARE.", faxConfigId);
            return FaxConfig.ProviderType.MIDDLEWARE;
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
     * @param faxUsers per-row fax usernames
     * @param faxPasswds per-row fax passwords
     * @param faxNumbers per-row sender fax numbers
     * @param senderEmails per-row sender emails
     * @param inboxQueues per-row inbox queue identifiers
     * @param idx row index currently being processed
     * @param faxConfigId persisted identifier used to distinguish new vs existing rows
     * @throws IllegalArgumentException when required values are missing or malformed
     */
    private void validateConfigRow(FaxConfig.ProviderType providerType, String faxUrl, String siteUser,
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
            String passwd = request.getParameter("sitePasswd");
            boolean isNewConfig = faxConfigId == null || faxConfigId <= 0;
            if (isNewConfig && StringUtils.isBlank(passwd)) {
                throw new IllegalArgumentException("Middleware site password is required for new Middleware accounts.");
            }
        }
        if (faxUsers == null || idx >= faxUsers.length || StringUtils.isBlank(faxUsers[idx])) {
            throw new IllegalArgumentException("Fax user is required for account row " + (idx + 1) + ".");
        }
        if (faxNumbers == null || idx >= faxNumbers.length || StringUtils.isBlank(faxNumbers[idx])) {
            throw new IllegalArgumentException("Fax number is required for account row " + (idx + 1) + ".");
        }
        if (senderEmails == null || idx >= senderEmails.length || StringUtils.isBlank(senderEmails[idx])) {
            throw new IllegalArgumentException("Sender email is required for account row " + (idx + 1) + ".");
        }
        if (inboxQueues == null || idx >= inboxQueues.length || StringUtils.isBlank(inboxQueues[idx])) {
            throw new IllegalArgumentException("Inbox queue is required for account row " + (idx + 1) + ".");
        }

        try {
            Integer.parseInt(inboxQueues[idx]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Inbox queue must be a numeric value for account row " + (idx + 1) + ".");
        }

        // Basic format check to give immediate, actionable feedback in admin UX.
        if (!senderEmails[idx].contains("@")) {
            throw new IllegalArgumentException("Sender email must be valid for account row " + (idx + 1) + ".");
        }

        if (providerType == FaxConfig.ProviderType.SRFAX) {
            boolean missingPassword = faxPasswds == null || idx >= faxPasswds.length || StringUtils.isBlank(faxPasswds[idx]);
            boolean isNewConfigRow = faxConfigId == null || faxConfigId <= 0;
            if (isNewConfigRow && missingPassword) {
                throw new IllegalArgumentException("SRFax password is required for new SRFax account row " + (idx + 1) + ".");
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
