//CHECKSTYLE:OFF
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


package ca.openosp.openo.eform.upload;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.eform.EFormUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.file.Files;

public class HtmlUpload2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }
        try {
            String formHtmlStr = new String(Files.readAllBytes(formHtml.toPath()));
            formHtmlStr = formHtmlStr.replaceAll("\\\\n", "\\\\\\\\n");
            String fileName = formHtml.getName();
            EFormUtil.saveEForm(formName, subject, fileName, formHtmlStr, showLatestFormOnly, patientIndependent, roleType);
            request.setAttribute("status", "success");
            return SUCCESS;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            return "fail";
        }

    }

    private File formHtml; // 上传的文件
    private String formHtmlContentType; // 文件的 MIME 类型
    private String formHtmlFileName; // 文件的原始名称
    private String formName;
    private String subject;
    private boolean showLatestFormOnly;
    private boolean patientIndependent;
    private String roleType;

    public File getFormHtml() {
        return formHtml;
    }

    public void setFormHtml(File formHtml) {
        this.formHtml = formHtml;
    }

    public String getFormHtmlContentType() {
        return formHtmlContentType;
    }

    public void setFormHtmlContentType(String formHtmlContentType) {
        this.formHtmlContentType = formHtmlContentType;
    }

    public String getFormHtmlFileName() {
        return formHtmlFileName;
    }

    public void setFormHtmlFileName(String formHtmlFileName) {
        this.formHtmlFileName = formHtmlFileName;
    }

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public boolean isShowLatestFormOnly() {
        return showLatestFormOnly;
    }

    public void setShowLatestFormOnly(boolean showLatestFormOnly) {
        this.showLatestFormOnly = showLatestFormOnly;
    }

    public boolean isPatientIndependent() {
        return patientIndependent;
    }

    public void setPatientIndependent(boolean patientIndependent) {
        this.patientIndependent = patientIndependent;
    }

    public String getRoleType() {
        return roleType;
    }

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
