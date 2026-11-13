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
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.commn.model.OscarMsgType;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
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

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;

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
 * <p><b>One send per rendered form.</b> The compose page carries a {@link FormSubmissionGuard}
 * token ({@code MESSENGER_SEND}) for new messages, replies, reply-all and forwards alike. A double
 * click, a re-click during a slow send or a reload of the confirmation page repeats the token and
 * is answered with the first send's own confirmation; the cases that cannot be (still sending,
 * unconfirmed, a token of another session) get HTTP 409 and the {@code submissionRefused}
 * page. A used form submitted again with a different message is not such a repeat: the compose
 * page is shown again with HTTP 409 and the reason, never the first send's confirmation.</p>
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

        String userName = bean.getUserName();
        HttpSession session = request.getSession();

        // One rendered compose form sends once (issue #4225): a double click, a second click while
        // a slow send is still out, or a reload of the "message sent" page repeats the form's
        // token, and the repeat ends as the first send did instead of delivering the message a
        // second time. It is claimed before anything is validated or taken from the compose
        // window, so a repeat never touches this window's attachments. A post without a token
        // keeps the previous behaviour.
        FormSubmissionGuard.Attempt attempt = FormSubmissionGuard.attempt(session,
                request.getParameter(FormSubmissionGuard.PARAMETER), FormSubmissionGuard.Scope.MESSENGER_SEND,
                null, FormSubmissionGuard.DEFAULT_WAIT_MILLIS);
        switch (attempt.verdict()) {
            case PROCEED, ABSENT -> { }
            case ALREADY_SAVED -> {
                return answerUsedForm(session, attempt.result());
            }
            default -> {
                return FormSubmissionGuard.refuse(request, response, attempt.verdict());
            }
        }
        try (FormSubmissionGuard.Claim claim = attempt.claim()) {
            return send(loggedInInfo, userNo, userName, session, claim);
        }
    }

    /**
     * Answers a submission of a compose form that has already sent a message.
     *
     * <p>The same message again (a double click, a re-click during a slow send, a reload of the
     * confirmation page) ends as the first send did: its confirmation, for what was sent. A
     * <em>different</em> message is not a repeat: the user went back to the used page, changed the
     * text or the recipients and sent again. Confirming the first send for it would tell them a
     * message was sent that never was, so the compose page is shown again (HTTP 409) with what
     * they submitted and the reason. That rendering has a token of its own, so sending from it
     * delivers the changed message once.</p>
     */
    private String answerUsedForm(HttpSession session, String savedResult) {
        String[] saved = savedResult == null ? new String[0] : savedResult.split(":", 3);
        boolean sameMessage = saved.length == 3 && saved[1].equals(contentDigest())
                // Attachments staged for this window after it sent are not part of what was sent.
                && MsgAttachmentStaging.peek(session, attachmentKey) == null;
        if (sameMessage) {
            // Confirm what the first send delivered, never what this repeat carries.
            request.setAttribute("SentMessageProvs", saved[2]);
            return SUCCESS;
        }
        MiscUtils.getLogger().info("Refused a changed message submitted from a compose form that had already sent");
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setHeader("Cache-Control", "no-store");
        String linkedPatient = demographic_no == null || demographic_no.isEmpty() || "null".equals(demographic_no)
                ? null : demographic_no;
        // Nothing is taken from the window: anything it has staged since stays under its key.
        return redisplay(session, null,
                LocaleUtils.getMessage(request, "messenger.CreateMessage.msgChangedAfterSend"),
                ConversionUtils.fromIntString(linkedPatient));
    }

    /** What a saved send records for its repeats: the message id, what was submitted, and who it was sent to. */
    private String savedResult(String messageId, String sentToWho) {
        return messageId + ":" + contentDigest() + ":" + (sentToWho == null ? "" : sentToWho);
    }

    /**
     * A digest of the message as this submission carries it: the ticked recipients (in any order),
     * the subject, the body and the linked patient. Two submissions of one rendered form are the
     * same message exactly when their digests are equal. Only the digest is kept in the session.
     */
    private String contentDigest() {
        List<String> recipients = new ArrayList<>();
        if (provider != null) {
            for (String id : provider) {
                if (id != null && !id.isBlank() && !recipients.contains(id.trim())) {
                    recipients.add(id.trim());
                }
            }
        }
        java.util.Collections.sort(recipients);
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            for (String part : new String[]{String.join(",", recipients), subject, message, demographic_no}) {
                byte[] bytes = (part == null ? "" : part).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                // Length-prefixed, so text moved from one field to the next is a different message.
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", impossible);
        }
    }

    /**
     * Validates and sends the message of one claimed submission. {@code claim} is null for a post
     * that carried no token.
     */
    private String send(LoggedInInfo loggedInInfo, String userNo, String userName, HttpSession session,
                        FormSubmissionGuard.Claim claim) {
        // This compose window's own attachments, taken once so a second submit of the same form
        // cannot send them again. Every path below that does not send puts them back.
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

            // A message holds a fixed amount of PDF text (MessageTbl.PDF_ATTACHMENT_MAX_BYTES) and the database cuts the
            // rest off without a word, so what does not fit is refused here, with everything kept, and not stored in two.
            if (!MsgMessageData.pdfAttachmentFits(pdfAtt)) {
                MiscUtils.getLogger().warn("Refused a message whose chart PDFs are larger than a message can hold");
                return redisplay(session, staged, MsgAttachPDF2Action.PDFS_TOO_LARGE, parsedDemoNo);
            }

            java.util.ArrayList<MsgProviderData> providerListing;


            subject = (subject == null) ? "" : subject.trim();
            // messagetbl.thesubject holds MessageTbl.SUBJECT_MAX_LENGTH characters (code points) and
            // the packaged database cuts a longer subject off without an error, so it is refused here,
            // with what was written kept. The compose page checks the same before posting; this is
            // the check that holds.
            if (!MessageTbl.subjectFits(subject)) {
                MiscUtils.getLogger().info("Refused a message whose subject is longer than a message subject can hold");
                return redisplay(session, staged,
                        LocaleUtils.getMessage(request, "messenger.CreateMessage.msgSubjectTooLong"), parsedDemoNo);
            }
            if (subject.isEmpty()) {
                subject = "none";
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
            // The message and its delivery rows commit together, in a transaction the claim
            // observes: a send that rolled back leaves the form submittable, and one whose commit
            // could not be confirmed never sends again from the same rendering.
            final String sendSubject = subject;
            final String sendTo = sentToWho;
            final ArrayList<MsgProviderData> recipients = providerListing;
            messageId = new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class)).execute(status -> {
                if (claim != null) {
                    claim.storageStarted();
                }
                String stored = messageData.sendMessage2(message, sendSubject, userName, sendTo, userNo, recipients,
                        att, pdfAtt, OscarMsgType.GENERAL_TYPE);
                if (stored == null || stored.isEmpty()) {
                    status.setRollbackOnly();
                }
                return stored;
            });
            sent = messageId != null && !messageId.isEmpty();
            if (claim != null && sent) {
                claim.saved(savedResult(messageId, sentToWho));
            }

            if (messageId == null || messageId.isEmpty()) {
                MiscUtils.getLogger().error("sendMessage2 returned null or empty messageId");
                return redisplay(session, staged, "Failed to send message. Please try again.", parsedDemoNo);
            }

            // Link message and demographic if both IDs are valid (> 0).
            Integer parsedMessageId = ConversionUtils.fromIntString(messageId);
            if (parsedMessageId > 0 && parsedDemoNo > 0) {
                messengerDemographicManager.attachDemographicToMessage(loggedInInfo, parsedMessageId, parsedDemoNo);
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
