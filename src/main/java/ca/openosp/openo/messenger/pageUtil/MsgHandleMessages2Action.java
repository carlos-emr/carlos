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
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.model.MessageTbl;
import ca.openosp.openo.commn.model.MsgDemoMap;
import ca.openosp.openo.managers.MessagingManager;
import ca.openosp.openo.managers.MessengerDemographicManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;


import ca.openosp.openo.messenger.data.ContactIdentifier;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for handling message operations (reply, forward, delete).
 * 
 * <p>This action serves as the central controller for message operations initiated
 * from the message viewing interface. It handles reply, reply all, forward, and
 * delete operations on messages, managing the workflow for each operation type.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Reply to sender with quoted original message</li>
 *   <li>Reply all to include all original recipients</li>
 *   <li>Forward messages with original content preserved</li>
 *   <li>Delete messages (soft delete)</li>
 *   <li>Demographic linking and unlinking support</li>
 *   <li>Integration with remote facilities via Integrator</li>
 * </ul>
 * 
 * <p>The action performs several important tasks:</p>
 * <ul>
 *   <li>Validates security permissions for message access</li>
 *   <li>Manages demographic associations for messages</li>
 *   <li>Formats reply/forward message content with proper quoting</li>
 *   <li>Prepares recipient lists for reply operations</li>
 * </ul>
 * 
 * <p>Message formatting conventions:</p>
 * <ul>
 *   <li>Reply messages prefix lines with '>' character</li>
 *   <li>Original message header includes date, time, and sender</li>
 *   <li>Subject lines are prefixed with "Re:" or "Fwd:" as appropriate</li>
 * </ul>
 * 
 * @version 2.0
 * @since 2003
 * @see MessagingManager
 * @see MessengerDemographicManager
 * @see ContactIdentifier
 */
public class MsgHandleMessages2Action extends ActionSupport {
    /**
     * HTTP request object for accessing parameters and session.
     */
    HttpServletRequest request = ServletActionContext.getRequest();
    
    /**
     * HTTP response object, maintained for consistency but not actively used.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Security manager for enforcing access control on message operations.
     */
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    
    /**
     * Manager for core messaging operations (read, delete, reply).
     */
    private MessagingManager messagingManager = SpringUtils.getBean(MessagingManager.class);
    
    /**
     * Manager for demographic-message associations and cross-facility linking.
     */
    private MessengerDemographicManager messengerDemographicManager = SpringUtils.getBean(MessengerDemographicManager.class);

    /**
     * Executes the message operation based on the action requested.
     * 
     * <p>This method determines which operation to perform (reply, reply all,
     * forward, or delete) based on request parameters and executes the
     * appropriate workflow. It also manages demographic associations for
     * messages, including handling unlinked Integrator demographics.</p>
     * 
     * <p>Operation workflows:</p>
     * <ul>
     *   <li><b>Delete:</b> Marks the message as deleted</li>
     *   <li><b>Reply:</b> Prepares quoted message for reply to sender</li>
     *   <li><b>Reply All:</b> Prepares quoted message for reply to all recipients</li>
     *   <li><b>Forward:</b> Prepares message for forwarding with original content</li>
     * </ul>
     * 
     * <p>The method also handles demographic linking by checking for attached
     * demographics and managing unlinked Integrator demographics for cross-facility
     * messaging.</p>
     * 
     * @return "reply" to forward to message composition page for reply/forward operations,
     *         or SUCCESS for delete operations and display refresh
     * @throws IOException if there's an I/O error
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks required permissions
     */
    public String execute() throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Verify user has permission to read messages
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "r", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        String demographicNo = this.getDemographic_no();
        String unlinkedIntegratorDemographicName = request.getParameter("unlinkedIntegratorDemographicName");

        // Check for attached demographics and update demographicNo if found
        List<MsgDemoMap> msgDemoMap = messengerDemographicManager.getAttachedDemographicList(loggedInInfo, Integer.parseInt(messageNo));
        if (msgDemoMap != null && msgDemoMap.size() > 0) {
            // Use the first attached demographic
            demographicNo = msgDemoMap.get(0).getDemographic_no() + "";
        }
        // Handle unlinked Integrator demographics
        else if (unlinkedIntegratorDemographicName != null && !unlinkedIntegratorDemographicName.isEmpty()) {
            request.setAttribute("unlinkedIntegratorDemographicName", unlinkedIntegratorDemographicName);
            demographicNo = null;
        }

        // Set the final demographic number for the view
        if (demographicNo != null) {
            request.setAttribute("demographic_no", demographicNo);
        }

        // Determine which operation to perform based on request parameters
        java.util.Enumeration enumeration = request.getParameterNames();
        while (enumeration.hasMoreElements()) {
            String param = ((String) enumeration.nextElement());
            if (param.equals("delete")) {
                delete = "Delete";
            } else if (param.equals("reply")) {
                reply = "Reply";
            } else if (param.equals("replyAll")) {
                replyAll = "reply All";
            } else if (param.equals("forward")) {
                forward = "Forward";
            }
        }

