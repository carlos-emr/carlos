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

import io.github.carlos_emr.carlos.messenger.data.MsgMessageData;
import io.github.carlos_emr.carlos.commn.model.OscarMsgType;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.messenger.data.MsgProviderData;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 Action for handling message creation and sending in the CARLOS EMR messaging system.
 *
 * <p>This action processes the message composition form submission, validates security permissions,
 * and sends the message to selected recipients. It manages attachments and saves messages
 * to the database.</p>
 *
 * <p>Key responsibilities include:
 * <ul>
 *   <li>Security validation for message write permissions</li>
 *   <li>Processing message content, subject, and attachments</li>
 *   <li>Managing recipient lists</li>
 *   <li>Associating messages with patient demographics when applicable</li>
 *   <li>Saving sent message preferences for future use</li>
 * </ul>
 * </p>
 * 
 * @version 2.0 Struts2 migration
 * @since 2003-07-21
 * @see MsgSessionBean
 * @see MsgMessageData
 */
/**
 * Struts2 action for composing and creating new messenger messages between providers.
 *
 * @since 2001-01-01
 */
public class MsgCreateMessage2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private MessengerDemographicManager messengerDemographicManager = SpringUtils.getBean(MessengerDemographicManager.class);

    /**
     * Main execution method that processes and sends the message.
     *
     * <p>This method performs the following steps:
     * <ol>
     *   <li>Validates user has write permissions for messaging</li>
     *   <li>Retrieves message data from session bean</li>
     *   <li>Processes recipients and message content</li>
     *   <li>Creates and sends the message to all recipients</li>
     *   <li>Saves user preferences for future messages</li>
     * </ol>
     * </p>
     *
     * @return String {@code SUCCESS} if message was sent, or {@code ERROR} if no valid recipients or message creation failed
     * @throws IOException if there's an I/O error during processing
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks message write permissions
     */
    public String execute()
            throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null) {
            throw new SecurityException("No valid session found");
        }

        // Validate security permissions
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "w", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // Use the authenticated session for sender identity — never trust session bean for providerNo
        String userNo = loggedInInfo.getLoggedInProviderNo();

        // Retrieve message session data for non-security fields (attachments, display name).
        // The session bean must have been initialized by MsgDisplayMessages2Action before this
        // action is invoked.
        MsgSessionBean bean;
        bean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");
        if (bean == null) {
            throw new SecurityException("Message session not initialized");
        }

        // Defensive check: verify the session bean belongs to the logged-in user
        if (!loggedInInfo.getLoggedInProviderNo().equals(bean.getProviderNo())) {
            throw new SecurityException("Cannot access another provider's messages");
        }

        String userName = bean.getUserName();
        String att = bean.getAttachment();
        String pdfAtt = bean.getPDFAttachment();
        bean.nullAttachment();
        String message = this.getMessage();
        String[] providers = this.getProvider();
        String subject = this.getSubject();
        // Clear message data from session after retrieval
        bean.setMessage(null);
        bean.setSubject(null);

        MiscUtils.getLogger().debug("Providers: " + Arrays.toString(providers));
        MiscUtils.getLogger().debug("Subject length: " + (subject != null ? subject.length() : 0));
        MiscUtils.getLogger().debug("Message length: " + (message != null ? message.length() : 0));

        String sentToWho = null;
        String messageId = null;
        String demographic_no = this.getDemographic_no();
        if (demographic_no != null && (demographic_no.equals("") || "null".equals(demographic_no))) {
            demographic_no = null;
        }

        java.util.ArrayList<MsgProviderData> providerListing;


        subject = (subject == null) ? "" : subject.trim();
        if (subject.isEmpty()) {
            subject = "none";
        }

        //FIXME remove MsgMessageData.getDups4/getProviderStructure/sendMessage2/createSentToString (JDBC-based) and migrate to MessagingManager/MessagingManagerImpl (Hibernate-based)
        MsgMessageData messageData = new MsgMessageData();
        providers = messageData.getDups4(providers);
        providerListing = messageData.getProviderStructure(loggedInInfo, providers);

        if (providerListing == null || providerListing.isEmpty()) {
            MiscUtils.getLogger().warn("No valid recipients after deduplication; message not sent");
            request.setAttribute("createMessageError", "No valid recipients selected. Please select at least one recipient.");
            return ERROR;
        }

        sentToWho = messageData.createSentToString(providerListing);
        if (sentToWho != null) {
            sentToWho = sentToWho.trim();
        }
        messageId = messageData.sendMessage2(message, subject, userName, sentToWho, userNo, providerListing, att, pdfAtt, OscarMsgType.GENERAL_TYPE);

        if (messageId == null || messageId.isEmpty()) {
            MiscUtils.getLogger().error("sendMessage2 returned null or empty messageId");
            request.setAttribute("createMessageError", "Failed to send message. Please try again.");
            return ERROR;
        }

        // Link message and demographic if both IDs are valid (> 0).
        // ConversionUtils.fromIntString() returns 0 for null/invalid input, never null.
        Integer parsedMessageId = ConversionUtils.fromIntString(messageId);
        Integer parsedDemoNo = ConversionUtils.fromIntString(demographic_no);
        if (parsedMessageId > 0 && parsedDemoNo > 0) {
            messengerDemographicManager.attachDemographicToMessage(loggedInInfo, parsedMessageId, parsedDemoNo);
        }

        request.setAttribute("SentMessageProvs", sentToWho);

        return SUCCESS;
    }

    private String[] provider = new String[0];
    private String message;
    private String subject;
    private String demographic_no;

    /** @return String[] array of recipient provider numbers */
    public String[] getProvider() {
        return provider;
    }

    /** @param provider String[] array of recipient provider numbers */
    @StrutsParameter
    public void setProvider(String[] provider) {
        this.provider = provider;
    }

    /** @return String the message body content (may contain HTML/Markdown) */
    public String getMessage() {
        return message;
    }

    /** @param message String the message body content */
    @StrutsParameter
    public void setMessage(String message) {
        this.message = message;
    }

    /** @return String the message subject line */
    public String getSubject() {
        return subject;
    }

    /** @param subject String the message subject line */
    @StrutsParameter
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /** @return String the demographic number to associate with this message, or null */
    public String getDemographic_no() {
        return demographic_no;
    }

    /** @param demographic_no String the demographic number to associate with this message */
    @StrutsParameter
    public void setDemographic_no(String demographic_no) {
        this.demographic_no = demographic_no;
    }
}
