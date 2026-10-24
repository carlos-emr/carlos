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

import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.util.Vector;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.Doc2PDF;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action for viewing PDF attachments stored in XML format within messages.
 * 
 * <p>This action retrieves and displays one PDF attachment of the message named by
 * {@code attId}, which the attachment list ({@code ViewPDFAttachment.jsp}) posts with the
 * file's index. It handles PDF files that were attached to messages using the XML-based
 * attachment system where multiple PDF files can be embedded within a single XML document
 * structure with CONTENT tags.</p>
 *
 * <p>The list used to be read from the session, where the attachment list last opened in any
 * window had copied its message's PDFs, so with two viewer windows open a download from one
 * returned the other message's document, possibly for another patient (issue #4673).</p>
 * 
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Validates read permissions for messaging</li>
 *   <li>Retrieves the PDF attachment XML of the message named by {@code attId}</li>
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
 *   <li>Returns SUCCESS even on errors to prevent error page display</li>
 *   <li>Logs exceptions but doesn't propagate them to user</li>
 *   <li>No validation that file_id is within bounds</li>
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
     * HTTP request object.
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
     *   <li>Retrieves the PDF attachment XML of the message named by {@code attId}</li>
     *   <li>Parses the XML to extract CONTENT tags containing PDFs</li>
     *   <li>Retrieves the specific PDF by its index (file_id)</li>
     *   <li>Streams the PDF binary content to the browser</li>
     * </ol>
     * 
     * <p>The PDF attachment XML is read from the message named by {@code attId}; the
     * file_id parameter indicates which PDF to extract from the XML (0-based index).
     * Without a message nothing is streamed.</p>
     * 
     * <p>The action owns its response: it streams the PDF, or answers 400 for a malformed index
     * and 404 when the message has no such PDF, and always returns {@code NONE}, so no page is
     * ever rendered after the PDF bytes.</p>
     * 
     * @return {@link #NONE}, after the response has been written
     * @throws IOException if there's an error writing to response stream
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks read permissions for messaging
     */
    public String execute() throws IOException, ServletException {
        // Verify user has read permission for messages
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_msg", "r", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // The PDFs of the message this download was chosen from, never another window's.
        int fileId;
        try {
            fileId = Integer.parseInt(String.valueOf(this.getFile_id()).trim());
        } catch (NumberFormatException malformed) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        String pdfFile = null;
        int messageId = ConversionUtils.fromIntString(attId);
        if (messageId > 0 && fileId >= 0) {
            MessageTbl message = MsgAttachmentAccess.find(LoggedInInfo.getLoggedInInfoFromSession(request), messageId);
            if (message != null && message.getPdfAttachment() != null) {
                // Every CONTENT tag of the message's PDF list, in order; file_id is an index.
                Vector<?> contents;
                try {
                    contents = Doc2PDF.getXMLTagValue(new String(message.getPdfAttachment(), StandardCharsets.UTF_8), "CONTENT");
                } catch (Exception unreadable) {
                    // No content in the log: the list is a patient's chart.
                    MiscUtils.getLogger().error("A message's PDF attachment list could not be read");
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    return NONE;
                }
                if (fileId < contents.size()) {
                    pdfFile = (String) contents.elementAt(fileId);
                }
            }
        }
        if (pdfFile == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        // Writes the PDF, or its own error status when the stored document cannot be decoded.
        Doc2PDF.PrintPDFFromBin(response, pdfFile);
        return NONE;
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
     * The message whose PDF attachments the file index refers to.
     */
    String attId = null;

    /**
     * Sets the message the attachment belongs to.
     *
     * @param attId String the message id posted by the attachment list
     */
    @StrutsParameter
    public void setAttId(String attId) {
        this.attId = attId;
    }

    /**
     * Gets the message the attachment belongs to.
     *
     * @return String the message id
     */
    public String getAttId() {
        return attId;
    }

    /**
     * Sets the attachment parameter.
     * 
     * <p>Note: This parameter is not currently used in the execute method.
     * The attachment is read from the message named by {@code attId}.</p>
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
