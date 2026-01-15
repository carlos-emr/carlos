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
 */
package ca.openosp.openo.fax.admin;

import com.opensymphony.xwork2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.FaxConfigDao;
import ca.openosp.openo.commn.model.FaxConfig;
import ca.openosp.openo.managers.FaxManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.form.JSONUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConfigureFax2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    private static final String PASSWORD_BLANKET = "**********";

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
                        faxConfigList.add(savedFaxConfig);
                    } else {
                        faxConfig.setId(null);
                        faxConfig.setSiteUser(siteUser);

                        if (sitePasswd != null && !PASSWORD_BLANKET.equals(sitePasswd)) {
                            faxConfig.setPasswd(sitePasswd.trim());
                        }
                        // the password carries over from the last configuration. Usually the first entry
                        else if ((masterFaxConfig = savedFaxConfigList.get(0)) != null) {
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
                else if ((masterFaxConfig = savedFaxConfigList.get(0)) != null) {
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

    public void restartFaxScheduler() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.fax.restart", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.fax.restart)");
        }
        faxManager.restartFaxScheduler(LoggedInInfo.getLoggedInInfoFromSession(request));
    }

    public void getFaxSchedularStatus() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.fax.restart", "r", null)) {
            throw new SecurityException("missing required sec object (_admin.fax.restart)");
        }
        JSONUtil.jsonResponse(response, faxManager.getFaxSchedularStatus(loggedInInfo));
    }

}
