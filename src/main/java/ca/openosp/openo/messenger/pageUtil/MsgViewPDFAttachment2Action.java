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

import ca.openosp.openo.commn.dao.MessageTblDao;
import ca.openosp.openo.commn.model.MessageTbl;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.util.ConversionUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for viewing PDF attachments stored directly in message records.
 * 
 * <p>This action retrieves PDF attachments that have been stored as binary data
 * in the pdfAttachment field of the message table. Unlike MsgViewPDF2Action which
 * handles XML-wrapped PDFs, this action deals with PDF data stored directly as
 * a byte array in the database.</p>
 * 
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Validates read permissions for messaging</li>
 *   <li>Retrieves message record by attachment ID</li>
 *   <li>Extracts PDF binary data from the message</li>
 *   <li>Prepares PDF data for display or download</li>
 * </ul>
 * 
 * <p>Storage differences from other attachment types:</p>
 * <ul>
 *   <li>PDF is stored as byte array in pdfAttachment field</li>
 *   <li>No XML wrapping or Base64 encoding in database</li>
 *   <li>Direct binary storage for efficient retrieval</li>
 * </ul>
 * 
 * <p>The action sets the PDF data as a request attribute for the view layer
 * to handle the actual streaming to the browser. This separation allows for
 * different rendering options in the JSP layer.</p>
 * 
 * @version 2.0
 * @since 2003
 * @see MessageTbl
 * @see MsgViewPDF2Action
 * @see MsgViewAttachment2Action
 */
public class MsgViewPDFAttachment2Action extends ActionSupport {
    /**
     * HTTP request object for setting attributes for the view.
     */
    HttpServletRequest request = ServletActionContext.getRequest();
    
    /**
     * HTTP response object, maintained for consistency but not actively used.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Security manager for enforcing read permissions on messaging operations.
     */
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Executes the PDF attachment retrieval workflow.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates that the user has read permissions for messaging</li>
     *   <li>Retrieves the message record using the attachment ID</li>
     *   <li>Extracts the PDF binary data from the message</li>
     *   <li>Converts the byte array to a String (potential encoding issue)</li>
     *   <li>Sets the PDF data as a request attribute for the view</li>
     * </ol>
     * 
     * <p>Note: The conversion of PDF byte array to String using new String()
     * is problematic as PDF files are binary data. This could corrupt the PDF
     * content if it contains bytes that don't map to valid characters in the
     * default character encoding. The PDF should be handled as binary data
     * throughout the process.</p>
     * 
     * <p>The attachment ID parameter is expected to correspond to a message ID
     * that contains a PDF attachment in its pdfAttachment field.</p>
     * 
     * @return SUCCESS constant to forward to PDF display page
     * @throws IOException if there's an I/O error
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks read permissions for messaging
     */
    public String execute() throws IOException, ServletException {

        // Verify user has read permission for messages
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_msg", "r", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        String pdfAtt = null;

        // Retrieve message and extract PDF attachment
        MessageTblDao dao = SpringUtils.getBean(MessageTblDao.class);
        MessageTbl m = dao.find(ConversionUtils.fromIntString(attachId));
        if (m != null) {
            // BUG: Converting binary PDF to String can corrupt the data
            pdfAtt = new String(m.getPdfAttachment());
        }
        
        // Set PDF data for view layer
        request.setAttribute("PDFAttachment", pdfAtt);
        request.setAttribute("attId", attachId);

        return SUCCESS;
    }

    /**
     * The attachment/message ID containing the PDF to retrieve.
     */
    String attachId;

    /**
     * Gets the attachment ID.
     * 
     * @return String the attachment ID, empty string if null
     */
    public String getAttachId() {
        if (this.attachId == null) {
            this.attachId = new String();
        }
        return this.attachId;
    }

    /**
     * Sets the attachment ID to retrieve.
     * 
     * @param str String the attachment/message ID
     */
    public void setAttachId(String str) {
        this.attachId = str;
    }
}
