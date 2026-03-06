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
 * LabPDFCreator.java
 *
 * Created on November 27, 2007, 9:43 AM
 *
 */

package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.openpdf.text.*;
import org.openpdf.text.Font;
import org.openpdf.text.Rectangle;
import org.openpdf.text.html.simpleparser.HTMLWorker;
import org.openpdf.text.pdf.*;
import org.openpdf.text.pdf.events.PdfPageEventForwarder;
import org.openrtf.text.rtf.RtfWriter2;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.IOUtils;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.printing.FontSettings;
import io.github.carlos_emr.carlos.commn.printing.PdfWriterFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.CLSHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.ExcellerisOntarioHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MEDITECHHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;


/**
 * Generates PDF and RTF renditions of HL7 laboratory results.
 *
 * <p>This class extends {@link PdfPageEventHelper} to add custom header/footer rendering
 * on each page (patient name identifier on pages 2+). It supports multiple Canadian
 * lab message formats including PATHL7, ExcellerisON, CLS, MEDITECH, MEDVUE, EPSILON,
 * PFHT, and TRUENORTH.
 *
 * <p>Key design features:
 * <ul>
 *   <li>Uses {@link PdfPageEventForwarder} to chain this class's {@link #onEndPage} handler
 *       with factory-installed page stampers (promo text, confidentiality, page numbers)
 *       rather than overwriting them.</li>
 *   <li>Uses {@link HTMLWorker} for parsing TRUENORTH lab format HTML comments into
 *       OpenPDF elements.</li>
 *   <li>Supports embedded PDF documents (Base64-encoded OBX segments of type "ED")
 *       which are appended after the main report via {@link ConcatPDF}.</li>
 *   <li>try-finally around {@link Document#close()} ensures resource cleanup even
 *       on exceptions during PDF generation.</li>
 *   <li>Handles structured results (tabular with columns for test name, result,
 *       abnormal flag, reference range, units, timestamp, status) and unstructured
 *       results (free-text narrative reports).</li>
 * </ul>
 *
 * @see io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory
 * @see io.github.carlos_emr.carlos.commn.printing.PdfWriterFactory
 * @since 2007-12-15
 */
public class LabPDFCreator extends PdfPageEventHelper {
    private OutputStream os;
    private boolean isUnstructuredDoc = false;
    private boolean isReportData = Boolean.FALSE;
    private MessageHandler handler;
    private List<MessageHandler> handlers = new ArrayList<MessageHandler>();

    private int versionNum;
    private String[] multiID;
    private String id;

    private Document document;
    private BaseFont bf;
    private Font font;
    private Font boldFont;
    private String dateLabReceived;

    private List<String> embeddedDocumentsToAppend = new ArrayList<String>();
    List<String> allLicenseNames = new ArrayList<String>();

    /**
     * Convenience method that generates a complete lab PDF as a byte array.
     *
     * @param segmentId String the HL7 message segment ID to render
     * @param providerNo String the provider number (currently unused but reserved for future filtering)
     * @return byte[] the generated PDF document bytes
     * @throws IOException if an I/O error occurs during PDF generation
     * @throws DocumentException if an OpenPDF document error occurs
     */
    public static byte[] getPdfBytes(String segmentId, String providerNo) throws IOException, DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        LabPDFCreator labPDFCreator = new LabPDFCreator(baos, segmentId, providerNo);
        labPDFCreator.printPdf();

