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
import io.github.carlos_emr.carlos.fax.ringcentral.RingCentralApiConnector;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.form.JSONUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.carlos_emr.carlos.fax.core.FaxImporter;

/**
 * Admin entrypoint for fax server / per-account configuration and scheduler controls. All
 * responses are JSON; this action does not forward to a JSP view.
 *
 * <p><strong>Dispatch.</strong> {@link #execute()} reads the {@code method} request parameter
 * and routes to one of:
 * <ul>
 *   <li>{@code configure} (mutator, POST-only) — persists fax server + per-account rows from the
 *       admin form. Auto-starts the scheduler if any active row exists.</li>
 *   <li>{@code restartFaxScheduler} (mutator, POST-only) — restarts the scheduler thread.</li>
 *   <li>{@code getFaxSchedularStatus} (read, any verb) — returns the scheduler health payload.</li>
 *   <li>{@code getPendingIncomingFaxes} (read, any verb) — returns files in the incoming
 *       directory that haven't been imported yet.</li>
 * </ul>
 *
 * <p><strong>Privilege split.</strong> {@code configure} requires {@code _admin.fax} write;
 * {@code restartFaxScheduler} and {@code getFaxSchedularStatus} require
 * {@code _admin.fax.restart}; {@code getPendingIncomingFaxes} requires {@code _admin.fax} read.
 *
 * <p><strong>Mutator GET/HEAD rejection.</strong> Mutator branches return HTTP 405 before any
 * DAO/manager/scheduler call when the request method is not POST — see
 * {@code MutatorActionGetRejectionContractTest} for the contract.
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
     *
     * <p>Mutator methods ({@code configure}, {@code restartFaxScheduler}) require POST.
     * Read-only methods ({@code getFaxSchedularStatus}, {@code getPendingIncomingFaxes}) accept
     * any verb. The POST gate runs BEFORE any mutation dependency (DAO, manager, scheduler) is
     * touched — see {@code MutatorActionGetRejectionContractTest} for the contract.</p>
     */
    public String execute() {
        String method = request.getParameter("method");

        if ("getFaxSchedularStatus".equals(method)) {
            getFaxSchedularStatus();
            return null;
        } else if ("restartFaxScheduler".equals(method)) {
            if (rejectIfNotPost()) {
                return null;
            }
            restartFaxScheduler();
            return null;
        } else if ("getPendingIncomingFaxes".equals(method)) {
            getPendingIncomingFaxes();
            return null;
        } else if ("configure".equals(method)) {
            if (rejectIfNotPost()) {
                return null;
            }
            return configure();
        }

        // Default case: action called without a method parameter — log for diagnostics
        // and return null to drop the response (no view forward, no mutation).
        MiscUtils.getLogger().warn("ConfigureFax2Action called without a method parameter.");
        return null;
    }

    /**
     * Returns {@code true} when the request method is not POST and a 405 has been sent. Mutator
     * branches MUST return immediately when this returns true so no DAO/manager/scheduler call
     * executes. The check is duplicated per-branch (rather than a single top-level guard) because
     * read-only methods on this dispatcher legitimately accept GET.
     */
    private boolean rejectIfNotPost() {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        try {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        } catch (IOException e) {
            // Response may already be committed (broken pipe, client abort). The gate already
            // prevented the mutation by short-circuiting; log for observability.
            MiscUtils.getLogger().warn("Failed to send 405 on non-POST fax admin request", e);
        }
        return true;
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

        // Hoist secret arrays out of the try so the finally can zero them regardless of which
        // catch fires. applyRingCentralFields zeroes individual entries after they are persisted,
        // but a validation throw earlier in the loop would leave un-processed entries holding
        // plaintext secret references. This belt-and-suspenders pass keeps the secret-input
        // lifetime bounded to this method invocation.
        String[] rcClientSecrets = null;
        String[] rcJwtTokens = null;
        String[] faxPasswds = null;
        try {
            FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
            List<FaxConfig> savedFaxConfigList = faxConfigDao.findAll(null, null);
            Map<Integer, FaxConfig> savedFaxConfigsById = indexById(savedFaxConfigList);
            List<FaxConfig> faxConfigList = new ArrayList<FaxConfig>();

            String faxUrl = request.getParameter("faxUrl");
            String siteUser = request.getParameter("siteUser");
            String sitePasswd = request.getParameter("sitePasswd");

            String[] faxConfigIds = request.getParameterValues("id");
            String[] faxUsers = request.getParameterValues("faxUser");
            faxPasswds = request.getParameterValues("faxPassword");
            String[] inboxQueues = request.getParameterValues("inboxQueue");
            String[] activeState = request.getParameterValues("activeState");
            String[] faxNumbers = request.getParameterValues("faxNumber");
            String[] senderEmails = request.getParameterValues("senderEmail");
            String[] accountNames = request.getParameterValues("accountName");
            String[] downloadState = request.getParameterValues("downloadState");
            String[] providerTypes = request.getParameterValues("providerType");
            String[] rcClientIds = request.getParameterValues("ringCentralClientId");
            rcClientSecrets = request.getParameterValues("ringCentralClientSecret");
            rcJwtTokens = request.getParameterValues("ringCentralJwtToken");
            String[] rcAccountIds = request.getParameterValues("ringCentralAccountId");
            String[] rcExtensionIds = request.getParameterValues("ringCentralExtensionId");

            Integer id;
            FaxConfig faxConfig;
            FaxConfig savedFaxConfig;

            if (faxConfigIds == null) {
                for (FaxConfig sfaxConfig : savedFaxConfigList) {
                    faxConfigDao.remove(sfaxConfig.getId());
                }
            } else {
                int expectedLength = faxConfigIds.length;
                if (faxNumbers == null || faxNumbers.length < expectedLength
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
                    savedFaxConfig = findSavedConfig(savedFaxConfigsById, id);
                    validateConfigRow(providerType, faxUrl, siteUser, sitePasswd, faxUsers, faxPasswds, faxNumbers,
                            senderEmails, inboxQueues, rcClientIds, rcClientSecrets, rcJwtTokens, idx, savedFaxConfig);

                    // Direct providers always use fixed API URLs; middleware uses the user-provided URL.
                    String resolvedFaxUrl = providerType == FaxConfig.ProviderType.SRFAX
                            ? SRFaxProviderClient.DEFAULT_SRFAX_API_URL
                            : providerType == FaxConfig.ProviderType.RINGCENTRAL
                            ? RingCentralApiConnector.DEFAULT_RINGCENTRAL_API_URL
                            : faxUrl;

                    if (savedFaxConfig != null) {
                        savedFaxConfig.setUrl(resolvedFaxUrl);
                        savedFaxConfig.setSiteUser(siteUser);

                        if (sitePasswd != null && !isPasswordUnchanged(sitePasswd)) {
                            savedFaxConfig.setPasswd(sitePasswd.trim());
                        }

                        savedFaxConfig.setFaxUser(valueAt(faxUsers, idx));

                        if (faxPasswds != null && idx < faxPasswds.length && faxPasswds[idx] != null && !isPasswordUnchanged(faxPasswds[idx])) {
                            savedFaxConfig.setFaxPasswd(faxPasswds[idx].trim());
                        }
                        if (faxPasswds != null && idx < faxPasswds.length) {
                            faxPasswds[idx] = null;
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
                        applyRingCentralFields(savedFaxConfig, providerType, rcClientIds, rcClientSecrets, rcJwtTokens,
                                rcAccountIds, rcExtensionIds, idx);
                        faxConfigList.add(savedFaxConfig);
                    } else {
                        faxConfig = new FaxConfig();
                        faxConfig.setId(null);
                        faxConfig.setSiteUser(siteUser);

                        if (sitePasswd != null && !isPasswordUnchanged(sitePasswd)) {
                            faxConfig.setPasswd(sitePasswd.trim());
                        }

                        faxConfig.setUrl(resolvedFaxUrl);
                        faxConfig.setFaxUser(valueAt(faxUsers, idx));

                        if (faxPasswds != null && idx < faxPasswds.length && faxPasswds[idx] != null && !isPasswordUnchanged(faxPasswds[idx])) {
                            faxConfig.setFaxPasswd(faxPasswds[idx].trim());
                        }
                        if (faxPasswds != null && idx < faxPasswds.length) {
                            faxPasswds[idx] = null;
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
                        applyRingCentralFields(faxConfig, providerType, rcClientIds, rcClientSecrets, rcJwtTokens,
                                rcAccountIds, rcExtensionIds, idx);
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
             * Ensure that the fax server information remains intact whenever all the gateway
             * accounts are wiped out — but only when the admin actually supplied middleware
             * fields. The MIDDLEWARE @PrePersist invariant requires url/siteUser/passwd; saving
             * a placeholder with blanks would now (correctly) throw at the JPA boundary. A
             * RINGCENTRAL-only or SRFAX-only deployment legitimately has no middleware fields
             * to preserve, so we skip the synthetic save in that case.
             */
            int auditList = faxConfigDao.getCountAll();
            if (auditList == 0
                    && StringUtils.isNotBlank(faxUrl)
                    && StringUtils.isNotBlank(siteUser)
                    && sitePasswd != null
                    && !isPasswordUnchanged(sitePasswd)
                    && StringUtils.isNotBlank(sitePasswd)) {
                faxConfig = new FaxConfig();
                faxConfig.setUrl(faxUrl);
                faxConfig.setSiteUser(siteUser);
                faxConfig.setPasswd(sitePasswd.trim());
                faxConfig.setProviderType(FaxConfig.ProviderType.MIDDLEWARE);
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
                String correlationId = newCorrelationId();
                MiscUtils.getLogger().error("Failed to auto-start fax scheduler after config save (correlationId={})",
                        correlationId, e);
                // The config DID save — keep success=true so the UI shows the persistence as
                // green. Surface the partial-failure via a separate "warning" field so the UI can
                // render an amber annotation distinct from a red "save failed" toast.
                jsonObject.put("warning", "Configuration saved, but fax scheduler failed to start. "
                        + "Use the Restart button to start it manually. (Reference: " + correlationId + ")");
            }
        } catch (IllegalArgumentException ex) {
            // Validation errors - the message is operator-facing (already sanitized by callers)
            // and gets the correlation id appended so support tickets can be matched to log lines.
            String correlationId = newCorrelationId();
            jsonObject = objectMapper.createObjectNode();
            jsonObject.put("success", false);
            String body = ex.getMessage() == null ? DEFAULT_ERROR_MESSAGE : ex.getMessage();
            jsonObject.put("message", body + " (Reference: " + correlationId + ")");
            MiscUtils.getLogger().error("Fax configuration validation failed (correlationId={}): {}",
                    correlationId, ex.getMessage(), ex);
        } catch (jakarta.persistence.PersistenceException ex) {
            // Database errors - generic message to avoid leaking schema details, but the
            // correlation id lets support reach the stack trace.
            String correlationId = newCorrelationId();
            jsonObject = objectMapper.createObjectNode();
            jsonObject.put("success", false);
            jsonObject.put("message", DEFAULT_ERROR_MESSAGE + " (Reference: " + correlationId + ")");
            MiscUtils.getLogger().error("Database error saving fax configuration (correlationId={})",
                    correlationId, ex);
        } catch (Exception ex) {
            // System errors - generic message + correlation id so admins can quote it.
            String correlationId = newCorrelationId();
            jsonObject = objectMapper.createObjectNode();
            jsonObject.put("success", false);
            jsonObject.put("message", DEFAULT_ERROR_MESSAGE + " (Reference: " + correlationId + ")");
            MiscUtils.getLogger().error("COULD NOT SAVE FAX CONFIGURATION (correlationId={})",
                    correlationId, ex);
        } finally {
            zeroSecretArray(rcClientSecrets);
            zeroSecretArray(rcJwtTokens);
            zeroSecretArray(faxPasswds);
        }

        MiscUtils.getLogger().debug("Fax configuration response: success={}", jsonObject.get("success"));
        JSONUtil.jsonResponse(response, jsonObject);
        return null;
    }

    /**
     * Best-effort clearing of plaintext secret references held in a request-parameter array. The
     * Strings themselves remain on the JVM heap until GC reclaims them, but dropping the array
     * references shortens the window during which a heap dump from this servlet thread would
     * surface plaintext credentials.
     */
    private static void zeroSecretArray(String[] secrets) {
        if (secrets == null) {
            return;
        }
        for (int i = 0; i < secrets.length; i++) {
            secrets[i] = null;
        }
    }

    /**
     * Generates a short opaque correlation id (8 hex chars) for pairing operator-facing error
     * messages with the corresponding server log entry. Truncating UUIDs to 8 chars keeps the
     * id quote-able in a UI message and a support ticket while still being unique enough at
     * the per-failure scale of an admin save.
     */
    private static String newCorrelationId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Returns {@code true} when the submitted password value is the UI mask sentinel,
     * indicating that the admin left the password field unchanged and the stored
     * credential should be preserved as-is.
     *
     * @param password the submitted password string from the request
     * @return true if the value is the placeholder mask, false if it is a real credential update
     */
    static boolean isPasswordUnchanged(String password) {
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
     * Resolves provider type selection from request arrays.
     *
     * <p>If the form submission omits the {@code providerType} parameter entirely (so
     * {@code providerTypes} is null), every row defaults to MIDDLEWARE for backward
     * compatibility with form templates that predate the provider-routing field.</p>
     *
     * <p>If {@code providerTypes} is non-null but the value at {@code idx} is null/missing,
     * that's a per-row form bug — silently defaulting to MIDDLEWARE would clear any RingCentral
     * or SRFax credentials on the row via {@link #applyRingCentralFields}. Throw so the
     * operator sees the misrouted submission.</p>
     *
     * @throws IllegalArgumentException if the provider type value is present but not a valid
     *         enum constant, or if the per-row value is missing while other rows supplied one
     */
    private FaxConfig.ProviderType resolveProviderType(String[] providerTypes, int idx, Integer faxConfigId) {
        if (providerTypes == null) {
            // Form omitted the providerType parameter entirely — fall back to MIDDLEWARE for
            // every row. Preserves backward compatibility with form templates that predate the
            // provider-routing field.
            MiscUtils.getLogger().info("Provider type field absent from form. "
                    + "Treating fax config id {} as MIDDLEWARE.", faxConfigId);
            return FaxConfig.ProviderType.MIDDLEWARE;
        }
        if (idx >= providerTypes.length || providerTypes[idx] == null) {
            // The form submitted some providerType values but missed this row. Treat as a form
            // error rather than silently switching the row to MIDDLEWARE — that path would wipe
            // any stored RingCentral/SRFax credentials.
            MiscUtils.getLogger().error("Provider type missing for row idx={} (fax config id {}) "
                    + "while other rows submitted values - treating as form error",
                    idx, faxConfigId);
            throw new IllegalArgumentException("Provider type is required for account row " + (idx + 1)
                    + ". Reload the page and try again.");
        }

        try {
            return FaxConfig.ProviderType.valueOf(providerTypes[idx]);
        } catch (IllegalArgumentException ex) {
            String sanitizedInput = providerTypes[idx].replaceAll("[^a-zA-Z0-9_]", "");
            String errorMsg = String.format("Invalid provider type '%s' for fax config id %d. Valid values are: MIDDLEWARE, SRFAX, RINGCENTRAL",
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
     * @param rcClientIds per-row RingCentral OAuth client identifiers
     * @param rcClientSecrets per-row RingCentral OAuth client secrets
     * @param rcJwtTokens per-row RingCentral JWT tokens
     * @param idx row index currently being processed
     * @param savedFaxConfig existing persisted configuration for this row, or null for new rows
     * @throws IllegalArgumentException when required values are missing or malformed
     */
    static void validateConfigRow(FaxConfig.ProviderType providerType, String faxUrl, String siteUser, String sitePasswd,
                                    String[] faxUsers, String[] faxPasswds, String[] faxNumbers, String[] senderEmails,
                                    String[] inboxQueues, String[] rcClientIds, String[] rcClientSecrets,
                                    String[] rcJwtTokens, int idx, FaxConfig savedFaxConfig) {
        validateCommonRowFields(providerType, faxUsers, faxNumbers, senderEmails, inboxQueues, idx);
        switch (providerType) {
            case MIDDLEWARE -> validateMiddlewareRow(faxUrl, siteUser, sitePasswd, idx, savedFaxConfig);
            case SRFAX -> validateSrfaxRow(faxPasswds, idx, savedFaxConfig);
            case RINGCENTRAL -> validateRingCentralRow(rcClientIds, rcClientSecrets, rcJwtTokens, idx, savedFaxConfig);
            default -> throw new IllegalArgumentException("Unsupported provider type: " + providerType);
        }
    }

    private static void validateCommonRowFields(FaxConfig.ProviderType providerType, String[] faxUsers,
            String[] faxNumbers, String[] senderEmails, String[] inboxQueues, int idx) {
        if (providerType != FaxConfig.ProviderType.RINGCENTRAL
                && (faxUsers == null || idx >= faxUsers.length || StringUtils.isBlank(faxUsers[idx]))) {
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
    }

    private static void validateMiddlewareRow(String faxUrl, String siteUser, String sitePasswd, int idx,
            FaxConfig savedFaxConfig) {
        if (StringUtils.isBlank(faxUrl)) {
            throw new IllegalArgumentException("Middleware relay URL is required for Middleware mode.");
        }
        if (StringUtils.isBlank(siteUser)) {
            throw new IllegalArgumentException("Middleware server username is required for Middleware mode.");
        }
        // Site password contract: required on create. On update, the form may submit either the
        // mask sentinel (preserve stored value) or a real new value. Treating a blank update as
        // "clear the password" would let admins silently land an invalid row that the entity-level
        // @PrePersist check would reject with only a generic "save failed" UI error.
        boolean isNewConfig = savedFaxConfig == null;
        if (isNewConfig && StringUtils.isBlank(sitePasswd)) {
            throw new IllegalArgumentException("Middleware site password is required for new Middleware accounts.");
        }
        if (!isNewConfig && sitePasswd != null && !isPasswordUnchanged(sitePasswd) && StringUtils.isBlank(sitePasswd)) {
            throw new IllegalArgumentException("Middleware site password cannot be cleared. Submit the masked value to keep the existing password, or enter a new one.");
        }
    }

    private static void validateSrfaxRow(String[] faxPasswds, int idx, FaxConfig savedFaxConfig) {
        boolean isNewConfigRow = savedFaxConfig == null;
        boolean missingPassword = faxPasswds == null || idx >= faxPasswds.length || StringUtils.isBlank(faxPasswds[idx]);
        if (isNewConfigRow && missingPassword) {
            throw new IllegalArgumentException("SRFax password is required for new SRFax account row " + (idx + 1) + ".");
        }
        // Same blank-on-update guard as Middleware: don't let admins inadvertently clear the
        // stored password — the entity-level invariant would reject the save with a generic
        // error. Submitting the mask sentinel signals "preserve stored value".
        if (!isNewConfigRow && faxPasswds != null && idx < faxPasswds.length
                && faxPasswds[idx] != null && !isPasswordUnchanged(faxPasswds[idx])
                && StringUtils.isBlank(faxPasswds[idx])) {
            throw new IllegalArgumentException("SRFax password cannot be cleared for row " + (idx + 1)
                    + ". Submit the masked value to keep the existing password, or enter a new one.");
        }
    }

    private static void validateRingCentralRow(String[] rcClientIds, String[] rcClientSecrets,
            String[] rcJwtTokens, int idx, FaxConfig savedFaxConfig) {
        boolean isNewConfigRow = savedFaxConfig == null;
        if (rcClientIds == null || idx >= rcClientIds.length || StringUtils.isBlank(rcClientIds[idx])) {
            throw new IllegalArgumentException("RingCentral client ID is required for account row " + (idx + 1) + ".");
        }
        boolean clientIdChanged = isRingCentralClientIdChanged(rcClientIds, idx, savedFaxConfig);
        validateRingCentralSecretField(rcClientSecrets, idx, isNewConfigRow, clientIdChanged,
                "RingCentral client secret");
        validateRingCentralSecretField(rcJwtTokens, idx, isNewConfigRow, clientIdChanged,
                "RingCentral JWT token");
    }

    /**
     * Enforces the same submit/sentinel/blank semantics for RingCentral secret fields:
     * required on create or when the client ID rotates, otherwise the operator must submit the
     * mask sentinel to keep the stored value or a non-blank new value to replace it. A blank
     * submission on an existing row would silently overwrite the encrypted secret with "" and
     * surface only as a generic save failure when @PrePersist later rejects the row.
     */
    private static void validateRingCentralSecretField(String[] values, int idx, boolean isNewConfigRow,
            boolean clientIdChanged, String fieldLabel) {
        boolean missing = values == null || idx >= values.length || StringUtils.isBlank(values[idx]);
        boolean unchanged = !missing && isPasswordUnchanged(values[idx]);
        // New rows must supply real credentials, never the mask sentinel — there's nothing
        // stored to "preserve". Without this check, a new row would persist the literal
        // "**********" string and only fail at @PrePersist with an opaque error.
        if (isNewConfigRow && (missing || unchanged)) {
            throw new IllegalArgumentException(fieldLabel
                    + " is required when creating a RingCentral account for row " + (idx + 1) + ".");
        }
        // Client-ID rotation invalidates any stored secret/JWT pair, so the operator must enter
        // fresh values; the mask sentinel is meaningless because the stored value can no longer
        // be trusted.
        if (clientIdChanged && (missing || unchanged)) {
            throw new IllegalArgumentException(fieldLabel
                    + " is required when changing the client ID for row " + (idx + 1) + ".");
        }
        // Update path: a blank submission that isn't the mask sentinel would clear the stored
        // value and break auth on the next call. Force the operator to choose explicitly.
        if (!isNewConfigRow && !clientIdChanged && values != null && idx < values.length
                && values[idx] != null && !isPasswordUnchanged(values[idx])
                && StringUtils.isBlank(values[idx])) {
            throw new IllegalArgumentException(fieldLabel + " cannot be cleared for row " + (idx + 1)
                    + ". Submit the masked value to keep the existing credential, or enter a new one.");
        }
    }

    private Map<Integer, FaxConfig> indexById(List<FaxConfig> savedFaxConfigList) {
        Map<Integer, FaxConfig> configsById = new HashMap<>();
        for (FaxConfig savedFaxConfig : savedFaxConfigList) {
            if (savedFaxConfig.getId() != null) {
                configsById.put(savedFaxConfig.getId(), savedFaxConfig);
            }
        }
        return configsById;
    }

    private FaxConfig findSavedConfig(Map<Integer, FaxConfig> savedFaxConfigsById, Integer id) {
        if (id == null) {
            return null;
        }
        return savedFaxConfigsById.get(id);
    }

    static boolean isRingCentralClientIdChanged(String[] rcClientIds, int idx, FaxConfig savedFaxConfig) {
        return savedFaxConfig != null
                && !StringUtils.equals(StringUtils.trimToEmpty(valueAt(rcClientIds, idx)),
                        StringUtils.trimToEmpty(savedFaxConfig.getRingCentralClientId()));
    }

    /**
     * Applies RingCentral-specific account fields and clears secret request arrays after use.
     *
     * <p>The submitted secret/JWT array entries are set to null after processing as a best-effort
     * reduction of plaintext credential lifetime in memory. Servlet request parameters are Strings,
     * so they cannot be zeroed like char arrays and remain subject to garbage collection.</p>
     */
    static void applyRingCentralFields(FaxConfig faxConfig, FaxConfig.ProviderType providerType, String[] rcClientIds, String[] rcClientSecrets,
            String[] rcJwtTokens, String[] rcAccountIds, String[] rcExtensionIds, int idx) {
        if (providerType != FaxConfig.ProviderType.RINGCENTRAL) {
            boolean hadCredentials = StringUtils.isNotBlank(faxConfig.getRingCentralClientId());
            faxConfig.setRingCentralClientId("");
            faxConfig.setRingCentralClientSecret("");
            faxConfig.setRingCentralJwtToken("");
            faxConfig.setRingCentralAccountId("");
            faxConfig.setRingCentralExtensionId("");
            if (hadCredentials) {
                // Credential clears can be unintentional (admin toggling provider for a quick test);
                // log so the audit trail shows when stored OAuth credentials were wiped. faxConfig
                // id is an internal surrogate, not PHI.
                MiscUtils.getLogger().info(
                        "Cleared RingCentral credentials for FaxConfig id={} due to provider switch to {}",
                        faxConfig.getId(), providerType);
            }
            clearRingCentralSecretInputs(rcClientSecrets, rcJwtTokens, idx);
            return;
        }
        faxConfig.setRingCentralClientId(valueAt(rcClientIds, idx));
        faxConfig.setRingCentralAccountId(valueAt(rcAccountIds, idx));
        faxConfig.setRingCentralExtensionId(valueAt(rcExtensionIds, idx));
        String clientSecret = valueAt(rcClientSecrets, idx);
        if (clientSecret != null && !isPasswordUnchanged(clientSecret)) {
            faxConfig.setRingCentralClientSecret(clientSecret.trim());
        }
        String jwtToken = valueAt(rcJwtTokens, idx);
        if (jwtToken != null && !isPasswordUnchanged(jwtToken)) {
            faxConfig.setRingCentralJwtToken(jwtToken.trim());
        }
        clearRingCentralSecretInputs(rcClientSecrets, rcJwtTokens, idx);
    }

    private static void clearRingCentralSecretInputs(String[] rcClientSecrets, String[] rcJwtTokens, int idx) {
        if (rcClientSecrets != null && idx < rcClientSecrets.length) {
            rcClientSecrets[idx] = null;
        }
        if (rcJwtTokens != null && idx < rcJwtTokens.length) {
            rcJwtTokens[idx] = null;
        }
    }

    private static String valueAt(String[] values, int idx) {
        return values != null && idx < values.length ? values[idx] : "";
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
