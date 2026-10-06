/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleImportRequest;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleImportResult;
import io.github.carlos_emr.carlos.billings.ca.on.service.FeeScheduleImportService;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
/**
 * Admin upload gate for an OHIP Schedule of Benefits fee-schedule file.
 * Validates the upload through {@link PathValidationUtils} and delegates
 * preview rendering to {@link FeeScheduleImportService} ({@link
 * FeeScheduleImportRequest} in, {@link FeeScheduleImportResult} out).
 * The actual apply step is the sibling {@code ScheduleOfBenefitsUpdate2Action}.
 * Requires {@code _admin.billing w}.
 */
public class ScheduleOfBenefitsUpload2Action extends ActionSupport implements UploadedFilesAware {
    private final SecurityInfoManager securityInfoManager;
    private final FeeScheduleImportService feeScheduleImportService;

    public ScheduleOfBenefitsUpload2Action(SecurityInfoManager securityInfoManager,
                                           FeeScheduleImportService feeScheduleImportService) {
        this.securityInfoManager = securityInfoManager;
        this.feeScheduleImportService = feeScheduleImportService;
    }

    Logger _logger = MiscUtils.getLogger();

    boolean checkBox(String str) {
        boolean check = false;
        if (str != null && str.equals("on")) {
            check = true;
        }
        return check;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() throws java.io.IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        // POST-only for multipart file upload + fee preview. Legacy GET
        // bookmarks for this URL are read-only form requests; route those to
        // the sibling view action instead of returning 405.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return "view";
        }

        List warnings = new ArrayList();
        String outcome = "";

        boolean forceUpdate = false;
        try {
            boolean updateAssistantFees = checkBox(request.getParameter("updateAssistantFees"));
            boolean updateAnaesthetistFees = checkBox(request.getParameter("updateAnaesthetistFees"));
            BigDecimal updateAssistantFeesValue = updateAssistantFees ? getBDValue(request.getParameter("updateAssistantFeesValue")) : null;
            BigDecimal updateAnaesthetistFeesValue = updateAnaesthetistFees ? getBDValue(request.getParameter("updateAnaesthetistFeesValue")) : null;

            importFile = PathValidationUtils.validateUpload(importFile);
            String codeChanges = request.getParameter("showChangedCodes");
            String newCodes = request.getParameter("showNewCodes");

            boolean showNewCodes = checkBox(newCodes);
            boolean showChangedCodes = checkBox(codeChanges);
            forceUpdate = checkBox(request.getParameter("forceUpdate"));

            FeeScheduleImportRequest importRequest = new FeeScheduleImportRequest(showNewCodes, showChangedCodes,
                    forceUpdate, updateAssistantFeesValue, updateAnaesthetistFeesValue);
            try (InputStream is = new java.io.FileInputStream(importFile)) { // codeql[java/path-injection] -- importFile is reassigned from PathValidationUtils.validateUpload(importFile) immediately before this open
                FeeScheduleImportResult result = feeScheduleImportService.preview(is, importRequest);
                warnings = result.warningMaps();
                request.setAttribute("feeScheduleChanges", result.changes());
                request.setAttribute("validationErrors", result.validationErrors());
                outcome = result.validationErrors().isEmpty() ? "success" : "exception";
                forceUpdate = result.forceUpdate();
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to preview Schedule of Benefits upload {}",
                    LogSafe.sanitize(importFileFileName), e);
            outcome = "exception";
            forceUpdate = false;
        }
        MiscUtils.getLogger().debug("warnings " + warnings.size());
        request.setAttribute("warnings", warnings);
        request.setAttribute("outcome", outcome);
        request.setAttribute("forceUpdate", forceUpdate);
        if (forceUpdate) {
            return "forceUpdate";
        }
        return "exception".equals(outcome) ? "exception" : SUCCESS;
    }

    private BigDecimal getBDValue(String value) {
        if (value == null || value.trim().equals("")) {
            return BigDecimal.ZERO;
        }
        return BillingMoney.amount(value);
    }


    private File importFile;          // 上传的文件
    private String importFileFileName; // 上传文件的名称
    private boolean updateAssistantFees;
    private boolean updateAnaesthetistFees;
    private BigDecimal updateAssistantFeesValue;
    private BigDecimal updateAnaesthetistFeesValue;

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.importFile = PathValidationUtils.validateUpload(new File(uploaded.getAbsolutePath()));
            this.importFileFileName = PathValidationUtils.validateStrictFileName(uploaded.getOriginalName());
        }
    }

    public File getImportFile() {
        return importFile;
    }

    public String getImportFileFileName() {
        return importFileFileName;
    }

    public boolean isUpdateAssistantFees() {
        return updateAssistantFees;
    }

    @StrutsParameter
    public void setUpdateAssistantFees(boolean updateAssistantFees) {
        this.updateAssistantFees = updateAssistantFees;
    }

    public boolean isUpdateAnaesthetistFees() {
        return updateAnaesthetistFees;
    }

    @StrutsParameter
    public void setUpdateAnaesthetistFees(boolean updateAnaesthetistFees) {
        this.updateAnaesthetistFees = updateAnaesthetistFees;
    }

    public BigDecimal getUpdateAssistantFeesValue() {
        return updateAssistantFeesValue;
    }

    @StrutsParameter
    public void setUpdateAssistantFeesValue(BigDecimal updateAssistantFeesValue) {
        this.updateAssistantFeesValue = updateAssistantFeesValue;
    }

    public BigDecimal getUpdateAnaesthetistFeesValue() {
        return updateAnaesthetistFeesValue;
    }

    @StrutsParameter
    public void setUpdateAnaesthetistFeesValue(BigDecimal updateAnaesthetistFeesValue) {
        this.updateAnaesthetistFeesValue = updateAnaesthetistFeesValue;
    }
}
