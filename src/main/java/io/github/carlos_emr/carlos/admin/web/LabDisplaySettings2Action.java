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
 *
 */
package io.github.carlos_emr.carlos.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.lab.service.LabPdfPreviewSettings;
import io.github.carlos_emr.carlos.lab.service.LabPdfPreviewSettingsService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Lab Display Settings admin page ({@code admin/LabDisplaySettings}): whether PDFs embedded in
 * HL7 lab results are previewed inline, and the largest PDF previewed (#3977).
 *
 * <p>GET/HEAD render the page with {@code _admin} read; a save ({@code dboperation=Save}) must be
 * a POST and needs {@code _admin} write. A save intent on GET/HEAD, or any other method, is
 * refused with 405 before any privilege check or preference access, matching
 * {@link EchartDisplaySettings2Action}. The size is entered in whole MiB between 1 and 100; any
 * other value re-renders the page with an error and saves nothing.</p>
 *
 * @since 2026-09-30
 */
public class LabDisplaySettings2Action extends ActionSupport {

    /** Form field for the size limit, in MiB. */
    static final String MAX_SIZE_MB_PARAMETER = "lab_pdf_max_size_mb";

    // transient: ActionSupport implements Serializable; Spring-managed beans are not serializable.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient LabPdfPreviewSettingsService previewSettingsService;

    public LabDisplaySettings2Action(SecurityInfoManager securityInfoManager,
            LabPdfPreviewSettingsService previewSettingsService) {
        this.securityInfoManager = securityInfoManager;
        this.previewSettingsService = previewSettingsService;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        String method = request.getMethod();
        boolean saveIntent = "Save".equals(request.getParameter("dboperation"));
        if ((!"GET".equals(method) && !"HEAD".equals(method) && !"POST".equals(method))
                || (saveIntent && !"POST".equals(method))) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", saveIntent ? "POST" : "GET, HEAD, POST");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String requiredPrivilege = "POST".equals(method) ? "w" : "r";
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", requiredPrivilege, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        boolean saved = false;
        boolean invalidSize = false;
        LabPdfPreviewSettings settings;
        if ("POST".equals(method) && saveIntent) {
            boolean enabled = "true".equals(request.getParameter(
                    SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_inline_preview.name()));
            Long maxMegabytes = megabytes(request.getParameter(MAX_SIZE_MB_PARAMETER));
            if (maxMegabytes == null) {
                invalidSize = true;
                settings = previewSettingsService.load();
            } else {
                settings = new LabPdfPreviewSettings(enabled, maxMegabytes * 1024 * 1024);
                previewSettingsService.save(settings);
                saved = true;
            }
        } else {
            settings = previewSettingsService.load();
        }
        request.setAttribute("labPdfInlinePreview", settings.inlinePreviewEnabled());
        request.setAttribute("labPdfMaxSizeMb", settings.maxMegabytes());
        request.setAttribute("labPdfMaxSizeMbLimit", LabPdfPreviewSettings.MAX_ALLOWED_BYTES / (1024 * 1024));
        request.setAttribute("saved", saved);
        request.setAttribute("invalidSize", invalidSize);
        return SUCCESS;
    }

    /** Whole MiB from 1 to the allowed maximum, or {@code null}. */
    private static Long megabytes(String value) {
        if (value == null || !value.trim().matches("\\d{1,4}")) {
            return null;
        }
        long megabytes = Long.parseLong(value.trim());
        long limit = LabPdfPreviewSettings.MAX_ALLOWED_BYTES / (1024 * 1024);
        return megabytes >= 1 && megabytes <= limit ? megabytes : null;
    }
}
