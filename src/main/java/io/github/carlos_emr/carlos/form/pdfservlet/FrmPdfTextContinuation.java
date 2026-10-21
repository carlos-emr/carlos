/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.pdfservlet;

import io.github.carlos_emr.carlos.commn.printing.PdfFonts;
import java.awt.Color;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;

/** Opt-in, same-page continuation for a fixed field whose complete value must remain readable.
 * Coordinates in the configuration are measured from the template's top edge, as in FrmPDFServlet. */
final class FrmPdfTextContinuation {
    private FrmPdfTextContinuation() { }

    static void render(PdfContentByte canvas, BaseFont baseFont, int style, String value,
                       String[] config, Rectangle page) throws DocumentException {
        if (config.length != 15 || !"left".equals(config[0].trim())) {
            throw new DocumentException("Invalid continued form field configuration");
        }
        if (value == null || value.isEmpty()) return;
        float x = number(config[1]), y = number(config[2]), size = number(config[5]);
        float inlineRight = number(config[7]);
        float left = number(config[8]), top = number(config[9]);
        float right = number(config[10]), bottom = number(config[11]), leading = number(config[12]);
        if (size <= 0 || leading < size || x < 0 || inlineRight <= x || inlineRight > page.getWidth()
                || y < size || y > page.getHeight() || left < 0 || right <= left || right > page.getWidth()
                || top < 0 || bottom <= top || bottom > page.getHeight()) {
            throw new DocumentException("Invalid continued form field bounds");
        }
        Font body = new Font(baseFont, size, style, Color.BLACK);
        Phrase text = completePhrase(value, body);
        boolean oneLine = value.indexOf('\n') < 0 && value.indexOf('\r') < 0 && value.indexOf('\t') < 0;
        if (oneLine && ColumnText.getWidth(text) <= inlineRight - x) {
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, text, x, page.getHeight() - y, 0);
            return;
        }

        Phrase reference = completePhrase(config[14].trim(), body);
        if (reference.isEmpty() || ColumnText.getWidth(reference) > inlineRight - x) {
            throw new DocumentException("Continued form field reference does not fit");
        }
        String label = config[13].trim();
        if (label.isEmpty()) throw new DocumentException("Continued form field label is missing");
        Phrase continuation = completePhrase(label + "\n" + value, body);
        ColumnText column = new ColumnText(null);
        column.setSimpleColumn(continuation, left, page.getHeight() - bottom,
                right, page.getHeight() - top, leading, Element.ALIGN_LEFT);
        // Simulation consumes column state: retain a fresh copy for the actual drawing.
        ColumnText drawing = ColumnText.duplicate(column);
        if (ColumnText.hasMoreText(column.go(true))) {
            throw new DocumentException("Complete form field does not fit its continuation area");
        }
        // Only add the cross-reference once the complete labelled value is known to fit.
        ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, reference, x, page.getHeight() - y, 0);
        drawing.setCanvas(canvas);
        if (ColumnText.hasMoreText(drawing.go())) {
            throw new DocumentException("Complete form field could not be rendered");
        }
    }

    private static Phrase completePhrase(String value, Font font) throws DocumentException {
        Phrase phrase = new Phrase();
        PdfFonts.addText(phrase, value, font);
        // Shared fallback marks unavailable glyphs with U+FFFD. A legal name must be complete,
        // so refuse rather than successfully emit a substituted or silently removed character.
        if (!value.equals(phrase.getContent())) {
            throw new DocumentException("Form field contains characters unavailable in the embedded fonts");
        }
        return phrase;
    }

    private static float number(String value) throws DocumentException {
        try {
            float number = Float.parseFloat(value.trim());
            if (Float.isFinite(number)) return number;
        } catch (NumberFormatException ignored) {
            // A malformed trusted layout must fail rather than silently omit a submitted value.
        }
        throw new DocumentException("Invalid continued form field coordinate");
    }
}
