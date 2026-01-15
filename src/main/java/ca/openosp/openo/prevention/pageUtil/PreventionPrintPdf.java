//CHECKSTYLE:OFF
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
 */


/*
 * PreventionPrintPdf.java
 *
 * Created on March 12, 2007, 4:05 PM
 */

package ca.openosp.openo.prevention.pageUtil;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;

import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.clinic.ClinicData;
import ca.openosp.openo.prevention.PreventionDisplayConfig;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.*;

/*
 * @author rjonasz
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

    public void printPdf(HttpServletRequest request, HttpServletResponse response) throws IOException, DocumentException {
        response.setContentType("application/pdf");  //octet-stream
        response.setHeader("Content-Disposition", "attachment; filename=\"Prevention.pdf\"");
        String[] headerIds = request.getParameterValues("printHP");
        printPdf(headerIds, request, response.getOutputStream());
    }

    public void printPdf(String[] headerIds1, HttpServletRequest request, OutputStream outputStream) throws IOException, DocumentException {

        //make sure we have data to print      
        if (headerIds1 == null) {
            throw new DocumentException();
        }

        String[] headerIds = null;
        List<String> validImmunizationsIds = new ArrayList<String>();
        List<String> validScreeningsIds = new ArrayList<String>();
        PreventionDisplayConfig pdc = PreventionDisplayConfig.getInstance();
        for (int x = 0; x < headerIds1.length; x++) {
            String pHeader = request.getParameter("preventionHeader" + headerIds1[x]);
            //check if this is an immunization!
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
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, BaseColor.BLACK);

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
        Phrase titlePhrase = new Phrase(HEADER_LEADING, heading, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.BOLD, BaseColor.BLACK));
        titlePhrase.add(Chunk.NEWLINE);
        titlePhrase.add(new Chunk(demo.getFormattedName(), FontFactory.getFont(FontFactory.HELVETICA, 14, Font.NORMAL, BaseColor.BLACK)));
        titlePhrase.add(Chunk.NEWLINE);
        titlePhrase.add(new Chunk(demoInfo.toString(), FontFactory.getFont(FontFactory.HELVETICA, 12, Font.NORMAL, BaseColor.BLACK)));

        String mrp = request.getParameter("mrp");
        if (mrp != null && OscarProperties.getInstance().getBooleanProperty("mrp_model", "yes")) {
            Properties prop = (Properties) request.getSession().getAttribute("providerBean");
            titlePhrase.add(Chunk.NEWLINE);
            titlePhrase.add(new Chunk("MRP: " + prop.getProperty(mrp, "unknown"), FontFactory.getFont(FontFactory.HELVETICA, 12, Font.BOLD, BaseColor.BLACK)));
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

        Paragraph clinicParagraph = new Paragraph(LEADING, clinicData.getClinicName(), FontFactory.getFont(FontFactory.HELVETICA, 12, Font.BOLD, BaseColor.BLACK));
        clinicParagraph.add(Chunk.NEWLINE);
        clinicParagraph.add(new Chunk(clinicData.getClinicAddress(), FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, BaseColor.BLACK)));
        clinicParagraph.add(Chunk.NEWLINE);
        clinicParagraph.add(new Chunk(clinicAddrCont.toString(), FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, BaseColor.BLACK)));
        clinicParagraph.add(Chunk.NEWLINE);
        clinicParagraph.add(new Chunk("Ph.", FontFactory.getFont(FontFactory.HELVETICA, 10, Font.BOLD, BaseColor.BLACK)));
        clinicParagraph.add(new Chunk(clinicData.getClinicPhone(), FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, BaseColor.BLACK)));
        clinicParagraph.add(new Chunk(" Fax.", FontFactory.getFont(FontFactory.HELVETICA, 10, Font.BOLD, BaseColor.BLACK)));
        clinicParagraph.add(new Chunk(clinicData.getClinicFax(), FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, BaseColor.BLACK)));
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
        boolean showComments = OscarProperties.getInstance().getBooleanProperty("prevention_show_comments", "true");

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

            Phrase procHeader = new Phrase(HEADER_LEADING, "Prevention " + preventionHeader + "\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, BaseColor.BLACK));
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

                Phrase procedure = new Phrase(LEADING, "Date: ", FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, BaseColor.BLACK));
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

                //We can't fit the prevention we are printing into the current column on the current page we are printing to 
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
                        Phrase contdProcHeader = new Phrase(HEADER_LEADING, "Prevention " + preventionHeader + " (cont'd)\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.ITALIC, BaseColor.BLACK));
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
     * This replaces HeaderFooter.getHeight() which was removed in iText 5.x.
     */
    private float getHeaderHeight() {
        if (headerPhrase == null) {
            return 0f;
        }
        // Estimate height based on the phrase leading
        // Using a reasonable default for multi-line headers
        return headerPhrase.getLeading() * 4f;
    }

    private float goToNewPage(Phrase headerPhrase, Font font) throws DocumentException, IOException {
        ColumnText.showTextAligned(cb, Phrase.ALIGN_CENTER, new Phrase("-" + curPage + "-"), document.right() / 2f, document.bottom() - (document.bottomMargin() / 2f), 0f);
        addPromoText(); // Assuming this method is accessible within your class
        float upperYcoord = document.top() - getHeaderHeight() - font.getCalculatedLeading(LINESPACING);
        document.newPage();
        ct.setSimpleColumn(document.left(), document.bottom(), document.right() / 2f, upperYcoord);
        curPage++;
        return upperYcoord;
    }

    private Paragraph addParagraph(String title, float size, int style) throws DocumentException, IOException {
        Paragraph paragraph = new Paragraph(LEADING, title, FontFactory.getFont(FontFactory.HELVETICA, size, style, BaseColor.BLACK));
        paragraph.add(Chunk.NEWLINE);
        paragraph.setAlignment(Paragraph.ALIGN_LEFT);
        return paragraph;
    }

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
        if (OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT") != null) {
            cb.beginText();
            cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 6);
            cb.showTextAligned(PdfContentByte.ALIGN_CENTER, OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT"), PageSize.LETTER.getWidth() / 2, 5, 0);
            cb.endText();
        }
    }
}
