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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.annotation.AnnotatedDocumentService;
import io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask;
import io.github.carlos_emr.carlos.documentManager.annotation.DocumentPatientLink;
import io.github.carlos_emr.carlos.documentManager.annotation.DocumentWordBoxes;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serves the word bounding boxes of one document page so the annotation viewer can snap a
 * highlight to text instead of to a hand-drawn rectangle.
 *
 * <p><strong>The text layer is optional.</strong> Inbound faxes reaching CARLOS usually
 * carry one applied upstream, so positioned glyphs are found even on scanned pages, but a
 * page may have none — never OCR'd, photographed, or image-only. That yields an empty list
 * and {@code hasTextLayer: false}, which is a normal answer rather than an error: the
 * viewer simply keeps the rectangle the provider drew. Highlighting, and every other tool,
 * works the same either way.
 *
 * <p>Boxes are returned in the same normalised, top-left, rotation-applied space the
 * annotation model uses, so the viewer can compare them against pointer coordinates
 * without knowing the render DPI. See {@code DocumentAnnotationDto} for that contract.
 *
 * <p>Read-scope gate: permits GET, refuses only unsupported verbs. Extraction is delegated
 * to {@link DocumentWordBoxes} and capped at {@value #MAX_WORDS_PER_PAGE} boxes, because
 * the source document is untrusted input.
 *
 * @since 2026-09
 */
public class DocumentTextBoxes2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    /** Beyond this a highlight snap is not useful and the payload becomes the bottleneck. */
    private static final int MAX_WORDS_PER_PAGE = 5_000;

    /**
     * Hard deadline on text extraction. The source is an inbound fax, so it is untrusted: a
     * document crafted to make PDFBox work pathologically would otherwise hold a request thread
     * for as long as it liked, and this endpoint is called once per page.
     */
    private static final int EXTRACT_TIMEOUT_SECONDS = 15;

    private final transient SecurityInfoManager securityInfoManager;
    private final transient ObjectMapper objectMapper = new ObjectMapper();

    public DocumentTextBoxes2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    DocumentTextBoxes2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        // Read-scope, so GET and HEAD only. This endpoint carries no CSRF token because it
        // mutates nothing; accepting POST would widen a token-free surface for no gain.
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", "GET, HEAD");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        int docId;
        int page;
        try {
            docId = Integer.parseInt(StringUtils.defaultString(
                    StringUtils.trimToNull(request.getParameter("docId"))));
            page = Integer.parseInt(StringUtils.defaultString(
                    StringUtils.trimToNull(request.getParameter("page"))));
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        EDoc doc = EDocUtil.getDoc(String.valueOf(docId));
        if (doc == null || StringUtils.isBlank(doc.getFileName())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        int linkedDemographicNo = DocumentPatientLink.demographicNoOf(doc);
        if (linkedDemographicNo > 0
                && !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, linkedDemographicNo)) {
            throw new SecurityException("Unauthorized access to patient record");
        }

        ArrayNode words = objectMapper.createArrayNode();
        // Tracked separately from the box count. A page can carry a text layer and still yield
        // no usable boxes, and an extraction that fails or times out yields none either — none
        // of which is the same statement as "this page was never OCR'd". Conflating them told
        // the viewer something that was not true.
        boolean extractionSucceeded = false;
        try {
            File documentDir = PathValidationUtils.resolveConfiguredDirectory(
                    CarlosProperties.getInstance().getDocumentDirectory(), "DOCUMENT_DIR");
            File pdf = PathValidationUtils.validateExistingPath(
                    new File(documentDir, doc.getFileName()), documentDir);
            // The save path enforces this ceiling in assertAnnotatable, but this endpoint is
            // reachable on its own and the viewer calls it once per visible page. Without the
            // same bound here, an oversized document turns every scroll into a full parse that
            // can only end at the deadline, and the deadline leaves the worker running.
            if (pdf.length() > AnnotatedDocumentService.MAX_ANNOTATABLE_BYTES) {
                logger.warn("Word boxes refused for document {}: file exceeds the annotation size limit", docId);
                throw new IOException("Document is too large to extract word boxes from.");
            }
            List<double[]> extracted = BoundedPdfTask.runWithin(
                    EXTRACT_TIMEOUT_SECONDS, "document-word-boxes",
                    () -> DocumentWordBoxes.extract(pdf, page, MAX_WORDS_PER_PAGE));
            extractionSucceeded = true;
            for (double[] box : extracted) {
                ObjectNode node = words.addObject();
                node.put("x", box[0]);
                node.put("y", box[1]);
                node.put("w", box[2]);
                node.put("h", box[3]);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // A page whose text cannot be read is not a viewer failure: it simply has no snap
            // targets, exactly like a page that was never OCR'd. Log and answer with an empty
            // list so the client falls back to free-hand rectangles.
            logger.warn("Word boxes unavailable for document {} page {}", docId, page);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("page", page);
        // True only when extraction actually ran to completion AND found positioned text.
        // Either way highlighting still works; only snapping to text is unavailable.
        payload.put("hasTextLayer", extractionSucceeded && !words.isEmpty());
        // Distinguishes "the page has no text" from "we could not read it", which the first
        // field alone cannot express and which a caller deciding whether to retry needs.
        payload.put("textLayerRead", extractionSucceeded);
        payload.set("words", words);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(payload));
        }
        return NONE;
    }

}
