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

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.util.Doc2PDF;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for attaching PDF documents to messages.
 * 
 * <p>This action handles the conversion of HTML content to PDF format and manages
 * the attachment process for the messaging system. It supports both single and
 * multiple PDF attachments, with preview capabilities and incremental attachment
 * handling for large document sets.</p>
 * 
 * <p>The action operates in two main modes:</p>
 * <ul>
 *   <li><b>Preview Mode:</b> Generates a PDF preview directly to the response stream
 *       for immediate viewing without saving the attachment</li>
 *   <li><b>Attachment Mode:</b> Converts HTML to PDF and stores it in the session
 *       bean for later inclusion in the message</li>
 * </ul>
 * 
 * <p>For multiple attachments, the action uses an incremental processing approach
 * where each attachment is processed individually with a small delay between
 * operations to prevent overwhelming the server. The action tracks progress using
 * currentAttachmentCount and totalAttachmentCount in the session bean.</p>
 * 
 * <p>Technical implementation details:</p>
 * <ul>
 *   <li>Uses Doc2PDF utility for HTML to PDF conversion</li>
 *   <li>Stores attachments as Base64-encoded strings in the session</li>
 *   <li>Implements a 500ms delay between multiple attachments</li>
 *   <li>Returns different result codes based on processing state</li>
 * </ul>
 * 
 * @version 2.0
 * @since 2005
 * @see MsgSessionBean
 * @see Doc2PDF
 */
public class MsgAttachPDF2Action extends ActionSupport {
    /**
     * HTTP request object for accessing session and parameters.
     */
    HttpServletRequest request = ServletActionContext.getRequest();
    
    /**
     * HTTP response object for sending PDF content directly to the client.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Logger instance for tracking attachment operations and debugging.
     */
    private static Logger logger = MiscUtils.getLogger();

    /**
     * Executes the PDF attachment workflow.
     * 
     * <p>This method handles two primary operations:</p>
     * 
     * <p><b>Preview Mode (isPreview=true):</b></p>
     * <ul>
     *   <li>Converts the HTML source text to PDF</li>
     *   <li>Streams the PDF directly to the response</li>
     *   <li>Does not store the PDF as an attachment</li>
     * </ul>
     * 
     * <p><b>Attachment Mode (isPreview=false):</b></p>
     * <ul>
     *   <li>Converts HTML to PDF and encodes as Base64</li>
     *   <li>Stores in session bean for message composition</li>
     *   <li>Handles multiple attachments incrementally</li>
     *   <li>Returns "attaching" if more attachments pending</li>
     *   <li>Returns SUCCESS when all attachments complete</li>
     * </ul>
     * 
     * <p>The method implements a stateful attachment process where multiple
     * attachments are processed one at a time with a 500ms delay between
     * each to prevent server overload.</p>
     * 
     * @return SUCCESS when all attachments are complete, "attaching" when more
     *         attachments are pending, or null for preview mode
     * @throws IOException if there's an error writing to the response stream
     * @throws ServletException if there's a servlet processing error
     */
    public String execute() throws IOException, ServletException {
        logger.info("Starting...");

        // Retrieve the message session bean containing attachment state
        MsgSessionBean bean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");

        // Handle preview mode - generate PDF and send directly to client
        if (isPreview) {
            logger.info("Got source text: " + srcText);
            
            // Convert HTML to PDF and stream to response
            Doc2PDF.parseString2PDF(request, response, "<HTML>" + srcText + "</HTML>");
            // Reset preview flag after processing
            isPreview = false;
        } else {
            // Handle attachment mode - store PDF in session for message composition

            try {
                if (bean != null) {
                    // Clear existing attachments if this is a new attachment session
                    if (isNew) {
                        logger.debug("Nullifying attachment");
                        bean.nullAttachment();
                    }

                    // Set the total number of attachments to process
                    bean.setTotalAttachmentCount(Integer.parseInt(attachmentCount));

                    // Process next attachment if more remain
                    if (bean.getCurrentAttachmentCount() < bean.getTotalAttachmentCount()) {
                        // Convert HTML to Base64-encoded PDF binary
                        String resultString = Doc2PDF.parseString2Bin(request, response, "<HTML>" + srcText + "</HTML>");
                        // Store the attachment in the session bean
                        bean.setAppendPDFAttachment(resultString, attachmentTitle);
                        // Increment the processed attachment counter
                        bean.setCurrentAttachmentCount(bean.getCurrentAttachmentCount() + 1);
                        logger.info("Sleeping for a short period...");
                        // Brief delay to prevent server overload with multiple attachments
                        Thread.sleep(500);
                    }

                    // Check if all attachments have been processed
                    if (bean.getCurrentAttachmentCount() >= bean.getTotalAttachmentCount()) {
                        // Reset counters for future attachment sessions
                        bean.setTotalAttachmentCount(0);
                        bean.setCurrentAttachmentCount(0);
                        return SUCCESS;
                    } else {
                        // More attachments to process - return intermediate status
                        return "attaching";
                    }
                } else {
                    logger.error("Bean is null");
                }
            } catch (Exception e) {
                logger.error("Error: " + e.getMessage(), e);
            }

        }
        return null;
    }
    /**
     * Total number of attachments to be processed in this session.
     * Used for multiple attachment handling.
     */
    private String attachmentCount = "0";
    
