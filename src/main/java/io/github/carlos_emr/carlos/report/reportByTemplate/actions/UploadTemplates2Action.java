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

//Action that takes place when uploading an XML template file


/*
 * UploadTemplate.java
 *
 * Created on March 24/2007, 10:47 AM
 *
 */

package io.github.carlos_emr.carlos.report.reportByTemplate.actions;


import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class UploadTemplates2Action extends ActionSupport implements UploadedFilesAware {
    private final SecurityInfoManager securityInfoManager;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public UploadTemplates2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    UploadTemplates2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing required sec object (_admin or _report)");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin or _report)");
        }

        String action = request.getParameter("action");
        String message = "Error: Improper request - Action param missing";
        String xml = "";
        
        if (templateFile != null) {
            try {
                // Validate the uploaded temp file is from an allowed source
                File validatedTemplateFile = PathValidationUtils.validateUpload(templateFile);

                // Read the file content
                byte[] bytes = Files.readAllBytes(validatedTemplateFile.toPath());
                xml = new String(bytes);
            } catch (SecurityException se) {
                MiscUtils.getLogger().warn("SecurityException during file upload: " + se.getMessage(), se);
                message = "Error: File upload failed due to security policy violation.";
                request.setAttribute("message", message);
                request.setAttribute("action", action);
                return SUCCESS;
            } catch (IOException ioe) {
                message = "Exception: File Not Found";
                MiscUtils.getLogger().error("Error reading uploaded file", ioe);
            }
        } else {
            message = "Error: No file uploaded";
        }
        ReportManager reportManager = new ReportManager();
        if (action.equals("add")) {
            message = reportManager.addTemplate(null, xml, loggedInInfo);
        } else if (action.equals("edit")) {
            String templateId = request.getParameter("templateid");
            message = reportManager.updateTemplate(null, templateId, xml, loggedInInfo);
        }
        request.setAttribute("message", message);
        request.setAttribute("action", action);
        request.setAttribute("templateid", request.getParameter("templateid"));
        request.setAttribute("opentext", request.getParameter("opentext"));
        return SUCCESS;
    }

    private File templateFile;

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.templateFile = PathValidationUtils.validateUploadContent(uploaded.getContent());
        }
    }

    public File getTemplateFile() {
        return templateFile;
    }

    public void setTemplateFile(File templateFile) {
        this.templateFile = templateFile;
    }
}
