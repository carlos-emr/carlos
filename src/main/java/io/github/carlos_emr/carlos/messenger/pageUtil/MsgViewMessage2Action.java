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

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.model.OscarMsgType;
import io.github.carlos_emr.carlos.managers.MessagingManager;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage;
import org.owasp.encoder.Encode;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Struts2 action for viewing individual messages with full details and attachments.
 * 
 * <p>This is the primary action for displaying message content when a user clicks on
 * a message from their inbox, sent items, or deleted messages list. It handles the
 * complete message viewing workflow including marking messages as read and managing
 * demographic associations.</p>
 *
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Retrieves and displays complete message content with metadata</li>
 *   <li>Marks messages as read (except for sent items)</li>
 *   <li>Manages demographic-message associations</li>
 *   <li>Processes special message types (e.g., OSCAR_REVIEW_TYPE)</li>
 *   <li>Manages attachment indicators for regular and PDF attachments</li>
 * </ul>
 *
 * <p>The action stores extensive message data in the session for display,
 * including message body, subject, sender, recipients, date/time, and attachment
 * information. It then redirects to the ViewMessage.jsp page for rendering.</p>
 * 
 * @version 2.0
 * @since 2003
 * @see MessagingManager
 * @see MessengerDemographicManager
 * @see MsgDisplayMessage
 * @see OscarMsgType
 */
public class MsgViewMessage2Action extends ActionSupport {
    /**
     * HTTP request object for accessing parameters and session.
     */
    HttpServletRequest request = ServletActionContext.getRequest();
    
    /**
     * HTTP response object used for redirecting to the view page.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Security manager for enforcing read permissions on messaging operations.
     */
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    
    /**
     * Manager for core messaging operations including message retrieval and status updates.
     */
    private MessagingManager messagingManager = SpringUtils.getBean(MessagingManager.class);
    
    /**
     * Manager for demographic-message associations.
     */
    private MessengerDemographicManager messengerDemographicManager = SpringUtils.getBean(MessengerDemographicManager.class);