    /**
     * Title or filename for the current attachment.
     * Displayed in the message attachment list.
     */
    private String attachmentTitle = "";
    
    /**
     * HTML source text to be converted to PDF.
     * Contains the document content including formatting.
     */
    private String srcText = "";
    
    /**
     * Flag indicating whether to preview the PDF without attaching.
     * When true, PDF is streamed directly to the response.
     */
    private boolean isPreview = false;
    
    /**
     * Flag indicating attachment is in progress.
     * Currently not actively used in the execute logic.
     */
    private boolean isAttaching = false;
    
    /**
     * Flag indicating whether this is a new attachment session.
     * When true, existing attachments are cleared before processing.
     */
    private boolean isNew = true;
    
    /**
     * Array of indices for batch attachment processing.
     * Currently not actively used in the implementation.
     */
    private String[] indexArray;

    /**
     * Gets the total number of attachments to process.
     * 
     * @return the attachment count as a string
     */
    public String getAttachmentCount() {
        return attachmentCount;
    }

    /**
     * Sets the total number of attachments to process.
     * 
     * @param attachmentCount the number of attachments as a string
     */
    public void setAttachmentCount(String attachmentCount) {
        this.attachmentCount = attachmentCount;
    }

    /**
     * Gets the title of the current attachment.
     * 
     * @return the attachment title
     */
    public String getAttachmentTitle() {
        return attachmentTitle;
    }

    /**
     * Sets the title of the current attachment.
     * 
     * @param attachmentTitle the title to set for the attachment
     */
    public void setAttachmentTitle(String attachmentTitle) {
        this.attachmentTitle = attachmentTitle;
    }

    /**
     * Gets the HTML source text to be converted to PDF.
     * 
     * @return the HTML source text
     */
    public String getSrcText() {
        return srcText;
    }

    /**
     * Sets the HTML source text to be converted to PDF.
     * 
     * @param srcText the HTML content to convert
     */
    public void setSrcText(String srcText) {
        this.srcText = srcText;
    }

    /**
     * Checks if the action is in preview mode.
     * 
     * @return true if preview mode is enabled, false otherwise
     */
    public boolean isPreview() {
        return isPreview;
    }

    /**
     * Sets the preview mode flag.
     * 
     * @param preview true to enable preview mode, false to disable
     */
    public void setIsPreview(boolean preview) {
        isPreview = preview;
    }

    /**
     * Checks if attachment is currently in progress.
     * 
     * @return true if attaching, false otherwise
     */
    public boolean isAttaching() {
        return isAttaching;
    }

    /**
     * Sets the attaching in progress flag.
     * 
     * @param attaching true if attachment is in progress
     */
    public void setIsAttaching(boolean attaching) {
        isAttaching = attaching;
    }

    /**
     * Checks if this is a new attachment session.
     * 
     * @return true if new session, false otherwise
     */
    public boolean isNew() {
        return isNew;
    }

    /**
     * Sets whether this is a new attachment session.
     * 
     * @param aNew true to indicate a new session requiring attachment clearing
     */
    public void setIsNew(boolean aNew) {
        isNew = aNew;
    }

    /**
     * Gets the array of indices for batch processing.
     * 
     * @return the index array
     */
    public String[] getIndexArray() {
        return indexArray;
    }

    /**
     * Sets the array of indices for batch processing.
     * 
     * @param indexArray the array of indices to set
     */
    public void setIndexArray(String[] indexArray) {
        this.indexArray = indexArray;
    }
}
