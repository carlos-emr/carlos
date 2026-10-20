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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import java.util.Locale;

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

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (the ReportManager outcome prefix "Error"/"Exception"); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (the ReportManager outcome prefix); not a security or authorization decision")
    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing required sec object (_admin or _report)");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin or _report)");
        }
        // An upload stores SQL that later runs against the clinical database, so it is a
        // write: POST only, and the same _report write ReportManager enforces when it saves.
        if (!"POST".equals(request.getMethod())) {
            try {
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (IOException e) {
                MiscUtils.getLogger().warn("Could not send 405 for template upload", e);
            }
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        String action = request.getParameter("action");
        String message = "Error: Improper request - Action param missing";
        String xml = "";
        
        if (templateFile != null) {
            try {
                // Validate the uploaded temp file is from an allowed source
                File validatedTemplateFile = PathValidationUtils.validateUpload(templateFile);

                // The upload interceptor supplies this temporary file (reviewed CodeQL alert 27453).
                // Struts does not bind request parameters to the unannotated setter.
                // The read uses the canonical validated file; arbitrary client paths remain untrusted.
                // See docs/static-analysis-workflows.md (CodeQL exceptions).
                byte[] bytes = Files.readAllBytes(validatedTemplateFile.toPath());
                xml = new String(bytes);
                if (xml.isBlank()) {
                    message = "Error: The uploaded template file is empty";
                }
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
        // An empty xml means nothing readable was uploaded: keep the message set above instead
        // of handing an empty document to the parser.
        if (!xml.isBlank() && "add".equals(action)) {
            message = reportManager.addTemplate(null, xml, loggedInInfo);
        } else if (!xml.isBlank() && "edit".equals(action)) {
            String templateId = request.getParameter("templateid");
            message = reportManager.updateTemplate(null, templateId, xml, loggedInInfo);
        }
        request.setAttribute("message", message);
        request.setAttribute("action", action);
        request.setAttribute("templateid", request.getParameter("templateid"));
        request.setAttribute("opentext", request.getParameter("opentext"));
        String outcome = message.toLowerCase(Locale.ROOT);
        if (!xml.isBlank() && (outcome.startsWith("error") || outcome.startsWith("exception"))) {
            // As in the editor: a refused upload is shown in the textarea, so the author can
            // fix the statement the message names instead of starting over.
            request.setAttribute("submittedXml", xml);
        }
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
