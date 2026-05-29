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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.FlowsheetDao;
import io.github.carlos_emr.carlos.commn.model.Flowsheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;

/**
 * Admin action for uploading a new flowsheet XML definition.
 *
 * <p>Requires any of {@code _admin w}, {@code _admin.misc w}, or {@code _admin.flowsheet w}
 * privilege and POST method. Uploaded XML is supplied by the Struts upload interceptor,
 * validated as a temporary upload, persisted, then redirected to the flowsheet list
 * (PRG pattern).</p>
 *
 * @since 2026-04-05
 */
public class ManageFlowsheetsUpload2Action extends ActionSupport implements UploadedFilesAware {

    private static final String MANAGE_FLOWSHEETS_ACTION = "ManageFlowsheets";
    private static final String FLASH_ERROR_ATTRIBUTE = "flashError";
    private static final String FLOWSHEET_UPLOAD_FAILED_MESSAGE =
            "Flowsheet upload failed. Please contact support.";
    private static final String INVALID_FLOWSHEET_MESSAGE =
            "Flowsheet upload failed: invalid flowsheet definition.";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private File uploadedFile;
    private String uploadValidationError;

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "w", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.flowsheet", "w", null)) {
            throw new SecurityException("missing required sec object (_admin, _admin.misc, or _admin.flowsheet)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        if (uploadValidationError != null) {
            request.getSession().setAttribute(FLASH_ERROR_ATTRIBUTE, uploadValidationError);
            response.sendRedirect(MANAGE_FLOWSHEETS_ACTION);
            return NONE;
        }

        if (uploadedFile != null) {
            try {
                String contents = Files.readString(uploadedFile.toPath(), StandardCharsets.UTF_8);
                MeasurementFlowSheet fs = MeasurementTemplateFlowSheetConfig.getInstance().validateFlowsheet(contents);
                if (fs != null) {
                    Flowsheet f = new Flowsheet();
                    f.setContent(contents);
                    f.setCreatedDate(new java.util.Date());
                    f.setEnabled(true);
                    f.setExternal(false);
                    f.setName(fs.getName());

                    FlowsheetDao flowsheetDao = SpringUtils.getBean(FlowsheetDao.class);
                    flowsheetDao.persist(f);
                    MeasurementTemplateFlowSheetConfig.getInstance().reloadFlowsheets();
                } else {
                    MiscUtils.getLogger().error("Rejected invalid flowsheet upload definition");
                    request.getSession().setAttribute(FLASH_ERROR_ATTRIBUTE, INVALID_FLOWSHEET_MESSAGE);
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Failed to upload flowsheet definition", e);
                request.getSession().setAttribute(FLASH_ERROR_ATTRIBUTE, FLOWSHEET_UPLOAD_FAILED_MESSAGE);
            }
        }

        response.sendRedirect(MANAGE_FLOWSHEETS_ACTION);
        return NONE;
    }

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.uploadedFile = PathValidationUtils.validateUploadContent(uploaded.getContent());
            try {
                PathValidationUtils.validateStrictFileName(uploaded.getOriginalName());
            } catch (FileValidationException e) {
                this.uploadValidationError = PathValidationUtils.INVALID_FILENAME_MESSAGE;
            }
        }
    }
}
