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
 * Struts2 action for managing fax gateway account configuration.
 * <p>
 * Provides three method-based operations dispatched via the {@code method} request parameter:
 * <ul>
 *   <li><b>configure</b>: Creates, updates, or deletes fax accounts. Reads form data from
 *       {@code configureFax.jsp} including integration type, credentials, fax number,
 *       inbox queue, and active/download state for each account.</li>
 *   <li><b>getFaxSchedularStatus</b>: Returns the current fax scheduler status as JSON
 *       for the status indicator on the admin page.</li>
 *   <li><b>restartFaxScheduler</b>: Restarts the fax scheduler TimerTask. Requires
 *       {@code _admin.fax.restart} write privilege.</li>
 * </ul>
 * <p>
 * All operations require the {@code _admin} read privilege. Password fields use a blanket
 * placeholder ("**********") to avoid round-tripping real credentials through the browser;
 * existing passwords are preserved when the blanket value is submitted unchanged.
 *
 * @since 2026-02-09 (modified for integration type support)
 */
public class ConfigureFax2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    /** Placeholder shown in password fields to indicate a saved password exists without revealing it. */
    private static final String PASSWORD_BLANKET = "**********";

    /** Shared Jackson ObjectMapper for building JSON responses. Thread-safe. */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Main entry point. Dispatches to the appropriate handler based on the {@code method}
     * request parameter: "configure", "getFaxSchedularStatus", or "restartFaxScheduler".
     *
     * @return String Struts result name, or null when writing JSON directly to response
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
     * Processes the fax configuration form submission from {@code configureFax.jsp}.
     * <p>
     * Reads all account parameters (credentials, fax numbers, integration types, queue
     * assignments, active/download state) from the request, reconciles them against the
     * existing saved configurations, and persists the changes. Accounts present in the
     * saved list but absent from the form submission are deleted.
     * <p>
     * Passwords are only updated if the submitted value differs from {@link #PASSWORD_BLANKET},
     * which prevents accidental password clearing when the form is re-submitted without changes.
     *
     * @return String always null (JSON response written directly to HttpServletResponse)
     */
    public String configure() {
        ObjectNode jsonObject;

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "r", null)) {
            throw new SecurityException("missing required sec object (_admin)");
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
            String[] integrationTypes = request.getParameterValues("integrationType");

            Integer id;
            int savedidx;
            FaxConfig faxConfig;
            FaxConfig savedFaxConfig;
            FaxConfig masterFaxConfig;

            // If no account IDs were submitted, all accounts have been removed
            if (faxConfigIds == null) {
                for (FaxConfig sfaxConfig : savedFaxConfigList) {
                    faxConfigDao.remove(sfaxConfig.getId());
                }
            } else {
                // Iterate each submitted account and reconcile with saved state
                for (int idx = 0; idx < faxConfigIds.length; ++idx) {
                    if (StringUtils.trimToNull(faxConfigIds[idx]) == null) {
                        continue;
                    }
                    id = Integer.parseInt(faxConfigIds[idx]);
                    faxConfig = new FaxConfig();
                    faxConfig.setId(id);

                    // Check if this account already exists in the database
                    savedidx = savedFaxConfigList.indexOf(faxConfig);
                    if (savedidx > -1) {
                        // UPDATE existing account: apply form values to the saved entity
                        savedFaxConfig = savedFaxConfigList.get(savedidx);
                        savedFaxConfig.setUrl(faxUrl);
                        savedFaxConfig.setSiteUser(siteUser);

                        // Only overwrite site password if user entered a real value (not the blanket placeholder)
                        if (sitePasswd != null && !PASSWORD_BLANKET.equals(sitePasswd)) {
                            savedFaxConfig.setPasswd(sitePasswd.trim());
                        }

                        savedFaxConfig.setFaxUser(faxUsers[idx]);

                        // Only overwrite fax account password if user entered a real value
                        if (faxPasswds[idx] != null && !PASSWORD_BLANKET.equals(faxPasswds[idx])) {
                            savedFaxConfig.setFaxPasswd(faxPasswds[idx].trim());
                        }

                        // Strip non-digit characters from fax number for consistency
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
                        // Set integration type (e.g., "" for legacy, "SRFAX" for direct API)
                        if (integrationTypes != null && idx < integrationTypes.length) {
                            savedFaxConfig.setIntegrationType(integrationTypes[idx]);
                        }
                        faxConfigList.add(savedFaxConfig);
                    } else {
                        // CREATE new account: build a fresh FaxConfig entity from form values
                        faxConfig.setId(null);
                        faxConfig.setSiteUser(siteUser);

                        if (sitePasswd != null && !PASSWORD_BLANKET.equals(sitePasswd)) {
                            faxConfig.setPasswd(sitePasswd.trim());
                        }
                        // the password carries over from the last configuration. Usually the first entry
                        else if (!savedFaxConfigList.isEmpty() &&
                                 (masterFaxConfig = savedFaxConfigList.get(0)) != null) {
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
                        if (integrationTypes != null && idx < integrationTypes.length) {
                            faxConfig.setIntegrationType(integrationTypes[idx]);
                        }
                        faxConfigList.add(faxConfig);
                    }
                }


                // Persist all submitted accounts (creates or updates as appropriate)
                for (FaxConfig faxConfig1 : faxConfigList) {
                    faxConfigDao.saveEntity(faxConfig1);
                }

                // Remove any saved accounts that were not in the submitted form (user deleted them)
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
                else if (!savedFaxConfigList.isEmpty() &&
                         (masterFaxConfig = savedFaxConfigList.get(0)) != null) {
                    faxConfig.setPasswd(masterFaxConfig.getPasswd());
                }
                faxConfigDao.saveEntity(faxConfig);
            }

            jsonObject = objectMapper.createObjectNode();
            jsonObject.put("success", true);
        } catch (Exception ex) {
                jsonObject = objectMapper.createObjectNode();
                jsonObject.put("success", false);
            MiscUtils.getLogger().error("COULD NOT SAVE FAX CONFIGURATION", ex);
        }

        MiscUtils.getLogger().info("JSON: " + jsonObject);
        JSONUtil.jsonResponse(response, jsonObject);
        return null;
    }

    /**
     * Restarts the fax scheduler TimerTask.
     * Requires {@code _admin.fax.restart} write privilege.
     *
     * @throws SecurityException if the logged-in user lacks the required privilege
     */
    public void restartFaxScheduler() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.fax.restart", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.fax.restart)");
        }
        faxManager.restartFaxScheduler(LoggedInInfo.getLoggedInInfoFromSession(request));
    }

    /**
     * Returns the current fax scheduler connection status as a JSON response.
     * Requires {@code _admin.fax.restart} read privilege.
     *
     * @throws SecurityException if the logged-in user lacks the required privilege
     */
    public void getFaxSchedularStatus() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.fax.restart", "r", null)) {
            throw new SecurityException("missing required sec object (_admin.fax.restart)");
        }
        JSONUtil.jsonResponse(response, faxManager.getFaxSchedularStatus(loggedInInfo));
    }

}