        // Process delete operation
        if (delete.compareToIgnoreCase("Delete") == 0) {
            // Soft delete the message
            messagingManager.deleteMessage(loggedInInfo, Integer.parseInt(messageNo));

        } 
        // Process reply or reply all operations
        else if (reply.equalsIgnoreCase("Reply") || (replyAll.equalsIgnoreCase("reply All"))) {

            // Retrieve original message and format for quoting
            StringBuilder theSendMessage = new StringBuilder();
            MessageTbl message = messagingManager.getMessage(loggedInInfo, Integer.parseInt(messageNo));
            String themessage = message.getMessage();
            // Add quote markers to each line
            themessage = themessage.replaceAll("\n", "\n>");

            // Add original message header with timestamp and sender
            theSendMessage = new StringBuilder(String.format("\n\n\n>On %1$td-%1$tm-%1$tY, at %2$s, %3$s wrote: \n", message.getDate(), message.getTime(), message.getSentBy()));
            theSendMessage.append(">" + themessage);

            // Prepare subject line with "Re:" prefix if not already present
            StringBuilder subject = new StringBuilder("");
            if (!message.getSubject().startsWith("Re:")) {
                subject.append("Re:");
            }
            subject.append(message.getSubject());

            // Determine recipients based on reply type
            List<ContactIdentifier> replyList = Collections.emptyList();
            if ("Reply".equalsIgnoreCase(reply)) {
                // Reply to sender only
                replyList = messagingManager.getReplyToSender(loggedInInfo, message);
            } else if ("reply All".equalsIgnoreCase(replyAll)) {
                // Reply to all original recipients
                replyList = messagingManager.getAllMessageReplyRecipients(loggedInInfo, message);
            }

            // Set attributes for message composition page
            request.setAttribute("ReText", theSendMessage.toString());
            request.setAttribute("replyList", replyList);
            request.setAttribute("ReSubject", subject.toString());

            return "reply";

        } 
        // Process forward operation
        else if (forward.equals("Forward")) {
            // Prepare subject with "Fwd:" prefix
            StringBuilder subject = new StringBuilder("Fwd:");
            String themessage = new String();
            StringBuilder theSendMessage = new StringBuilder();

            // Retrieve original message
            MessageTbl forwardMessage = messagingManager.getMessage(loggedInInfo, Integer.parseInt(messageNo));

            subject.append(forwardMessage.getSubject());
            themessage = forwardMessage.getMessage();
            // Add quote markers (Note: bug - this replaces newlines incorrectly)
            themessage = themessage.replace('\n', '>');
            theSendMessage = new StringBuilder(themessage);
            theSendMessage.insert(0, "\n\n\n>");

            // Set attributes for message composition page
            request.setAttribute("ReText", theSendMessage.toString());
            request.setAttribute("ReSubject", subject.toString());
            return "reply";
        }

        return SUCCESS;
    }


    /**
     * Reply action flag.
     */
    String reply;
    
    /**
     * Reply all action flag.
     */
    String replyAll;
    
    /**
     * Delete action flag.
     */
    String delete;
    
    /**
     * Forward action flag.
     */
    String forward;
    
    /**
     * Message ID to operate on.
     */
    String messageNo;
    
    /**
     * Associated demographic number.
     */
    String demographic_no;

    /**
     * Gets the forward action flag.
     * 
     * @return String the forward flag, empty string if null
     */
    public String getForward() {
        if (this.forward == null) {
            this.forward = new String();
        }
        return this.forward;
    }

    /**
     * Sets the forward action flag.
     * 
     * @param str String the forward flag value
     */
    public void setForward(String str) {
        this.forward = str;
    }

    /**
     * Sets the message number to operate on.
     * 
     * @param messageNo String the message ID
     */
    public void setMessageNo(String messageNo) {
        this.messageNo = messageNo;
    }

    /**
     * Gets the message number.
     * 
     * @return String the message ID, empty string if null
     */
    public String getMessageNo() {
        return messageNo != null ? messageNo : "";
    }

    /**
     * Sets the reply action flag.
     * 
     * <p>Clears other action flags to ensure only one operation is performed.</p>
     * 
     * @param reply String the reply flag value
     */
    public void setReply(String reply) {
        this.reply = reply;
        this.replyAll = "";
        this.delete = "";
    }

    /**
     * Gets the reply action flag.
     * 
     * @return String the reply flag, empty string if null
     */
    public String getReply() {
        return reply != null ? reply : "";
    }

    /**
     * Sets the reply all action flag.
     * 
     * <p>Clears other action flags to ensure only one operation is performed.</p>
     * 
     * @param replyAll String the reply all flag value
     */
    public void setReplyAll(String replyAll) {
        this.replyAll = replyAll;
        this.reply = "";
        this.delete = "";
    }

    /**
     * Gets the reply all action flag.
     * 
     * @return String the reply all flag, empty string if null
     */
    public String getReplyAll() {
        return replyAll != null ? replyAll : "";
    }

    /**
     * Sets the delete action flag.
     * 
     * <p>Clears other action flags to ensure only one operation is performed.</p>
     * 
     * @param delete String the delete flag value
     */
    public void setDelete(String delete) {
        this.delete = delete;
        this.reply = "";
        this.replyAll = "";
    }

    /**
     * Gets the delete action flag.
     * 
     * @return String the delete flag, empty string if null
     */
    public String getDelete() {
        return delete != null ? delete : "";
    }

    /**
     * Gets the demographic number.
     * 
     * @return String the demographic number
     */
    public String getDemographic_no() {
        return demographic_no;
    }

    /**
     * Sets the demographic number.
     * 
     * @param demographic_no String the demographic number to set
     */
    public void setDemographic_no(String demographic_no) {
        this.demographic_no = demographic_no;
    }

}
