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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.actions;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApproval;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Re-renders an already-saved eForm as a downloadable PDF, using a clinician's exact approval for a
 * render the completeness gate refused.
 *
 * <p>This exists as its own route rather than a retry through {@code AddEForm2Action} for a reason
 * that matters clinically: that action calls {@code saveEformData}, which persists a <em>new</em>
 * {@code EFormData} and returns a new fdid on every submit. Resubmitting the original form to
 * approve a render would therefore duplicate the saved record. It would also mean carrying every
 * form field — patient data — through the approval page as hidden inputs. By the time a download is
 * refused the eForm is already saved and the failure is in rendering, so the retry needs only the
 * fdid, the demographic, and the approval token.</p>
 *
 * <p>Read-scope: it renders an existing record and writes nothing. POST-only all the same, so an
 * approval cannot be replayed from a link or a browser history entry.</p>
 *
 * @since 2026-07-26
 */
public class DownloadEFormPdf2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();
    private static final String PDF_DOWNLOAD_FAILURE_MESSAGE =
            "This eForm (and attachments, if applicable) could not be downloaded.";

    private final SecurityInfoManager securityInfoManager;
    private final DocumentAttachmentManager documentAttachmentManager;
    private final EFormRenderApprovalService renderApprovalService;
    private final DemographicManager demographicManager;

    /** Struts instantiates this action reflectively, so the dependencies are resolved here. */
    public DownloadEFormPdf2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(DocumentAttachmentManager.class),
                SpringUtils.getBean(EFormRenderApprovalService.class),
                SpringUtils.getBean(DemographicManager.class));
    }

    DownloadEFormPdf2Action(SecurityInfoManager securityInfoManager,
            DocumentAttachmentManager documentAttachmentManager,
            EFormRenderApprovalService renderApprovalService,
            DemographicManager demographicManager) {
        this.securityInfoManager = securityInfoManager;
        this.documentAttachmentManager = documentAttachmentManager;
        this.renderApprovalService = renderApprovalService;
        this.demographicManager = demographicManager;
    }

    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        String fdid = request.getParameter("fdid");
        String demographicNo = request.getParameter("demographicNo");
        int fdidValue;
        int demographicValue;
        try {
            fdidValue = Integer.parseInt(fdid);
            demographicValue = Integer.parseInt(demographicNo);
        } catch (NumberFormatException | NullPointerException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, demographicNo)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        // A null approval is not an error: the gate simply refuses again and the caller sees the
        // ordinary failure. Only a ticket matching this provider, session, form, patient and
        // operation yields one.
        EFormRenderApproval approval = renderApprovalService.consume(
                request, loggedInInfo, fdidValue, demographicNo,
                EFormRenderApprovalService.Operation.DOWNLOAD,
                request.getParameter("renderApproval"));

        // renderEFormWithAttachments reads these as request ATTRIBUTES, not parameters.
        request.setAttribute("fdid", fdid);
        request.setAttribute("demographicId", demographicNo);

        try {
            Path pdfPath = documentAttachmentManager.renderEFormWithAttachments(request, response, approval);
            request.setAttribute("eFormPDF", documentAttachmentManager.convertPDFToBase64(pdfPath));
            request.setAttribute("eFormPDFName", generateFileName(loggedInInfo, demographicValue));
            request.setAttribute("isDownload", "true");
            request.setAttribute("fdid", fdid);
            return "download";
        } catch (EformContentUnavailableException e) {
            // Still incomplete, either because no approval was supplied or because this render
            // reported a different issue set than the one approved — the digest binds to the exact
            // set, so a changed document cannot ride an older ticket.
            logger.warn("Approved eForm download still incomplete: fdid={} issues={}",
                    fdidValue, e.getIssueCount());
            request.setAttribute("error", "true");
            request.setAttribute("errorMessage", PDF_DOWNLOAD_FAILURE_MESSAGE);
            return "error";
        } catch (PDFGenerationException e) {
            logger.error("eForm download render failed: fdid={} type={}", fdidValue, e.getClass().getName());
            request.setAttribute("error", "true");
            request.setAttribute("errorMessage", PDF_DOWNLOAD_FAILURE_MESSAGE);
            return "error";
        }
    }

    /** Mirrors {@code AddEForm2Action.generateFileName} so an approved download is named the same. */
    private String generateFileName(LoggedInInfo loggedInInfo, int demographicNo) {
        String lastName = demographicManager
                .getDemographicFormattedName(loggedInInfo, demographicNo).split(", ")[0];
        return new SimpleDateFormat("yyyy_MM_dd").format(new Date()) + "_" + lastName + ".pdf";
    }
}
