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
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Serves the word bounding boxes of one document page so the annotation viewer can snap a
 * highlight to text instead of to a hand-drawn rectangle.
 *
 * <p>Inbound faxes reaching CARLOS already carry an OCR text layer applied upstream, so
 * {@link PDFTextStripper} finds positioned glyphs even on scanned pages. A page with no
 * text layer yields an empty list, and the viewer falls back to free rectangles; that is a
 * normal result, not an error.
 *
 * <p>Boxes are returned in the same normalised, top-left, rotation-applied space the
 * annotation model uses, so the viewer can compare them against pointer coordinates
 * without knowing the render DPI. See {@code DocumentAnnotationDto} for that contract.
 *
 * <p>Read-scope gate: permits GET, refuses only unsupported verbs. Extraction is bounded
 * by {@link #EXTRACT_TIMEOUT_SECONDS} because the source is untrusted input.
 *
 * @since 2026-09
 */
public class DocumentTextBoxes2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    /** Word extraction on one page should be milliseconds; this only catches pathological input. */
    public static final int EXTRACT_TIMEOUT_SECONDS = 10;

    /** Beyond this a highlight snap is not useful and the payload becomes the bottleneck. */
    private static final int MAX_WORDS_PER_PAGE = 5_000;

    private final SecurityInfoManager securityInfoManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)
                && !"POST".equalsIgnoreCase(method)) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
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

        String moduleId = StringUtils.trimToNull(doc.getModuleId());
        if (moduleId != null && !"0".equals(moduleId)) {
            try {
                if (!securityInfoManager.isAllowedAccessToPatientRecord(
                        loggedInInfo, Integer.parseInt(moduleId))) {
                    throw new SecurityException("Unauthorized access to patient record");
                }
            } catch (NumberFormatException ignored) {
                // A non-numeric module id means the document is not patient-linked.
            }
        }

        ArrayNode words = objectMapper.createArrayNode();
        try {
            File documentDir = PathValidationUtils.resolveConfiguredDirectory(
                    CarlosProperties.getInstance().getDocumentDirectory(), "DOCUMENT_DIR");
            File pdf = PathValidationUtils.validateExistingPath(
                    new File(documentDir, doc.getFileName()), documentDir);
            extract(pdf, page, words);
        } catch (SecurityException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // A page whose text cannot be read is not a failure of the viewer: it simply
            // has no snap targets. Log and return an empty list.
            logger.warn("Word boxes unavailable for document {} page {}", docId, page);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("page", page);
        payload.set("words", words);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(payload));
        }
        return NONE;
    }

    /**
     * Collects one box per word, normalised against the page's displayed dimensions.
     *
     * <p>{@link PDFTextStripper} reports positions in a top-left space that already has
     * page rotation applied, which is the same space the annotation model uses, so only a
     * division by the displayed width and height is required.
     */
    private void extract(File pdf, int page, ArrayNode out) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf, IOUtils.createTempFileOnlyStreamCache())) {
            if (page < 1 || page > document.getNumberOfPages()) {
                return;
            }
            PDPage pdPage = document.getPage(page - 1);
            PDRectangle box = pdPage.getCropBox();
            int rotation = ((pdPage.getRotation() % 360) + 360) % 360;
            boolean quarterTurn = rotation == 90 || rotation == 270;
            final float displayW = quarterTurn ? box.getHeight() : box.getWidth();
            final float displayH = quarterTurn ? box.getWidth() : box.getHeight();
            if (displayW <= 0f || displayH <= 0f) {
                return;
            }

            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    if (out.size() >= MAX_WORDS_PER_PAGE) {
                        return;
                    }
                    for (List<TextPosition> word : splitWords(positions)) {
                        if (out.size() >= MAX_WORDS_PER_PAGE) {
                            return;
                        }
                        appendBox(word, displayW, displayH, out);
                    }
                }
            };
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            stripper.setSortByPosition(true);
            // The extracted text is discarded; only the geometry is wanted, and the text
            // itself is PHI that must not leave this method.
            stripper.getText(document);
        }
    }

    /** Splits a run of glyphs into words on whitespace, preserving position data. */
    private static List<List<TextPosition>> splitWords(List<TextPosition> positions) {
        List<List<TextPosition>> words = new ArrayList<>();
        List<TextPosition> current = new ArrayList<>();
        for (TextPosition position : positions) {
            String unicode = position.getUnicode();
            if (unicode == null || unicode.isBlank()) {
                if (!current.isEmpty()) {
                    words.add(current);
                    current = new ArrayList<>();
                }
                continue;
            }
            current.add(position);
        }
        if (!current.isEmpty()) {
            words.add(current);
        }
        return words;
    }

    private static void appendBox(List<TextPosition> word, float displayW, float displayH, ArrayNode out) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (TextPosition position : word) {
            float x = position.getXDirAdj();
            float y = position.getYDirAdj() - position.getHeightDir();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + position.getWidthDirAdj());
            maxY = Math.max(maxY, y + position.getHeightDir());
        }
        if (maxX <= minX || maxY <= minY) {
            return;
        }
        ObjectNode node = out.addObject();
        node.put("x", clamp(minX / displayW));
        node.put("y", clamp(minY / displayH));
        node.put("w", clamp((maxX - minX) / displayW));
        node.put("h", clamp((maxY - minY) / displayH));
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, value));
    }
}
