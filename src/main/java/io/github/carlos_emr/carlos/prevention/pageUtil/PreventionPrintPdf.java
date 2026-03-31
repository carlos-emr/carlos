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

import java.awt.Color;
import org.openpdf.text.*;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfWriter;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.clinic.ClinicData;
import io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.*;

/**
 * Generates a multi-page PDF document containing a patient's prevention and immunization records.
 *
 * <p>Produces a two-column, paginated PDF with clinic letterhead, patient demographics,
 * and categorized prevention entries (immunizations and screenings). Each entry includes
 * date, age, status, result, comments, shot location, manufacturer, vaccine name, lot ID,
 * and dose information.</p>
 *
 * <p>Uses OpenPDF ({@code org.openpdf.*}) for PDF generation with {@link HeaderPageEvent}
 * for repeating page headers. Prevention categories are configured via
 * {@link PreventionDisplayConfig} and status codes are mapped to human-readable labels.</p>
 *
 * @see PreventionPrint2Action
 * @see HeaderPageEvent
 * @see PreventionDisplayConfig
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
     * Generates a prevention PDF and writes it directly to the HTTP response.
     *
     * <p>Sets the response content type to {@code application/pdf} with a download
     * attachment filename of {@code Prevention.pdf}.</p>
     *
     * @param request HttpServletRequest containing prevention data parameters
     * @param response HttpServletResponse to write the PDF output to
     * @throws IOException if an I/O error occurs writing to the response
     * @throws DocumentException if a PDF generation error occurs
     */
    public void printPdf(HttpServletRequest request, HttpServletResponse response) throws IOException, DocumentException {
        response.setContentType("application/pdf");  //octet-stream
        response.setHeader("Content-Disposition", "attachment; filename=\"Prevention.pdf\"");
        String[] headerIds = request.getParameterValues("printHP");
        printPdf(headerIds, request, response.getOutputStream());
    }

    /**
     * Generates the full prevention PDF document to the given output stream.
     *
     * <p>Separates prevention entries into immunizations and screenings, builds a two-column
     * layout with automatic pagination, and includes clinic address, patient demographics,
     * and page numbering. Each prevention entry includes status, dates, vaccine details,
     * and optional comments.</p>
     *
     * @param headerIds1 String[] array of prevention header IDs to include in the PDF
     * @param request HttpServletRequest containing all prevention procedure parameters
     * @param outputStream OutputStream to write the generated PDF to
     * @throws IOException if an I/O error occurs
     * @throws DocumentException if the PDF document is empty or generation fails
     */
    public void printPdf(String[] headerIds1, HttpServletRequest request, OutputStream outputStream) throws IOException, DocumentException {

        //make sure we have data to print      
        if (headerIds1 == null) {
            throw new DocumentException();
        }

        String[] headerIds = null;
        List<String> validImmunizationsIds = new ArrayList<String>();
        List<String> validScreeningsIds = new ArrayList<String>();
        // Separate preventions into immunizations vs screenings based on their
        // headingName from PreventionDisplayConfig — they render in separate sections.
        PreventionDisplayConfig pdc = PreventionDisplayConfig.getInstance();
        for (int x = 0; x < headerIds1.length; x++) {
            String pHeader = request.getParameter("preventionHeader" + headerIds1[x]);
            HashMap<String, String> prev = pdc.getPrevention(pHeader);
            if (prev != null && prev.get("headingName") != null && "Screenings".equals(prev.get("headingName"))) {
                validScreeningsIds.add(headerIds1[x]);
            } else {
                validImmunizationsIds.add(headerIds1[x]);
            }
        }
        if ("false".equals(request.getParameter("immunizationOnly"))) {
            validImmunizationsIds.addAll(validScreeningsIds);
        }
        headerIds = validImmunizationsIds.toArray(new String[validImmunizationsIds.size()]);

        if (headerIds1 == null)
            throw new DocumentException();


        String demoNo = request.getParameter("demographicNo");
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic demo = demographicManager.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo);

        if (demo == null)
            throw new DocumentException();

        //Create the document we are going to write to
        document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, outputStream);
        document.setPageSize(PageSize.LETTER);
        document.setMargins(36, 36, 80, 36);

        //Create the font we are going to print to       
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.BLACK);

        StringBuilder demoInfo = new StringBuilder(demo.getSexDesc())
                .append(" Age: ")
                .append(demo.getAge())
                .append(" (")
                .append(demo.getBirthDayAsString())
                .append(")")
                .append(" HIN: (")
                .append(demo.getHcType())
                .append(") ")
                .append(demo.getHin())
                .append(" ")
                .append(demo.getVer());

        //Header will be printed at top of every page beginning with p2
        String heading = ("true".equals(request.getParameter("immunizationOnly"))) ? "Immunizations" : "Immunizations and Screenings";
        Phrase titlePhrase = new Phrase(HEADER_LEADING, heading, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.BOLD, Color.BLACK));
        titlePhrase.add(Chunk.NEWLINE);
        titlePhrase.add(new Chunk(demo.getFormattedName(), FontFactory.getFont(FontFactory.HELVETICA, 14, Font.NORMAL, Color.BLACK)));
        titlePhrase.add(Chunk.NEWLINE);
        titlePhrase.add(new Chunk(demoInfo.toString(), FontFactory.getFont(FontFactory.HELVETICA, 12, Font.NORMAL, Color.BLACK)));

        String mrp = request.getParameter("mrp");
        if (mrp != null && CarlosProperties.getInstance().getBooleanProperty("mrp_model", "yes")) {
            Properties prop = (Properties) request.getSession().getAttribute("providerBean");
            titlePhrase.add(Chunk.NEWLINE);
            titlePhrase.add(new Chunk("MRP: " + prop.getProperty(mrp, "unknown"), FontFactory.getFont(FontFactory.HELVETICA, 12, Font.BOLD, Color.BLACK)));
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

        StringBuilder clinicAddrCont = new StringBuilder(clinicData.getClinicCity()).append(", ").append(clinicData.getClinicProvince()).append(" ").append(clinicData.getClinicPostal());

        Paragraph clinicParagraph = new Paragraph(LEADING, clinicData.getClinicName(), FontFactory.getFont(FontFactory.HELVETICA, 12, Font.BOLD, Color.BLACK));
        clinicParagraph.add(Chunk.NEWLINE);
        clinicParagraph.add(new Chunk(clinicData.getClinicAddress(), FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK)));
        clinicParagraph.add(Chunk.NEWLINE);
        clinicParagraph.add(new Chunk(clinicAddrCont.toString(), FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK)));
        clinicParagraph.add(Chunk.NEWLINE);
        clinicParagraph.add(new Chunk("Ph.", FontFactory.getFont(FontFactory.HELVETICA, 10, Font.BOLD, Color.BLACK)));
        clinicParagraph.add(new Chunk(clinicData.getClinicPhone(), FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK)));
        clinicParagraph.add(new Chunk(" Fax.", FontFactory.getFont(FontFactory.HELVETICA, 10, Font.BOLD, Color.BLACK)));
        clinicParagraph.add(new Chunk(clinicData.getClinicFax(), FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK)));
        clinicParagraph.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(clinicParagraph);

        //get top y-coord for starting to print columns
        upperYcoord = document.top() - getHeaderHeight() - (clinicParagraph.getLeading() * 4f) - font.getCalculatedLeading(LINESPACING);

        int subIdx;
        String preventionHeader, procedureAge, procedureDate, procedureStatus, procedureResult, procedureReason, procedureComments, procedureLocationOfShot,
                procedureManufacturer, procedureNameOfVaccine, procedureLotID, procedureDoseAdministered;

        //boolean values for headers of Immunizations and Screenings
        boolean isImmunizationHeaderAdded = false, isScreeningsHeaderAdded = false;

        //1 - obtain number of lines of incoming prevention data
        boolean showComments = CarlosProperties.getInstance().getBooleanProperty("prevention_show_comments", "true");

        //3 - Start the column
        ct = new ColumnText(cb);
        ct.setSimpleColumn(document.left(), document.bottom(), document.right() / 2f, upperYcoord);

        curPage = 1;

        boolean onColumnLeft = true;

        //now we can start to print the prevention data
        for (int idx = 0; idx < headerIds.length; ++idx) {

            preventionHeader = request.getParameter("preventionHeader" + headerIds[idx]);
            //check if this is an immunization!
            HashMap<String, String> prev = pdc.getPrevention(preventionHeader);

            if (prev != null && ((prev.get("headingName") != null && !"Screenings".equals(prev.get("headingName")) || prev.get("headingName") == null))
                    && !isImmunizationHeaderAdded && "false".equals(request.getParameter("immunizationOnly"))) {
                //adding immunizations header before start adding immunizations into the pdf
                Paragraph iParagraph = addParagraph("\nImmunizations: \n", 12, Font.BOLD);
                document.add(iParagraph);

                //get top y-coord for starting to print columns
                upperYcoord = document.top() - getHeaderHeight() - (clinicParagraph.getLeading() * 4f) - iParagraph.getLeading() - font.getCalculatedLeading(LINESPACING);
                ct.setSimpleColumn(document.left(), document.bottom(), document.right() / 2f, upperYcoord);
                onColumnLeft = true;

                //immunizations header is added
                isImmunizationHeaderAdded = true;
            } else if (prev != null && prev.get("headingName") != null && "Screenings".equals(prev.get("headingName"))
                    && !isScreeningsHeaderAdded && "false".equals(request.getParameter("immunizationOnly"))) {
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

            Phrase procHeader = new Phrase(HEADER_LEADING, "Prevention " + preventionHeader + "\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, Color.BLACK));
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

            subIdx = 0;

            while ((procedureAge = request.getParameter("preventProcedureAge" + headerIds[idx] + "-" + subIdx)) != null) {
                procedureStatus = request.getParameter("preventProcedureStatus" + headerIds[idx] + "-" + subIdx);
                if (prev != null && prev.get("layout") != null && "history".equals(prev.get("layout"))) {
                    procedureStatus = readableStatusesForHistoryTypeLayout.get(procedureStatus);
                } else {
                    procedureStatus = readableStatuses.get(procedureStatus);
                }

                if (procedureStatus == null) {
                    procedureStatus = "N/A";
                }

                Phrase procedure = new Phrase(LEADING, "Date: ", FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK));
                addLabelsAndValuesToProcedure(request, procedure, "preventProcedureDate", "Date: ", headerIds, idx, subIdx, font);
                addLabelsAndValuesToProcedure(request, procedure, "preventProcedureAge", "Age: ", headerIds, idx, subIdx, font);

                //We are not adding status using "addLabelsAndValuesToProcedure" function because here "procedureStatus" is itself labelValue 
                procedure.add("Status: ");
                procedure.add(new Chunk(procedureStatus, font));
                procedure.add(Chunk.NEWLINE);

                procedureResult = request.getParameter("preventProcedureResult" + headerIds[idx] + "-" + subIdx);
                if (procedureResult != null && !procedureResult.isEmpty()) {
                    procedure.add("Result: ");
                    procedure.add(new Chunk(procedureResult, font));
                    procedureReason = request.getParameter("preventProcedureReason" + headerIds[idx] + "-" + subIdx);
                    if (procedureReason != null && !procedureReason.isEmpty()) {
                        procedure.add(new Chunk(" (" + procedureReason + ")", font));
                    } else if (procedureResult.equals("other")) {
                        procedure.add(new Chunk(" (no reason submitted)", font));
                    }
                    procedure.add(Chunk.NEWLINE);
                }

                addLabelsAndValuesToProcedure(request, procedure, "preventProcedureComments", "Comments: ", headerIds, idx, subIdx, font);
                addLabelsAndValuesToProcedure(request, procedure, "preventProcedureLocationOfShot", "Location of shot: ", headerIds, idx, subIdx, font);
                addLabelsAndValuesToProcedure(request, procedure, "preventProcedureManufacture", "Manufacturer: ", headerIds, idx, subIdx, font);
                addLabelsAndValuesToProcedure(request, procedure, "preventProcedureNameOfVaccine", "Name of Vaccine: ", headerIds, idx, subIdx, font);
                addLabelsAndValuesToProcedure(request, procedure, "preventProcedureLotID", "Lot#: ", headerIds, idx, subIdx, font);
                addLabelsAndValuesToProcedure(request, procedure, "preventProcedureDoseAdministered", "Dose Administered: ", headerIds, idx, subIdx, font);
                addLabelsAndValuesToProcedure(request, procedure, "preventProcedureProvider", "Provider: ", headerIds, idx, subIdx, font);

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

                //Comments
                Phrase commentsPhrase = new Phrase(LEADING, "", font);
                procedureComments = request.getParameter("preventProcedureComments" + headerIds[idx] + "-" + subIdx);
                if (showComments && procedureComments != null && !procedureComments.isEmpty()) {
                    commentsPhrase.add(procedureComments);
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
                        Phrase contdProcHeader = new Phrase(HEADER_LEADING, "Prevention " + preventionHeader + " (cont'd)\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.ITALIC, Color.BLACK));
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

                ++subIdx;
            }

            // after adding immunizations data checking if there is screening data if not avalible add screening header with no data
            if (idx == headerIds.length - 1 && !isScreeningsHeaderAdded
                    && "false".equals(request.getParameter("immunizationOnly"))) {
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
        Paragraph paragraph = new Paragraph(LEADING, title, FontFactory.getFont(FontFactory.HELVETICA, size, style, Color.BLACK));
        paragraph.add(Chunk.NEWLINE);
        paragraph.setAlignment(Paragraph.ALIGN_LEFT);
        return paragraph;
    }

    /**
     * Adds a label-value pair to a prevention procedure entry. Omits the label text
     * for date fields (preventProcedureDate); defaults provider to "Unknown" when missing.
     *
     * @param request HttpServletRequest containing the form parameter values
     * @param procedure Phrase the procedure phrase to append content to
     * @param labelParameter String the request parameter name prefix
     * @param label String the display label text
     * @param headerIds String[] the prevention header IDs array
     * @param idx int the current prevention index
     * @param subIdx int the sub-procedure index
     * @param font Font the font for the value text
     */
    private void addLabelsAndValuesToProcedure(HttpServletRequest request, Phrase procedure, String labelParameter, String label, String[] headerIds,
                                               int idx, int subIdx, Font font) throws DocumentException, IOException {
        String labelValue = request.getParameter(labelParameter + headerIds[idx] + "-" + subIdx);
        if (labelValue != null && !labelValue.isEmpty()) {
            if (!labelParameter.equals("preventProcedureDate")) {
                procedure.add(label);
            }
            procedure.add(new Chunk(labelValue, font));
            procedure.add(Chunk.NEWLINE);
        } else {
            if (labelParameter.equalsIgnoreCase("preventProcedureProvider")) {
                procedure.add(label);
                procedure.add(new Chunk("Unknown", font));
                procedure.add(Chunk.NEWLINE);
            }
        }
    }

    private void addPromoText() throws DocumentException, IOException {
        if (CarlosProperties.getInstance().getProperty("FORMS_PROMOTEXT") != null) {
            cb.beginText();
            cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 6);
            cb.showTextAligned(PdfContentByte.ALIGN_CENTER, CarlosProperties.getInstance().getProperty("FORMS_PROMOTEXT"), PageSize.LETTER.getWidth() / 2, 5, 0);
            cb.endText();
        }
    }
}
