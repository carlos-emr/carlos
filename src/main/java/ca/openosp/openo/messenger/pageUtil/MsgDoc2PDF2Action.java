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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.util.Doc2PDF;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for converting HTML content to PDF documents.
 * 
 * <p>This action provides functionality to convert HTML text to PDF format,
 * either for preview purposes or for attachment to messages. It supports two
 * modes of operation: preview mode where the PDF is streamed directly to the
 * response, and attachment mode where the PDF is stored in the session bean
 * for later inclusion in a message.</p>
 * 
 * <p>The action wraps the provided HTML text in HTML tags before conversion,
 * ensuring proper document structure for the PDF generation process.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>HTML to PDF conversion using Doc2PDF utility</li>
 *   <li>Preview mode for immediate PDF viewing</li>
 *   <li>Attachment mode for message composition</li>
 *   <li>Support for custom PDF titles</li>
 * </ul>
 * 
 * <p>This class is marked as final to prevent inheritance, ensuring the
 * PDF conversion behavior cannot be modified through subclassing.</p>
 * 
 * @version 1.0
 * @since 2003
 * @see Doc2PDF
 * @see MsgSessionBean
 * @see MsgAttachPDF2Action
 */
public final class MsgDoc2PDF2Action extends ActionSupport {
    /**
     * HTTP request object for accessing session and parameters.
     */
    HttpServletRequest request = ServletActionContext.getRequest();
    
    /**
     * HTTP response object for streaming PDF content to client.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Executes the HTML to PDF conversion workflow.
     * 
     * <p>This method operates in two modes based on the isPreview flag:</p>
     * 
     * <p><b>Preview Mode (isPreview=true):</b></p>
     * <ul>
     *   <li>Converts HTML to PDF and streams directly to response</li>
     *   <li>User can view the PDF immediately in browser</li>
     *   <li>PDF is not saved for attachment</li>
     * </ul>
     * 
     * <p><b>Attachment Mode (isPreview=false):</b></p>
     * <ul>
     *   <li>Converts HTML to Base64-encoded PDF binary</li>
     *   <li>Stores PDF in session bean with specified title</li>
     *   <li>PDF can be attached to message being composed</li>
     * </ul>
     * 
     * <p>The method wraps the source text in HTML tags to ensure proper
     * document structure for PDF generation. After processing, the preview
     * flag is reset to false.</p>
     * 
     * @return SUCCESS constant indicating successful conversion
     * @throws IOException if there's an error writing to response stream
     * @throws ServletException if there's a servlet processing error
     */
    public String execute() throws IOException, ServletException {
        if (this.getIsPreview()) {
            // Preview mode - stream PDF directly to browser
            Doc2PDF.parseString2PDF(request, response, "<HTML>" + srcText + "</HTML>");
            this.setIsPreview(false);
        } else {
            // Attachment mode - store PDF in session for message composition

            MsgSessionBean bean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");

            if (bean != null) {
                // Convert HTML to Base64-encoded PDF and store in session
                bean.setAppendPDFAttachment(Doc2PDF.parseString2Bin(request, response, "<HTML>" + srcText + "</HTML>"), pdfTitle);
                this.setIsPreview(false);
            } else {
                MiscUtils.getLogger().debug(" ca.openosp.openo.messenger.pageUtil.MsgSessionBean is null");
            }
        }
        return SUCCESS;
    }

    /**
     * HTML source text to be converted to PDF.
     */
    String srcText;
    
    /**
     * Flag indicating whether to preview PDF (true) or attach it (false).
     */
    boolean isPreview;
    
    /**
     * Session ID for maintaining state across requests.
     * Currently not actively used in the implementation.
     */
    String jsessionid;
    
    /**
     * Array of URIs for processing multiple documents.
     * Currently not actively used in the implementation.
     */
    String[] uriArray;
    
    /**
     * Attachment number for tracking multiple attachments.
     * Currently not actively used in the implementation.
     */
    String attachmentNumber = null;
    
    /**
     * Title for the PDF attachment when saved to session.
     */
    String pdfTitle;

    /**
     * Gets the HTML source text to be converted.
     * 
     * @return String the HTML source text
     */
    public String getSrcText() {
        return srcText;
    }

    /**
     * Sets the HTML source text to be converted.
     * 
     * @param srcText String the HTML content to convert to PDF
     */
    public void setSrcText(String srcText) {
        this.srcText = srcText;
    }


    /**
     * Checks if the action is in preview mode.
     * 
     * @return boolean true for preview mode, false for attachment mode
     */
    public boolean getIsPreview() {
        return isPreview;
    }

    /**
     * Sets the preview mode flag.
     * 
     * @param isPreview boolean true to enable preview mode, false for attachment mode
     */
    public void setIsPreview(boolean isPreview) {
        this.isPreview = isPreview;
    }

    /**
     * Gets the session ID.
     * 
     * @return String the session ID
     */
    public String getJsessionid() {
        return jsessionid;
    }

    /**
     * Sets the session ID.
     * 
     * @param jsessionid String the session ID to set
     */
    public void setJsessionid(String jsessionid) {
        this.jsessionid = jsessionid;
    }

    /**
     * Gets the PDF title for the attachment.
     * 
     * @return String the PDF title
     */
    public String getPdfTitle() {
        return pdfTitle;
    }

    /**
     * Sets the PDF title for the attachment.
     * 
     * @param pdfTitle String the title to assign to the PDF
     */
    public void setPdfTitle(String pdfTitle) {
        this.pdfTitle = pdfTitle;
    }

    /**
     * Gets the array of URIs.
     * 
     * @return String[] the array of URIs
     */
    public String[] getUriArray() {
        return uriArray;
    }

    /**
     * Sets the array of URIs.
     * 
     * @param uriArray String[] the array of URIs to set
     */
    public void setUriArray(String[] uriArray) {
        this.uriArray = uriArray;
    }

    /**
     * Sets the attachment number.
     * 
     * @param attachmentNumber String the attachment number to set
     */
    public void setAttachmentNumber(String attachmentNumber) {
        this.attachmentNumber = attachmentNumber;
    }

    /**
     * Gets the attachment number.
     * 
     * @return String the attachment number
     */
    public String getAttachmentNumber() {
        return attachmentNumber;
    }
}
