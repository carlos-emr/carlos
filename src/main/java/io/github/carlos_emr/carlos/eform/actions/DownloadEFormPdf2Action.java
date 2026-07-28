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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApproval;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
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
    private static final String APPROVAL_EXPIRED_MESSAGE_KEY = "eform.download.approvalExpired";
    private static final String APPROVAL_EXPIRED_MESSAGE_FALLBACK =
            "The incomplete-render approval is no longer valid. Download the eForm again to review "
            + "the listed issues and approve them.";
    private static final String PDF_DOWNLOAD_FAILURE_MESSAGE_KEY = "eform.download.failure";
    private static final String PDF_DOWNLOAD_FAILURE_MESSAGE_FALLBACK =
            "This eForm (and attachments, if applicable) could not be downloaded.";

    private final SecurityInfoManager securityInfoManager;
    private final DocumentAttachmentManager documentAttachmentManager;
    private final EFormRenderApprovalService renderApprovalService;
    private final DemographicManager demographicManager;
    private final EFormDataDao eFormDataDao;

    /** Struts instantiates this action reflectively, so the dependencies are resolved here. */
    public DownloadEFormPdf2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(DocumentAttachmentManager.class),
                SpringUtils.getBean(EFormRenderApprovalService.class),
                SpringUtils.getBean(DemographicManager.class),
                SpringUtils.getBean(EFormDataDao.class));
    }

    DownloadEFormPdf2Action(SecurityInfoManager securityInfoManager,
            DocumentAttachmentManager documentAttachmentManager,
            EFormRenderApprovalService renderApprovalService,
            DemographicManager demographicManager,
            EFormDataDao eFormDataDao) {
        this.securityInfoManager = securityInfoManager;
        this.documentAttachmentManager = documentAttachmentManager;
        this.renderApprovalService = renderApprovalService;
        this.demographicManager = demographicManager;
        this.eFormDataDao = eFormDataDao;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        // Exact comparison, not equalsIgnoreCase. This is an ALLOW-list — anything that is not
        // exactly "POST" is refused — so exact matching is the strictly stricter choice, and HTTP
        // method tokens are case-sensitive uppercase ASCII (RFC 9110 §9.1). Note the inverse holds
        // for a deny-list ("is this GET?"), where case-insensitive is the safe form; see
        // SaveEFormAsEDoc2Action, which is written as an allow-list for exactly this reason.
        if (!"POST".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        String fdid = request.getParameter("fdid");
        String demographicNo = request.getParameter("demographicNo");
        int fdidValue;
        try {
            fdidValue = Integer.parseInt(fdid);
            Integer.parseInt(demographicNo);
        } catch (NumberFormatException | NullPointerException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        EFormData eformData = eFormDataDao.find(fdidValue);
        if (eformData == null || eformData.getDemographicId() == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        String storedDemographicNo = String.valueOf(eformData.getDemographicId());
        if (!storedDemographicNo.equals(demographicNo)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(
                loggedInInfo, "_eform", SecurityInfoManager.READ, storedDemographicNo)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        // Only a ticket matching this provider, session, form, patient and operation yields an
        // approval. A null one is not by itself an error — the gate simply refuses again.
        String approvalToken = request.getParameter("renderApproval");
        EFormRenderApproval approval = renderApprovalService.consume(
                request, loggedInInfo, fdidValue, storedDemographicNo,
                EFormRenderApprovalService.Operation.DOWNLOAD, approvalToken);
        if (approvalToken != null && approval == null) {
            // A token WAS presented and did not survive. The two-minute lifetime is the likely
            // cause, and the approval page is a list of clinical omissions meant to be read — so
            // this is an ordinary outcome, not a failure of the eForm. Reporting it as "could not
            // be downloaded" sent the clinician looking for a problem with the document.
            // Fax2Action and DocumentPreview2Action already say this; say it here too.
            logger.info("eForm download approval expired or did not match: fdid={}", fdidValue);
            request.setAttribute("error", "true");
            request.setAttribute("errorMessage", message(
                    request, APPROVAL_EXPIRED_MESSAGE_KEY, APPROVAL_EXPIRED_MESSAGE_FALLBACK));
            return "error";
        }

        // renderEFormWithAttachments reads these as request ATTRIBUTES, not parameters.
        request.setAttribute("fdid", fdid);
        request.setAttribute("demographicId", storedDemographicNo);

        EformDataManager.EformPdfRender rendered = null;
        try {
            rendered = documentAttachmentManager
                    .renderEFormPacketWithCompleteness(request, response, approval);
            request.setAttribute("eFormPDF", documentAttachmentManager.convertPDFToBase64(rendered.path()));
            request.setAttribute("advisoryIssues", rendered.completeness().advisoryIssueCount());
            request.setAttribute("eFormPDFName",
                    generateFileName(loggedInInfo, eformData.getDemographicId()));
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
            request.setAttribute("errorMessage", message(
                    request, PDF_DOWNLOAD_FAILURE_MESSAGE_KEY, PDF_DOWNLOAD_FAILURE_MESSAGE_FALLBACK));
            return "error";
        } catch (PDFGenerationException e) {
            logger.error("eForm download render failed: fdid={} type={}", fdidValue, e.getClass().getName());
            request.setAttribute("error", "true");
            request.setAttribute("errorMessage", message(
                    request, PDF_DOWNLOAD_FAILURE_MESSAGE_KEY, PDF_DOWNLOAD_FAILURE_MESSAGE_FALLBACK));
            return "error";
        } finally {
            // The rendered packet is the patient's full document, and EformPdfRender's contract puts
            // cleanup on the caller. The base64 copy is already in the request by this point, so the
            // file has no further use — left behind, every approved download sat in the renderer temp
            // root until the 24h sweep.
            deleteRenderedPacket(rendered, fdidValue);
        }
    }

    /** Best-effort removal of the temporary render output; never fails the download over it. */
    private void deleteRenderedPacket(EformDataManager.EformPdfRender rendered, int fdidValue) {
        if (rendered == null || rendered.path() == null) {
            return;
        }
        try {
            java.nio.file.Files.deleteIfExists(rendered.path());
        } catch (java.io.IOException e) {
            logger.warn("Could not delete the temporary eForm download render: fdid={}", fdidValue);
        }
    }

    /**
     * Mirrors {@code AddEForm2Action.generateFileName} so an approved download is named the same.
     *
     * <p>Tolerates a missing demographic row. {@code getDemographicFormattedName} returns null when
     * the lookup finds nothing, and this runs AFTER the one-time approval ticket has been consumed
     * and the PDF rendered — so an NPE here cost the clinician the ticket and forced them through
     * the whole approval again, to produce a file whose only defect would have been its name.</p>
     */
    private String generateFileName(LoggedInInfo loggedInInfo, int demographicNo) {
        String formattedName = demographicManager.getDemographicFormattedName(loggedInInfo, demographicNo);
        String lastName = formattedName == null || formattedName.isBlank()
                ? "eform"
                : formattedName.split(", ")[0];
        return new SimpleDateFormat("yyyy_MM_dd").format(new Date()) + "_" + lastName + ".pdf";
    }

    private static String message(HttpServletRequest request, String key, String fallback) {
        try {
            return ResourceBundle.getBundle("oscarResources", request.getLocale()).getString(key);
        } catch (MissingResourceException e) {
            return fallback;
        }
    }
}
