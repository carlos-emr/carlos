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
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.annotation.AnnotatedDocumentComposer;
import io.github.carlos_emr.carlos.documentManager.annotation.AnnotatedDocumentService;
import io.github.carlos_emr.carlos.documentManager.annotation.DocumentAnnotationDto;
import io.github.carlos_emr.carlos.documentManager.annotation.DocumentAnnotationParser;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Receives an annotation model as JSON and files the composed result as a new document.
 *
 * <p>This action deliberately does <em>not</em> accept a PDF. The browser sends a small
 * description of what the provider drew — types, pages and normalised coordinates — and
 * the server composes the document with PDFBox. Two properties follow from that. The
 * browser never authors a clinical artifact, and a compromised page cannot substitute
 * arbitrary content for a patient's record.
 *
 * <p>The original document is untouched. A successful save creates a second document for
 * the same patient; see {@link AnnotatedDocumentService}.
 *
 * <p><strong>Mutator.</strong> GET and HEAD are refused with 405 before any dependency is
 * touched, so the aggregated contract test can drive this class directly. Registered in
 * {@code MutatorActionGetRejectionContractUnitTest.unconditionalMutators()}.
 *
 * <p>Responses are JSON. Errors carry a message written by this application, never the
 * offending input, because annotation text is PHI.
 *
 * @since 2026-09
 */
public class SaveAnnotatedDocument2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    /** Generous for a coordinate model, far below anything that could carry a document. */
    private static final int MAX_BODY_BYTES = 256 * 1024;

    private final transient SecurityInfoManager securityInfoManager;
    private final transient DocumentAnnotationParser parser;
    private final transient AnnotatedDocumentService injectedService;
    private final transient ObjectMapper objectMapper = new ObjectMapper();

    public SaveAnnotatedDocument2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), new DocumentAnnotationParser(), null);
    }

    /**
     * @param injectedService when {@code null}, a service is built per request from the
     *                        servlet context so it can locate the bundled font. Tests pass
     *                        one in to keep composition and the filesystem out of the way.
     */
    SaveAnnotatedDocument2Action(SecurityInfoManager securityInfoManager,
                                 DocumentAnnotationParser parser,
                                 AnnotatedDocumentService injectedService) {
        this.securityInfoManager = securityInfoManager;
        this.parser = parser;
        this.injectedService = injectedService;
    }

    @Override
    // IMPROPER_UNICODE: case-insensitive comparison of the HTTP verb, an ASCII protocol/domain
    // constant. String.equalsIgnoreCase is locale-independent, and the detector fires on the
    // call shape regardless of Locale, so it cannot be cleared in code.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an ASCII protocol/domain constant; equalsIgnoreCase is locale-independent")
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        // Verb gate first: nothing below may run on the wrong method, including the privilege
        // lookup. POST is the only verb this route has; allowing PUT/PATCH/DELETE/OPTIONS to
        // reach composition and filing lets the action's contract drift away from its caller.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", "POST");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null)) {
            // Answered as JSON rather than thrown. A SecurityException here resolves to the
            // global securityError.jsp mapping, so the caller's response.json() would reject an
            // HTML page and the provider would see a save silently do nothing.
            logger.warn("Refused annotated-document save: caller lacks _edoc write");
            return json(response, HttpServletResponse.SC_FORBIDDEN,
                    error("You do not have permission to save annotated documents."));
        }

        int docId;
        String raw = StringUtils.trimToNull(request.getParameter("docId"));
        try {
            docId = Integer.parseInt(StringUtils.defaultString(raw));
        } catch (NumberFormatException e) {
            return json(response, HttpServletResponse.SC_BAD_REQUEST,
                    error("A document must be selected."));
        }

        EDoc source = EDocUtil.getDoc(String.valueOf(docId));
        if (source == null) {
            return json(response, HttpServletResponse.SC_NOT_FOUND,
                    error("The document could not be found."));
        }
        // Read from the FILE, matching what AnnotateDocument2Action showed the provider. The
        // stored count is metadata: legacy rows carry zero, which this used to floor at 1, so a
        // mark the viewer legitimately allowed on page 7 of a 10-page scan was rejected here as
        // "outside the document's 1 to 1". The gate and this bound must name the same number or
        // the provider loses the work with no way to tell why.
        int pageCount;
        try {
            pageCount = AnnotatedDocumentService.pageCountOf(source);
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not read the page count for document {} while saving annotations", docId);
            return json(response, HttpServletResponse.SC_BAD_REQUEST,
                    error("This document could not be opened."));
        }
        if (pageCount < 1) {
            return json(response, HttpServletResponse.SC_BAD_REQUEST,
                    error("This document has no pages to annotate."));
        }

        String body;
        try {
            body = readBody(request);
        } catch (IllegalStateException e) {
            return json(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    error("The annotation data was too large."));
        }

        List<DocumentAnnotationDto> annotations;
        try {
            annotations = parser.parse(body, pageCount);
        } catch (IllegalArgumentException e) {
            // Parser messages name the rule that failed and contain no annotation content.
            return json(response, HttpServletResponse.SC_BAD_REQUEST, error(e.getMessage()));
        }
        if (annotations.isEmpty()) {
            return json(response, HttpServletResponse.SC_BAD_REQUEST,
                    error("There are no annotations to save."));
        }

        AnnotatedDocumentService service = injectedService != null ? injectedService
                : new AnnotatedDocumentService(securityInfoManager, new AnnotatedDocumentComposer(),
                        request.getServletContext().getRealPath("/"));

        try {
            int newDocNo = service.save(loggedInInfo, docId, annotations);
            ObjectNode ok = objectMapper.createObjectNode();
            ok.put("success", true);
            ok.put("documentNo", newDocNo);
            ok.put("demographicNo", StringUtils.defaultString(source.getModuleId(), "0"));
            return json(response, HttpServletResponse.SC_OK, ok);
        } catch (SecurityException e) {
            return json(response, HttpServletResponse.SC_FORBIDDEN,
                    error("You do not have access to this patient's records."));
        } catch (IllegalArgumentException e) {
            return json(response, HttpServletResponse.SC_CONFLICT, error(e.getMessage()));
        } catch (IOException | IllegalStateException e) {
            // The cause can quote document internals; log it, do not return it.
            logger.error("Failed to compose annotated copy of document {}", docId, e);
            return json(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    error("The annotated document could not be saved."));
        }
    }

    /**
     * Reads the request body with a hard ceiling. A {@code Content-Length} header is not
     * trusted on its own, so the read itself stops at the limit.
     */
    private static String readBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[8192];
        int total = 0;
        try (BufferedReader reader = request.getReader()) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IllegalStateException("Request body exceeds the permitted size");
                }
                sb.append(buffer, 0, read);
            }
        }
        return sb.toString();
    }

    private ObjectNode error(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("success", false);
        node.put("error", StringUtils.defaultIfBlank(message, "The request could not be completed."));
        return node;
    }

    // FindSecBugs XSS_SERVLET: the body is an application/json document serialised by Jackson,
    // never HTML, and every value is a number or an application-authored message.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "application/json body serialised by Jackson; no HTML context and no caller-supplied content")
    private String json(HttpServletResponse response, int status, ObjectNode payload) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(payload));
        }
        return NONE;
    }
}
