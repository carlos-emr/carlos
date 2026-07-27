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

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApproval;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Archives an already-saved eForm to the patient's documents, using a clinician's exact approval for
 * a render the completeness gate refused.
 *
 * <p>Like the download retry, this is a separate route rather than a resubmission of
 * {@code AddEForm2Action}: that action calls {@code saveEformData}, which persists a <em>new</em>
 * eForm on every submit, so approving a render through it would duplicate the saved clinical record
 * and would carry every form field — patient data — through the approval page as hidden inputs. The
 * eForm is already saved when the archive is refused; only its rendering failed.</p>
 *
 * <p>Unlike the download retry this action <strong>is</strong> a mutator — it creates a document —
 * so it rejects GET and HEAD before any side effect, per the project's mutator contract. Duplicate
 * archiving is bounded by the approval ticket itself: {@code consume} removes it, so one ticket
 * yields at most one eDoc.</p>
 *
 * @since 2026-07-26
 */
public class SaveEFormAsEDoc2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();
    private static final String APPROVAL_EXPIRED_MESSAGE =
            "The incomplete-render approval is no longer valid. Add the eForm to documents again to "
            + "review the listed issues and approve them.";
    private static final String EDOC_FAILURE_MESSAGE =
            "This eForm (and attachments, if applicable) could not be added to this patient’s documents.";

    private final SecurityInfoManager securityInfoManager;
    private final DocumentAttachmentManager documentAttachmentManager;
    private final EFormRenderApprovalService renderApprovalService;

    /** Struts instantiates this action reflectively, so the dependencies are resolved here. */
    public SaveEFormAsEDoc2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(DocumentAttachmentManager.class),
                SpringUtils.getBean(EFormRenderApprovalService.class));
    }

    SaveEFormAsEDoc2Action(SecurityInfoManager securityInfoManager,
            DocumentAttachmentManager documentAttachmentManager,
            EFormRenderApprovalService renderApprovalService) {
        this.securityInfoManager = securityInfoManager;
        this.documentAttachmentManager = documentAttachmentManager;
        this.renderApprovalService = renderApprovalService;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        // Mutator: reject every verb but POST before any side effect fires.
        //
        // Written as an ALLOW-list on purpose. The obvious alternative — a deny-list asking "is this
        // GET or HEAD?" — cannot safely use exact comparison, because a request whose method is
        // "get" would stop matching and fall straight through to the archive. Allow-listing gives
        // the exact, case-sensitive comparison (RFC 9110 §9.1 makes method tokens case-sensitive)
        // AND refuses anything unexpected, rather than only the verbs someone remembered to list.
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
        // UPDATE, matching what saveEFormWithAttachmentsAsEDoc enforces downstream, so an
        // unauthorized caller is refused before any rendering work is done.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.UPDATE, demographicNo)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        String approvalToken = request.getParameter("renderApproval");
        EFormRenderApproval approval = renderApprovalService.consume(
                request, loggedInInfo, fdidValue, demographicNo,
                EFormRenderApprovalService.Operation.EDOC, approvalToken);
        if (approvalToken != null && approval == null) {
            // A token was presented and did not survive — most likely the two-minute lifetime, since
            // the approval page is a list of clinical omissions meant to be read. Saying so lets the
            // clinician retry instead of hunting for a problem with the eForm itself.
            logger.info("eForm eDoc approval expired or did not match: fdid={}", fdidValue);
            request.setAttribute("error", "true");
            request.setAttribute("errorMessage", APPROVAL_EXPIRED_MESSAGE);
            return "error";
        }

        // saveEFormAsEDoc reads these as request ATTRIBUTES, not parameters.
        request.setAttribute("fdid", fdid);
        request.setAttribute("demographicId", demographicNo);

        try {
            documentAttachmentManager.saveEFormAsEDoc(request, response, approval);
            request.setAttribute("isSuccess_Autoclose", "true");
            return "close";
        } catch (EformContentUnavailableException e) {
            // Still incomplete: either no approval was supplied, or this render reported a different
            // issue set than the approved one — the digest binds to the exact set.
            logger.warn("Approved eForm eDoc archive still incomplete: fdid={} issues={}",
                    fdidValue, e.getIssueCount());
            request.setAttribute("error", "true");
            request.setAttribute("errorMessage", EDOC_FAILURE_MESSAGE);
            return "error";
        } catch (PDFGenerationException e) {
            logger.error("eForm eDoc archive failed: fdid={} type={}", fdidValue, e.getClass().getName());
            request.setAttribute("error", "true");
            request.setAttribute("errorMessage", EDOC_FAILURE_MESSAGE);
            return "error";
        }
    }
}
