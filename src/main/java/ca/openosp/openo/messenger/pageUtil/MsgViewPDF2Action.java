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


package ca.openosp.openo.messenger.pageUtil;

import java.io.IOException;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.util.Doc2PDF;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

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
     *   <li>Retrieves the PDF attachment XML from the session</li>
     *   <li>Parses the XML to extract CONTENT tags containing PDFs</li>
     *   <li>Retrieves the specific PDF by its index (file_id)</li>
     *   <li>Streams the PDF binary content to the browser</li>
     * </ol>
     * 
     * <p>The method expects the PDF attachment data to be stored in the session
     * under the key "PDFAttachment" as an XML string. The file_id parameter
     * indicates which PDF to extract from the XML (0-based index).</p>
     * 
     * <p>Error handling is minimal - exceptions are logged but the method
     * returns SUCCESS regardless to prevent error pages from displaying.
     * This could result in blank responses if the PDF cannot be retrieved.</p>
     * 
     * @return SUCCESS constant regardless of whether PDF was successfully displayed
     * @throws IOException if there's an error writing to response stream
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks read permissions for messaging
     */
    public String execute() throws IOException, ServletException {
        // Verify user has read permission for messages
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_msg", "r", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        try {
            // Retrieve PDF attachment XML from session
            String pdfAttachment = (String) request.getSession().getAttribute("PDFAttachment");
            String id = this.getFile_id();
            int fileID = Integer.parseInt(id);

            if (pdfAttachment != null && pdfAttachment.length() != 0) {
                // Extract all CONTENT tags from XML
                Vector attVector = Doc2PDF.getXMLTagValue(pdfAttachment, "CONTENT");
                // Get the specific PDF by index
                String pdfFile = (String) attVector.elementAt(fileID);
                // Stream PDF to browser
                Doc2PDF.PrintPDFFromBin(response, pdfFile);
            }
        } catch (Exception e) {
            // Log error but return SUCCESS to avoid error page
            MiscUtils.getLogger().error("Error", e);
            return SUCCESS;
        }

        return SUCCESS;
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
