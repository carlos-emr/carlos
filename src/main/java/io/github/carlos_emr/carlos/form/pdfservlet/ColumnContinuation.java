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
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;

import io.github.carlos_emr.carlos.commn.printing.PdfFonts;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Keeps the text of an opted-in print-config column from being cut off.
 *
 * <p>{@link FrmPDFServlet} prints a column field
 * ({@code name : align, llx, top, 0, font, size, _, urx, bottom, leading}) into its box and drops
 * whatever does not fit. A form opts one column in by adding {@code key=value} values after the
 * leading; values cannot contain commas:</p>
 * <ul>
 *   <li>{@code continue=<heading>} (required, first): what does not fit continues on extra pages
 *       after the form's own pages, under this heading;</li>
 *   <li>{@code marker=<text>}: printed at the right end of the box's last line when the text
 *       continues; {@code {page}} becomes the page it continues on;</li>
 *   <li>{@code patient=<text>}: the line at the top of every extra page that says whose page it is;
 *       each {@code {name}} becomes that print value;</li>
 *   <li>{@code fallback=<size>/<leading>/<top>}: a smaller font size, leading and top to try when
 *       the text does not fit the box at the column's own size.</li>
 * </ul>
 *
 * <p>The extra pages repeat the form's {@code printer_info} line, so every page says "Page x of y".
 * The pages are planned before anything is drawn ({@link #plan}), which is what lets page 1 count
 * them. No form's print config other than the Patient Encounter Worksheet's has more than ten
 * values on a line, so every other form prints exactly as before.</p>
 *
 * @since 2026-10-15
 */
final class ColumnContinuation {

    private static final Logger log = MiscUtils.getLogger();

    static final String OPT_IN = "continue=";
    private static final int OPT_IN_INDEX = 10;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    // Extra-page layout, in points down from the top of the page, like the print config.
    private static final float PATIENT_LINE = 44;
    private static final float FIRST_HEADING = 70;
    private static final float BOTTOM_MARGIN = 36;
    private static final float HEADING_SIZE = 11;
    private static final float PATIENT_SIZE = 9;
    private static final float HEADING_TO_TEXT = 6;
    private static final float MARKER_GAP = 4;

    private final List<Column> columns;
    private final Properties props;
    private final float pageHeight;
    private final int firstPage;
    private final String[] printerInfo;
    private int pages;

    private ColumnContinuation(List<Column> columns, Properties props, float pageHeight, int firstPage,
                               String[] printerInfo) {
        this.columns = columns;
        this.props = props;
        this.pageHeight = pageHeight;
        this.firstPage = firstPage;
        this.printerInfo = printerInfo;
    }

    /**
     * Finds the opted-in columns of every page's print config and works out, without drawing, how
     * many extra pages their text needs and where each column's text continues.
     *
     * @param printCfg   the print config of each template page (may be {@code null})
     * @param cfgFileNo  how many of {@code printCfg} are in use
     * @param props      the print values, for the patient line
     * @param value      a field's print value, looked up as {@link FrmPDFServlet} does
     * @param pageHeight the template page height
     * @param firstPage  the page number the first extra page gets
     * @return the plan; with no opted-in column, or nothing to continue, it adds no pages
     */
    static ColumnContinuation plan(Properties[] printCfg, int cfgFileNo, Properties props,
                                   UnaryOperator<String> value, float pageHeight, int firstPage)
            throws DocumentException, IOException {
        List<Column> columns = new ArrayList<>();
        String[] printerInfo = null;
        for (int page = 1; page <= cfgFileNo; page++) {
            for (String name : printCfg[page - 1].stringPropertyNames()) {
                String[] values = printCfg[page - 1].getProperty(name).split(",");
                if (values.length > OPT_IN_INDEX && values[OPT_IN_INDEX].trim().startsWith(OPT_IN)) {
                    try {
                        columns.add(Column.parse(page, name, values, value.apply(name)));
                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                        // A malformed line prints as a plain column, as it would have before.
                        log.warn("Print field {} has a malformed continuation setting; printing it without", name, e);
                    }
                }
            }
            if (printerInfo == null && printCfg[page - 1].getProperty("printer_info") != null) {
                printerInfo = printCfg[page - 1].getProperty("printer_info").split(",");
            }
        }
        columns.sort(Comparator.comparingInt((Column c) -> c.templatePage).thenComparingDouble(c -> c.top));
        ColumnContinuation continuation =
                new ColumnContinuation(columns, props, pageHeight, firstPage, printerInfo);
        // Twice: the first pass finds the page each column continues on, and the second lays the
        // boxes out again with that page number in their markers (a longer number can shorten the
        // box's last line).
        for (int pass = 0; pass < 2 && !columns.isEmpty(); pass++) {
            for (Column column : columns) {
                column.rest = continuation.fillBox(column, null, true);
            }
            continuation.pages = continuation.flow(null, null, null, true);
        }
        return continuation;
    }

    /** How many extra pages the opted-in columns need. */
    int pages() {
        return pages;
    }

    /** Whether this field of this template page is an opted-in column that this class prints. */
    boolean prints(int templatePage, String name) {
        return find(templatePage, name) != null;
    }

    /**
     * Prints an opted-in column into its box on the current page, keeping what does not fit for
     * {@link #writePages}.
     */
    void fillBox(int templatePage, String name, PdfContentByte canvas) throws DocumentException {
        Column column = find(templatePage, name);
        column.rest = fillBox(column, canvas, false);
    }

    /**
     * Adds the extra pages after the form's own pages: each continuing column's heading and text,
     * in page and top order, under the printed-on line and the patient line.
     *
     * @param printerInfoText the printed-on line for a page number
     */
    void writePages(Document document, PdfContentByte canvas, IntFunction<String> printerInfoText)
            throws DocumentException, IOException {
        if (pages > 0) {
            int written = flow(document, canvas, printerInfoText, false);
            if (written != pages) {
                log.warn("Continuation pages differ from the plan: planned {}, wrote {}", pages, written);
            }
        }
    }

    private Column find(int templatePage, String name) {
        for (Column column : columns) {
            if (column.templatePage == templatePage && column.name.equals(name)) {
                return column;
            }
        }
        return null;
    }

    /**
     * Prints the column's text into its box, at the column's own size or else the fallback size,
     * and returns what is left, or {@code null} when it all fits. When text is left, the box's last
     * line is shortened to make room for the marker at its right end.
     */
    private ColumnText fillBox(Column column, PdfContentByte canvas, boolean simulate) throws DocumentException {
        if (column.text.isEmpty()) {
            return null;
        }
        Fit fit = column.own;
        if (!fits(column, fit) && column.fallback != null) {
            fit = column.fallback;
        }
        ColumnText text = text(column, fit, canvas);
        if (fits(column, fit)) {
            text.go(simulate);
            return null;
        }
        // All but the last line, then the last line short enough to leave room for the marker.
        String marker = column.marker(firstPage + startPageOffset(column));
        float markerWidth = marker.isEmpty() ? 0 : width(fit.font, marker) + MARKER_GAP;
        text.setSimpleColumn(column.llx, pageHeight - (column.bottom - fit.leading), column.urx,
                pageHeight - fit.top, fit.leading, column.align);
        text.go(simulate);
        float lastLineTop = text.getYLine();
        text.setSimpleColumn(column.llx, pageHeight - column.bottom, column.urx - markerWidth, lastLineTop,
                fit.leading, column.align);
        int status = text.go(simulate);
        if (!ColumnText.hasMoreText(status)) {
            return null;
        }
        if (!simulate && !marker.isEmpty()) {
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT, new Phrase(marker, fit.font), column.urx,
                    lastLineTop - fit.leading, 0);
        }
        column.restLeading = fit.leading;
        return text;
    }

    private boolean fits(Column column, Fit fit) throws DocumentException {
        return !ColumnText.hasMoreText(text(column, fit, null).go(true));
    }

    private ColumnText text(Column column, Fit fit, PdfContentByte canvas) {
        ColumnText text = new ColumnText(canvas);
        text.setSimpleColumn(column.llx, pageHeight - column.bottom, column.urx, pageHeight - fit.top, fit.leading,
                column.align);
        text.setText(new Phrase(12, column.text, fit.font));
        return text;
    }

    /** Pages before the one this column's text continues on, as planned; 0 while planning. */
    private int startPageOffset(Column column) {
        return column.startPage < 0 ? 0 : column.startPage;
    }

    /**
     * Lays out every continuing column's heading and text on extra pages. With {@code simulate} it
     * only measures (no document or canvas is needed) and records where each column starts.
     *
     * @return the number of extra pages used
     */
    private int flow(Document document, PdfContentByte canvas, IntFunction<String> printerInfoText, boolean simulate)
            throws DocumentException, IOException {
        Font headingFont = new Font(PdfFonts.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED),
                HEADING_SIZE);
        float bottom = pageHeight - BOTTOM_MARGIN;
        int page = 0;
        float y = 0;
        for (Column column : columns) {
            if (column.rest == null) {
                continue;
            }
            if (page == 0 || y + HEADING_TO_TEXT + 2 * column.restLeading > bottom) {
                page = newPage(document, canvas, printerInfoText, page, column, simulate);
                y = FIRST_HEADING;
            }
            if (simulate) {
                column.startPage = page - 1;
            } else {
                ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(column.heading, headingFont),
                        column.llx, pageHeight - y, 0);
            }
            float top = y + HEADING_TO_TEXT;
            while (true) {
                column.rest.setSimpleColumn(column.llx, pageHeight - bottom, column.urx, pageHeight - top,
                        column.restLeading, column.align);
                int status = column.rest.go(simulate);
                if (!ColumnText.hasMoreText(status)) {
                    y = pageHeight - column.rest.getYLine() + 2 * column.restLeading + HEADING_SIZE;
                    break;
                }
                if (column.rest.getLinesWritten() == 0 && top == FIRST_HEADING - HEADING_SIZE) {
                    log.warn("Continued text of {} does not fit an empty page; the rest is not printed", column.name);
                    break;
                }
                page = newPage(document, canvas, printerInfoText, page, column, simulate);
                top = FIRST_HEADING - HEADING_SIZE;
            }
        }
        return page;
    }

    private int newPage(Document document, PdfContentByte canvas, IntFunction<String> printerInfoText, int page,
                        Column column, boolean simulate) throws DocumentException, IOException {
        int next = page + 1;
        if (!simulate) {
            document.newPage();
            if (printerInfo != null && printerInfo.length >= 6) {
                Font font = FrmPDFServlet.cfgFont(printerInfo[4]).columnFont(Integer.parseInt(printerInfo[5].trim()));
                ColumnText.showTextAligned(canvas, align(printerInfo[0]),
                        new Phrase(printerInfoText.apply(firstPage + next - 1), font),
                        Float.parseFloat(printerInfo[1].trim()), pageHeight - Float.parseFloat(printerInfo[2].trim()), 0);
            }
            String patient = column.patient(props);
            if (!patient.isEmpty()) {
                Font font = new Font(column.font.columnFont(column.size).getBaseFont(), PATIENT_SIZE);
                ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(patient, font), column.llx,
                        pageHeight - PATIENT_LINE, 0);
            }
        }
        return next;
    }

    private static float width(Font font, String text) {
        return font.getBaseFont().getWidthPoint(text, font.getSize());
    }

    private static int align(String value) {
        String align = value.trim();
        return align.equals("left") ? Element.ALIGN_LEFT
                : align.equals("right") ? Element.ALIGN_RIGHT : Element.ALIGN_CENTER;
    }

    /** One way to set the column's text: font, leading and the top of the first line's box. */
    private record Fit(Font font, float leading, float top) {
    }

    /** An opted-in column, parsed from its print-config line. */
    private static final class Column {
        final int templatePage;
        final String name;
        final int align;
        final float llx;
        final float top;
        final float urx;
        final float bottom;
        final float leading;
        final FrmPDFServlet.CfgFont font;
        final int size;
        final String heading;
        final String marker;
        final String patient;
        final Fit own;
        final Fit fallback;
        final String text;
        ColumnText rest;
        float restLeading;
        int startPage = -1;

        private Column(int templatePage, String name, String[] values, String text,
                       FrmPDFServlet.CfgFont font) {
            this.templatePage = templatePage;
            this.name = name;
            this.align = ColumnContinuation.align(values[0]);
            this.llx = Float.parseFloat(values[1].trim());
            this.top = Float.parseFloat(values[2].trim());
            this.font = font;
            this.size = Integer.parseInt(values[5].trim());
            this.urx = Float.parseFloat(values[7].trim());
            this.bottom = Float.parseFloat(values[8].trim());
            this.leading = Float.parseFloat(values[9].trim());
            this.own = new Fit(font.columnFont(size), leading, top);
            this.text = text;
            String heading = "";
            String marker = "";
            String patient = "";
            Fit fallback = null;
            for (int i = OPT_IN_INDEX; i < values.length; i++) {
                String value = values[i].trim();
                int eq = value.indexOf('=');
                String key = eq < 0 ? value : value.substring(0, eq);
                String setting = eq < 0 ? "" : value.substring(eq + 1).trim();
                switch (key) {
                    case "continue" -> heading = setting;
                    case "marker" -> marker = setting;
                    case "patient" -> patient = setting;
                    case "fallback" -> {
                        String[] parts = setting.split("/");
                        fallback = new Fit(font.columnFont(Integer.parseInt(parts[0].trim())),
                                Float.parseFloat(parts[1].trim()), Float.parseFloat(parts[2].trim()));
                    }
                    default -> log.warn("Unknown continuation setting '{}' on print field {}", key, name);
                }
            }
            this.heading = heading;
            this.marker = marker;
            this.patient = patient;
            this.fallback = fallback;
        }

        static Column parse(int templatePage, String name, String[] values, String text)
                throws DocumentException, IOException {
            return new Column(templatePage, name, values, text, FrmPDFServlet.cfgFont(values[4]));
        }

        String marker(int page) {
            return marker.replace("{page}", String.valueOf(page));
        }

        String patient(Properties props) {
            Matcher m = PLACEHOLDER.matcher(patient);
            StringBuilder line = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(line, Matcher.quoteReplacement(props.getProperty(m.group(1), "")));
            }
            m.appendTail(line);
            return line.toString().trim();
        }
    }
}
