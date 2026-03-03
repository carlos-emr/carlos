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


package io.github.carlos_emr.carlos.eform.actions;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.WebUtils;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.data.EFormBase;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.HashMap;

public class HtmlEdit2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        try {
            String fid = this.getFid();
            String formName = this.getFormName();
            String formSubject = this.getFormSubject();
            String formFileName = this.getFormFileName();
            String formHtml = this.getFormHtml();
            boolean showLatestFormOnly = WebUtils.isChecked(request, "showLatestFormOnly");
            boolean patientIndependent = WebUtils.isChecked(request, "patientIndependent");
            String roleType = this.getRoleType();

            HashMap<String, String> errors = new HashMap<>();
            EFormBase updatedform = new EFormBase(fid, formName, formSubject, formFileName, formHtml, showLatestFormOnly, patientIndependent, roleType); //property container (bean)
            //validation...
            if ((formName == null) || (formName.length() == 0)) {
                errors.put("formNameMissing", "eform.errors.form_name.missing.regular");
            }
            if ((fid.length() > 0) && (EFormUtil.formExistsInDBn(formName, fid) > 0)) {
                errors.put("formNameExists", "eform.errors.form_name.exists.regular");
            }
            if ((fid.length() == 0) && (errors.size() == 0)) {
                fid = EFormUtil.saveEForm(formName, formSubject, formFileName, formHtml, showLatestFormOnly, patientIndependent, roleType);
                request.setAttribute("success", "true");
            } else if (errors.size() == 0) {
                EFormUtil.updateEForm(updatedform);
                request.setAttribute("success", "true");
            }

            HashMap<String, Object> curht = createHashMap(fid, formName, formSubject, formFileName, formHtml, showLatestFormOnly, patientIndependent, roleType);
            request.setAttribute("submitted", curht);

            request.setAttribute("errors", errors);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return SUCCESS;
    }

    private HashMap<String, Object> createHashMap(String fid, String formName, String formSubject, String formFileName, String formHtml, boolean showLatestFormOnly, boolean patientIndependent, String roleType) {
        HashMap<String, Object> curht = new HashMap<String, Object>();
        curht.put("fid", fid);
        curht.put("formName", formName);
        curht.put("formSubject", formSubject);
        curht.put("formFileName", formFileName);
        curht.put("formHtml", formHtml);
        curht.put("showLatestFormOnly", showLatestFormOnly);
        curht.put("patientIndependent", patientIndependent);
        curht.put("roleType", roleType);

        if (fid.length() == 0) {
            curht.put("formDate", "--");
            curht.put("formTime", "--");
        } else {
            curht.put("formDate", EFormUtil.getEFormParameter(fid, "formDate"));
            curht.put("formTime", EFormUtil.getEFormParameter(fid, "formTime"));
        }
        return curht;
    }

    private File uploadFile;
    private String fid = "";
    private String formName = "";
    private String formSubject = "";
    private String formFileName = "";
    private String formHtml = "";
    private boolean showLatestFormOnly = false;
    private boolean patientIndependent = false;
    private String roleType = "";

    public File getUploadFile() {
        return uploadFile;
    }

    @StrutsParameter
    public void setUploadFile(File uploadFile) {
        this.uploadFile = uploadFile;
    }

    public String getFid() {
        return fid;
    }

    @StrutsParameter
    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getFormName() {
        return formName;
    }

    @StrutsParameter
    public void setFormName(String formName) {
        this.formName = formName;
    }

    public String getFormSubject() {
        return formSubject;
    }

    @StrutsParameter
    public void setFormSubject(String formSubject) {
        this.formSubject = formSubject;
    }

    public String getFormFileName() {
        return formFileName;
    }

    @StrutsParameter
    public void setFormFileName(String formFileName) {
        this.formFileName = formFileName;
    }

    public String getFormHtml() {
        return formHtml;
    }

    @StrutsParameter
    public void setFormHtml(String formHtml) {
        this.formHtml = formHtml;
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
}
