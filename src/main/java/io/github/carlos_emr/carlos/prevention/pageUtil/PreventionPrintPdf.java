/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


/*
 * PreventionPrintPdf.java
 *
 * Created on March 12, 2007, 4:05 PM
 */

package io.github.carlos_emr.carlos.prevention.pageUtil;

import io.github.carlos_emr.carlos.commn.printing.PdfFonts;

import java.awt.Color;
import org.openpdf.text.*;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfWriter;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.clinic.ClinicData;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * Generates a multi-page PDF document containing a patient's prevention and immunization records.
 *
 * <p>Produces a two-column, paginated PDF with clinic letterhead, patient demographics,
 * and categorized prevention entries (immunizations and screenings). Each entry includes
 * date, age, status, result, comments, shot location, manufacturer, vaccine name, lot ID,
 * and dose information.</p>
 *
 * <p>Uses OpenPDF ({@code org.openpdf.*}) for PDF generation with {@link HeaderPageEvent}
 * for repeating page headers. It draws a {@link PreventionPrintLoader.Printout}, which holds
 * values read from the chart: the browser only chooses which prevention types to print. Status
 * codes are mapped to human-readable labels.</p>
 *
 * @see PreventionPrint2Action
 * @see PreventionPrintLoader
 * @see HeaderPageEvent
 * @since 2007-03-14
 */
public class PreventionPrintPdf {

    private int curPage;
    private float upperYcoord, upperYScreeningCoord;
    private ColumnText ct;
    private Document document;
    private PdfContentByte cb;
    private Phrase headerPhrase; // Stores the header phrase for height calculations

    private final int LINESPACING = 1;
    private final float LEADING = 12;
    private final float HEADER_LEADING = 1;
    /** Top margin around the page header, before its text lines. */
    private static final float HEADER_BASE_MARGIN = 32;
    /** Top margin per header text line (ColumnText's default leading). */
    private static final float HEADER_LINE_HEIGHT = 16;

    private final Map<String, String> readableStatuses = new HashMap<String, String>();
    private final Map<String, String> readableStatusesForHistoryTypeLayout = new HashMap<String, String>();

    // Creates a new instance of PreventionPrintPdf 
    public PreventionPrintPdf() {
        readableStatuses.put("0", "Completed");
        readableStatuses.put("1", "Refused");
        readableStatuses.put("2", "Ineligible");
        // This is for "Completed Externally" status
        readableStatuses.put("3", "Completed externally");
        // This is for the history type layout(smoking and phv) because they have different type of statuses
        readableStatusesForHistoryTypeLayout.put("0", "Yes");
        readableStatusesForHistoryTypeLayout.put("1", "Never");
        readableStatusesForHistoryTypeLayout.put("2", "Previous");
    }

    /**
     * Draws the printout as a two-column, paginated PDF.
     *
     * <p>Every value comes from {@code printout}, which {@link PreventionPrintLoader} read from the
     * chart; nothing is taken from the request. Immunization sections print first, under an
     * "Immunizations:" heading, and the screening sections follow on a new page under
     * "Screenings:" (a full printout says which part has no entries). A section that does not fit
     * continues in the next column or on the next page under a "(cont'd)" title. The header on
     * every page names the patient, and the MRP when {@link PreventionPrintLoader.Printout#mrpName()}
     * is set; the top margin grows by a line so that line is not cut off.
     *
     * @param printout the patient and the selected sections
     * @param outputStream the stream to write the PDF to; the caller buffers it
     * @throws IOException if a font cannot be created or the stream fails
     * @throws DocumentException if the printout has no sections or the PDF cannot be laid out
     */
    public void printPdf(PreventionPrintLoader.Printout printout, OutputStream outputStream) throws IOException, DocumentException {

        List<PreventionPrintLoader.Section> sections = printout.sections();
        if (sections.isEmpty()) {
            throw new DocumentException("No preventions were selected for printing");
        }
        boolean immunizationsOnly = printout.immunizationsOnly();
        Demographic demo = printout.patient();

        //Create the document we are going to write to
        document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, outputStream);
        document.setPageSize(PageSize.LETTER);
        // HeaderPageEvent draws the header inside the top margin and silently drops a line that
        // does not fit, which is how the MRP line used to disappear: size the margin to the lines.
        int headerLines = printout.mrpName() == null ? 3 : 4;
        document.setMargins(36, 36, HEADER_BASE_MARGIN + HEADER_LINE_HEIGHT * headerLines, 36);

