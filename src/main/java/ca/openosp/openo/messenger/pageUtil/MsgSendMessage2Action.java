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
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.dao.MessageListDao;
import ca.openosp.openo.commn.dao.MessageTblDao;
import ca.openosp.openo.commn.model.MessageList;
import ca.openosp.openo.commn.model.MessageTbl;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for sending messages to multiple healthcare providers.
 * 
 * <p>This action handles the actual sending of messages after composition. It creates
 * a new message record in the database and distributes it to all selected recipients.
 * The action supports sending to multiple providers simultaneously and tracks the
 * message status for each recipient.</p>
 * 
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Validates write permissions for messaging operations</li>
 *   <li>Creates a message record in the messagetbl table</li>
 *   <li>Creates message list entries for each recipient</li>
 *   <li>Generates a recipient list string for confirmation</li>
 *   <li>Sets default subject if none provided</li>
 * </ul>
 * 
 * <p>Message distribution process:</p>
 * <ol>
 *   <li>Message content is stored once in messagetbl</li>
 *   <li>Each recipient gets an entry in messagelisttbl with "new" status</li>
 *   <li>Recipients can independently read/delete their copy</li>
 * </ol>
 * 
 * <p>Known issues:</p>
 * <ul>
 *   <li>Hardcoded "jay" as sender instead of using actual provider</li>
 *   <li>No attachment handling in this simplified version</li>
 *   <li>No validation of provider IDs before sending</li>
 * </ul>
 * 
 * @version 2.0
 * @since 2003
 * @see MessageTbl
 * @see MessageList
 * @see MsgCreateMessage2Action
 */
public class MsgSendMessage2Action extends ActionSupport {
    /**
     * HTTP request object for accessing session and parameters.
     */
    HttpServletRequest request = ServletActionContext.getRequest();
    
    /**
     * HTTP response object, maintained for consistency but not actively used.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Security manager for enforcing write permissions on messaging operations.
     */
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Executes the message sending workflow.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates user has write permissions for messaging</li>
     *   <li>Retrieves the list of recipient provider IDs</li>
     *   <li>Sets a default subject if none provided</li>
     *   <li>Creates the message record with content and metadata</li>
     *   <li>Creates distribution entries for each recipient</li>
     *   <li>Generates confirmation text with recipient names</li>
     * </ol>
     * 
     * <p>The message is stored once in the database and referenced by multiple
     * message list entries, one per recipient. This allows each recipient to
     * independently manage their copy of the message.</p>
     * 
     * @return SUCCESS constant to display confirmation page
     * @throws IOException if there's an I/O error
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks write permissions for messaging
     */
    public String execute() throws IOException, ServletException {
        // Verify user has write permission for messages
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_msg", "w", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        String[] providers = this.getProvider();
        subject.trim();

        // Set default subject if empty
        if (subject.length() == 0) {
            subject = "none";
        }

        // Build list of recipient names for confirmation display
        ProviderDao pDao = SpringUtils.getBean(ProviderDao.class);
        List<Provider> ps = pDao.getProviders(providers);
        StringBuilder sentToWho = new StringBuilder("Sent to : ");
        for (Provider p : ps) {
            sentToWho.append(" " + p.getFirstName() + " " + p.getLastName() + ". ");
        }

        // Create and persist the message record
        MessageTbl mt = new MessageTbl();
        mt.setDate(new Date());
        mt.setTime(new Date());
        mt.setMessage(message);
        mt.setSubject(subject);
        // BUG: Hardcoded sender name instead of using actual provider
        mt.setSentBy("jay");
        mt.setSentTo(sentToWho.toString());

        MessageTblDao dao = SpringUtils.getBean(MessageTblDao.class);
        dao.persist(mt);

        // Create message list entries for each recipient
        MessageListDao mld = SpringUtils.getBean(MessageListDao.class);
        for (int i = 0; i < providers.length; i++) {
            MessageList ml = new MessageList();
            ml.setMessage(mt.getId());
            ml.setProviderNo(providers[i]);
            ml.setStatus("new");
            mld.persist(ml);
        }
        
        // Set confirmation message for display
        request.setAttribute("SentMessageProvs", sentToWho.toString());
        return SUCCESS;
    }

    /**
     * Array of provider IDs who will receive the message.
     */
    private String[] provider;
    
    /**
     * The message body content.
     */
    private String message;
    
    /**
     * The message subject line.
     */
    private String subject;
    
    /**
     * Associated demographic number if message is patient-related.
     */
    private String demographic_no;

    /**
     * Gets the array of recipient provider IDs.
     * 
     * @return String[] array of provider IDs
     */
    public String[] getProvider() {
        return provider;
    }

    /**
     * Sets the array of recipient provider IDs.
     * 
     * @param provider String[] array of provider IDs to receive the message
     */
    public void setProvider(String[] provider) {
        this.provider = provider;
    }

    /**
     * Gets the message body content.
     * 
     * @return String the message content
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message body content.
     * 
     * @param message String the message content to send
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the message subject line.
     * 
     * @return String the message subject
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the message subject line.
     * 
     * @param subject String the subject line for the message
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Gets the associated demographic number.
     * 
     * @return String the demographic number, may be null
     */
    public String getDemographic_no() {
        return demographic_no;
    }

    /**
     * Sets the associated demographic number.
     * 
     * @param demographic_no String the patient demographic number to associate
     */
    public void setDemographic_no(String demographic_no) {
        this.demographic_no = demographic_no;
    }
}
