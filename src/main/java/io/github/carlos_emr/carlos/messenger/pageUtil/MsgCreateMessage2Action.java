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

import io.github.carlos_emr.carlos.messenger.data.ContactIdentifier;
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
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
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
 * <p><b>Attachments.</b> The message takes the attachments of the compose window it was written
 * in, named by the form's {@code attachmentKey} ({@link MsgAttachmentStaging}), and takes them
 * once. They used to come from the login-wide {@link MsgSessionBean}, so with two compose windows
 * open the message sent from one carried the chart PDFs attached in the other, for another patient
 * (issue #4673). Chart PDFs are refused on a message linked to a patient other than the one they
 * were rendered for, and a message whose attachments the session released while its window was
 * open is refused once rather than sent without them. When the message does not go out, the
 * attachments are put back and the compose page is shown again with the error and what was
 * written.</p>
 * 
 * @version 2.0 Struts2 migration
 * @since 2003-07-21
 * @see MsgSessionBean
 * @see MsgMessageData
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

        String submission = request.getParameter(MessengerSubmissionGuard.PARAMETER);
        request.setAttribute(MessengerSubmissionGuard.PARAMETER, submission);
        String userName = bean.getUserName();
        // This compose window's own attachments, taken once so a second submit of the same form
        // cannot send them again. Every path below that does not send puts them back.
        HttpSession session = request.getSession();
        MsgAttachmentStaging.Staged staged = MsgAttachmentStaging.take(session, attachmentKey);
        String att = staged == null ? null : staged.itemAttachment();
        String pdfAtt = staged == null ? null : staged.pdfAttachment();
        // A failure before the message is stored must not lose this window's attachments.
        boolean sent = false;
        try {
            String message = this.getMessage();
            String[] providers = this.getProvider();
            String subject = this.getSubject();

            MiscUtils.getLogger().debug("Providers: " + Arrays.toString(providers));
            MiscUtils.getLogger().debug("Subject length: " + (subject != null ? subject.length() : 0));
            MiscUtils.getLogger().debug("Message length: " + (message != null ? message.length() : 0));

            String sentToWho = null;
            String messageId = null;
            String demographic_no = this.getDemographic_no();
            if (demographic_no != null && (demographic_no.equals("") || "null".equals(demographic_no))) {
                demographic_no = null;
            }
            // ConversionUtils.fromIntString() returns 0 for null/invalid input, never null.
            int parsedDemoNo = ConversionUtils.fromIntString(demographic_no);

            // The session keeps a few compose windows' attachments, and staging in other windows
            // released this one's while it was open. Its page may show them, so the message is not
            // sent without them unannounced: the first send is refused with the reason, and sending
            // again goes out without them.
            if (staged == null && MsgAttachmentStaging.claimDropped(session, attachmentKey)) {
                MiscUtils.getLogger().warn("Refused a message whose attachments were released before it was sent");
                return redisplay(session, null, "This message was not sent: its attachments were released because"
                        + " attachments were being prepared in too many other windows at once. Attach them again,"
                        + " or send the message again without them.", parsedDemoNo);
            }

            // Chart PDFs go only on a message linked to the patient they were rendered for: the
            // linked patient can be changed or cleared after attaching.
            if (pdfAtt != null && staged.pdfDemographicNo() != parsedDemoNo) {
                MiscUtils.getLogger().warn("Refused a message whose chart PDFs were attached for another patient");
                return redisplay(session, staged, "The attached chart documents were prepared for a different patient"
                        + " than the one this message is linked to. Attach them again for the linked patient,"
                        + " or clear the message.", parsedDemoNo);
            }

            java.util.ArrayList<MsgProviderData> providerListing;


            subject = (subject == null) ? "" : subject.trim();
            if (subject.isEmpty()) {
                subject = "none";
            }

            if (subject.codePointCount(0, subject.length()) > MessageTbl.SUBJECT_MAX_LENGTH) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return redisplay(session, staged, "Subject exceeds the maximum length of "
                        + MessageTbl.SUBJECT_MAX_LENGTH + " characters. Shorten it and send again.", parsedDemoNo);
            }

            //FIXME remove MsgMessageData.getDups4/getProviderStructure/sendMessage2/createSentToString (JDBC-based) and migrate to MessagingManager/MessagingManagerImpl (Hibernate-based)
            MsgMessageData messageData = new MsgMessageData();
            providers = messageData.getDups4(providers);
            providerListing = messageData.getProviderStructure(loggedInInfo, providers);

            if (providerListing == null || providerListing.isEmpty()) {
                MiscUtils.getLogger().warn("No valid recipients after deduplication; message not sent");
                return redisplay(session, staged, "No valid recipients selected. Please select at least one recipient.",
                        parsedDemoNo);
            }

            sentToWho = messageData.createSentToString(providerListing);
            if (sentToWho != null) {
                sentToWho = sentToWho.trim();
            }
            var attempt = MessengerSubmissionGuard.attempt(session, submission, userNo);
            if (attempt.claim() == null) {
                MsgAttachmentStaging.restore(session, attachmentKey, staged);
                return submissionConflict("messenger.SubmissionConflict.msgUnavailable");
            }
            final String normalizedSubject = subject;
            final String recipients = sentToWho;
            final int patient = parsedDemoNo;
            final var selectedProviders = providerListing;
            try (var claim = attempt.claim()) {
                try {
                    TransactionTemplate transaction = new TransactionTemplate(
                            SpringUtils.getBean(PlatformTransactionManager.class));
                    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    transaction.executeWithoutResult(status -> {
                        claim.storageStarted();
                        String savedId = messageData.sendMessage2(message, normalizedSubject, userName, recipients,
                                userNo, selectedProviders, att, pdfAtt, OscarMsgType.GENERAL_TYPE);
                        int savedMessage = ConversionUtils.fromIntString(savedId);
                        if (savedMessage <= 0) {
                            throw new IllegalStateException("Message persistence did not return a valid identifier");
                        }
                        if (patient > 0) {
                            messengerDemographicManager.attachDemographicToMessage(loggedInInfo, savedMessage, patient);
                        }
                    });
                    if (!claim.isCommitted()) {
                        throw new IllegalStateException("Message transaction did not confirm a commit");
                    }
                    sent = true;
                } catch (RuntimeException e) {
                    MiscUtils.getLogger().error("Message send transaction failed", e);
                    if (!claim.canRetry()) {
                        return submissionConflict("messenger.SubmissionConflict.msgUnconfirmed");
                    }
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    return redisplay(session, staged, "The message was not sent. Your draft is retained; please try again.", parsedDemoNo);
                }
            }

            request.setAttribute("SentMessageProvs", sentToWho);
            // A reply or forward of a message whose chart items were saved has sent them; the next
            // reply or forward of that message starts without them, as it did when sending cleared
            // the messenger bean (issue #4673).
            MsgAttachmentStaging.releaseSource(session, attachmentKey, staged);

            return SUCCESS;
        } catch (RuntimeException e) {
            if (!sent) {
                MsgAttachmentStaging.restore(session, attachmentKey, staged);
            }
            throw e;
        }
    }

    /**
     * Shows this window's compose page again with {@code error}: its attachments are put back
     * under the same key, and the subject, body, linked patient and ticked recipients are kept, so
     * sending again, as the refusals say, reaches the same people. (The page used to come back
     * empty, and the attachments were already gone.)
     */
    private String redisplay(HttpSession session, MsgAttachmentStaging.Staged staged, String error,
                             int demographicNo) {
        if (MsgAttachmentStaging.isKey(attachmentKey)) {
            MsgAttachmentStaging.restore(session, attachmentKey, staged);
            request.setAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE, attachmentKey);
        }
        request.setAttribute("createMessageError", error);
        request.setAttribute("ReSubject", subject);
        request.setAttribute("ReText", message);
        request.setAttribute("replyList", tickedRecipients(provider));
        request.setAttribute("archiveMessageId", archiveMessageId(request));
        if (demographicNo > 0) {
            request.setAttribute("demographic_no", Integer.toString(demographicNo));
        }
        return ERROR;
    }

    /** Optional compose carrier only; the archive endpoint still authorizes the requested message. */
    private static Integer archiveMessageId(HttpServletRequest request) {
        String[] values = request.getParameterValues("archiveMessageId");
        if (values == null || values.length == 0) return null;
        Integer selected = null;
        for (String value : values) {
            if (value == null || !value.matches("[0-9]{1,10}")) return null;
            try {
                int candidate = Integer.parseInt(value);
                if (candidate <= 0 || selected != null && selected != candidate) return null;
                selected = candidate;
            } catch (NumberFormatException invalidMessage) {
                return null;
            }
        }
        return selected;
    }

    /**
     * The recipients ticked on a refused message, as the compose page's {@code replyList}, which
     * it ticks again. An id that is not a contact's composite id is left out.
     */
    static List<ContactIdentifier> tickedRecipients(String[] providers) {
        List<ContactIdentifier> ticked = new ArrayList<>();
        if (providers == null) {
            return ticked;
        }
        for (String id : providers) {
            if (id == null || id.isBlank()) {
                continue;
            }
            try {
                ticked.add(new ContactIdentifier(id.trim()));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException notAContact) {
                // Not a recipient the page offers, so there is nothing to tick for it.
            }
        }
        return ticked;
    }

    private String submissionConflict(String messageKey) {
        // sendError uses the shared error page, which intentionally hides servlet error details.
        // These fixed recovery messages must remain visible in the normal production configuration.
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        request.setAttribute("messageSubmissionErrorKey", messageKey);
        return "conflict";
    }


    private String[] provider = new String[0];
    private String message;
    private String subject;
    private String demographic_no;
    private String attachmentKey;

    /** @return the key of the compose window this message was written in */
    public String getAttachmentKey() {
        return attachmentKey;
    }

    /** @param attachmentKey the key of the compose window this message was written in */
    @StrutsParameter
    public void setAttachmentKey(String attachmentKey) {
        this.attachmentKey = attachmentKey;
    }

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