        //Create the font we are going to print to       
        Font font = PdfFonts.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.BLACK);

        StringBuilder demoInfo = new StringBuilder(StringUtils.defaultString(demo.getSexDesc()))
                .append(" Age: ")
                .append(demo.getAge())
                .append(" (")
                .append(demo.getBirthDayAsString())
                .append(")")
                .append(" HIN: (")
                .append(StringUtils.defaultString(demo.getHcType()))
                .append(")");
        // A patient without a health card number or version used to print "null null".
        for (String part : new String[]{demo.getHin(), demo.getVer()}) {
            if (StringUtils.isNotBlank(part)) {
                demoInfo.append(' ').append(part.trim());
            }
        }

        //Header will be printed at top of every page beginning with p2
        String heading = immunizationsOnly ? "Immunizations" : "Immunizations and Screenings";
        Phrase titlePhrase = new Phrase(HEADER_LEADING, heading, PdfFonts.getFont(FontFactory.HELVETICA_BOLD, 20, Font.BOLD, Color.BLACK));
        titlePhrase.add(Chunk.NEWLINE);
        // Chart values go through PdfFonts.addText, which draws Chinese, Japanese, Korean and emoji
        // characters in bundled fallback faces; DejaVu alone silently dropped them.
        PdfFonts.addText(titlePhrase, demo.getFormattedName(), PdfFonts.getFont(FontFactory.HELVETICA, 14, Font.NORMAL, Color.BLACK));
        titlePhrase.add(Chunk.NEWLINE);
        PdfFonts.addText(titlePhrase, demoInfo.toString(), PdfFonts.getFont(FontFactory.HELVETICA, 12, Font.NORMAL, Color.BLACK));

        if (printout.mrpName() != null) {
            titlePhrase.add(Chunk.NEWLINE);
            PdfFonts.addText(titlePhrase, "MRP: " + printout.mrpName(), PdfFonts.getFont(FontFactory.HELVETICA, 12, Font.BOLD, Color.BLACK));
        }

        // Store header phrase, border flag, header padding, and border spacing for the header
        this.headerPhrase = titlePhrase;
        boolean hasBorder = true;
        float headerPadding = 20f;
        float borderSpacing = 5f;

        HeaderPageEvent header = new HeaderPageEvent(headerPhrase, hasBorder, headerPadding, borderSpacing);
        writer.setPageEvent(header);
        document.open();
        cb = writer.getDirectContent();

        //Clinic Address Information
        ClinicData clinicData = new ClinicData();
        clinicData.refreshClinicData();

        StringBuilder clinicAddrCont = new StringBuilder(StringUtils.defaultString(clinicData.getClinicCity())).append(", ")
                .append(StringUtils.defaultString(clinicData.getClinicProvince())).append(" ")
                .append(StringUtils.defaultString(clinicData.getClinicPostal()));

        Font clinicNameFont = PdfFonts.getFont(FontFactory.HELVETICA, 12, Font.BOLD, Color.BLACK);
        Font clinicFont = PdfFonts.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK);
        Paragraph clinicParagraph = new Paragraph(LEADING, "", clinicNameFont);
        PdfFonts.addText(clinicParagraph, clinicData.getClinicName(), clinicNameFont);
        clinicParagraph.add(Chunk.NEWLINE);
        PdfFonts.addText(clinicParagraph, clinicData.getClinicAddress(), clinicFont);
        clinicParagraph.add(Chunk.NEWLINE);
        PdfFonts.addText(clinicParagraph, clinicAddrCont.toString(), clinicFont);
        clinicParagraph.add(Chunk.NEWLINE);
        clinicParagraph.add(new Chunk("Ph.", PdfFonts.getFont(FontFactory.HELVETICA, 10, Font.BOLD, Color.BLACK)));
        PdfFonts.addText(clinicParagraph, clinicData.getClinicPhone(), clinicFont);
        clinicParagraph.add(new Chunk(" Fax.", PdfFonts.getFont(FontFactory.HELVETICA, 10, Font.BOLD, Color.BLACK)));
        PdfFonts.addText(clinicParagraph, clinicData.getClinicFax(), clinicFont);
        clinicParagraph.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(clinicParagraph);

        //get top y-coord for starting to print columns
        upperYcoord = document.top() - getHeaderHeight() - (clinicParagraph.getLeading() * 4f) - font.getCalculatedLeading(LINESPACING);

        //boolean values for headers of Immunizations and Screenings
        boolean isImmunizationHeaderAdded = false, isScreeningsHeaderAdded = false;

        //3 - Start the column
        ct = new ColumnText(cb);
        ct.setSimpleColumn(document.left(), document.bottom(), document.right() / 2f, upperYcoord);

        curPage = 1;

        boolean onColumnLeft = true;

        //now we can start to print the prevention data
        for (int idx = 0; idx < sections.size(); ++idx) {

            PreventionPrintLoader.Section section = sections.get(idx);
            String preventionHeader = section.preventionType();

            if (!section.screening() && !isImmunizationHeaderAdded && !immunizationsOnly) {
                //adding immunizations header before start adding immunizations into the pdf
                Paragraph iParagraph = addParagraph("\nImmunizations: \n", 12, Font.BOLD);
                document.add(iParagraph);

                //get top y-coord for starting to print columns
                upperYcoord = document.top() - getHeaderHeight() - (clinicParagraph.getLeading() * 4f) - iParagraph.getLeading() - font.getCalculatedLeading(LINESPACING);
                ct.setSimpleColumn(document.left(), document.bottom(), document.right() / 2f, upperYcoord);
                onColumnLeft = true;

                //immunizations header is added
                isImmunizationHeaderAdded = true;
            } else if (section.screening() && !isScreeningsHeaderAdded && !immunizationsOnly) {
                //checking immunizations header added or not. if not which means there no data of immunizations
                if (!isImmunizationHeaderAdded) {
                    Paragraph iParagraph = addParagraph("\nImmunizations: \n", 12, Font.BOLD);
                    Paragraph immNoDataParagraph = addParagraph("No immunization entries for this patient\n", 9, Font.NORMAL);

                    document.add(iParagraph);
                    document.add(immNoDataParagraph);

                    //get top y-coord for starting to print columns
                    upperYcoord = document.top() - getHeaderHeight() - (clinicParagraph.getLeading() * 4f) - iParagraph.getLeading() - immNoDataParagraph.getLeading() - font.getCalculatedLeading(LINESPACING);
                    ct.setSimpleColumn(document.left(), document.bottom(), document.right() / 2f, upperYcoord);
                }

                //Printing screenings from the new page
                onColumnLeft = true;
                upperYcoord = goToNewPage(headerPhrase, font);

                //Screenings header
                Paragraph sParagraph = addParagraph("\nScreenings: \n", 12, Font.BOLD);
                document.add(sParagraph);
                upperYcoord = document.top() - getHeaderHeight() - sParagraph.getLeading() - font.getCalculatedLeading(LINESPACING);
                ct.setSimpleColumn(document.left(), document.bottom(), document.right() / 2f, upperYcoord);
                isScreeningsHeaderAdded = true;
            }

            Font procHeaderFont = PdfFonts.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, Color.BLACK);
            Phrase procHeader = new Phrase(HEADER_LEADING, "", procHeaderFont);
            PdfFonts.addText(procHeader, "Prevention " + preventionHeader + "\n", procHeaderFont);
            ct.addText(procHeader);
            ct.setAlignment(Element.ALIGN_LEFT);
            ct.setIndent(0);
            ct.setFollowingIndent(0);
            float titleYPos = ct.getYLine();

            //check whether the Prevention Title can fit on the page
            boolean writeTitleOk = true;
            int status = ct.go(true);
            if (ColumnText.hasMoreText(status)) {
                writeTitleOk = false;
            }

            List<PreventionPrintLoader.Entry> entries = section.entries();
            for (int subIdx = 0; subIdx < entries.size(); ++subIdx) {
                PreventionPrintLoader.Entry entry = entries.get(subIdx);
                String procedureStatus = section.historyLayout()
                        ? readableStatusesForHistoryTypeLayout.get(entry.status())
                        : readableStatuses.get(entry.status());

                if (procedureStatus == null) {
                    procedureStatus = "N/A";
                }

                Phrase procedure = new Phrase(LEADING, "Date: ", PdfFonts.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK));
                // The "Date: " label opens the phrase, so the date itself goes in unlabelled.
                addLabelAndValue(procedure, null, entry.date(), font);
                addLabelAndValue(procedure, "Age: ", entry.age(), font);

                //Status is always printed: "N/A" stands in for an unknown code
                procedure.add("Status: ");
                procedure.add(new Chunk(procedureStatus, font));
                procedure.add(Chunk.NEWLINE);

                String procedureResult = entry.result();
                if (procedureResult != null && !procedureResult.isEmpty()) {
                    procedure.add("Result: ");
                    PdfFonts.addText(procedure, procedureResult, font);
                    String procedureReason = entry.reason();
                    if (procedureReason != null && !procedureReason.isEmpty()) {
                        PdfFonts.addText(procedure, " (" + procedureReason + ")", font);
                    } else if (procedureResult.equals("other")) {
                        procedure.add(new Chunk(" (no reason submitted)", font));
                    }
                    procedure.add(Chunk.NEWLINE);
                }

                addLabelAndValue(procedure, "Location of shot: ", entry.location(), font);
                addLabelAndValue(procedure, "Manufacturer: ", entry.manufacturer(), font);
                addLabelAndValue(procedure, "Name of Vaccine: ", entry.vaccineName(), font);
                addLabelAndValue(procedure, "Lot#: ", entry.lot(), font);
                addLabelAndValue(procedure, "Dose Administered: ", entry.dose(), font);
                String provider = entry.provider();
                addLabelAndValue(procedure, "Provider: ", provider == null || provider.isEmpty() ? "Unknown" : provider, font);

                //Check if immunizations or preventions related labels can fit
                ct.addText(procedure);
                ct.setAlignment(Element.ALIGN_LEFT);
                ct.setIndent(10);
                ct.setFollowingIndent(0);
                float detailYPos = ct.getYLine();
                status = ct.go(true);

                boolean writeDetailOk = true;
                if (ColumnText.hasMoreText(status)) {
                    writeDetailOk = false;
                }

                // Comments print once, in their own block under the details. The loader leaves them
                // out when prevention_show_comments is off; they used to print under a "Comments:"
                // label whatever the setting said, and a second time here when it was on.
                Phrase commentsPhrase = new Phrase(LEADING, "", font);
                String procedureComments = entry.comments();
                if (procedureComments != null && !procedureComments.isEmpty()) {
                    PdfFonts.addText(commentsPhrase, procedureComments, font);
                    commentsPhrase.add(Chunk.NEWLINE);
                }

                commentsPhrase.add(Chunk.NEWLINE);

                //Check if the comments can fit on the page
                ct.addText(commentsPhrase);
                ct.setAlignment(Element.ALIGN_JUSTIFIED);
                ct.setIndent(25);
                ct.setFollowingIndent(25);
                float commentYPos = ct.getYLine();
                status = ct.go(true);

                boolean writeCommentsOk = true;
                if (ColumnText.hasMoreText(status)) {
                    writeCommentsOk = false;
                }

                boolean proceedWrite = true;
                if (writeDetailOk && writeCommentsOk) {

                    //write on the same column and page
                    if (subIdx == 0) {
                        if (writeTitleOk) {
                            //we still need to write the title
                            ct.addText(procHeader);
                            ct.setAlignment(Element.ALIGN_LEFT);
                            ct.setYLine(titleYPos);
                            ct.setIndent(0);
                            ct.setFollowingIndent(0);
                            ct.go();
                        } else {
                            proceedWrite = false;
                        }
                    }

                    if (proceedWrite) {
                        //Adding immunizations or preventions related labels
                        ct.addText(procedure);
                        ct.setAlignment(Element.ALIGN_LEFT);
                        ct.setYLine(detailYPos);
                        ct.setIndent(10);
                        ct.setFollowingIndent(0);
                        ct.go();

                        //Comments
                        ct.addText(commentsPhrase);
                        ct.setAlignment(Element.ALIGN_JUSTIFIED);
                        ct.setYLine(commentYPos);
                        ct.setIndent(25);
                        ct.setFollowingIndent(25);
                        ct.go();
                    }
                } else {
                    proceedWrite = false;
                }

                // Two-column layout: fill left column first, overflow to right column,
                // then start a new page if both columns are full.
                if (!proceedWrite) {

                    if (onColumnLeft) {
                        //Print to the right column (i.e. we are printing to the current page)
                        onColumnLeft = false;
                        ct.setSimpleColumn(document.right() / 2f, document.bottom(), document.right(), upperYcoord);
                    } else {
                        //Print to the left column (i.e. we are starting a new page)
                        onColumnLeft = true;
                        upperYcoord = goToNewPage(headerPhrase, font);
                    }

                    //Title (if we are starting to print a new prevention, use the Prevention name as title, otherwise if we 
                    //are in the middle of printing a prevention that has multiple items, identify this as a continued prevention
                    if (subIdx != 0) {
                        Font contdFont = PdfFonts.getFont(FontFactory.HELVETICA_BOLD, 12, Font.ITALIC, Color.BLACK);
                        Phrase contdProcHeader = new Phrase(HEADER_LEADING, "", contdFont);
                        PdfFonts.addText(contdProcHeader, "Prevention " + preventionHeader + " (cont'd)\n", contdFont);
                        ct.setText(contdProcHeader);
                    } else {
                        ct.setText(procHeader);
                    }
                    ct.setAlignment(Element.ALIGN_LEFT);
                    ct.setIndent(0);
                    ct.setFollowingIndent(0);
                    ct.go();
                    titleYPos = ct.getYLine();


                    //Date and Age
                    ct.setText(procedure);
                    ct.setAlignment(Element.ALIGN_LEFT);
                    ct.setIndent(10);
                    ct.setFollowingIndent(0);
                    ct.go();

                    //Comments
                    ct.setText(commentsPhrase);
                    ct.setAlignment(Element.ALIGN_JUSTIFIED);
                    ct.setIndent(25);
                    ct.setFollowingIndent(25);
                    ct.go();

                }
            }

            // after adding immunizations data checking if there is screening data if not avalible add screening header with no data
            if (idx == sections.size() - 1 && !isScreeningsHeaderAdded && !immunizationsOnly) {
                upperYcoord = goToNewPage(headerPhrase, font);

                Paragraph sParagraph = addParagraph("\nScreenings: \n", 12, Font.BOLD);
                Paragraph sNoDataParagraph = addParagraph("No screening entries for this patient\n", 9, Font.NORMAL);
                document.add(sParagraph);
                document.add(sNoDataParagraph);
            }
        }

        //Make sure last page has the footer
        ColumnText.showTextAligned(cb, Phrase.ALIGN_CENTER, new Phrase("-" + curPage + "-"), document.right() / 2f, document.bottom() - (document.bottomMargin() / 2f), 0f);
        addPromoText();

        document.close();
    }

    /**
     * Calculates the approximate height of the header phrase.
     *
     * <p>This replaces {@code HeaderFooter.getHeight()} which was removed in newer
     * PDF library versions. Estimates height based on phrase leading for multi-line headers.</p>
     *
     * @return float the estimated header height in points, or 0 if no header phrase is set
     */
    private float getHeaderHeight() {
        if (headerPhrase == null) {
            return 0f;
        }
        // Estimate height based on the phrase leading
        // Using a reasonable default for multi-line headers
        return headerPhrase.getLeading() * 4f;
    }

    /**
     * Renders the page number footer on the current page and advances to a new page.
     *
     * @param headerPhrase Phrase the section header (used for height calculation)
     * @param font Font the current font (used for leading calculation)
     * @return float the Y coordinate of the top of the content area on the new page
     * @throws DocumentException if page creation fails
     * @throws IOException if font creation fails
     */
    private float goToNewPage(Phrase headerPhrase, Font font) throws DocumentException, IOException {
        ColumnText.showTextAligned(cb, Phrase.ALIGN_CENTER, new Phrase("-" + curPage + "-"), document.right() / 2f, document.bottom() - (document.bottomMargin() / 2f), 0f);
        addPromoText();
        float upperYcoord = document.top() - getHeaderHeight() - font.getCalculatedLeading(LINESPACING);
        document.newPage();
        ct.setSimpleColumn(document.left(), document.bottom(), document.right() / 2f, upperYcoord);
        curPage++;
        return upperYcoord;
    }

    /**
     * Creates a left-aligned paragraph with the given title and font style.
     *
     * @param title String the paragraph text
     * @param size float the font size in points
     * @param style int OpenPDF Font constant (e.g., Font.BOLD, Font.NORMAL)
     * @return Paragraph the configured paragraph
     */
    private Paragraph addParagraph(String title, float size, int style) throws DocumentException, IOException {
        Paragraph paragraph = new Paragraph(LEADING, title, PdfFonts.getFont(FontFactory.HELVETICA, size, style, Color.BLACK));
        paragraph.add(Chunk.NEWLINE);
        paragraph.setAlignment(Paragraph.ALIGN_LEFT);
        return paragraph;
    }

    /**
     * Appends one labelled line to a procedure entry; a missing or empty value adds nothing.
     *
     * @param procedure the entry's phrase
     * @param label the label, or {@code null} when the phrase already ends with it
     * @param value the value
     * @param font the font for the value text
     */
    private static void addLabelAndValue(Phrase procedure, String label, String value, Font font) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (label != null) {
            procedure.add(label);
        }
        PdfFonts.addText(procedure, value, font);
        procedure.add(Chunk.NEWLINE);
    }

    private void addPromoText() throws DocumentException, IOException {
        if (CarlosProperties.getInstance().getProperty("FORMS_PROMOTEXT") != null) {
            cb.beginText();
            cb.setFontAndSize(PdfFonts.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 6);
            cb.showTextAligned(PdfContentByte.ALIGN_CENTER, CarlosProperties.getInstance().getProperty("FORMS_PROMOTEXT"), PageSize.LETTER.getWidth() / 2, 5, 0);
            cb.endText();
        }
    }
}
