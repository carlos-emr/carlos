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
package io.github.carlos_emr.carlos.fax.admin;

import com.opensymphony.xwork2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.form.JSONUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

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
    private static final String PASSWORD_BLANKET = "**********";
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
            FaxConfig masterFaxConfig;

            if (faxConfigIds == null) {
                for (FaxConfig sfaxConfig : savedFaxConfigList) {
                    faxConfigDao.remove(sfaxConfig.getId());
                }
            } else {
                for (int idx = 0; idx < faxConfigIds.length; ++idx) {
                    if (StringUtils.trimToNull(faxConfigIds[idx]) == null) {
                        continue;
                    }
                    id = Integer.parseInt(faxConfigIds[idx]);
                    FaxConfig.ProviderType providerType = resolveProviderType(providerTypes, idx, id);
                    validateConfigRow(providerType, faxUrl, siteUser, faxUsers, faxPasswds, faxNumbers, senderEmails, inboxQueues, idx, id);

                    faxConfig = new FaxConfig();
                    faxConfig.setId(id);

                    savedidx = savedFaxConfigList.indexOf(faxConfig);
                    if (savedidx > -1) {
                        savedFaxConfig = savedFaxConfigList.get(savedidx);
                        savedFaxConfig.setUrl(faxUrl);
                        savedFaxConfig.setSiteUser(siteUser);

                        if (sitePasswd != null && !PASSWORD_BLANKET.equals(sitePasswd)) {
                            savedFaxConfig.setPasswd(sitePasswd.trim());
                        }

                        savedFaxConfig.setFaxUser(faxUsers[idx]);

                        if (faxPasswds[idx] != null && !PASSWORD_BLANKET.equals(faxPasswds[idx])) {
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

                        if (sitePasswd != null && !PASSWORD_BLANKET.equals(sitePasswd)) {
                            faxConfig.setPasswd(sitePasswd.trim());
                        }
                        // the password carries over from the last configuration. Usually the first entry
                        else if (!savedFaxConfigList.isEmpty() && (masterFaxConfig = savedFaxConfigList.get(0)) != null) {
                            faxConfig.setPasswd(masterFaxConfig.getPasswd());
                        }

                        faxConfig.setUrl(faxUrl);
                        faxConfig.setFaxUser(faxUsers[idx]);

                        if (faxPasswds[idx] != null && !PASSWORD_BLANKET.equals(faxPasswds[idx])) {
                            faxConfig.setFaxPasswd(faxPasswds[idx].trim());
                        }

                        faxConfig.setFaxNumber(faxNumbers[idx]);
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

                if (sitePasswd != null && !PASSWORD_BLANKET.equals(sitePasswd)) {
                    faxConfig.setPasswd(sitePasswd.trim());
                }
                // the password carries over from the last configuration. Usually the first entry
                else if (!savedFaxConfigList.isEmpty() && (masterFaxConfig = savedFaxConfigList.get(0)) != null) {
                    faxConfig.setPasswd(masterFaxConfig.getPasswd());
                }
                faxConfig.setProviderType(FaxConfig.ProviderType.MIDDLEWARE);
                faxConfigDao.saveEntity(faxConfig);
            }

            jsonObject = objectMapper.createObjectNode();
            jsonObject.put("success", true);
            jsonObject.put("message", "Configuration saved!");
        } catch (Exception ex) {
            jsonObject = objectMapper.createObjectNode();
            jsonObject.put("success", false);
            jsonObject.put("message", ex.getMessage() == null ? DEFAULT_ERROR_MESSAGE : ex.getMessage());
            MiscUtils.getLogger().error("COULD NOT SAVE FAX CONFIGURATION", ex);
        }

        MiscUtils.getLogger().info("JSON: " + jsonObject);
        JSONUtil.jsonResponse(response, jsonObject);
        return null;
    }


    /**
     * Resolves provider type selection from request arrays with safe middleware fallback.
     *
     * @param providerTypes provider type request values
     * @param idx row index currently being processed
     * @param faxConfigId persisted identifier for logging context
     * @return resolved provider type, defaulting to {@link FaxConfig.ProviderType#MIDDLEWARE} when absent/invalid
     */
    private FaxConfig.ProviderType resolveProviderType(String[] providerTypes, int idx, Integer faxConfigId) {
        FaxConfig.ProviderType providerType = FaxConfig.ProviderType.MIDDLEWARE;

        if (providerTypes != null && idx < providerTypes.length && providerTypes[idx] != null) {
            try {
                providerType = FaxConfig.ProviderType.valueOf(providerTypes[idx]);
            } catch (IllegalArgumentException ex) {
                MiscUtils.getLogger().warn("Invalid provider type '{}' for fax config id {}. Falling back to MIDDLEWARE.",
                        providerTypes[idx], faxConfigId);
            }
        }

        return providerType;
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
        if (StringUtils.isBlank(faxUrl)) {
            throw new IllegalArgumentException("Fax server URL is required.");
        }
        if (StringUtils.isBlank(siteUser)) {
            throw new IllegalArgumentException("Fax server username is required.");
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

            if (!faxUrl.toLowerCase().contains("srfax")) {
                MiscUtils.getLogger().warn("SRFax provider selected for row {} but URL does not appear to be an SRFax endpoint: {}", idx + 1, faxUrl);
            }
        }
    }

    /**
     * Restarts fax scheduler thread/task via manager layer.
     */
    public void restartFaxScheduler() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("No valid session found");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.fax.restart", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.fax.restart)");
        }
        faxManager.restartFaxScheduler(loggedInInfo);
    }

    /**
     * Returns scheduler health/status payload for admin UI polling.
     */
    public void getFaxSchedularStatus() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("No valid session found");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.fax.restart", "r", null)) {
            throw new SecurityException("missing required sec object (_admin.fax.restart)");
        }
        JSONUtil.jsonResponse(response, faxManager.getFaxSchedularStatus(loggedInInfo));
    }

}
