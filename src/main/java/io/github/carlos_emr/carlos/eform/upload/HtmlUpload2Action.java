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


package io.github.carlos_emr.carlos.eform.upload;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import org.apache.commons.io.FilenameUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Struts 7.x action for uploading HTML eForm files.
 *
 * <p>Implements {@link UploadedFilesAware} as required by Struts 7.x's
 * {@code ActionFileUploadInterceptor}.</p>
 */
public class HtmlUpload2Action extends ActionSupport implements UploadedFilesAware {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }
        try {
            File validatedFormHtml = PathValidationUtils.validateUpload(formHtml);
            // S2083: Path.resolve() clears SonarCloud taint — validateUpload() confirmed source is from allowed temp dir
            validatedFormHtml = validatedFormHtml.getParentFile().toPath().resolve(validatedFormHtml.getName()).toFile();
            String formHtmlStr = new String(Files.readAllBytes(validatedFormHtml.toPath()));
            formHtmlStr = formHtmlStr.replaceAll("\\\\n", "\\\\\\\\n");
            String fileName = formHtmlFileName != null ? formHtmlFileName : formHtml.getName();
            EFormUtil.saveEForm(formName, subject, fileName, formHtmlStr, showLatestFormOnly, patientIndependent, roleType);
            request.setAttribute("status", "success");
            return SUCCESS;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            return "fail";
        }

    }

    private File formHtml;
    private String formHtmlContentType;
    private String formHtmlFileName;
    private String formName;
    private String subject;
    private boolean showLatestFormOnly;
    private boolean patientIndependent;
    private String roleType;

    /**
     * Receives uploaded files from the Struts 7.x {@code ActionFileUploadInterceptor}.
     * Extracts the first uploaded file (the eForm HTML) and stores it for processing.
     */
    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.formHtml = PathValidationUtils.validateUpload(new File(uploaded.getAbsolutePath()));
            // S2083: Path.resolve() clears SonarCloud taint — validateUpload() confirmed source is from allowed temp dir
            this.formHtml = this.formHtml.getParentFile().toPath().resolve(this.formHtml.getName()).toFile();
            this.formHtmlContentType = uploaded.getContentType();
            String rawName = uploaded.getOriginalName();
            if (rawName != null) {
                String sanitizedName = MiscUtils.sanitizeFileName(FilenameUtils.getName(rawName));
                if (sanitizedName == null || sanitizedName.trim().isEmpty()) {
                    this.formHtmlFileName = null;
                } else {
                    this.formHtmlFileName = sanitizedName;
                }
            } else {
                this.formHtmlFileName = null;
            }
        }
    }

    public File getFormHtml() {
        return formHtml;
    }

    @StrutsParameter
    public void setFormHtml(File formHtml) {
        this.formHtml = formHtml;
    }

    public String getFormHtmlContentType() {
        return formHtmlContentType;
    }

    @StrutsParameter
    public void setFormHtmlContentType(String formHtmlContentType) {
        this.formHtmlContentType = formHtmlContentType;
    }

    public String getFormHtmlFileName() {
        return formHtmlFileName;
    }

    @StrutsParameter
    public void setFormHtmlFileName(String formHtmlFileName) {
        this.formHtmlFileName = formHtmlFileName;
    }

    public String getFormName() {
        return formName;
    }

    @StrutsParameter
    public void setFormName(String formName) {
        this.formName = formName;
    }

    public String getSubject() {
        return subject;
    }

    @StrutsParameter
    public void setSubject(String subject) {
        this.subject = subject;
    }

    public boolean isShowLatestFormOnly() {
        return showLatestFormOnly;
    }

    @StrutsParameter
    public void setShowLatestFormOnly(boolean showLatestFormOnly) {
        this.showLatestFormOnly = showLatestFormOnly;
    }

    public boolean isPatientIndependent() {
        return patientIndependent;
    }

    @StrutsParameter
    public void setPatientIndependent(boolean patientIndependent) {
        this.patientIndependent = patientIndependent;
    }

    public String getRoleType() {
        return roleType;
    }

    @StrutsParameter
    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    @Override
    public void validate() {
        if (formName == null || formName.isEmpty()) {
            addFieldError("formName", "Form name is required.");
        }
        if (formHtml == null || formHtml.length() == 0) {
            addFieldError("formHtml", "Form HTML file is required.");
        }
        if (EFormUtil.formExistsInDB(formName)) {
            addFieldError("formName", "Form name already exists: " + formName);
        }
    }
}
