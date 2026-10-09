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
package io.github.carlos_emr.carlos.email.admin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.EmailFooterLogo;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.LogoRejectedException;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.Rejection;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.dispatcher.multipart.MultiPartRequestWrapper;
import org.apache.struts2.interceptor.ValidationWorkflowAware;

/**
 * Saves or removes the clinic's email footer logo from Configure Email (issue #3981).
 *
 * <p>POST only, with {@code _admin} write; GET and HEAD get 405 before anything is read. The
 * {@code logoAction} parameter is {@code upload} (with the file in {@code logoFile}) or
 * {@code remove}. The page always comes back through a redirect to Configure Email, which says
 * what happened: {@code logoSaved}, {@code logoRemoved} or {@code logoError} with a fixed reason
 * code. Each change is audited (who and when, not the picture).</p>
 *
 * @since 2026-10-08
 */
public class SaveClinicEmailLogo2Action extends ActionSupport implements UploadedFilesAware, ValidationWorkflowAware {

    static final String ACTION_PARAM = "logoAction";
    static final String FILE_PARAM = "logoFile";
    private static final String PAGE = "/admin/ViewConfigureEmail";
    private static final String AUDIT_CONTENT = "emailFooterLogo";
    private static final Set<String> SIZE_ERROR_KEYS = Set.of(
            "struts.messages.upload.error.FileUploadSizeException",
            "struts.messages.upload.error.FileUploadByteCountLimitException");
    private static final Logger logger = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager;
    private final EmailFooterLogoService logoService;
    private File logoFile;
    private boolean uploadFailed;

    /** Used by Struts, which needs a no-argument constructor. */
    public SaveClinicEmailLogo2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(EmailFooterLogoService.class));
    }

    // Package-private for tests.
    SaveClinicEmailLogo2Action(SecurityInfoManager securityInfoManager, EmailFooterLogoService logoService) {
        this.securityInfoManager = securityInfoManager;
        this.logoService = logoService;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String action = request.getParameter(ACTION_PARAM);
        if ("upload".equals(action)) {
            if (uploadFailed) {
                return backToPage(request, response, "logoError=" + Rejection.UPLOAD_FAILED.name());
            }
            if (logoFile == null) {
                return backToPage(request, response, "logoError=" + Rejection.EMPTY.name());
            }
            // Struts already caps the request (struts-admin.xml); this refuses a big file unread.
            if (logoFile.length() > EmailFooterLogoService.MAX_BYTES) {
                return backToPage(request, response, "logoError=" + Rejection.TOO_BIG.name());
            }
            byte[] upload;
            try {
                upload = Files.readAllBytes(logoFile.toPath());
            } catch (IOException e) {
                logger.warn("Clinic email logo upload could not be read; cause={}", e.getClass().getSimpleName());
                return backToPage(request, response, "logoError=" + Rejection.UPLOAD_FAILED.name());
            }
            EmailFooterLogo saved;
            try {
                saved = logoService.replace(upload, providerNo);
            } catch (LogoRejectedException e) {
                return backToPage(request, response, "logoError=" + e.reason().name());
            }
            LogAction.addLog(providerNo, "update", AUDIT_CONTENT, String.valueOf(saved.getId()), request.getRemoteAddr());
            return backToPage(request, response, "logoSaved=true");
        }
        if ("remove".equals(action)) {
            if (!logoService.remove(providerNo)) {
                // Nothing to remove (another administrator got there first): no message, no audit.
                return backToPage(request, response, null);
            }
            LogAction.addLog(providerNo, "delete", AUDIT_CONTENT, "", request.getRemoteAddr());
            return backToPage(request, response, "logoRemoved=true");
        }
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return NONE;
    }

    // FindSecBugs UNVALIDATED_REDIRECT: the target is this application's context path, a fixed route
    // and a fixed outcome code (a literal or an enum name); no request text reaches the URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "Fixed internal route with a fixed outcome code")
    private static String backToPage(HttpServletRequest request, HttpServletResponse response, String outcome)
            throws IOException {
        // The outcome is a fixed literal or an enum name, or none: no request text reaches the URL.
        response.sendRedirect(request.getContextPath() + PAGE + (outcome == null ? "" : "?" + outcome)); // nosemgrep: java.lang.security.audit.unvalidated-redirect.unvalidated-redirect -- fixed internal route and fixed outcome codes
        return NONE;
    }

    /**
     * Struts can reject multipart data before execute runs. Only known size failures use the
     * size message; malformed requests and upload-parser faults use the server-failure message.
     * Error keys and metadata are checked without reading the uploaded content or matching
     * translated error strings.
     *
     * @return a fixed Struts result name
     */
    @Override
    public String getInputResultName() {
        HttpServletRequest request = ServletActionContext.getRequest();
        while (request instanceof HttpServletRequestWrapper wrapper) {
            if (request instanceof MultiPartRequestWrapper multipart) {
                if (multipart.hasErrors()) {
                    return !multipart.getErrors().isEmpty() && multipart.getErrors().stream()
                            .allMatch(error -> SIZE_ERROR_KEYS.contains(error.getTextKey()))
                            ? "uploadTooBig" : INPUT;
                }
                UploadedFile[] files = multipart.getFiles(FILE_PARAM);
                if (!hasActionErrors() && getFieldErrors().keySet().stream().allMatch(FILE_PARAM::equals)
                        && files != null) {
                    for (UploadedFile file : files) {
                        Long length = file.length();
                        if (length != null && length > EmailFooterLogoService.MAX_BYTES) {
                            return "uploadTooBig";
                        }
                    }
                }
                return INPUT;
            }
            request = (HttpServletRequest) wrapper.getRequest();
        }
        return INPUT;
    }

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles == null) {
            return;
        }
        for (UploadedFile uploaded : uploadedFiles) {
            if (FILE_PARAM.equals(uploaded.getInputName())) {
                // Runs during Struts binding; preserve a server fault for the result page.
                try {
                    this.logoFile = PathValidationUtils.validateUploadContent(uploaded.getContent());
                    this.uploadFailed = false;
                } catch (SecurityException e) {
                    // Not the administrator's doing (the server's upload folder): say so in the log.
                    logger.warn("Clinic email logo upload refused: the uploaded file is not in an allowed upload folder");
                    this.logoFile = null;
                    this.uploadFailed = true;
                }
                return;
            }
        }
    }
}
