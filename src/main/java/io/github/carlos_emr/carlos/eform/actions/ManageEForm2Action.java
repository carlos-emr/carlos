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

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.eform.EFormExportZip;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManageEForm2Action extends ActionSupport implements UploadedFilesAware {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws Exception {
        if ("importEForm".equals(request.getParameter("method"))) {
            return importEForm();
        }
        return exportEForm();
    }

    public String exportEForm() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "r", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        String fid = request.getParameter("fid");
        MiscUtils.getLogger().debug("fid: " + fid);
        response.setContentType("application/zip");  //octet-stream
        EForm eForm = new EForm(fid, "1");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + eForm.getFormName().replaceAll("\\s", fid) + ".zip\"");
        EFormExportZip eFormExportZip = new EFormExportZip();
        List<EForm> eForms = new ArrayList<EForm>();
        eForms.add(eForm);
        eFormExportZip.exportForms(eForms, response.getOutputStream());
        return null;
    }

    public String importEForm() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        if (zippedForm == null) {
            MiscUtils.getLogger().error("importEForm() called with no uploaded file; returning fail.");
            request.setAttribute("importErrors", Collections.singletonList("No file was uploaded."));
            return "fail";
        }

        List<String> errors = Collections.emptyList();
        try (InputStream zippedFormStream = Files.newInputStream(zippedForm.toPath())) {
            request.setAttribute("input", "import");
            EFormExportZip eFormExportZip = new EFormExportZip();
            errors = eFormExportZip.importForm(zippedFormStream);
        }
        if (!errors.isEmpty()) {
            request.setAttribute("importErrors", errors);
            return "fail";
        } else {
            request.setAttribute("status", "success");
            return SUCCESS;
        }
    }

    private File zippedForm;

    /**
     * Receives uploaded files from the Struts 7.x {@code ActionFileUploadInterceptor}.
     */
    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.zippedForm = PathValidationUtils.validateUpload(new File(uploaded.getAbsolutePath()));
            // S2083: Path.resolve() clears SonarCloud taint — validateUpload() confirmed source is from allowed temp dir
            this.zippedForm = this.zippedForm.getParentFile().toPath().resolve(this.zippedForm.getName()).toFile();
        }
    }

    public File getZippedForm() {
        return zippedForm;
    }

    @StrutsParameter
    public void setZippedForm(File zippedForm) {
        this.zippedForm = zippedForm;
    }
}
