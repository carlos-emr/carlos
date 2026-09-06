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
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.annotation.AnnotatedDocumentService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;

import java.io.IOException;

/**
 * Read-scope gate for the document annotation viewer.
 *
 * <p>Loads the document's metadata, refuses anything the viewer cannot handle, and
 * forwards to {@code annotateDocument.jsp}. The viewer itself never receives PDF bytes;
 * it requests server-rendered page images from {@link ManageDocument2Action}, so this
 * action only needs to hand it a document number, a page count and a title.
 *
 * <p>The gate requires {@code _edoc} write even though it renders a page, because
 * reaching the viewer is the first step of authoring a new clinical document. A provider
 * who may only read documents has no reason to open it.
 *
 * <p>Permits GET: it is a view, and mutation happens in
 * {@link SaveAnnotatedDocument2Action}. Registered accordingly in the mutator contract
 * test's non-mutator list.
 *
 * @since 2026-09
 */
public class AnnotateDocument2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private final transient SecurityInfoManager securityInfoManager;

    private int docId;
    private int pageCount;
    private String documentTitle;
    private int demographicNo;
    private String message;

    public AnnotateDocument2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    AnnotateDocument2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String raw = StringUtils.trimToNull(request.getParameter("docId"));
        if (raw == null) {
            return unavailable("A document must be selected before it can be annotated.");
        }
        try {
            docId = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return unavailable("That document could not be opened for annotation.");
        }

        EDoc doc = EDocUtil.getDoc(String.valueOf(docId));
        if (doc == null || StringUtils.isBlank(doc.getFileName())) {
            return unavailable("The document could not be found.");
        }

        if (!"application/pdf".equalsIgnoreCase(StringUtils.trimToEmpty(doc.getContentType()))) {
            return unavailable("Only PDF documents can be annotated.");
        }

        String moduleId = StringUtils.trimToNull(doc.getModuleId());
        if (moduleId != null && !"0".equals(moduleId)) {
            try {
                demographicNo = Integer.parseInt(moduleId);
            } catch (NumberFormatException e) {
                demographicNo = 0;
            }
        }
        if (demographicNo > 0
                && !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
            throw new SecurityException("Unauthorized access to patient record");
        }

        // The stored count is metadata: legacy rows carry zero and a row can drift from the file
        // it names. Defaulting a zero to 1 rendered a single page of a multi-page document and,
        // worse, let a document past the page ceiling that the save path then had to refuse. The
        // count is read from the file instead, under a deadline because it is untrusted input.
        try {
            pageCount = AnnotatedDocumentService.pageCountOf(doc);
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not read the page count for document {}", docId);
            return unavailable("This document could not be opened for annotation. "
                    + "It can still be faxed as it is.");
        }
        if (pageCount < 1) {
            return unavailable("This document has no pages to annotate. "
                    + "It can still be faxed as it is.");
        }
        if (pageCount > AnnotatedDocumentService.MAX_ANNOTATABLE_PAGES) {
            return unavailable("Documents longer than "
                    + AnnotatedDocumentService.MAX_ANNOTATABLE_PAGES
                    + " pages cannot be annotated. It can still be faxed as it is.");
        }

        documentTitle = StringUtils.defaultIfBlank(doc.getDescription(), "Document");

        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.READ, LogConst.CON_DOCUMENT,
                String.valueOf(docId), request.getRemoteAddr(),
                demographicNo > 0 ? String.valueOf(demographicNo) : null);

        return SUCCESS;
    }

    private String unavailable(String reason) {
        this.message = reason;
        return "noAnnotate";
    }

    public int getDocId() {
        return docId;
    }

    public int getPageCount() {
        return pageCount;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public int getDemographicNo() {
        return demographicNo;
    }

    public String getMessage() {
        return message;
    }
}
