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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
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
 *   <li>Returns SUCCESS even on errors to prevent error page display</li>
 *   <li>Logs exceptions but doesn't propagate them to user</li>
 *   <li>No validation that file_id is within bounds</li>
 * </ul>
 * 
 * @version 2.0
 * @since 2003
 * @see PlaywrightPdfConverter
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

            if (pdfAttachment != null && !pdfAttachment.isEmpty()) {
                // Extract all CONTENT tags from XML
                List<String> attList = getXMLTagValues(pdfAttachment, "CONTENT");
                // Get the specific PDF by index
                String pdfFile = attList.get(fileID);
                // Stream Base64-encoded PDF to browser
                streamPdfFromBase64(response, pdfFile);
            }
        } catch (Exception e) {
            // Log error but return SUCCESS to avoid error page
            MiscUtils.getLogger().error("Error", e);
            return SUCCESS;
        }

        return SUCCESS;
    }

    /**
     * Extracts values between XML tags from a string.
     */
    private static List<String> getXMLTagValues(String xml, String section) {
        List<String> values = new ArrayList<>();
        String beginTag = "<" + section + ">";
        String endTag = "</" + section + ">";
        int index = xml.indexOf(beginTag);
        while (index != -1) {
            int lastIndex = xml.indexOf(endTag, index);
            if (lastIndex == -1 || lastIndex < index) break;
            values.add(xml.substring(index + beginTag.length(), lastIndex));
            xml = xml.substring(lastIndex + endTag.length());
            index = xml.indexOf(beginTag);
        }
        return values;
    }

    /**
     * Decodes a Base64-encoded PDF string and streams it to the HTTP response.
     */
    private static void streamPdfFromBase64(HttpServletResponse response, String base64Pdf) throws IOException {
        byte[] pdfBytes = Base64.decodeBase64(base64Pdf.getBytes(StandardCharsets.UTF_8));
        response.setContentType("application/pdf");
        response.setContentLength(pdfBytes.length);
        response.setHeader("Expires", "0");
        response.setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
        response.setHeader("Pragma", "public");
        try (OutputStream out = response.getOutputStream();
             InputStream in = new BufferedInputStream(new ByteArrayInputStream(pdfBytes))) {
            byte[] buf = new byte[32 * 1024];
            int nRead;
            while ((nRead = in.read(buf)) != -1) {
                out.write(buf, 0, nRead);
            }
            out.flush();
        }
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
