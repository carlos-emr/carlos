//CHECKSTYLE:OFF
/**
 * Copyright (c) 2026. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2026.
 */

package ca.openosp.openo.managers;

import ca.openosp.OscarProperties;
import ca.openosp.openo.commn.dao.DocumentDao;
import ca.openosp.openo.commn.model.Document;
import ca.openosp.openo.documentManager.EDoc;
import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Implementation of PdfMarkupManager that uses Apache PDFBox to flatten
 * Fabric.js canvas annotations into PDF documents.
 *
 * This service converts Fabric.js JSON objects (paths, text, rectangles, images)
 * into PDFBox drawing operations, creating a new annotated PDF file that is
 * saved as a new document in the patient's chart.
 *
 * @since 2026-01-23
 */
@Service
public class PdfMarkupManagerImpl implements PdfMarkupManager {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private DocumentDao documentDao = SpringUtils.getBean(DocumentDao.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * {@inheritDoc}
     */
    @Override
    public Document createAnnotatedCopy(LoggedInInfo info, Integer originalDocNo,
                                         Integer demographicNo, String annotationsJson) throws IOException {
        String providerNo = info.getLoggedInProviderNo();
        String documentDir = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
        if (!documentDir.endsWith(File.separator)) {
            documentDir = documentDir + File.separator;
        }

        // Security check - user must have write access to documents
        if (!securityInfoManager.hasPrivilege(info, "_edoc", "w", demographicNo.toString())) {
            throw new SecurityException("No write access to documents");
        }

        // Load original document
        EDoc originalDoc = EDocUtil.getDoc(originalDocNo.toString());
        if (originalDoc == null || originalDoc.getFileName() == null || originalDoc.getFileName().isEmpty()) {
            throw new IllegalArgumentException("Document not found: " + originalDocNo);
        }

        // Validate file path
        File originalFile = new File(documentDir + originalDoc.getFileName());
        PathValidationUtils.validateExistingPath(originalFile, new File(documentDir));

        // Validate JSON size to prevent DoS attacks (5MB limit)
        if (annotationsJson == null || annotationsJson.length() > 5_000_000) {
            throw new IllegalArgumentException("Annotation data is missing or exceeds maximum size (5MB)");
        }

        // Parse Fabric.js JSON
        JsonNode pagesNode = objectMapper.readTree(annotationsJson);

        // Generate filename for annotated copy
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        String baseFilename = originalDoc.getFileName();
        if (baseFilename.toLowerCase().endsWith(".pdf")) {
            baseFilename = baseFilename.substring(0, baseFilename.length() - 4);
        }
        String newFileName = baseFilename + "_annotated_" + timestamp + ".pdf";
        newFileName = MiscUtils.sanitizeFileName(newFileName);
        File newFile = new File(documentDir + newFileName);

        // Load PDF, draw annotations, save
        try (PDDocument pdf = PDDocument.load(originalFile)) {
            for (JsonNode pageData : pagesNode) {
                JsonNode objectsNode = pageData.get("objects");
                // Skip pages with no annotations
                if (objectsNode == null || !objectsNode.isArray() || objectsNode.size() == 0) {
                    continue;
                }
                float scale = (float) pageData.get("scale").asDouble(1.5);
                int pageNum = pageData.get("page").asInt() - 1;
                if (pageNum >= 0 && pageNum < pdf.getNumberOfPages()) {
                    PDPage page = pdf.getPage(pageNum);
                    drawFabricObjects(pdf, page, objectsNode, scale);
                }
            }
            pdf.save(newFile);
        }

        // Get page count using existing utility
        int numPages = EDocUtil.getPDFPageCount(newFile.getAbsolutePath());

        // Create Document record using EDocUtil (follows SplitDocument2Action pattern)
        EDoc newDoc = new EDoc(
            originalDoc.getDescription() + " (Annotated)",
            originalDoc.getType(),
            newFileName,
            "",
            providerNo,
            providerNo,
            "",
            'A',
            DateFormatUtils.format(new Date(), "yyyy-MM-dd"),
            "",
            "",
            "demographic",
            demographicNo.toString(),
            numPages
        );
        newDoc.setDocPublic("0");
        newDoc.setContentType("application/pdf");

        String newDocId = EDocUtil.addDocumentSQL(newDoc);

        // Audit log
        LogAction.addLog(providerNo, LogConst.ADD, LogConst.CON_DOCUMENT,
                         newDocId, info.getIp(), demographicNo.toString());

        // Return the saved document
        return documentDao.getDocument(newDocId);
    }

    /**
     * Draws Fabric.js objects onto a PDF page using PDFBox.
     *
     * @param pdf PDDocument the PDF document being modified
     * @param page PDPage the page to draw on
     * @param objects JsonNode array of Fabric.js objects
     * @param scale float the scale factor used in the frontend canvas
     * @throws IOException if drawing operations fail
     */
    private void drawFabricObjects(PDDocument pdf, PDPage page, JsonNode objects, float scale)
            throws IOException {
        float pageHeight = page.getMediaBox().getHeight();

        // Collect images to process separately (avoids nested content streams)
        List<JsonNode> imageObjects = new ArrayList<>();

        // First pass: draw paths, text, rectangles with a single content stream
        try (PDPageContentStream stream = new PDPageContentStream(pdf, page,
                PDPageContentStream.AppendMode.APPEND, true, true)) {

            for (JsonNode obj : objects) {
                String type = obj.has("type") ? obj.get("type").asText() : "";

                switch (type) {
                    case "path":
                        drawPath(stream, obj, scale, pageHeight);
                        break;
                    case "i-text":
                    case "text":
                        drawText(stream, obj, scale, pageHeight);
                        break;
                    case "rect":
                        drawRect(stream, obj, scale, pageHeight);
                        break;
                    case "image":
                        imageObjects.add(obj);
                        break;
                    default:
                        // Skip unsupported types
                        break;
                }
            }
        }

        // Second pass: draw images (each needs its own content stream for the image XObject)
        for (JsonNode imageObj : imageObjects) {
            drawImage(pdf, page, imageObj, scale, pageHeight);
        }
    }

    /**
     * Draws a freehand path from Fabric.js path data.
     *
     * @param stream PDPageContentStream the content stream to draw on
     * @param obj JsonNode the Fabric.js path object
     * @param scale float the scale factor
     * @param pageHeight float the PDF page height for coordinate conversion
     * @throws IOException if drawing fails
     */
    private void drawPath(PDPageContentStream stream, JsonNode obj, float scale, float pageHeight)
            throws IOException {
        JsonNode path = obj.get("path");
        if (path == null || !path.isArray()) {
            return;
        }

        // Parse stroke color
        String strokeColor = obj.has("stroke") ? obj.get("stroke").asText() : "#000000";
        float[] rgb = parseColor(strokeColor);
        stream.setStrokingColor(rgb[0], rgb[1], rgb[2]);

        float strokeWidth = obj.has("strokeWidth") ? (float) obj.get("strokeWidth").asDouble() / scale : 1f;
        stream.setLineWidth(strokeWidth);

        // Get object position
        float left = obj.has("left") ? (float) obj.get("left").asDouble() : 0;
        float top = obj.has("top") ? (float) obj.get("top").asDouble() : 0;

        boolean started = false;
        for (JsonNode cmd : path) {
            if (!cmd.isArray() || cmd.size() < 3) {
                continue;
            }
            String command = cmd.get(0).asText();
            float x = (left + (float) cmd.get(1).asDouble()) / scale;
            float y = pageHeight - ((top + (float) cmd.get(2).asDouble()) / scale);

            if ("M".equals(command)) {
                stream.moveTo(x, y);
                started = true;
            } else if (("L".equals(command) || "Q".equals(command)) && started) {
                stream.lineTo(x, y);
            }
        }
        if (started) {
            stream.stroke();
        }
    }

    /**
     * Draws a text annotation.
     *
     * @param stream PDPageContentStream the content stream to draw on
     * @param obj JsonNode the Fabric.js text object
     * @param scale float the scale factor
     * @param pageHeight float the PDF page height for coordinate conversion
     * @throws IOException if drawing fails
     */
    private void drawText(PDPageContentStream stream, JsonNode obj, float scale, float pageHeight)
            throws IOException {
        String text = obj.has("text") ? obj.get("text").asText() : "";
        if (text.isEmpty()) {
            return;
        }

        float x = obj.has("left") ? (float) obj.get("left").asDouble() / scale : 0;
        float y = pageHeight - (obj.has("top") ? (float) obj.get("top").asDouble() / scale : 0);
        float fontSize = obj.has("fontSize") ? (float) obj.get("fontSize").asDouble() / scale : 12;

        String fillColor = obj.has("fill") ? obj.get("fill").asText() : "#000000";
        float[] rgb = parseColor(fillColor);

        stream.beginText();
        stream.setFont(PDType1Font.HELVETICA, fontSize);
        stream.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
        stream.newLineAtOffset(x, y - fontSize);

        // Handle special characters by filtering to WinAnsi encoding
        String safeText = filterToWinAnsi(text);
        stream.showText(safeText);
        stream.endText();
    }

    /**
     * Draws a rectangle (for highlights).
     *
     * @param stream PDPageContentStream the content stream to draw on
     * @param obj JsonNode the Fabric.js rect object
     * @param scale float the scale factor
     * @param pageHeight float the PDF page height for coordinate conversion
     * @throws IOException if drawing fails
     */
    private void drawRect(PDPageContentStream stream, JsonNode obj, float scale, float pageHeight)
            throws IOException {
        float x = obj.has("left") ? (float) obj.get("left").asDouble() / scale : 0;
        float y = obj.has("top") ? (float) obj.get("top").asDouble() / scale : 0;
        float width = obj.has("width") ? (float) obj.get("width").asDouble() / scale : 0;
        float height = obj.has("height") ? (float) obj.get("height").asDouble() / scale : 0;

        // Apply scale transforms if present
        if (obj.has("scaleX")) {
            width *= obj.get("scaleX").asDouble();
        }
        if (obj.has("scaleY")) {
            height *= obj.get("scaleY").asDouble();
        }

        // Convert to PDF coordinates (bottom-left origin)
        float pdfY = pageHeight - y - height;

        String fillColor = obj.has("fill") ? obj.get("fill").asText() : "rgba(255,255,0,0.3)";
        float[] rgba = parseColorWithAlpha(fillColor);

        // For semi-transparent fills (highlights)
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(rgba[3]);
        stream.setGraphicsStateParameters(gs);

        stream.setNonStrokingColor(rgba[0], rgba[1], rgba[2]);
        stream.addRect(x, pdfY, width, height);
        stream.fill();

        // Reset alpha
        gs.setNonStrokingAlphaConstant(1.0f);
        stream.setGraphicsStateParameters(gs);
    }

    /**
     * Draws an image (for signatures) using a separate content stream.
     *
     * @param pdf PDDocument the PDF document
     * @param page PDPage the page to draw on
     * @param obj JsonNode the Fabric.js image object
     * @param scale float the scale factor
     * @param pageHeight float the PDF page height for coordinate conversion
     * @throws IOException if drawing fails
     */
    private void drawImage(PDDocument pdf, PDPage page, JsonNode obj, float scale, float pageHeight)
            throws IOException {
        String src = obj.has("src") ? obj.get("src").asText() : "";
        if (src.isEmpty() || !src.startsWith("data:image")) {
            return;
        }

        // Parse base64 image data
        int commaIndex = src.indexOf(",");
        if (commaIndex < 0) {
            return;
        }

        byte[] imageBytes;
        try {
            String base64Data = src.substring(commaIndex + 1);
            imageBytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            MiscUtils.getLogger().warn("Failed to decode base64 image data, skipping image annotation", e);
            return;
        }

        PDImageXObject image;
        try {
            image = PDImageXObject.createFromByteArray(pdf, imageBytes, "annotation_image");
        } catch (IOException e) {
            MiscUtils.getLogger().warn("Failed to create image from decoded data, skipping image annotation", e);
            return;
        }

        float x = obj.has("left") ? (float) obj.get("left").asDouble() / scale : 0;
        float y = obj.has("top") ? (float) obj.get("top").asDouble() / scale : 0;
        float width = obj.has("width") ? (float) obj.get("width").asDouble() / scale : image.getWidth();
        float height = obj.has("height") ? (float) obj.get("height").asDouble() / scale : image.getHeight();

        // Apply Fabric.js scale if present
        if (obj.has("scaleX")) {
            width *= obj.get("scaleX").asDouble();
        }
        if (obj.has("scaleY")) {
            height *= obj.get("scaleY").asDouble();
        }

        float pdfY = pageHeight - y - height;

        try (PDPageContentStream stream = new PDPageContentStream(pdf, page,
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            stream.drawImage(image, x, pdfY, width, height);
        }
    }

    /**
     * Parses a CSS color string to RGB floats (0-1 range).
     * Handles hex (#RRGGBB, #RGB) and rgb(r,g,b) formats.
     *
     * @param color String the CSS color string
     * @return float[] array of [r, g, b] values in 0-1 range, defaults to black on parse failure
     */
    private float[] parseColor(String color) {
        if (color == null || color.isEmpty()) {
            return new float[]{0, 0, 0};
        }
        try {
            if (color.startsWith("#")) {
                String hex = color.substring(1);
                if (hex.length() == 3) {
                    hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
                }
                if (hex.length() >= 6) {
                    int r = Integer.parseInt(hex.substring(0, 2), 16);
                    int g = Integer.parseInt(hex.substring(2, 4), 16);
                    int b = Integer.parseInt(hex.substring(4, 6), 16);
                    return new float[]{r / 255f, g / 255f, b / 255f};
                }
            } else if (color.startsWith("rgb(") && color.endsWith(")")) {
                String inner = color.substring(4, color.length() - 1);
                String[] parts = inner.split(",");
                if (parts.length >= 3) {
                    return new float[]{
                        Float.parseFloat(parts[0].trim()) / 255f,
                        Float.parseFloat(parts[1].trim()) / 255f,
                        Float.parseFloat(parts[2].trim()) / 255f
                    };
                }
            }
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            MiscUtils.getLogger().warn("Failed to parse color value: " + color + ", using default black", e);
        }
        return new float[]{0, 0, 0};
    }

    /**
     * Parses a CSS color with alpha (rgba format).
     *
     * @param color String the CSS color string
     * @return float[] array of [r, g, b, a] values in 0-1 range
     */
    private float[] parseColorWithAlpha(String color) {
        if (color != null && color.startsWith("rgba(") && color.endsWith(")")) {
            String inner = color.substring(5, color.length() - 1);
            String[] parts = inner.split(",");
            if (parts.length >= 4) {
                return new float[]{
                    Float.parseFloat(parts[0].trim()) / 255f,
                    Float.parseFloat(parts[1].trim()) / 255f,
                    Float.parseFloat(parts[2].trim()) / 255f,
                    Float.parseFloat(parts[3].trim())
                };
            }
        }
        float[] rgb = parseColor(color);
        return new float[]{rgb[0], rgb[1], rgb[2], 1.0f};
    }

    /**
     * Filters text to only include characters in the WinAnsi encoding,
     * which is supported by PDType1Font.HELVETICA.
     *
     * @param text String the input text
     * @return String the filtered text safe for WinAnsi encoding
     */
    private String filterToWinAnsi(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            // WinAnsi encoding supports basic ASCII and some extended chars
            if (c >= 32 && c <= 126) {
                sb.append(c);
            } else if (c >= 160 && c <= 255) {
                sb.append(c);
            } else {
                // Replace unsupported characters with space
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
