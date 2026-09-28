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
package io.github.carlos_emr.carlos.clinical.summary.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryGenerationException;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryGenerationService;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryTextExtractor;
import io.github.carlos_emr.carlos.clinical.summary.DocumentSummaryService;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Read-only Document Manager adapter around the reusable single-document summarizer. */
public final class AiDocumentSummary2Action extends ActionSupport {
    private final SecurityInfoManager security = SpringUtils.getBean(SecurityInfoManager.class);
    private final DocumentManager documents = SpringUtils.getBean(DocumentManager.class);
    private final DocumentSummaryService summarizer;

    public AiDocumentSummary2Action() { this(new DocumentSummaryService()); }

    AiDocumentSummary2Action(DocumentSummaryService summarizer) { this.summarizer = summarizer; }

    @Override
    public String execute() throws Exception { return render(false); }

    public String generate() throws Exception { return render(true); }

    private String render(boolean generate) throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (generate ? !"POST".equals(request.getMethod())
                : !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            response.setHeader("Allow", generate ? "POST" : "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        LoggedInInfo user = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (user == null || !security.hasPrivilege(user, "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }
        CarlosProperties properties = CarlosProperties.getInstance();
        if (!"true".equals(properties.getProperty(DocumentSummaryService.ENABLED_PROPERTY, "false"))
                || !"true".equals(properties.getProperty(ClinicalSummaryGenerationService.ENABLED_PROPERTY, "false"))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        int documentId;
        try {
            documentId = positiveId(request.getParameterValues("documentId"));
        } catch (IllegalArgumentException invalid) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        var document = authorizedDocument(user, documentId);
        ClinicalSummaryTextExtractor.Extract extract = extract(document.filename(), document.contentType());
        request.setAttribute("documentSummaryId", documentId);
        request.setAttribute("documentSummaryTitle", document.title());
        request.setAttribute("documentSummaryExtraction", extract.reason());
        request.setAttribute("documentSummaryAllowed", !extract.text().isBlank());
        if (generate && !extract.text().isBlank()) {
            LogAction.addLogSynchronous(user, "DocumentSummary.generate", "documentId=" + documentId);
            try {
                var summary = summarizer.summarize(extract.text());
                if (!security.hasPrivilege(user, "_edoc", "r", null)) {
                    throw new SecurityException("missing required sec object (_edoc)");
                }
                var fresh = authorizedDocument(user, documentId);
                var freshExtract = extract(fresh.filename(), fresh.contentType());
                if (!document.equals(fresh)
                        || !extract.text().equals(freshExtract.text())) {
                    throw new ClinicalSummaryGenerationException(
                            "The document changed during generation. The draft was discarded; reopen the document and try again.");
                }
                request.setAttribute("documentSummaryOverview", summary.overview());
                request.setAttribute("documentSummaryPoints", summary.points());
                request.setAttribute("documentSummaryGenerated", true);
            } catch (ClinicalSummaryGenerationException rejected) {
                request.setAttribute("documentSummaryError", rejected.getMessage());
                LogAction.addLogSynchronous(user, "DocumentSummary.generateRejected", "documentId=" + documentId);
            }
        }
        return SUCCESS;
    }

    // Copy values before inference: a managed entity (including its Date) may be mutable.
    private record Snapshot(int patientId, String filename, String contentType, String title, Long updated) { }

    private Snapshot authorizedDocument(LoggedInInfo user, int documentId) {
        var link = documents.getCtlDocumentByDocumentId(user, documentId);
        if (link == null || link.getId() == null || !link.isDemographicDocument()
                || link.getId().getModuleId() == null || link.getId().getModuleId() <= 0
                || !Objects.equals(link.getId().getDocumentNo(), documentId)) {
            throw new SecurityException("Document scope unavailable");
        }
        String patient = String.valueOf(link.getId().getModuleId());
        boolean visible = EDocUtil.listDocs(user, "demographic", patient, "all", EDocUtil.PRIVATE,
                        EDocUtil.EDocSort.OBSERVATIONDATE, "active").stream()
                .anyMatch(item -> String.valueOf(documentId).equals(item.getDocId()));
        if (!visible) throw new SecurityException("Document scope unavailable");
        var document = documents.getDocument(user, documentId);
        if (document == null || !Objects.equals(document.getDocumentNo(), documentId)
                || document.getStatus() != 'A') {
            throw new SecurityException("Document scope unavailable");
        }
        return new Snapshot(link.getId().getModuleId(), document.getDocfilename(), document.getContenttype(),
                document.getDocdesc(), document.getUpdatedatetime() == null ? null : document.getUpdatedatetime().getTime());
    }

    private static ClinicalSummaryTextExtractor.Extract extract(String filename, String contentType) {
        try {
            return ClinicalSummaryTextExtractor.document(filename, contentType);
        } catch (IOException | IllegalArgumentException unavailable) {
            return new ClinicalSummaryTextExtractor.Extract("", false,
                    "Document content is unavailable or unreadable; review the original.");
        }
    }

    private static int positiveId(String[] values) {
        if (values == null || values.length != 1 || values[0] == null
                || !values[0].matches("[1-9][0-9]{0,9}")) throw new IllegalArgumentException("Invalid document ID");
        try {
            return Integer.parseInt(values[0]);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid document ID", invalid);
        }
    }
}
