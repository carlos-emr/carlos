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
import java.util.Collections;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.commn.model.MsgDemoMap;
import io.github.carlos_emr.carlos.managers.MessagingManager;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;


import io.github.carlos_emr.carlos.messenger.data.ContactIdentifier;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
    /** The value the {@code delete} flag holds when this request deletes its message. */
    private static final String DELETE_FLAG = "Delete";

    /**
     * HTTP request object for accessing parameters and session.
     */
    HttpServletRequest request = ServletActionContext.getRequest();
    
    /**
     * HTTP response object, used to refuse a delete that is not posted.
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
     * messages.</p>
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
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Verify user has permission to read messages
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "r", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // Delete is this route's only write: Reply, Reply All and Forward just open the compose page.
        // ViewMessage.jsp posts it, but the route's name has no verb HttpMethodGuardFilter knows, so a
        // GET carrying "delete" (which has no CSRF token) deleted the message (issue #3394). The test is
        // the one the delete below makes: the parameter, or a value Struts already bound to the field.
        boolean deletes = request.getParameter("delete") != null || DELETE_FLAG.equalsIgnoreCase(delete);
        if (deletes && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // Module access alone does not authorize a saved message. Check before reading its
        // linked patients or quoting its body, including a reply/forward reconstructed on GET.
        int requestedMessageId = io.github.carlos_emr.carlos.util.ConversionUtils.fromIntString(messageNo);
        boolean sharedChart = MsgAttachmentAccess.find(loggedInInfo, requestedMessageId) == null;
        String chartPatient = request.getParameter("chartPatientNo");
        if (sharedChart && (deletes || MsgAttachmentAccess.find(loggedInInfo, requestedMessageId, chartPatient) == null)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        String demographicNo = this.getDemographic_no();
        if (sharedChart) {
            demographicNo = Integer.toString(io.github.carlos_emr.carlos.util.ConversionUtils.fromIntString(chartPatient));
            request.setAttribute("chartPatientNo", demographicNo);
        }

        // Check for attached demographics and update demographicNo if found
        List<MsgDemoMap> msgDemoMap = sharedChart ? Collections.emptyList()
                : messengerDemographicManager.getAttachedDemographicList(loggedInInfo, requestedMessageId);
        if (msgDemoMap != null && msgDemoMap.size() > 0) {
            // Use the first attached demographic
            demographicNo = msgDemoMap.get(0).getDemographic_no() + "";
        }

        // Set the final demographic number for the view
        if (demographicNo != null) {
            request.setAttribute("demographic_no", demographicNo);
        }

        // Determine which operation to perform based on request parameters
        if (request.getParameter("delete") != null) {
            delete = DELETE_FLAG;
        } else if (request.getParameter("reply") != null) {
            reply = "Reply";
        } else if (request.getParameter("replyAll") != null) {
            replyAll = "reply All";
        } else if (request.getParameter("forward") != null) {
            forward = "Forward";
        }

        // Process delete operation
        if (DELETE_FLAG.equalsIgnoreCase(delete)) {
            // Soft delete the message
            messagingManager.deleteMessage(loggedInInfo, requestedMessageId);

        } 
        // Process reply or reply all operations
        else if ("Reply".equalsIgnoreCase(reply) || "reply All".equalsIgnoreCase(replyAll)) {

            // Retrieve original message and format for quoting
            StringBuilder theSendMessage = new StringBuilder();
            MessageTbl message = messagingManager.getMessage(loggedInInfo, requestedMessageId);
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
            request.setAttribute("archiveMessageId", Integer.parseInt(messageNo));
            keepForwardedAttachments(message.getId());

            return "reply";

        } 
        // Process forward operation
        else if ("Forward".equalsIgnoreCase(forward)) {
            // Prepare subject with "Fwd:" prefix
            StringBuilder subject = new StringBuilder("Fwd:");
            String themessage = new String();
            StringBuilder theSendMessage = new StringBuilder();

            // Retrieve original message
            MessageTbl forwardMessage = messagingManager.getMessage(loggedInInfo, requestedMessageId);

            subject.append(forwardMessage.getSubject());
            themessage = forwardMessage.getMessage();
            // Quote each line, as Reply does. Replacing the line break itself joined the lines of a message
            // stored with "\n" breaks; a "\r\n" break keeps its "\r" ahead of the "\n>".
            themessage = themessage.replace("\n", "\n>");
            theSendMessage = new StringBuilder(themessage);
            theSendMessage.insert(0, "\n\n\n>");

            // Set attributes for message composition page
            request.setAttribute("ReText", theSendMessage.toString());
            request.setAttribute("ReSubject", subject.toString());
            request.setAttribute("archiveMessageId", Integer.parseInt(messageNo));
            keepForwardedAttachments(forwardMessage.getId());
            return "reply";
        }

        return SUCCESS;
    }

    /**
     * Hands the compose page a key of its own that starts with the chart items saved for this
     * message ("Save Attachments" in the view's Attachment window), so the reply or forward sends
     * what was saved for this message and whatever is attached in this window only. The items
     * used to wait on the login-wide messenger bean, where a compose page opened anywhere, for any
     * patient, took them (issue #4673).
     */
    private void keepForwardedAttachments(Integer messageId) {
        if (messageId != null && messageId > 0) {
            request.setAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE,
                    MsgAttachmentStaging.isKey(request.getParameter(MsgAttachmentStaging.KEY_PARAMETER))
                            ? request.getParameter(MsgAttachmentStaging.KEY_PARAMETER)
                            : MsgAttachmentStaging.composeKey(request.getSession(), messageId));
        }
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
    @StrutsParameter
    public void setForward(String str) {
        this.forward = str;
    }

    /**
     * Sets the message number to operate on.
     * 
     * @param messageNo String the message ID
     */
    @StrutsParameter
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
    @StrutsParameter
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
    @StrutsParameter
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
    @StrutsParameter
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
    @StrutsParameter
    public void setDemographic_no(String demographic_no) {
        this.demographic_no = demographic_no;
    }

}
