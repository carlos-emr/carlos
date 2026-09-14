/**
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao;
import io.github.carlos_emr.carlos.commn.model.EncounterTemplate;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Manages encounter/provider templates in the admin module.
 *
 * <p>Handles create, edit, and delete operations on {@link EncounterTemplate} records,
 * then loads the full list of templates for display. Enforces POST for mutating operations
 * and requires {@code _newCasemgmt.templates w} privilege.</p>
 *
 * @since 2026-04-05
 */
public class ProviderTemplate2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private EncounterTemplateDao encounterTemplateDao = SpringUtils.getBean(EncounterTemplateDao.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.templates", "w", null)) {
            throw new SecurityException("missing required sec object (_newCasemgmt.templates)");
        }

        String dboperation = request.getParameter("dboperation");
        String trimmedOp = dboperation != null ? dboperation.trim() : "";

        if ("POST".equalsIgnoreCase(request.getMethod())
                && ("Save".equalsIgnoreCase(trimmedOp) || "Delete".equalsIgnoreCase(trimmedOp))) {

            String templateName = request.getParameter("name");
            if (templateName == null || templateName.trim().isEmpty()) {
                request.setAttribute("resultMsg", "Template name is required.");
            } else if ("Save".equalsIgnoreCase(trimmedOp)) {
                try {
                    String templateValue = request.getParameter("value");
                    EncounterTemplate existing = encounterTemplateDao.find(templateName);
                    if (existing != null) {
                        existing.setEncounterTemplateValue(templateValue);
                        encounterTemplateDao.merge(existing);
                    } else {
                        EncounterTemplate newTemplate = new EncounterTemplate();
                        newTemplate.setEncounterTemplateName(templateName);
                        newTemplate.setEncounterTemplateValue(templateValue);
                        newTemplate.setCreatorProviderNo(loggedInInfo.getLoggedInProviderNo());
                        newTemplate.setCreatedDate(new java.util.Date());
                        encounterTemplateDao.persist(newTemplate);
                    }
                    LogAction.addLog(loggedInInfo.getLoggedInProviderNo(),
                            LogConst.UPDATE, "encounterTemplate", templateName, request.getRemoteAddr());
                    request.setAttribute("resultMsg", "Template saved.");
                } catch (RuntimeException e) {
                    MiscUtils.getLogger().error("Failed to save encounter template", e);
                    request.setAttribute("resultMsg", "Failed to save template.");
                }
            } else {
                try {
                    EncounterTemplate toDelete = encounterTemplateDao.find(templateName);
                    if (toDelete != null) {
                        encounterTemplateDao.remove(toDelete);
                        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(),
                                LogConst.DELETE, "encounterTemplate", templateName, request.getRemoteAddr());
                        request.setAttribute("resultMsg", "Template deleted.");
                    } else {
                        request.setAttribute("resultMsg", "Template not found.");
                    }
                } catch (RuntimeException e) {
                    MiscUtils.getLogger().error("Failed to delete encounter template", e);
                    request.setAttribute("resultMsg", "Failed to delete template.");
                }
            }
        }

        // Load the specific template for Edit mode (GET or POST with Edit)
        if ("Edit".equalsIgnoreCase(trimmedOp)) {
            String templateName = request.getParameter("name");
            EncounterTemplate editTemplate = encounterTemplateDao.find(templateName);
            request.setAttribute("editTemplate", editTemplate);
        }

        // Always load all templates
        List<EncounterTemplate> allTemplates = encounterTemplateDao.findAll();
        request.setAttribute("allTemplates", allTemplates);
        request.setAttribute("curUser_no", loggedInInfo.getLoggedInProviderNo());
        request.setAttribute("dboperation", dboperation);

        return SUCCESS;
    }
}
