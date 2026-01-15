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

import ca.openosp.openo.messenger.data.MsgMessageData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.OscarMsgType;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.managers.MessagingManagerImpl;
import ca.openosp.openo.managers.MessengerDemographicManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.messenger.data.ContactIdentifier;
import ca.openosp.openo.messenger.data.MsgProviderData;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 Action for handling message creation and sending in the OpenO EMR messaging system.
 * 
 * <p>This action processes the message composition form submission, validates security permissions,
 * and sends the message to selected recipients. It handles both local and remote providers,
 * manages attachments, and saves messages to the database.</p>
 * 
 * <p>Key responsibilities include:
 * <ul>
 *   <li>Security validation for message write permissions</li>
 *   <li>Processing message content, subject, and attachments</li>
 *   <li>Managing recipient lists (local and remote providers)</li>
 *   <li>Associating messages with patient demographics when applicable</li>
 *   <li>Saving sent message preferences for future use</li>
 * </ul>
 * </p>
 * 
 * @version 2.0
 * @since 2002
 * @see MsgSessionBean
 * @see MsgMessageData
 * @see MessagingManagerImpl
 */
public class MsgCreateMessage2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private static MessengerDemographicManager messengerDemographicManager = SpringUtils.getBean(MessengerDemographicManager.class);

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
     * @return SUCCESS constant for Struts navigation
     * @throws IOException if there's an I/O error during processing
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks message write permissions
     */
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute()
            throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // Validate security permissions
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "w", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // Retrieve message session data
        // TODO: Remove dependency on HTTP sessions
        MsgSessionBean bean;
        bean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");
        String userNo = bean.getProviderNo();
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
        MiscUtils.getLogger().debug("Subject: " + subject);
        MiscUtils.getLogger().debug("Message: " + message);

        String sentToWho = null;
        String messageId = null;
        String demographic_no = this.getDemographic_no();
        if (demographic_no != null && (demographic_no.equals("") || "null".equals(demographic_no))) {
            demographic_no = null;
        }

        java.util.ArrayList<MsgProviderData> providerListing, remoteProviderListing;


        subject.trim();
        if (subject.length() == 0) {
            subject = "none";
        }

        //FIXME remove these deprecated methods and use the Messenger Managers instead (the deprecated classes still use JDBC)
        MsgMessageData messageData = new MsgMessageData();
        providers = messageData.getDups4(providers);
        providerListing = messageData.getProviderStructure(loggedInInfo, providers);

        //FIXME currently unused - I think...
        remoteProviderListing = messageData.getRemoteProvidersStructure();

        /*
         * A demographic that has not consented to the Integrator cannot be sent to remote providers.
         * This is a short circuit that will return a warning that the patient has not consented.
         * This message will not send until the non-consenting patient has consented -or- is removed
         * from the message.
         */
        if (demographic_no != null
                && userPropertyDao.getProp(UserProperty.INTEGRATOR_PATIENT_CONSENT) != null
                && ("1".equals(userPropertyDao.getProp(UserProperty.INTEGRATOR_PATIENT_CONSENT).getValue())
                || "1".equals(userPropertyDao.getProp(UserProperty.INTEGRATOR_DEMOGRAPHIC_CONSENT).getValue()))) {
            if (MessagingManagerImpl.doesContainRemoteRecipient(loggedInInfo, providerListing)
                    && !messengerDemographicManager.isPatientConsentedForIntegrator(loggedInInfo, Integer.parseInt(demographic_no))) {
                return error("messenger.CreateMessage.patientConsentError");
            }
        }

        /*
         * The Integrator does not support attachments at this time.  Stop attachments from being sent externally.
         */
        if ((att != null || pdfAtt != null) && MessagingManagerImpl.doesContainRemoteRecipient(loggedInInfo, providerListing)) {
            return error("messenger.CreateMessage.attachmentsNotPermitted");
        }

        //FIXME remove these deprecated methods and use the Messenger Managers instead
        sentToWho = messageData.createSentToString(providerListing);
        sentToWho = sentToWho + " " + messageData.getRemoteNames(remoteProviderListing);
        sentToWho = sentToWho.trim();
        messageId = messageData.sendMessage2(message, subject, userName, sentToWho, userNo, providerListing, att, pdfAtt, OscarMsgType.GENERAL_TYPE);

        // link msg and demographic if both messageId and demographic_no are not null.
        if (messageId != null && demographic_no != null) {
            messengerDemographicManager.attachDemographicToMessage(loggedInInfo, Integer.parseInt(messageId), Integer.parseInt(demographic_no));
        }

        request.setAttribute("SentMessageProvs", sentToWho.toString());

        return SUCCESS;
    }

    private String error(String messageKey) {
        String message = getText(messageKey);
        request.setAttribute("createMessageError", message);
        request.setAttribute("messageSubject", this.getSubject());
        request.setAttribute("messageBody", this.getMessage());
        request.setAttribute("demographic_no", this.getDemographic_no());
        List<ContactIdentifier> replyList = MessagingManagerImpl.createContactIdentifierList(this.getProvider());
        ArrayNode jsonArray = objectMapper.createArrayNode();
        for (ContactIdentifier contact : replyList) {
            jsonArray.add(objectMapper.valueToTree(contact));
        }
        request.setAttribute("replyList", jsonArray);
        return "error";
    }
    
    private String[] provider = new String[0];
    private String message;
    private String subject;
    private String demographic_no;

    public String[] getProvider() {
        return provider;
    }

    public void setProvider(String[] provider) {
        this.provider = provider;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDemographic_no() {
        return demographic_no;
    }

    public void setDemographic_no(String demographic_no) {
        this.demographic_no = demographic_no;
    }
}
