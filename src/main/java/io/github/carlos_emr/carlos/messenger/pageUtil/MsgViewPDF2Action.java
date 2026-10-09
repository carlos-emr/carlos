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


package io.github.carlos_emr.carlos.messenger.pageUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.ErrorPageMessage;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.Doc2PDF;

import org.apache.commons.codec.binary.Base64;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action for viewing PDF attachments stored in XML format within messages.
 *
 * <p>This action retrieves and displays PDF attachments that have been stored in an
 * XML structure within the session. It handles PDF files that were attached to messages
 * using the XML-based attachment system where multiple PDF files can be embedded within
 * a single XML document structure with CONTENT tags.</p>
 *
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Validates read permissions for messaging</li>
 *   <li>Retrieves PDF attachment XML from session</li>
 *   <li>Extracts specific PDF by file ID from XML structure</li>
 *   <li>Streams PDF content directly to browser</li>
 * </ul>
 *
 * <p>The PDF attachment storage format:</p>
 * <ul>
 *   <li>PDFs are stored as Base64-encoded strings within XML</li>
 *   <li>Multiple PDFs can exist within one XML document</li>
 *   <li>Each PDF is wrapped in a CONTENT tag</li>
 *   <li>Files are accessed by their index (file_id)</li>
 * </ul>
 *
 * <p>Error handling:</p>
 * <ul>
 *   <li>Returns NONE after streaming PDF content directly to the response</li>
 *   <li>Rejects a missing, non-numeric or out-of-range file_id with HTTP 400</li>
 *   <li>Answers a session that holds no PDF attachment with HTTP 404</li>
 *   <li>Answers unreadable attachment XML, or an attachment that is not a PDF, with HTTP 500</li>
 *   <li>Shows each refusal on the CARLOS error page with a short translated message saying what to do
 *       ({@link ErrorPageMessage}); no request value is echoed</li>
 * </ul>
 *
 * @version 2.0
 * @since 2003
 * @see Doc2PDF
 * @see MsgViewPDFAttachment2Action
 * @see MsgAttachPDF2Action
 */
public class MsgViewPDF2Action extends ActionSupport {
    /**
     * Message shown on the error page with HTTP 400 when file_id is missing, not a number, or out of range.
     */
    static final String INVALID_FILE_ID = "messenger.ViewPDFFile.invalidFileId";

    /**
     * Message shown on the error page with HTTP 404 when the session holds no PDF attachment to view.
     */
    static final String NO_ATTACHMENT = "messenger.ViewPDFFile.noAttachment";

    /**
     * Message shown on the error page with HTTP 500 when the attachment held in the session cannot be read.
     */
    static final String UNREADABLE = "messenger.ViewPDFFile.unreadable";

    /**
     * Bytes every PDF starts with; checked before anything is written to the response.
     */
    private static final byte[] PDF_HEADER = new byte[] {'%', 'P', 'D', 'F', '-'};

    /**
     * HTTP request object for accessing session data.
     */
    HttpServletRequest request = ServletActionContext.getRequest();

