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
 * Struts2 action for viewing file attachments associated with messages.
 * 
 * <p>This action retrieves and displays file attachments that have been included
 * with messages in the messaging system. It handles the retrieval of attachment
 * data from the database and prepares it for display in the user interface.
 * Unlike PDF attachments which are stored separately, these are typically
 * Base64-encoded file attachments stored directly in the message table.</p>
 * 
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Validates read permissions for messaging operations</li>
 *   <li>Retrieves attachment data from the message table</li>
 *   <li>Prepares attachment for display or download</li>
 *   <li>Maintains attachment ID for reference</li>
 * </ul>
 * 
 * <p>Security considerations:</p>
 * <ul>
 *   <li>Requires read permission on messages to view attachments</li>
 *   <li>No additional validation that user has access to specific message</li>
 *   <li>Attachment content is not sanitized before display</li>
 * </ul>
 * 
 * @version 2.0
 * @since 2003
 * @see MessageTbl
 * @see MsgViewPDFAttachment2Action
 * @see MsgAttachPDF2Action
 */
public class MsgViewAttachment2Action extends ActionSupport {
    /**
     * HTTP request object for accessing parameters and setting attributes.
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
     * Executes the attachment viewing workflow.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates that the user has read permissions for messaging</li>
     *   <li>Retrieves the message record using the attachment ID</li>
     *   <li>Extracts the attachment data from the message</li>
     *   <li>Sets the attachment data and ID as request attributes</li>
     * </ol>
     * 
     * <p>The attachment data is typically Base64-encoded and needs to be
     * decoded by the view layer for proper display or download. The method
     * does not perform any content type detection or validation.</p>
     * 
     * <p>Note: This implementation assumes the attachment ID corresponds to
     * a message ID, which may be a design flaw as attachments and messages
     * should have separate identifiers.</p>
     * 
     * @return SUCCESS constant to forward to attachment display page
     * @throws IOException if there's an I/O error
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks read permissions for messaging
     */
    public String execute() throws IOException, ServletException {

        // Verify user has read permission for messages
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_msg", "r", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        String att = null;

        // Retrieve message and extract attachment data
        MessageTblDao dao = SpringUtils.getBean(MessageTblDao.class);
        MessageTbl m = dao.find(ConversionUtils.fromIntString(attachId));
        if (m != null) {
            att = m.getAttachment();
        }
        
        // Set attachment data for display
        request.setAttribute("Attachment", att);
        request.setAttribute("attId", attachId);

        return SUCCESS;
    }

    /**
     * The attachment/message ID to retrieve.
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