    /**
     * Executes the message viewing workflow with demographic integration support.
     * 
     * <p>This method performs a complex series of operations:</p>
     * <ol>
     *   <li>Validates user has read permissions for messaging</li>
     *   <li>Retrieves the message using the provided message ID</li>
     *   <li>Processes attached demographics</li>
     *   <li>Handles special message types and their associated links</li>
     *   <li>Stores all message data in session for display</li>
     *   <li>Marks the message as read (unless viewing sent items)</li>
     *   <li>Optionally links demographics to the message</li>
     *   <li>Redirects to the message viewing JSP page</li>
     * </ol>
     * 
     * <p>Parameters processed:</p>
     * <ul>
     *   <li>messageID - The message to view</li>
     *   <li>messagePosition - Position in message list for navigation</li>
     *   <li>linkMsgDemo - Flag to link message to demographic</li>
     *   <li>demographic_no - Demographic to link</li>
     *   <li>orderBy - Ordering for message list</li>
     *   <li>from - Source page (default "messenger")</li>
     *   <li>boxType - Type of message box (inbox/sent/deleted)</li>
     * </ul>
     * 
     * @return NONE as the method performs a redirect instead of forwarding
     * @throws IOException if there's an error with the redirect
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks read permissions
     */
    public String execute() throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null) {
            throw new SecurityException("No valid session found");
        }

        // Verify user has read permission for messages
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // Always use the logged-in provider's identity — never override from session bean
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // Extract request parameters
        String messageNo = request.getParameter("messageID");
        String messagePosition = request.getParameter("messagePosition");
        String linkMsgDemo = request.getParameter("linkMsgDemo");
        String demographic_no = request.getParameter("demographic_no");
        String orderBy = request.getParameter("orderBy");
        String from = request.getParameter("from") == null ? "messenger" : request.getParameter("from");
        String boxType = request.getParameter("boxType") == null ? "" : request.getParameter("boxType");

        // Validate messageNo before use.
        // ConversionUtils.fromIntString() returns 0 for null/invalid input, never null.
        Integer parsedMessageNo = ConversionUtils.fromIntString(messageNo);
        if (parsedMessageNo <= 0) {
            MiscUtils.getLogger().warn("Invalid or missing messageID parameter");
            response.sendRedirect(request.getContextPath() + "/messenger/DisplayMessages.jsp");
            return NONE;
        }

        // Retrieve the message and process its content
        MsgDisplayMessage msgDisplayMessage = messagingManager.getInboxMessage(loggedInInfo, parsedMessageNo);

        // Early return if message not found
        if (msgDisplayMessage == null) {
            MiscUtils.getLogger().warn("Message not found: ID=" + parsedMessageNo);
            response.sendRedirect(request.getContextPath() + "/messenger/DisplayMessages.jsp");
            return NONE;
        }

        int messageType = msgDisplayMessage.getType();
        String msgType_link = msgDisplayMessage.getTypeLink();

        // Get demographics already attached to this message
        Integer msgId = ConversionUtils.fromIntString(msgDisplayMessage.getMessageId());
        Map<Integer, String> attachedDemographics = (msgId > 0)
                ? messengerDemographicManager.getAttachedDemographicNameMap(loggedInInfo, msgId)
                : new HashMap<>();

        // Store all message data in session for display
        request.getSession().setAttribute("attachedDemographics", attachedDemographics);
        request.getSession().setAttribute("viewMessageMessage", msgDisplayMessage.getMessageBody());
        request.getSession().setAttribute("viewMessageSubject", msgDisplayMessage.getThesubject());
        request.getSession().setAttribute("viewMessageSentby", msgDisplayMessage.getSentby());
        request.getSession().setAttribute("viewMessageSentto", msgDisplayMessage.getSentto());
        request.getSession().setAttribute("viewMessageTime", msgDisplayMessage.getThetime());
        request.getSession().setAttribute("viewMessageDate", msgDisplayMessage.getThedate());
        request.getSession().setAttribute("viewMessageAttach", msgDisplayMessage.getAttach());
        request.getSession().setAttribute("viewMessagePDFAttach", msgDisplayMessage.getPdfAttach());
        request.getSession().setAttribute("viewMessageId", messageNo);
        request.getSession().setAttribute("viewMessageNo", messageNo);
        request.getSession().setAttribute("viewMessagePosition", messagePosition);
        request.getSession().setAttribute("from", from);
        request.getSession().setAttribute("providerNo", providerNo);

        if (orderBy != null) {
            request.getSession().setAttribute("orderBy", orderBy);
        }

        // Store message type if present
        if (messageType > 0) {
            request.getSession().setAttribute("msgType", messageType + "");
        }

        // Process OSCAR review type messages with special link handling
        if (messageType == OscarMsgType.OSCAR_REVIEW_TYPE && msgType_link != null) {
            // Parse the type link into a structured format
            HashMap<String, List<String>> hashMap = new HashMap<String, List<String>>();
            String[] keyValues = msgType_link.split(",");

            for (String s : keyValues) {
                String[] keyValue = s.split(":");
                if (keyValue.length == 4) {
                    // Group related links by their primary key
                    if (hashMap.containsKey(keyValue[0])) {
                        hashMap.get(keyValue[0]).add(keyValue[1] + ":" + keyValue[2] + ":" + keyValue[3]);
                    } else {
                        List<String> list = new ArrayList<String>();
                        list.add(keyValue[1] + ":" + keyValue[2] + ":" + keyValue[3]);
                        hashMap.put(keyValue[0], list);
                    }
                } else {
                    MiscUtils.getLogger().debug("Skipping malformed msgType_link segment (expected 4 colon-separated parts): " + s);
                }
            }

            request.getSession().setAttribute("msgTypeLink", hashMap);
        }

        MiscUtils.getLogger().debug("viewMessagePosition: " + messagePosition + "IsLastMsg: " + request.getAttribute("viewMessageIsLastMsg"));

        // Mark message as read unless viewing sent items (boxType 1)
        if (!"1".equals(boxType)) {
            Long msgIdLong = ConversionUtils.fromLongString(msgDisplayMessage.getMessageId());
            if (msgIdLong > 0L) {
                messagingManager.setMessageRead(loggedInInfo, msgIdLong, providerNo);
            }
        }

        // Handle demographic linking if requested
        if (linkMsgDemo != null && demographic_no != null) {
            if (linkMsgDemo.equalsIgnoreCase("true")) {
                Integer parsedDemoNo = ConversionUtils.fromIntString(demographic_no);
                if (parsedMessageNo > 0 && parsedDemoNo > 0) {
                    messengerDemographicManager.attachDemographicToMessage(loggedInInfo, parsedMessageNo, parsedDemoNo);
                }
            }
        }

        // Set today's date for display
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy");
        request.getSession().setAttribute("today", simpleDateFormat.format(new Date(System.currentTimeMillis())));

        // Validate boxType against allowlist before using in redirect URL
        if (!boxType.matches("[0-3]?")) {
            boxType = "";
        }

        // Redirect to the message viewing page with encoded parameters
        String actionforward = request.getContextPath() + "/messenger/ViewMessage.jsp?boxType="
                + Encode.forUriComponent(boxType) + "&linkMsgDemo=" + Encode.forUriComponent(linkMsgDemo != null ? linkMsgDemo : "");
        response.sendRedirect(actionforward);

        // Return NONE since we're redirecting rather than forwarding
        return NONE;
    }
}