    /**
     * HTTP response object for streaming PDF content to browser.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Security manager for enforcing read permissions on messaging operations.
     */
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Executes the PDF viewing workflow.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates that the user has read permissions for messaging</li>
     *   <li>Reads file_id, the 0-based index of the PDF to show</li>
     *   <li>Retrieves the PDF attachment XML from the session</li>
     *   <li>Parses the XML to extract CONTENT tags containing PDFs</li>
     *   <li>Retrieves the specific PDF by its index (file_id)</li>
     *   <li>Checks that it is a PDF, then streams it to the browser</li>
     * </ol>
     *
     * <p>The method expects the PDF attachment data to be stored in the session
     * under the key "PDFAttachment" as an XML string. The file_id parameter
     * indicates which PDF to extract from the XML (0-based index).</p>
     *
     * <p>Every path owns the response and returns {@link #NONE}. In the order they
     * are checked: a missing or non-numeric file_id gets HTTP 400, a session with no
     * PDF attachment gets HTTP 404, attachment XML that cannot be read gets HTTP 500,
     * an out-of-range file_id gets HTTP 400, an attachment that does not decode to a
     * PDF (one that failed to render when it was attached) gets HTTP 500, and a PDF
     * that cannot be written gets HTTP 500 from {@link Doc2PDF#PrintPDFFromBytes} if
     * nothing has been sent yet (a stream cut off part-way keeps its 200).</p>
     *
     * @return {@link #NONE} always, after streaming the PDF or sending an error response
     * @throws IOException if there's an error writing to response stream
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks read permissions for messaging
     */
    public String execute() throws IOException, ServletException {
        // Verify user has read permission for messages
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_msg", "r", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // A malformed request is rejected before looking at what the session holds
        int fileID;
        try {
            fileID = Integer.parseInt(this.getFile_id());
        } catch (NumberFormatException e) {
            ErrorPageMessage.sendError(request, response, HttpServletResponse.SC_BAD_REQUEST, INVALID_FILE_ID);
            return NONE;
        }

        // Retrieve PDF attachment XML from session; ViewPDFAttachment.jsp puts it there
        String pdfAttachment = (String) request.getSession().getAttribute("PDFAttachment");
        if (pdfAttachment == null || pdfAttachment.isEmpty()) {
            ErrorPageMessage.sendError(request, response, HttpServletResponse.SC_NOT_FOUND, NO_ATTACHMENT);
            return NONE;
        }

        // Extract all CONTENT tags from XML
        List<?> attachments;
        try {
            attachments = Doc2PDF.getXMLTagValue(pdfAttachment, "CONTENT");
        } catch (Exception e) {
            // The session holds attachment XML this action cannot read: a server-side fault
            MiscUtils.getLogger().error("Could not read the PDF attachments held in the session", e);
            ErrorPageMessage.sendError(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, UNREADABLE);
            return NONE;
        }

        // Reject invalid file_id values before accessing the attachment list
        if (fileID < 0 || fileID >= attachments.size()) {
            ErrorPageMessage.sendError(request, response, HttpServletResponse.SC_BAD_REQUEST, INVALID_FILE_ID);
            return NONE;
        }

        // Base64 decoding is lenient: an attachment that failed to render is stored with
        // "null" as its content, which decodes to a few bytes that are not a PDF
        byte[] pdf = Base64.decodeBase64((String) attachments.get(fileID));
        if (!isPdf(pdf)) {
            MiscUtils.getLogger().warn("PDF attachment {} held in the session is not a PDF", fileID);
            ErrorPageMessage.sendError(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, UNREADABLE);
            return NONE;
        }

        // Stream PDF to browser; Doc2PDF sends its own error response if it cannot
        Doc2PDF.PrintPDFFromBytes(response, pdf);
        return NONE;
    }

    /**
     * Checks that decoded attachment bytes start with the PDF header.
     *
     * @param bytes byte[] the decoded attachment
     * @return true if the bytes start with {@code %PDF-}
     */
    private static boolean isPdf(byte[] bytes) {
        return bytes.length >= PDF_HEADER.length
                && Arrays.equals(bytes, 0, PDF_HEADER.length, PDF_HEADER, 0, PDF_HEADER.length);
    }

    /**
     * Attachment parameter, currently not used in implementation.
     */
    String attachment = null;

    /**
     * Index of the PDF file to retrieve from the XML structure.
     */
    String file_id = null;

    /**
     * Sets the attachment parameter.
     *
     * <p>Note: This parameter is not currently used in the execute method.
     * The actual attachment is retrieved from the session.</p>
     *
     * @param attachment String the attachment parameter
     */
    @StrutsParameter
    public void setAttachment(String attachment) {
        this.attachment = attachment;
    }

    /**
     * Gets the attachment parameter.
     *
     * @return String the attachment parameter
     */
    public String getAttachment() {
        return attachment;
    }

    /**
     * Sets the file ID index for PDF retrieval.
     *
     * @param file_id String the 0-based index of the PDF to retrieve
     */
    @StrutsParameter
    public void setFile_id(String file_id) {
        this.file_id = file_id;
    }

    /**
     * Gets the file ID index.
     *
     * @return String the file ID index
     */
    public String getFile_id() {
        return file_id;
    }
}