        return (baos.toByteArray());
    }

    public LabPDFCreator() {
        // Default constructor.
    }

    /**
     * Creates a new instance of LabPDFCreator from an HTTP request.
     *
     * <p>Extracts {@code segmentID} and {@code providerNo} from request parameters
     * or attributes (parameter takes precedence).
     *
     * @param request HttpServletRequest containing segmentID and providerNo
     * @param os OutputStream to write the generated PDF to
     */
    public LabPDFCreator(HttpServletRequest request, OutputStream os) {
        this(os, (request.getParameter("segmentID") != null ? request.getParameter("segmentID") : (String) request.getAttribute("segmentID")), (request.getParameter("providerNo") != null ? request.getParameter("providerNo") : (String) request.getAttribute("providerNo")));
    }

    /**
     * Creates a new instance of LabPDFCreator with explicit parameters.
     *
     * <p>Initializes the HL7 message handler via {@link Factory#getHandler}, retrieves
     * the lab received date from the database, and determines the lab version number
     * among related labs (multiple versions of the same requisition).
     *
     * @param os OutputStream to write the generated PDF to
     * @param segmentId String the HL7 message segment ID to render
     * @param providerNo String the provider number (currently unused but reserved for future filtering)
     */
    public LabPDFCreator(OutputStream os, String segmentId, String providerNo) {
        this.os = os;
        this.id = segmentId;

        // Retrieve date lab was received by CARLOS
        Hl7TextMessageDao hl7TxtMsgDao = (Hl7TextMessageDao) SpringUtils.getBean(Hl7TextMessageDao.class);
        Hl7TextMessage hl7TextMessage = hl7TxtMsgDao.find(Integer.parseInt(segmentId));
        java.util.Date date = hl7TextMessage.getCreated();
        String stringFormat = "yyyy-MM-dd HH:mm";
        dateLabReceived = UtilDateUtilities.DateToString(date, stringFormat);

        // create handler
        this.handler = Factory.getHandler(id);

        // determine lab version
        String multiLabId = Hl7textResultsData.getMatchingLabs(id);
        this.multiID = multiLabId.split(",");

        int i = 0;
        while (!multiID[i].equals(id)) {
            i++;
        }
        this.versionNum = i + 1;
    }

    /**
     * Generates an RTF rendition of the lab results (used for VIHA RTF-format labs).
     *
     * <p>Creates the RTF document using OpenPDF's {@link RtfWriter2}, adds patient
     * information via text paragraphs (since PdfPTable is not supported in RTF),
     * and imports the raw RTF content from the first OBX result.
     *
     * @throws IOException if an I/O error occurs during RTF generation
     * @throws DocumentException if an OpenPDF document error occurs
     */
    public void printRtf() throws IOException, DocumentException {
        //create an input stream from the rtf string bytes
        byte[] rtfBytes = handler.getOBXResult(0, 0).getBytes();
        try (ByteArrayInputStream rtfStream = new ByteArrayInputStream(rtfBytes)) {
            //create & open the document we are going to write to and its writer
            org.openpdf.text.Document document = new org.openpdf.text.Document();
            RtfWriter2 writer = RtfWriter2.getInstance(document, os);
            document.setPageSize(org.openpdf.text.PageSize.LETTER);
            document.addTitle("CARLOS Laboratory Report");
            document.addCreator("CARLOS EMR");
            document.open();

            //Create the fonts that we are going to use
            bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            font = new Font(bf, 11, Font.NORMAL);
            boldFont = new Font(bf, 12, Font.BOLD);

            //add the patient information
            addRtfPatientInfo();

            //add the results
            writer.importRtfDocument(rtfStream, null);

            document.close();
        } catch (org.openpdf.text.DocumentException e) {
            MiscUtils.getLogger().error("Failed to import RTF document", e);
            throw e;
        }
    }

    /**
     * Generates the main lab report PDF document.
     *
     * <p>Creates an OpenPDF document with Letter page size, adds patient/lab info header
     * table, iterates through all HL7 headers to render structured or unstructured test
     * results, and appends an "END OF REPORT" footer. Uses try-finally to ensure the
     * document is closed even on exceptions.
     *
     * <p>This method registers itself as a page event handler via
     * {@link PdfPageEventForwarder} chaining so that promo text, confidentiality
     * notices, and page numbers from {@link PdfWriterFactory} are preserved with
     * deterministic ordering.
     *
     * @throws IOException if an I/O error occurs during PDF generation
     * @throws DocumentException if the handler is null or an OpenPDF error occurs
     */
    public void printPdf() throws IOException, DocumentException {

        // check that we have data to print
        if (handler == null) {
            throw new DocumentException("No lab handler available for PDF generation");
        }

        //Create the document we are going to write to
        document = new Document();
        PdfWriter writer = PdfWriterFactory.newInstance(document, os, FontSettings.HELVETICA_10PT);

        try {
            // Add this class's onEndPage handler to the factory-installed PdfPageEventForwarder
            // to preserve deterministic ordering of promo/confidentiality/page-number stampers
            PdfPageEvent existingEvent = writer.getPageEvent();
            if (existingEvent instanceof PdfPageEventForwarder forwarder) {
                forwarder.addPageEvent(this);
            } else {
                writer.setPageEvent(this);
            }

            document.setPageSize(PageSize.LETTER);
            document.addTitle("CARLOS Laboratory Report");
            document.addCreator("CARLOS EMR");
            document.open();

            //Create the fonts that we are going to use
            bf = BaseFont.createFont(BaseFont.TIMES_ROMAN, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            font = new Font(bf, 9, Font.NORMAL);
            boldFont = new Font(bf, 10, Font.BOLD);

            // add the header table containing the patient and lab info to the document
            createInfoTable();

            // add the tests and test info for each header
            ArrayList<String> headers = handler.getHeaders();
            for (int i = 0; i < headers.size(); i++) {

                String specimenSource = null;
                String specimenDescription = null;

                if ((handler instanceof MEDITECHHandler) && ("MIC".equals(((MEDITECHHandler) handler).getSendingApplication()))) {
                    specimenSource = ((MEDITECHHandler) handler).getSpecimenSource(i);
                    specimenSource = "SPECIMEN SOURCE: " + specimenSource;
                    specimenDescription = ((MEDITECHHandler) handler).getSpecimenDescription(i);
                    specimenDescription = "SPECIMEN DESCRIPTION: " + specimenDescription;
                }

                addLabCategory(headers.get(i), specimenSource, specimenDescription);
            }

            // `handlers` is a secondary handler list for multi-segment lab messages.
            // In the common single-handler case the list is empty, making this loop a no-op.
            for (MessageHandler extraHandler : handlers) {
                ArrayList<String> extraHeaders = extraHandler.getHeaders();
                for (int i = 0; i < extraHeaders.size(); i++)
                    addLabCategory(extraHeaders.get(i), extraHandler);
            }


            // add end of report table
            PdfPTable table = new PdfPTable(1);
            table.setWidthPercentage(100);
            PdfPCell cell = new PdfPCell();
            cell.setBorder(0);
            cell.setPhrase(new Phrase("  "));
            table.addCell(cell);
            cell.setBorder(15);
            cell.setBackgroundColor(new Color(210, 212, 255));
            if (handler.getMsgType().equals("CLS")) {
                cell.setPhrase(new Phrase("Legend:  A=Abnormal  L=Low  H=High  C=Critical", boldFont));
            } else {
                cell.setPhrase(new Phrase("END OF REPORT", boldFont));
            }
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
            document.add(table);

            if (handler.getMsgType().equals("ExcellerisON")) {
                PdfPTable table2 = new PdfPTable(1);
                table2.setWidthPercentage(100);
                for (String x : allLicenseNames) {
                    PdfPCell cell2 = new PdfPCell();
                    cell2.setBorder(0);
                    cell2.setPhrase(new Phrase(x, new Font(bf, 9, Font.NORMAL)));
                    table2.addCell(cell2);
                }
                document.add(table2);
            }
        } finally {
            document.close();
            if (writer != null) {
                writer.close();
            }
        }

        os.flush();
    }

    /**
     * Appends any Base64-encoded embedded PDF documents (from OBX segments of type "ED")
     * to the main lab report PDF using {@link ConcatPDF}.
     *
     * @param currentPDF File the main lab report PDF file to prepend
     * @param os OutputStream to write the concatenated result to
     */
    public void addEmbeddedDocuments(File currentPDF, OutputStream os) {
        List<Object> alist = new ArrayList<Object>();

        InputStream mainPDF = null;
        try {

            mainPDF = new FileInputStream(currentPDF);

            alist.add(mainPDF);

            for (String data : embeddedDocumentsToAppend) {
                InputStream tmp = new ByteArrayInputStream(Base64.decodeBase64(data));
                alist.add(tmp);
            }

            ConcatPDF.concat(alist, os);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            IOUtils.closeQuietly(mainPDF);
        }
    }

    /**
     * Convenience overload that renders a lab category using an alternate handler
     * (for multi-segment lab messages) without specimen metadata.
     *
     * @param header String the lab category header name
     * @param extraHandler MessageHandler the handler for this segment
     * @throws DocumentException if PDF rendering fails
     */
    private void addLabCategory(String header, MessageHandler extraHandler) throws DocumentException {
        addLabCategory(header, extraHandler, null, null);
    }

    /**
     * Convenience overload that renders a lab category using the primary handler
     * with optional MEDITECH specimen source and description metadata.
     *
     * @param header String the lab category header name
     * @param specimenSource String the MEDITECH specimen source text, or null
     * @param specimenDescription String the MEDITECH specimen description, or null
     * @throws DocumentException if PDF rendering fails
     */
    private void addLabCategory(String header, String specimenSource, String specimenDescription) throws DocumentException {
        addLabCategory(header, null, specimenSource, specimenDescription);
    }

    /**
     * Renders a single lab category to the PDF document. Handles 8+ message types
     * (PATHL7, CLS, Excelleris, MEDITECH, etc.) with different column layouts,
     * performs duplicate result suppression, and applies abnormal-flag color coding.
     *
     * @param header String the lab category header name
     * @param extraHandler MessageHandler alternate handler for multi-segment messages, or null for primary
     * @param specimenSource String MEDITECH specimen source text, or null
     * @param specimenDescription String MEDITECH specimen description, or null
     * @throws DocumentException if PDF rendering fails
     */
    private void addLabCategory(String header, MessageHandler extraHandler, String specimenSource, String specimenDescription) throws DocumentException {
        String currentLicenseNo = null, lastLicenseNo = null;

        MessageHandler handler = (extraHandler != null) ? extraHandler : this.handler;
        if (handler.getMsgType().equals("PATHL7")) {
            this.isUnstructuredDoc = ((PATHL7Handler) handler).unstructuredDocCheck(header);
        } else if (handler.getMsgType().equals("CLS")) {
            this.isUnstructuredDoc = ((CLSHandler) handler).isUnstructured();
        }

        PdfPCell cell = new PdfPCell();
        float[] mainTableWidths;
        PdfPTable table = null;

        if ((handler instanceof MEDITECHHandler && ((MEDITECHHandler) handler).isReportData())
                || (handler instanceof PATHL7Handler && ((PATHL7Handler) handler).isReportData())) {

            table = new PdfPTable(2);
            table.setWidthPercentage(100);
            this.isReportData = Boolean.TRUE;

			
		} else {
		
			if (isUnstructuredDoc){
				if (handler.getMsgType().equals("CLS"))
				{
					mainTableWidths = new float[] { 5f, 10f, 3f, 2f};
				} else
				{
					mainTableWidths = new float[] { 5f, 12f, 3f};
				}
			}else {
				if (handler.getMsgType().equals("ExcellerisON")) {
					mainTableWidths = new float[] {5f, 3f, 1f, 3f, 2f, 4f, 2f, 2f };
				} else {
					mainTableWidths = new float[] {5f, 3f, 1f, 3f, 2f, 4f, 2f };
				}
			}
		
			table = new PdfPTable(mainTableWidths);
			table.setWidthPercentage(100);
			
			if (isUnstructuredDoc){
				// The table will only render in the PDF if more than 1 row is added to the table
				table.setHeaderRows(1);
			}
			else {
				// The table will only render in the PDF if more than 3 rows is added to the table
				table.setHeaderRows(3);
			}
	
			// category name
			if (!isUnstructuredDoc){
				
				// blank filler
				cell.setPadding(3);
				cell.setPhrase(new Phrase("  "));				
				cell.setBorder(0);				
				if (handler.getMsgType().equals("ExcellerisON")) {
					cell.setColspan(8);
				} else { 
					cell.setColspan(7);
				}
				table.addCell(cell);
				
				// lab title. ie: PT Panel, CBC
				cell.setBorder(15);
				cell.setPadding(3);
				cell.setColspan(2);
				cell.setPhrase(new Phrase(header.replaceAll("<br\\s*/*>", "\n"),
						new Font(bf, 12, Font.BOLD)));
				table.addCell(cell);
				
				// place holder after lab title
				cell.setPhrase(new Phrase("  "));
				cell.setBorder(0);
				if (handler.getMsgType().equals("ExcellerisON")) {
					cell.setColspan(6);
				} else {
					cell.setColspan(5);
				}
				table.addCell(cell);
			}

            // table headers
            if (isUnstructuredDoc) {
                cell.setColspan(1);
                cell.setBorder(Rectangle.BOX);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setBackgroundColor(new Color(210, 212, 255));
                cell.setPhrase(new Phrase("Test Name(s)", boldFont));
                table.addCell(cell);
                cell.setPhrase(new Phrase("Result", boldFont));
                table.addCell(cell);

                if (handler.getMsgType().equals("CLS")) {
                    cell.setPhrase(new Phrase("Date/Time Collected", boldFont));
                    table.addCell(cell);
                    cell.setPhrase(new Phrase("Status", boldFont));
                    table.addCell(cell);
                } else {
                    cell.setPhrase(new Phrase("Date/Time Completed", boldFont));
                    table.addCell(cell);
                }

            } else {

                if ((handler instanceof MEDITECHHandler) && ("MIC".equals(((MEDITECHHandler) handler).getSendingApplication()))) {

                    cell.setPhrase(new Phrase(specimenSource, boldFont));
                    if (handler.getMsgType().equals("ExcellerisON")) {
                        cell.setColspan(8);
                    } else {
                        cell.setColspan(7);
                    }

                    cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                    table.addCell(cell);
                    cell.setColspan(1);


                    cell.setPhrase(new Phrase(specimenDescription, boldFont));
                    cell.setColspan(7);
                    cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                    table.addCell(cell);
                    cell.setColspan(1);
                }

                cell.setColspan(1);
                cell.setBorder(Rectangle.BOX);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setBackgroundColor(new Color(210, 212, 255));
                cell.setPhrase(new Phrase("Test Name(s)", boldFont));
                table.addCell(cell);
                cell.setPhrase(new Phrase("Result", boldFont));
                table.addCell(cell);
                cell.setPhrase(new Phrase("Abn", boldFont));
                table.addCell(cell);
                cell.setPhrase(new Phrase("Reference Range", boldFont));
                table.addCell(cell);
                cell.setPhrase(new Phrase("Units", boldFont));
                table.addCell(cell);
                if (handler.getMsgType().equals("CLS")) {
                    cell.setPhrase(new Phrase("Date/Time Collected", boldFont));
                } else {
                    cell.setPhrase(new Phrase("Date/Time Completed", boldFont));
                }
                table.addCell(cell);
                cell.setPhrase(new Phrase("Status", boldFont));
                table.addCell(cell);

                if (handler.getMsgType().equals("ExcellerisON")) {
                    cell.setPhrase(new Phrase("Lab Lic #", boldFont));
                    table.addCell(cell);
                }

            }
        } // end alternate to Meditech Unstructured doc.

        // add test results
        int obrCount = handler.getOBRCount();

        // reset the borders
        cell.setBorder(Rectangle.NO_BORDER);

        if (handler.getMsgType().equals("MEDVUE")) {

			cell.setPhrase(new Phrase(handler.getRadiologistInfo(), boldFont));
			cell.setColspan(7);
			cell.setHorizontalAlignment(Element.ALIGN_LEFT);
			table.addCell(cell);
			cell.setPaddingLeft(100);
			cell.setColspan(7);
			cell.setHorizontalAlignment(Element.ALIGN_LEFT);
			cell.setPhrase(new Phrase(handler.getOBXComment(1, 1, 1)
					.replaceAll("<br\\s*/*>", "\n"), font));
			table.addCell(cell);
		} else {
			for (int j = 0; j < obrCount; j++) {
				boolean obrFlag = false;
				int obxCount = handler.getOBXCount(j);

				if (handler.getMsgType().equals("ExcellerisON") && header.equals(handler.getObservationHeader(j, 0))) {
					String orderRequestStatus = ((ExcellerisOntarioHandler) handler).getOrderStatus(j);
					int obrCommentCount = handler.getOBRCommentCount(j);

					if (orderRequestStatus.equals(ExcellerisOntarioHandler.OrderStatus.DELETED.getDescription())) { continue; }

					if (obxCount == 0 && (!orderRequestStatus.isEmpty() || obrCommentCount > 0)) {
						cell.setHorizontalAlignment(Element.ALIGN_LEFT);
						cell.setBackgroundColor( Color.WHITE );
						cell.setPhrase(new Phrase(handler.getOBRName(j), boldFont));
						cell.setColspan(1);
						table.addCell(cell);
						cell.setPhrase(new Phrase(((ExcellerisOntarioHandler) handler).getOrderStatus(j), new Font(bf, 9, Font.NORMAL)));
						cell.setColspan(7);
						table.addCell(cell);
						obrFlag = true;
					}
				}

				for (int k = 0; k < obxCount; k++) {
					
					if (handler.getMsgType().equals("ExcellerisON")) {
						lastLicenseNo = currentLicenseNo;
						currentLicenseNo = ((ExcellerisOntarioHandler) handler).getLabLicenseNo(j, k);
						String licenseName = ((ExcellerisOntarioHandler) handler).getLabLicenseName(j, k);
						if (!allLicenseNames.contains(licenseName)) {
							allLicenseNames.add(licenseName);
						}
					}
					
					cell.setHorizontalAlignment(Element.ALIGN_LEFT);
					cell.setBorder(Rectangle.BOTTOM);
					if (handler.getOBXCommentCount(j, k) > 0) {
						cell.setBorder( Rectangle.NO_BORDER );
					}
					cell.setBorderColor( Color.LIGHT_GRAY );
					cell.setBackgroundColor( Color.WHITE );
					
					String obxName = handler.getOBXName(j, k);
					
					boolean isAllowedDuplicate = false;
					if (handler.getMsgType().equals("PATHL7")){
						// Culture (LOINC 6463-4) and Organism (X433, X30011) are Excelleris-specific
					// OBX identifiers that legitimately repeat across test groups per the TX/FT
					// format specification — exempt from duplicate result name suppression.
						if ((handler.getOBXName(j, k).equals("Culture") && handler.getOBXIdentifier(j, k).equals("6463-4")) || 
								(handler.getOBXName(j, k).equals("Organism") && (handler.getOBXIdentifier(j, k).equals("X433") || handler.getOBXIdentifier(j, k).equals("X30011")))){
		   					isAllowedDuplicate = true;
		   				}
					}
					if (!handler.getOBXResultStatus(j, k).equals("TDIS")) {

						// ensure that the result is a real result
						if ((!handler.getOBXResultStatus(j, k).equals("DNS") && !obxName.equals("") && header.equals(handler.getObservationHeader(j, k))) 
								|| (handler.getMsgType().equals("EPSILON") && header.equals(handler.getOBXIdentifier(j, k)) && !obxName.equals("")) 
								|| (handler.getMsgType().equals("PFHT") && !obxName.equals("") && header.equals(handler.getObservationHeader(j, k)))) { // <<-- DNS only needed for
													// MDS messages
							String obrName = handler.getOBRName(j);
							// add the obrname if necessary

							boolean showOBRTestName = ( !(obxName.contains(obrName) && obxCount < 2 && !isUnstructuredDoc) );
							// For 'ExcellerisON' type reports, showing test names (OBR4.2) for all OBRs is required.
							if (handler.getMsgType().equals("ExcellerisON")) {
								showOBRTestName = !isUnstructuredDoc && obxCount > 0;
							}
							if ( !obrFlag && !obrName.equals("") && showOBRTestName) {
								cell.setPhrase(new Phrase(obrName, boldFont));
								if (handler.getMsgType().equals("ExcellerisON")) {
									cell.setColspan(1);
								} else {
									cell.setColspan(7);
								}
								cell.setBorderColor(Color.BLACK);
								table.setWidthPercentage(100);
								table.addCell(cell);
								if (handler.getMsgType().equals("ExcellerisON")) {
									cell.setPhrase(new Phrase(((ExcellerisOntarioHandler) handler).getOrderStatus(j), new Font(bf, 9, Font.NORMAL)));
									cell.setColspan(7);
									table.addCell(cell);
								}
								cell.setBorderColor( Color.LIGHT_GRAY );
								cell.setColspan(1);
								obrFlag = true;
							}

                            // add the obx results and info
                            Font lineFont = new Font(bf, 9, Font.NORMAL, getTextColor(handler.getOBXAbnormalFlag(j, k)));

                            if (this.isReportData) {
                                cell.setColspan(2);
                                cell.setBorder(Rectangle.NO_BORDER);
                                cell.setBorderColor(Color.WHITE);
                                cell.setPadding(0);
                                cell.setPaddingLeft(10);

                                if (handler instanceof PATHL7Handler &&
                                        "".equals(((PATHL7Handler) handler).getOBXSubId(j, k))) {
                                    PdfPTable infoTable = new PdfPTable(2);
                                    infoTable.setWidthPercentage(100);
                                    cell.setPhrase(new Phrase(handler.getOBXName(j, k).replaceAll("<br\\s*/*>", " "), lineFont));
                                    infoTable.addCell(cell);
                                    cell.setPhrase(new Phrase(handler.getOBXResult(j, k).replaceAll("<br\\s*/*>", " "), lineFont));
                                    infoTable.addCell(cell);
                                    table.addCell(infoTable);
                                } else {
                                    String data = handler.getOBXResult(j, k);
                                    if ("".equals(handler.getOBXResult(j, k))) {
                                        data = "\n";
                                    }
                                    int colspan = cell.getColspan();

                                    if (j == 0 && k == 0) {
                                        cell.setColspan(colspan - 1);
                                        cell.setNoWrap(true);
                                        cell.setPhrase(new Phrase(data.replaceAll("<br\\s*/*>", "\n"), lineFont));
                                        table.addCell(cell);

                                        cell.setColspan(1);
                                        int ha = cell.getHorizontalAlignment();
                                        cell.setHorizontalAlignment(PdfPCell.ALIGN_RIGHT);
                                        cell.setPhrase(new Phrase(handler.getTimeStamp(j, k), lineFont));
                                        table.addCell(cell);
                                        cell.setHorizontalAlignment(ha);
                                    } else {
                                        cell.setNoWrap(false);
                                        cell.setPhrase(new Phrase(data.replaceAll("<br\\s*/*>", "\n"), lineFont));
                                        table.addCell(cell);
                                    }

                                }

                            } else if (isUnstructuredDoc) {

                                //if there are duplicate obxNames, display only the first
                                cell.setBorder(Rectangle.NO_BORDER);
                                cell.setPadding(0);
                                cell.setPaddingTop(3);

                                if (!"MEDITECH".equalsIgnoreCase(handler.getMsgType())) {
                                    if (((handler.getOBXIdentifier(j, k).equalsIgnoreCase(handler.getOBXIdentifier(j, k - 1)) && (obxCount > 1))
                                            || (obxName.equalsIgnoreCase(obrName)))) {
                                        cell.setPhrase(new Phrase("", lineFont));
                                        table.addCell(cell);
                                    } else {
                                        String indent = "   ";
                                        if (handler.getMsgType().equals("ExcellerisON")) {
                                            indent = "";
                                        }
                                        if (!StringUtils.isEmpty(indent)) {
                                            cell.setPaddingLeft(3);
                                            indent = "";
                                        }
                                        cell.setPhrase(new Phrase((obrFlag ? indent : "") + obxName, lineFont));
                                        table.addCell(cell);
                                        cell.setPaddingLeft(0);
                                    }
                                }

                                cell.setPhrase(new Phrase(handler.getOBXResult(j, k).replaceAll("<br\\s*/*>", "\n").replace("\t", "\u00a0\u00a0\u00a0\u00a0"), lineFont));
                                table.addCell(cell);

                                //if there are duplicate Times, display only the first
                                if (!"MEDITECH".equalsIgnoreCase(handler.getMsgType())) {
                                    if (handler.getTimeStamp(j, k).equals(handler.getTimeStamp(j, k - 1)) && (obxCount > 1)) {
                                        cell.setPhrase(new Phrase("", lineFont));
                                        table.addCell(cell);
                                    } else {
                                        cell.setPhrase(new Phrase(handler.getTimeStamp(j, k), lineFont));
                                        table.addCell(cell);
                                    }
                                }

                                if (handler.getMsgType().equals("CLS")) {
                                    cell.setPhrase(new Phrase(handler.getOBXResultStatus(j, k), lineFont));
                                    table.addCell(cell);
                                }

                                if (handler.getMsgType().equals("ExcellerisON")) {
                                    cell.setPhrase(new Phrase(!currentLicenseNo.equals(lastLicenseNo) ? currentLicenseNo : "", lineFont));
                                    table.addCell(cell);
                                }
                                cell.setBorder(Rectangle.BOTTOM);
                                cell.setPadding(5);
                            } else {

                                if (!isAllowedDuplicate
                                        && (obxCount > 1)
                                        && k > 0
                                        && handler.getOBXIdentifier(j, k).equals(handler.getOBXIdentifier(j, k - 1))
                                        && (handler.getOBXValueType(j, k).equals("TX") || handler.getOBXValueType(j, k).equals("FT"))) {
                                    cell.setPhrase(new Phrase("", lineFont));
                                    table.addCell(cell);
                                } else {
                                    String indent = "   ";
                                    if (handler.getMsgType().equals("ExcellerisON")) {
                                        indent = "";
                                    }
                                    if (!StringUtils.isEmpty(indent)) {
                                        cell.setPaddingLeft(3);
                                        indent = "";
                                    }
                                    cell.setPhrase(new Phrase((obrFlag ? indent : "") + obxName, lineFont));
                                    table.addCell(cell);
                                    cell.setPaddingLeft(0);

                                }

                                boolean isLongText = false;

                                if ((handler.getMsgType().equals("ExcellerisON") || handler.getMsgType().equals("PATHL7")) && StringUtils.isEmpty(handler.getOBXReferenceRange(j, k))) {
                                    if ("FT".equals(handler.getOBXValueType(j, k)) && (handler.getOBXReferenceRange(j, k).isEmpty() && handler.getOBXUnits(j, k).isEmpty())) {
                                        isLongText = true;
                                    }
                                }

                                if (handler.getMsgType().equals("PATHL7")) {

                                    if (handler.getOBXValueType(j, k).equals("ED")) {
                                        if (((PATHL7Handler) handler).isLegacy(j, k)) {
                                            embeddedDocumentsToAppend.add(((PATHL7Handler) handler).getLegacyOBXResult(j, k));
                                        } else {
                                            embeddedDocumentsToAppend.add(handler.getOBXResult(j, k));
                                        }

                                        cell.setPhrase(new Phrase("PDF Report (Appended to end of Laboratory Report)", lineFont));
                                        table.addCell(cell);
                                    } else {
                                        cell.setPhrase(new Phrase(handler.getOBXResult(j, k).replaceAll("<br\\s*/*>", "\n").replace("\t", "\u00a0\u00a0\u00a0\u00a0"), lineFont));
                                        //if this PATHL7 result is from CDC/SG and is greater than 100 characters
                                        if ((handler.getOBXResult(j, k).length() > 100) && (handler.getPatientLocation().equals("SG") || handler.getPatientLocation().equals("CDC"))) {

											//if the Abn, Reference Range and Units are empty or equal to null, give the long result the use of those columns
											if (( handler.getOBXAbnormalFlag(j, k) == null ||handler.getOBXAbnormalFlag(j, k).isEmpty()) &&
											( handler.getOBXReferenceRange(j, k) == null || handler.getOBXReferenceRange(j, k).isEmpty()) &&
											(handler.getOBXUnits(j, k) == null || handler.getOBXUnits(j, k).isEmpty())){
												isLongText = true;
												cell.setColspan(4);
												table.addCell(cell);
											}else {
												//else use the 6 remaining columns, and add a new empty cell that takes the first two columns(Test & Results). 
												//This will allow the corresponding Abn, RR and Units to be printed beneath the long result in the appropriate columns
												cell.setColspan(6);
												table.addCell(cell);
												cell.setPhrase(new Phrase("", lineFont));
												cell.setColspan(2);
												table.addCell(cell);
											}
										}else {
											if (isLongText) {
												cell.setColspan(4);
											}
											// cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
											table.addCell(cell);
										}
										cell.setColspan(1);
									}
									
									
									
								} else { // end PATHHL7 labs
									
									if (isLongText) {
										cell.setColspan(4);
									}
									if (handler instanceof ExcellerisOntarioHandler &&  handler.getOBXValueType(j, k).equals("ED")) {
										embeddedDocumentsToAppend.add(handler.getOBXResult(j, k));
										cell.setPhrase(new Phrase("PDF Report (Appended to end of Laboratory Report)", lineFont));
										table.addCell(cell);
									} else if (handler instanceof ExcellerisOntarioHandler && !((ExcellerisOntarioHandler) handler).getOBXSubId(j, k).isEmpty()) {
										cell.setPhrase(new Phrase(((ExcellerisOntarioHandler) handler).getOBXSubIdWithObservationValue(j, k).replaceAll("<br\\s*/*>", "\n"), lineFont));
										table.addCell(cell);
									} else {
										cell.setPhrase(new Phrase(handler.getOBXResult(j, k).replaceAll("<br\\s*/*>", "\n"), lineFont));
										table.addCell(cell);
									}
									cell.setColspan(1);
								}

                                String abnFlag = handler.getOBXAbnormalFlag(j, k);

                                if (!isLongText) {//if the Abn, RR and Unit columns have not been occupied above
                                    if (handler.getMsgType().equals("PATHL7")) {
                                        cell.setPhrase(new Phrase(abnFlag, lineFont));
                                    } else if ("CLS".equals(handler.getMsgType())) {
                                        cell.setPhrase(new Phrase(
                                                (handler.isOBXAbnormal(j, k) ?
                                                        handler.getOBXAbnormalFlag(j, k) :
                                                        ""),
                                                lineFont));
                                    } else if ("ExcellerisON".equals(handler.getMsgType())) {
                                        cell.setPhrase(new Phrase(StringUtils.trimToEmpty(abnFlag), lineFont));
                                    } else {
                                        if (abnFlag == null || abnFlag.trim().equals(""))
                                            abnFlag = "N";
                                        cell.setPhrase(new Phrase(abnFlag, lineFont));
                                    }

                                    table.addCell(cell);
                                    cell.setPhrase(new Phrase(handler.getOBXReferenceRange(j, k), lineFont));
                                    table.addCell(cell);
                                    cell.setPhrase(new Phrase(handler.getOBXUnits(j, k), lineFont));
                                    table.addCell(cell);
                                }// end of isLongText

                                cell.setPhrase(new Phrase(handler.getTimeStamp(j, k), lineFont));
                                table.addCell(cell);

                                String status = handler.getOBXResultStatus(j, k);
                                if (handler.isTestResultBlocked(j, k)) {
                                    if (!StringUtils.isEmpty(status)) {
                                        status += "/";
                                    }
                                    status += "BLOCKED";
                                }

                                cell.setPhrase(new Phrase(status, lineFont));

                                table.addCell(cell);

                                if (handler.getMsgType().equals("ExcellerisON")) {
                                    cell.setPhrase(new Phrase(!currentLicenseNo.equals(lastLicenseNo) ? currentLicenseNo : "", lineFont));
                                    table.addCell(cell);

                                }
                            } // end else not unstructured.

                            if (!handler.getMsgType().equals("PFHT")) {
                                // add obx comments
                                if (handler.getOBXCommentCount(j, k) > 0) {
                                    cell.setBorder(Rectangle.BOTTOM);

                                    //	cell.setBorderColor(Color.white);

                                    for (int l = 0; l < handler.getOBXCommentCount(j, k); l++) {

                                        cell.setPhrase(new Phrase("", font));
                                        cell.setColspan(1);
                                        table.addCell(cell);

                                        if (handler.getMsgType().equals("ExcellerisON")) {
                                            cell.setColspan(8);
                                        } else {
                                            cell.setColspan(7);
                                        }
                                        cell.setPhrase(new Phrase(handler.getOBXComment(j, k, l).replaceAll("<br\\s*/*>", "\n"), font));
                                        table.addCell(cell);

                                    }

                                    cell.setBorderColor(Color.LIGHT_GRAY);
                                    cell.setColspan(1);
                                }
                                cell.setColspan(1);
                            }
                            // end if not DNS
                        } else if (
                                (handler.getMsgType().equals("EPSILON") && header.equals(handler.getOBXIdentifier(j, k)) && obxName.equals(""))
                                        || (handler.getMsgType().equals("PFHT") && obxName.equals("") && header.equals(handler.getObservationHeader(j, k)))
                                        || (handler.getMsgType().equals("MEDITECH") && obxName.equals(""))
                        ) {

                            cell.setBorder(Rectangle.NO_BORDER);
                            cell.setBorderColor(Color.WHITE);
                            cell.setPadding(0);
                            cell.setPaddingLeft(10);
                            cell.setColspan(7);

                            table.setWidthPercentage(100);
                            cell.setPhrase(new Phrase(handler.getOBXResult(j, k).replaceAll("<br\\s*/*>", "\n"), font));
                            table.addCell(cell);

                            cell.setColspan(1);
                            cell.setBorder(Rectangle.BOTTOM);
                            cell.setBorderColor(Color.LIGHT_GRAY);
                            cell.setPadding(5);
                        }
                        if (handler.getMsgType().equals("PFHT") && !handler.getNteForOBX(j, k).equals("") && handler.getNteForOBX(j, k) != null) {

                            cell.setPaddingLeft(100);
                            cell.setColspan(7);

                            cell.setPhrase(new Phrase(handler.getNteForOBX(j, k).replaceAll("<br\\s*/*>", "\n"), font));
                            table.addCell(cell);
                            cell.setPaddingLeft(5);
                            cell.setColspan(1);

                            if (handler.getOBXCommentCount(j, k) > 0) {
                                cell.setBorder(Rectangle.BOTTOM);
                                cell.setColspan(7);

                                for (int l = 0; l < handler.getOBXCommentCount(
                                        j, k); l++) {

                                    cell.setPhrase(new Phrase(handler
                                            .getOBXComment(j, k, l).replaceAll(
                                                    "<br\\s*/*>", "\n"), font));
                                    table.addCell(cell);

                                }

                                cell.setColspan(1);
                            }
                        }

                    } else {
                        if (handler.getOBXCommentCount(j, k) > 0) {

                            if (handler.getMsgType().equals("ExcellerisON")) {
                                cell.setColspan(8);
                            } else {
                                cell.setColspan(7);
                            }

                            for (int l = 0; l < handler
                                    .getOBXCommentCount(j, k); l++) {

                                cell.setPhrase(new Phrase(handler
                                        .getOBXComment(j, k, l).replaceAll(
                                                "<br\\s*/*>", "\n"), font));
                                table.addCell(cell);

                            }

							cell.setColspan(1);
						}
					} // if (!handler.getOBXResultStatus(j, k).equals("TDIS"))
				}
				
			if (!handler.getMsgType().equals("PFHT")) {
				// add obr comments
				if (handler.getObservationHeader(j, 0).equals(header)) {
					if (handler.getMsgType().equals("ExcellerisON")) { 
						cell.setColspan(8);
					} else {
						cell.setColspan(7);
					}
					// cell.setHorizontalAlignment(Element.ALIGN_LEFT);
					for (int k = 0; k < handler.getOBRCommentCount(j); k++) {
						// the obrName should only be set if it has not been
						// set already which will only have occured if the
						// obx name is "" or if it is the same as the obr name
						if (!obrFlag && handler.getOBXName(j, 0).equals("") && !handler.getMsgType().equals("ExcellerisON")) {

                                cell.setPhrase(new Phrase(handler.getOBRName(j),
                                        boldFont));
                                table.addCell(cell);
                                obrFlag = true;
                            }

                            if (handler.getMsgType().equals("TRUENORTH")) {
                                try {
                                    Phrase phrase = new Phrase();
                                    StringReader strReader = new StringReader(handler.getOBRComment(j, k));
                                    @SuppressWarnings("rawtypes")
                                    ArrayList p = (ArrayList) HTMLWorker.parseToList(strReader, null);
                                    strReader.close();
                                    for (int h = 0; h < p.size(); h++) {
                                        phrase.add((String) p.get(h));
                                        phrase.add("\n");
                                    }
                                    cell.setPhrase(phrase);
                                } catch (Exception e) {
                                    throw new ExceptionConverter(e);
                                }

                            } else {
                                int colSpan = cell.getColspan();
                                cell.setColspan(1);
                                cell.setPhrase(new Phrase("", font));
                                table.addCell(cell);
                                cell.setColspan(colSpan - 1);
                                cell.setPhrase(new Phrase(handler.getOBRComment(j, k)
                                        .replaceAll("<br\\s*/*>", "\n"), font));
                            }
                            table.addCell(cell);
                            cell.setPadding(3);
                        }
                        cell.setColspan(1);
                    }
                }
            } // for (j)

        }// if (isMEDVUE)

        document.add(table);

    }


    /**
     * Returns the color corresponding to the abnormal status flag of a lab result.
     * Red for abnormal/high ("A", "H*"), blue for low ("L*"), black for normal.
     *
     * @param abn String the HL7 abnormal flag value
     * @return Color the display color for the result text
     */
    private Color getTextColor(String abn) {
        Color ret = Color.BLACK;
        if (abn != null && (abn.equals("A") || abn.startsWith("H"))) {
            ret = Color.RED;
        } else if (abn != null && abn.startsWith("L")) {
            ret = Color.BLUE;
        }

        return ret;
    }


    /**
     * Creates and adds the patient/lab information table at the top of the document.
     * Contains patient demographics, ordering physician, dates, and report status.
     *
     * @throws DocumentException if the table cannot be added to the document
     */
    private void createInfoTable() throws DocumentException {

        //Create patient info table
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        float[] pInfoWidths = {2f, 4f, 3f, 2f};
        PdfPTable pInfoTable = new PdfPTable(pInfoWidths);
        cell.setPhrase(new Phrase("Patient Name: ", boldFont));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getPatientName(), font));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase("Home Phone: ", boldFont));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getHomePhone(), font));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase("Date of Birth: ", boldFont));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getDOB(), font));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase("Work Phone: ", boldFont));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getWorkPhone(), font));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase("Age: ", boldFont));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getAge(), font));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase("Sex: ", boldFont));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getSex(), font));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase("Health Care #: ", boldFont));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getHealthNum(), font));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getMsgType().equals("ExcellerisON") ? "Reported by: " : "Patient Location: ", boldFont));
        pInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getPatientLocation(), font));
        pInfoTable.addCell(cell);

        //Create results info table
        PdfPTable rInfoTable = new PdfPTable(2);
        cell.setPhrase(new Phrase("Date of Service: ", boldFont));
        rInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getServiceDate(), font));
        rInfoTable.addCell(cell);
		if (handler.getMsgType().equals("ExcellerisON")) {
			cell.setPhrase(new Phrase("Reported on: ", boldFont));
			rInfoTable.addCell(cell);
			cell.setPhrase(new Phrase(((ExcellerisOntarioHandler) handler).getReportStatusChangeDate(), font));
			rInfoTable.addCell(cell);
		}
        cell.setPhrase(new Phrase("Date Received: ", boldFont));
        rInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(dateLabReceived, font));
        rInfoTable.addCell(cell);
        cell.setPhrase(new Phrase("Report Status: ", boldFont));
        rInfoTable.addCell(cell);
        if (handler.getMsgType().equals("PATHL7")) {
            cell.setPhrase(new Phrase(handler.getOrderStatus(), font));
            rInfoTable.addCell(cell);
        } else {

            cell.setPhrase(new Phrase(handler.getOrderStatus(), font));
            rInfoTable.addCell(cell);
        }
        cell.setPhrase(new Phrase("Client Ref. #: ", boldFont));
        rInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getClientRef(), font));
        rInfoTable.addCell(cell);
        cell.setPhrase(new Phrase("Accession #: ", boldFont));
        rInfoTable.addCell(cell);
        cell.setPhrase(new Phrase(handler.getAccessionNum(), font));
        rInfoTable.addCell(cell);
        if (handler.getMsgType().equals("ExcellerisON") && !((ExcellerisOntarioHandler) handler).getAlternativePatientIdentifier().isEmpty()) {
            cell.setPhrase(new Phrase("Reference #: ", boldFont));
            rInfoTable.addCell(cell);
            cell.setPhrase(new Phrase(((ExcellerisOntarioHandler) handler).getAlternativePatientIdentifier(), font));
            rInfoTable.addCell(cell);
        }

        //Create client table
        float[] clientWidths = {2f, 3f};
        Phrase clientPhrase = new Phrase();
        PdfPTable clientTable = new PdfPTable(clientWidths);
        clientPhrase.add(new Chunk("Requesting Client:  ", boldFont));
        clientPhrase.add(new Chunk(handler.getDocName(), font));
        cell.setPhrase(clientPhrase);
        clientTable.addCell(cell);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("cc: Client:  ", boldFont));
        clientPhrase.add(new Chunk(handler.getCCDocs(), font));
        cell.setPhrase(clientPhrase);
        clientTable.addCell(cell);

        //Create header info table
        float[] tableWidths = {2f, 1f};
        PdfPTable table = new PdfPTable(tableWidths);
        if (multiID != null && multiID.length > 1) {
            cell = new PdfPCell(new Phrase("Version: " + versionNum + " of " + multiID.length, boldFont));
            cell.setBackgroundColor(new Color(210, 212, 255));
            cell.setPadding(3);
            cell.setColspan(2);
            table.addCell(cell);
        }
        cell = new PdfPCell(new Phrase("Detail Results: Patient Info", boldFont));
        cell.setBackgroundColor(new Color(210, 212, 255));
        cell.setPadding(3);
        table.addCell(cell);
        cell.setPhrase(new Phrase("Results Info", boldFont));
        table.addCell(cell);

        // add the created tables to the document
        table = addTableToTable(table, pInfoTable, 1);
        table = addTableToTable(table, rInfoTable, 1);
        table = addTableToTable(table, clientTable, 2);

        table.setWidthPercentage(100);

        document.add(table);
    }

    /**
     * Adds patient and lab information to an RTF document using Chunks and Paragraphs.
     *
     * <p>This is the RTF equivalent of {@link #createInfoTable()}. PdfPTable is not
     * properly supported in RTF output, so this method uses tab-aligned text instead.
     *
     * @throws DocumentException if an OpenPDF document error occurs
     */
    private void addRtfPatientInfo() throws DocumentException {
        Paragraph patientInfo = new Paragraph();

        Phrase clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Patient Name: ", boldFont));
        clientPhrase.add(new Chunk(handler.getPatientName() + "\t\t\t", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Home Phone: ", boldFont));
        clientPhrase.add(new Chunk(handler.getHomePhone() + "\n", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Date of Birth: ", boldFont));
        clientPhrase.add(new Chunk(handler.getDOB() + "\t\t\t\t\t", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Work Phone: ", boldFont));
        clientPhrase.add(new Chunk(handler.getWorkPhone() + "\n", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Age: ", boldFont));
        clientPhrase.add(new Chunk(handler.getAge() + "\t\t\t\t\t\t\t", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Sex: ", boldFont));
        clientPhrase.add(new Chunk(handler.getSex() + "\n", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Health #: ", boldFont));
        clientPhrase.add(new Chunk(handler.getHealthNum() + "\t\t\t\t\t\t", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk(handler.getMsgType().equals("ExcellerisON") ? "Reported by: " : "Patient Location: ", boldFont));
        clientPhrase.add(new Chunk(handler.getPatientLocation() + "\n", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Date of Service: ", boldFont));
        clientPhrase.add(new Chunk(handler.getServiceDate() + "\t\t\t\t", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Date Received: ", boldFont));
        clientPhrase.add(new Chunk(dateLabReceived + "\n", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Report Status: ", boldFont));
        // "F" and "C" are HL7 OBR result status codes: F = Final, C = Corrected
        clientPhrase.add(new Chunk((handler.getOrderStatus().equals("F") ? "Final" : (handler.getOrderStatus().equals("C") ? "Corrected" : "Preliminary")) + "\t\t\t\t\t\t", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Client Ref. #: ", boldFont));
        clientPhrase.add(new Chunk(handler.getClientRef() + "\n", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Accession #: ", boldFont));
        clientPhrase.add(new Chunk(handler.getAccessionNum() + "\t\t\t\t\t", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("Requesting Client:  ", boldFont));
        clientPhrase.add(new Chunk(handler.getDocName() + "\n", font));
        patientInfo.add(clientPhrase);

        clientPhrase = new Phrase();
        clientPhrase.add(new Chunk("cc: Client:  ", boldFont));
        clientPhrase.add(new Chunk(handler.getCCDocs() + "\n\n", font));
        patientInfo.add(clientPhrase);

        document.add(patientInfo);
    }

    /**
     * Nests a table inside another table as a cell spanning the specified number of columns.
     *
     * @param main PdfPTable the parent table to add the nested table to
     * @param add PdfPTable the child table to embed as a cell
     * @param colspan int the number of columns the nested cell should span
     * @return PdfPTable the parent table with the nested cell added
     */
    private PdfPTable addTableToTable(PdfPTable main, PdfPTable add, int colspan) {
        PdfPCell cell = new PdfPCell(add);
        cell.setPadding(3);
        cell.setColspan(colspan);
        main.addCell(cell);
        return main;
    }


    /**
     * Page event handler invoked when each page finishes rendering. Adds the patient
     * identifier and page number to the footer area of every page.
     *
     * @param writer PdfWriter the active PDF writer
     * @param document Document the current document
     */
    public void onEndPage(PdfWriter writer, Document document) {
        try {

            Rectangle page = document.getPageSize();
            PdfContentByte cb = writer.getDirectContent();
            BaseFont bf = BaseFont.createFont(BaseFont.TIMES_ROMAN, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            int pageNum = document.getPageNumber();
            float width = page.getWidth();
            float height = page.getHeight();

            if (pageNum > 1){
				//add patient name header for every page but the first.
				String pageIdentifier = getPageIdentifier();
                cb.beginText();
                cb.setFontAndSize(bf, 8);
                cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, pageIdentifier, 575, height - 30, 0);
                cb.endText();

            }


            // throw any exceptions
        } catch (Exception e) {
            throw new ExceptionConverter(e);
        }
    }

	/**
	 * Builds the patient identifier line for the page footer. Prefers Health Insurance
	 * Number over demographic number; falls back to name only if neither is available.
	 *
	 * @return String the patient identifier text for footer display
	 */
	private String getPageIdentifier() {
		if (handler.getMsgType().equals("ExcellerisON")) {
			if (!handler.getHealthNum().isEmpty()) {
				return handler.getPatientName() + " " + handler.getHealthNum();
			} else if (!handler.getHealthNum().equalsIgnoreCase("UNKNOWN")) {
				return handler.getPatientName() + " " + handler.getDOB();
			}
		}
		return handler.getPatientName();
	}

	public boolean isUnstructuredDoc() {
		return isUnstructuredDoc;
	}

    public void setUnstructuredDoc(boolean isUnstructuredDoc) {
        this.isUnstructuredDoc = isUnstructuredDoc;
    }

    public OutputStream getOs() {
        return os;
    }

    public void setOs(OutputStream os) {
        this.os = os;
    }

    public void closeOs() throws IOException {
        if (this.os != null) {
            flushOs();
            os.close();
        }
    }

    private void flushOs() throws IOException {
        if (this.os != null) {
            os.flush();
        }
    }

    public MessageHandler getHandler() {
        return handler;
    }

    public void setHandler(MessageHandler handler) {
        this.handler = handler;
    }

    public List<MessageHandler> getHandlers() {
        return handlers;
    }

    public void setHandlers(List<MessageHandler> handlers) {
        this.handlers = handlers;
    }

}
