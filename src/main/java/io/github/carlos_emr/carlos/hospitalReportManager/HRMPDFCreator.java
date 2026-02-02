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
package io.github.carlos_emr.carlos.hospitalReportManager;


import com.itextpdf.text.Element;
import com.itextpdf.text.*;
import com.itextpdf.text.Font;
import com.itextpdf.text.pdf.*;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMProviderConfidentialityStatementDao;

import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HRMPDFCreator extends PdfPageEventHelper {
    private Logger logger = MiscUtils.getLogger();
    private OutputStream outputStream;
    private HRMDocument hrmDocument;
    private HRMReport hrmReport;
    private Document document;
    private LoggedInInfo loggedInInfo;


    public HRMPDFCreator(OutputStream outputStream, Integer hrmId, LoggedInInfo loggedInInfo) {

        //Gets the HRMDocumentDao
        HRMDocumentDao hrmDocumentDao = SpringUtils.getBean(HRMDocumentDao.class);
        //Stores the output stream and hrmId
        this.outputStream = outputStream;
        this.loggedInInfo = loggedInInfo;

        try {
            //Gets the HRMDocument by the provided Id

            List<HRMDocument> hrmDocuments = hrmDocumentDao.findById(hrmId);

            //If the list is not null and it has items in it
            if (hrmDocuments != null && hrmDocuments.size() > 0) {
                hrmDocument = hrmDocuments.get(0);

                logger.info("Parsing the HRM Document into a report for printing");
                //Gets and parses the HRMReport storing it in the class variable
                hrmReport = HRMReportParser.parseReport(loggedInInfo, hrmDocument.getReportFile());
            }
        } catch (NumberFormatException e) {
            logger.error("HRM Id is not a valid integer", e);
        }
    }

    public void printPdf() {
        try {
            if (!hrmReport.isBinary()) {
                document = new Document();

                PdfWriter writer = PdfWriter.getInstance(document, outputStream);
                writer.setPageEvent(this);

                document.setPageSize(PageSize.LETTER);

                document.open();

                if (hrmReport != null) {
                    generateHRMReport(hrmReport);
                } else {
                    logger.error("There is no HRM Report");
                }

                document.close();
            } else if (hrmReport.getFileExtension() != null && (hrmReport.getFileExtension().equals(".gif") || hrmReport.getFileExtension().equals(".jpg") || hrmReport.getFileExtension().equals(".png"))) {
                document = new Document();

                PdfWriter writer = PdfWriter.getInstance(document, outputStream);
                writer.setPageEvent(this);

                document.setPageSize(PageSize.LETTER);

                //Sets the margins to 0 so that the entire page is used
                document.setMargins(0, 0, 0, 0);
                document.open();

                //Add confidentiality statement to PDF
                float footerHeight = 0f;
                HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);
                String confidentialityStatement = hrmProviderConfidentialityStatementDao.getConfidentialityStatementForProvider(loggedInInfo.getLoggedInProviderNo());
                PdfPTable confidentialityStatementTable = null;
                if (confidentialityStatement != null && confidentialityStatement.trim().length() > 0) {
                    BaseFont baseFont = BaseFont.createFont(BaseFont.COURIER, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                    Font font = new Font(baseFont, 10, Font.NORMAL);
                    Font italicFont = new Font(baseFont, 10, Font.ITALIC);
                    //Creates a cell to be used as a separator line
                    PdfPCell separator = new PdfPCell();
                    separator.setBorder(PdfPCell.BOTTOM);
                    separator.setFixedHeight(0.1f);
                    //Creates a cell to be used to insert a small amount of space
                    PdfPCell space = new PdfPCell();
                    space.setBorder(0);
                    space.setFixedHeight(2f);
                    //Creates the main page table
                    confidentialityStatementTable = new PdfPTable(1);
                    confidentialityStatementTable.setTotalWidth(PageSize.LETTER.getWidth());
                    confidentialityStatementTable.setLockedWidth(true);
                    PdfPCell cell = new PdfPCell();
                    cell.setBorder(0);

                    confidentialityStatementTable.addCell(separator);
                    cell.setPhrase(new Phrase("Confidentiality Statement:", italicFont));
                    confidentialityStatementTable.addCell(cell);
                    cell.setPhrase(new Phrase(confidentialityStatement, font));
                    confidentialityStatementTable.addCell(cell);
                    confidentialityStatementTable.addCell(space);

                    footerHeight += confidentialityStatementTable.getTotalHeight() + 5;
                }

                //Translates the binary content into an image
                Image image = Image.getInstance(hrmReport.getBinaryContent());
                //Scales the image in case one of the dimensions is bigger than the document

                image.scaleToFit(document.getPageSize().getWidth(), document.getPageSize().getHeight() - footerHeight);

                //Checks if the scaled width is bigger than the document width and that the height can fit the width
                if (image.getScaledWidth() >= document.getPageSize().getWidth() && image.getScaledHeight() <= document.getPageSize().getWidth()) {
                    //Rotates the image 90 degrees so that it displays portrait style on the document
                    image.setRotationDegrees(90);
                    //Rescales the image so that it fits the page better

                    image.scaleToFit(document.getPageSize().getWidth(), document.getPageSize().getHeight() - footerHeight);

                }

                document.add(image);

                if (confidentialityStatementTable != null) {
                    //Adds the table to the document
                    document.add(confidentialityStatementTable);
                }

                document.close();
            } else if (hrmReport.getFileExtension() != null && (hrmReport.getFileExtension().equals(".html"))) {
                EFormData eFormData = new EFormData();
                byte[] htmlHrmReportData = hrmReport.getBinaryContent();
                eFormData.setFormData(new String(htmlHrmReportData, StandardCharsets.UTF_8));
                Path path = ConvertToEdoc.saveAsTempPDF(eFormData);
                outputStream.write(Files.readAllBytes(path));
            } else {
                logger.info("HRM Report is binary, only printing the attachment");
                outputStream.write(hrmReport.getBinaryContent());
            }

            outputStream.flush();
        } catch (IOException e) {
            logger.error("An I/O Exception has occurred while either getting a PDFWriter Instance or creating the BaseFont", e);
        } catch (DocumentException e) {
            logger.error("A Document Exception occurred while either getting a PDFWriter or creating the BaseFont", e);
        }
    }

    private void generateHRMReport(HRMReport hrmReport) throws IOException, DocumentException {
        BaseFont baseFont = BaseFont.createFont(BaseFont.COURIER, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        Font font = new Font(baseFont, 10, Font.NORMAL);
        Font italicFont = new Font(baseFont, 10, Font.ITALIC);
        Font boldFont = new Font(baseFont, 10, Font.BOLD);

        //Creates a cell to be used to insert a small amount of space
        PdfPCell space = new PdfPCell();
        space.setBorder(0);
        space.setFixedHeight(2f);

        //Creates a cell to be used as a separator line
        PdfPCell separator = new PdfPCell();
        separator.setBorder(PdfPCell.BOTTOM);
        separator.setFixedHeight(0.1f);

        //Creates the main page table
        PdfPTable mainPage = new PdfPTable(1);
        mainPage.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);

        //Creates the Demographic Info area
        cell.setPhrase(new Phrase("Demographic Info:", boldFont));
        mainPage.addCell(cell);

        cell.setPhrase(new Phrase(hrmReport.getLegalName(), font));
        mainPage.addCell(cell);

        cell.setPhrase(new Phrase(hrmReport.getHCN() + " " + hrmReport.getHCNVersion() + " " + hrmReport.getGender(), font));
        mainPage.addCell(cell);

        cell.setPhrase(new Phrase("DOB: " + hrmReport.getDateOfBirthAsString(), font));
        mainPage.addCell(cell);

        //Adds a spacing cell
        mainPage.addCell(space);

        //Outputs the when the HRM report was received
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPhrase(new Phrase("This report was received from the Hospital Report Manager (HRM) at " + hrmDocument.getTimeReceived(), italicFont));
        mainPage.addCell(cell);

        //Adds a separator and spacing
        mainPage.addCell(separator);
        mainPage.addCell(space);

        //If the report is binary, outputs a message letting the user know that there was an attachment
        if (hrmReport.isBinary()) {
            cell.setPhrase(new Phrase("This report contains an attachment", font));
            mainPage.addCell(cell);
        }

        //Outputs the main content from the report
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setPhrase(new Phrase(hrmReport.getFirstReportTextContent(), font));
        mainPage.addCell(cell);
        mainPage.addCell(space);

        //Add confidentiality statement to PDF
        HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);
        String confidentialityStatement = hrmProviderConfidentialityStatementDao.getConfidentialityStatementForProvider(loggedInInfo.getLoggedInProviderNo());
        if (confidentialityStatement != null && confidentialityStatement.trim().length() > 0) {
            mainPage.addCell(separator);
            cell.setPhrase(new Phrase("Confidentiality Statement:", italicFont));
            mainPage.addCell(cell);
            cell.setPhrase(new Phrase(confidentialityStatement, font));
            mainPage.addCell(cell);
            mainPage.addCell(space);
        }

        //Creates a box at the bottom of the report that contains the metadata
        float[] metaDataBoxWidths = {1f, 2f};
        PdfPTable metaDataBox = new PdfPTable(metaDataBoxWidths);

        cell.setPhrase(new Phrase("Message Unique ID: ", boldFont));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        metaDataBox.addCell(cell);

        cell.setPhrase(new Phrase(hrmReport.getMessageUniqueId(), font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        metaDataBox.addCell(cell);

        cell.setPhrase(new Phrase("Sending Author: ", boldFont));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        metaDataBox.addCell(cell);

        cell.setPhrase(new Phrase(hrmReport.getSendingAuthor(), font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        metaDataBox.addCell(cell);

        cell.setPhrase(new Phrase("Sending Facility ID: ", boldFont));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        metaDataBox.addCell(cell);

        cell.setPhrase(new Phrase(hrmReport.getSendingFacilityId(), font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        metaDataBox.addCell(cell);

        cell.setPhrase(new Phrase("Sending Facility Report No.: ", boldFont));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        metaDataBox.addCell(cell);

        cell.setPhrase(new Phrase(hrmReport.getSendingFacilityReportNo(), font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        metaDataBox.addCell(cell);

        cell.setPhrase(new Phrase("Date and Time of Report: ", boldFont));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        metaDataBox.addCell(cell);

        cell.setPhrase(new Phrase(HRMReportParser.getAppropriateDateStringFromReport(hrmReport), font));

        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        metaDataBox.addCell(cell);

        cell.setPhrase(new Phrase("Result Status: ", boldFont));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        metaDataBox.addCell(cell);

        String resultStatus = hrmReport.getResultStatus() != null && hrmReport.getResultStatus().equalsIgnoreCase("C") ? "Cancelled" : "Signed by the responsible author and Released by health records";
        cell.setPhrase(new Phrase(resultStatus, font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        metaDataBox.addCell(cell);

        cell = new PdfPCell(metaDataBox);
        cell.setBorder(15);

        //Adds the metaDataBox to the main page
        mainPage.addCell(cell);

        //Adds the table to the document
        document.add(mainPage);
    }
}
