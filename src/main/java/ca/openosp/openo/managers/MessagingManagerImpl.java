//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */
package ca.openosp.openo.managers;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.openosp.openo.commn.dao.MessageListDao;
import ca.openosp.openo.commn.dao.MessageTblDao;
import ca.openosp.openo.commn.dao.OscarCommLocationsDao;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.dao.ProviderDataDao;
import ca.openosp.openo.commn.model.ProviderData;
import ca.openosp.openo.commn.model.MessageList;
import ca.openosp.openo.commn.model.MessageTbl;
import ca.openosp.openo.commn.model.MsgDemoMap;
import ca.openosp.openo.commn.model.OscarCommLocations;

import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.messenger.data.ContactIdentifier;
import ca.openosp.openo.messenger.data.MessengerSystemMessage;
import ca.openosp.openo.messenger.data.MsgDisplayMessage;
import ca.openosp.openo.messenger.data.MsgMessageData;
import ca.openosp.openo.messenger.data.MsgProviderData;


@Service
public class MessagingManagerImpl implements MessagingManager {

    @Autowired
    private MessageListDao messageListDao;
    @Autowired
    private MessageTblDao messageTblDao;
    @Autowired
    private SecurityInfoManager securityInfoManager;
    @Autowired
    private OscarCommLocationsDao oscarCommLocationsDao;
    @Autowired
    private MessengerDemographicManager messengerDemographicManager;

    /**
     * PREFERRED METHOD
     * Get the entire inbox for the logged-in provider only.
     * Results can be reduced by specifying the message status and/or a result limit.
     *
     * @param loggedInInfo the logged in user information
     * @param messageStatus the message status to filter by
     * @param offset the offset for pagination
     * @param limit the limit for pagination
     * @return list of display messages
     */
    public List<MsgDisplayMessage> getInbox(LoggedInInfo loggedInInfo, String messageStatus, int offset, int limit) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        List<MessageList> messageList = getMyInboxMessages(loggedInInfo, loggedInInfo.getLoggedInProviderNo(), messageStatus, offset, limit);
        List<MsgDisplayMessage> inboxMessages = new ArrayList<MsgDisplayMessage>();
        for (MessageList messageItem : messageList) {
            MsgDisplayMessage msgDisplayMessage = getInboxMessage(loggedInInfo, (int) messageItem.getMessage());
            inboxMessages.add(msgDisplayMessage);
        }

        return inboxMessages;
    }

    public MsgDisplayMessage getInboxMessage(LoggedInInfo loggedInInfo, int messageId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        MessageTbl messageTbl = getMessage(loggedInInfo, messageId);
        MsgDisplayMessage msgDisplayMessage = new MsgDisplayMessage();
        String attachedDemographics = messengerDemographicManager.getAttachedDemographicNamesAndAges(loggedInInfo, messageTbl.getId());

        String sentDate = new SimpleDateFormat("dd-MM-yyyy").format(messageTbl.getDate());
        String sentTime = messageTbl.getTime().toString();

        Integer messageType = messageTbl.getType();
        if (messageType == null) {
            messageType = 0;
        }

        msgDisplayMessage.setStatus(messageTbl.getActionStatus());
        msgDisplayMessage.setMessageId(messageTbl.getId() + "");
        msgDisplayMessage.setSentby(messageTbl.getSentBy());
        msgDisplayMessage.setSentto(messageTbl.getSentTo());
        msgDisplayMessage.setThedate(sentDate);
        msgDisplayMessage.setThetime(sentTime);
        msgDisplayMessage.setThesubject(messageTbl.getSubject());
        msgDisplayMessage.setNameage(attachedDemographics);
        msgDisplayMessage.setMessageBody(messageTbl.getMessage());
        msgDisplayMessage.setType(messageType);
        msgDisplayMessage.setTypeLink(messageTbl.getType_link());

        String attachment = messageTbl.getAttachment();
        byte[] pdfAttachment = messageTbl.getPdfAttachment();

        if (attachment == null || "".equals(attachment)) {
            msgDisplayMessage.setAttach("0");
        } else {
            msgDisplayMessage.setAttach("1");
        }
        if (pdfAttachment == null) {
            msgDisplayMessage.setPdfAttach("0");
        } else {
            msgDisplayMessage.setPdfAttach("1");
        }

        return msgDisplayMessage;
    }

    public MessageTbl getMessage(LoggedInInfo loggedInInfo, int messageId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }
        return messageTblDao.find(messageId);
    }

    public List<MessageList> getMyInboxMessages(LoggedInInfo loggedInInfo, String providerNo, int offset, int limit) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        return getMyInboxMessages(loggedInInfo, providerNo, null, offset, limit);
    }

    public List<MessageList> getMyInboxMessages(LoggedInInfo loggedInInfo, String providerNo, String status, int offset, int limit) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        List<MessageList> msgs = messageListDao.search(providerNo, status, offset, limit);

        for (MessageList msg : msgs) {
            LogAction.addLogSynchronous(loggedInInfo, "MessagingManager.getMyInboxMessages", "msglistid=" + msg.getId());
        }

        return msgs;
    }

    public List<MessageTbl> getMessagesLinkedToDemographic(LoggedInInfo loggedInInfo, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        List<MsgDemoMap> msgDemoMapList = messengerDemographicManager.getMessageMapByDemographicNo(loggedInInfo, demographicNo);
        List<MessageTbl> messageTblList = new ArrayList<MessageTbl>();
        if (msgDemoMapList != null) {
            for (MsgDemoMap msgDemoMap : msgDemoMapList) {
                MessageTbl messageTbl = getMessage(loggedInInfo, msgDemoMap.getMessageID());
                messageTblList.add(messageTbl);
            }
        }

        sortByDate(messageTblList);

        return messageTblList;
    }

    public Integer getMyInboxMessagesCount(LoggedInInfo loggedInInfo, String providerNo, String status) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        Integer result = messageListDao.searchAndReturnTotal(providerNo, status);

        return result;
    }

    public int getMyInboxMessageCount(LoggedInInfo loggedInInfo, String providerNo, boolean onlyWithPatientAttached) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        if (!onlyWithPatientAttached) {
            return getMyInboxMessagesCount(loggedInInfo, providerNo, MessageList.STATUS_NEW);
        } else {

            return getCountNewMessagesDemographicAttached(loggedInInfo, providerNo);
        }
    }

    /**
     * This is a short cut hack that returns a proper count in situations when multiple demographics are
     * attached to a single message. This is because Hibernate does not handle SQL methods such as GROUP BY and DISTINCT
     * This method can be eliminated if/when JPA is upgraded or joins are used in the DB model.
     *
     * @param loggedInInfo
     * @param providerNo
     * @return
     */
    public int getCountNewMessagesDemographicAttached(LoggedInInfo loggedInInfo, String providerNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        int count = 0;
        // get all messages by provider.
        List<MessageList> messageList = messageListDao.findUnreadByProvider(providerNo);

        if (messageList != null) {
            for (MessageList message : messageList) {
                List<MsgDemoMap> msgDemoMap = messengerDemographicManager.getAttachedDemographicList(loggedInInfo, (int) message.getMessage());
                if (msgDemoMap != null && msgDemoMap.size() > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    public Integer getMyInboxIntegratorMessagesCount(LoggedInInfo loggedInInfo, String providerNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        return messageListDao.countUnreadByProviderAndFromIntegratedFacility(providerNo);
    }

    /**
     * Get the count of all messages attached to the given demographic Id.
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return
     */
    public int getInboxCountByDemographicNo(LoggedInInfo loggedInInfo, int demographicNo) {
        List<MsgDemoMap> demoMap = messengerDemographicManager.getMessageMapByDemographicNo(loggedInInfo, demographicNo);
        if (demoMap != null) {
            return demoMap.size();
        }
        return 0;
    }

    /**
     * Change the status of a message (sent, read, del, new)
     *
     * @param loggedInInfo the logged in user information
     * @param messageList the message list to update
     * @param status the new status
     * @return the message ID
     */
    public Long setMessageStatus(LoggedInInfo loggedInInfo, MessageList messageList, String status) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.UPDATE, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        messageList.setStatus(status);
        messageListDao.merge(messageList);
        return messageList.getMessage();
    }

    /**
     * Set the message opened by this local provider from new to read. Matching provider numbers from
     * remote locations will be ignored.
     *
     * @param loggedInInfo
     * @param messageId
     * @param providerNo
     */
    public void setMessageRead(LoggedInInfo loggedInInfo, Long messageId, String providerNo) {
        List<MessageList> messageList = messageListDao.findByProviderNoAndMessageNo(providerNo, messageId);
        for (MessageList message : messageList) {
            if (!MessageList.STATUS_DELETED.equalsIgnoreCase(message.getStatus())
                    && !MessageList.STATUS_SENT.equalsIgnoreCase(message.getStatus())
                    && message.getDestinationFacilityId() < 1) {
                setMessageStatus(loggedInInfo, message, MessageList.STATUS_READ);
            }
        }
    }

    public void deleteMessage(LoggedInInfo loggedInInfo, int messageId) {
        List<MessageList> messageList = messageListDao.findByProviderNoAndMessageNo(loggedInInfo.getLoggedInProviderNo(), (long) messageId);
        for (MessageList message : messageList) {
            setMessageStatus(loggedInInfo, message, MessageList.STATUS_DELETED);
        }
    }

    public String getLabRecallMsgSubjectPref(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        String subject = "";
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop = userPropertyDao.getProp(providerNo, UserProperty.LAB_RECALL_MSG_SUBJECT);

        if (prop != null) {
            subject = prop.getValue();
        }

        return subject;
    }

    public String getLabRecallDelegatePref(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        String delegate = "";
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop = userPropertyDao.getProp(providerNo, UserProperty.LAB_RECALL_DELEGATE);

        if (prop != null) {
            delegate = prop.getValue();
        }

        return delegate;
    }

    public String getDelegateName(String delegate) {

        String delegateName = "";
        ProviderDataDao dao = SpringUtils.getBean(ProviderDataDao.class);
        ProviderData pd = dao.findByProviderNo(delegate);

        delegateName = pd.getLastName() + ", " + pd.getFirstName();

        return delegateName;
    }

    /**
     * Use this method for sending messages from the Oscar system to Oscar users.
     */
    public Integer sendSystemMessage(LoggedInInfo loggedInInfo, MessengerSystemMessage systemMessage) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        if (systemMessage.getRecipients() == null || systemMessage.getRecipients().length == 0) {
            // no recipients
            return null;
        }

        //TODO transition from using the MsgMessageData Object.
        MsgMessageData messageData = new MsgMessageData();
        String[] recipients = removeDuplicates(systemMessage.getRecipients());

        ArrayList<MsgProviderData> providerListing = messageData.getProviderStructure(loggedInInfo, recipients);
        String sentToWho = messageData.createSentToString(providerListing);
        ArrayList<MsgProviderData> remoteProviderListing = messageData.getRemoteProvidersStructure();
        sentToWho = sentToWho + " " + messageData.getRemoteNames(remoteProviderListing);
        Integer messageIdInteger = null;
        String messageId = messageData.sendMessageReview(
                systemMessage.getMessage(),
                systemMessage.getSubject(),
                systemMessage.getSentBy(),
                sentToWho,
                systemMessage.getSentByNo(),
                providerListing,
                systemMessage.getAttachment(),
                systemMessage.getPdfAttachment(),
                systemMessage.getType(),
                systemMessage.getType_link());

        if (messageId != null && systemMessage.getAttachedDemographicNo() != null) {
            messageIdInteger = Integer.parseInt(messageId);
            messengerDemographicManager.attachDemographicToMessage(loggedInInfo, messageIdInteger, systemMessage.getAttachedDemographicNo());
        }

        return messageIdInteger;
    }

    /**
     * Attach all providers that should receive this message.
     *
     * @param loggedInInfo the logged in user information
     * @param messageId the message ID
     * @param contactIdentifier array of contact identifiers
     * @param status the message status
     */
    public void addRecipientsToMessage(LoggedInInfo loggedInInfo, int messageId, ContactIdentifier[] contactIdentifier, String status) {
        for (ContactIdentifier contact : contactIdentifier) {
            addRecipientToMessage(loggedInInfo, messageId, contact, status);
        }
    }

    public void addRecipientsToMessage(LoggedInInfo loggedInInfo, int messageId, ContactIdentifier[] contactIdentifier) {
        addRecipientsToMessage(loggedInInfo, messageId, contactIdentifier, MessageList.STATUS_NEW);
    }

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, ContactIdentifier contactIdentifier) {
        addRecipientToMessage(loggedInInfo, messageId, contactIdentifier.getContactId(), contactIdentifier.getClinicLocationNo(), contactIdentifier.getFacilityId(), MessageList.STATUS_NEW);
    }

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, ContactIdentifier contactIdentifier, String status) {
        addRecipientToMessage(loggedInInfo, messageId, contactIdentifier.getContactId(), contactIdentifier.getClinicLocationNo(), contactIdentifier.getFacilityId(), status);
    }

    public void addRecipientsToMessage(LoggedInInfo loggedInInfo, int messageId, String[] providerNoArray, int clinicLocationNo, int facilityId, int sourceFacilityId) {
        addRecipientsToMessage(loggedInInfo, messageId, providerNoArray, clinicLocationNo, facilityId, sourceFacilityId, MessageList.STATUS_NEW);
    }

    public void addRecipientsToMessage(LoggedInInfo loggedInInfo, int messageId, String[] providerNoArray, int clinicLocationNo, int facilityId, int sourceFacilityId, String status) {
        for (String providerNo : providerNoArray) {
            addRecipientToMessage(loggedInInfo, messageId, providerNo, clinicLocationNo, facilityId, sourceFacilityId, status);
        }
    }

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, String providerNo, int clinicLocationNo, int facilityId, int sourceFacilityId) {
        addRecipientToMessage(loggedInInfo, messageId, providerNo, clinicLocationNo, facilityId, sourceFacilityId, MessageList.STATUS_NEW);
    }

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, String providerNo, int clinicLocationNo, int facilityId, int sourceFacilityId, String status) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        MessageList messageList = new MessageList();
        messageList.setMessage(messageId);
        messageList.setProviderNo(providerNo);
        messageList.setStatus(status);
        messageList.setRemoteLocation(clinicLocationNo);
        messageList.setDestinationFacilityId(facilityId);
        messageList.setSourceFacilityId(sourceFacilityId);
        messageListDao.persist(messageList);
    }

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, String providerNo, int clinicLocationNo, int facilityId) {
        addRecipientToMessage(loggedInInfo, messageId, providerNo, clinicLocationNo, facilityId, MessageList.STATUS_NEW);
    }

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, String providerNo, int clinicLocationNo, int facilityId, String status) {
        addRecipientToMessage(loggedInInfo, messageId, providerNo, clinicLocationNo, facilityId, 0, status);
    }

    /**
     * A combined result of both the local reply recipients and recipients located in remote
     * facilities including the original sender.
     */
    public final List<ContactIdentifier> getAllMessageReplyRecipients(LoggedInInfo loggedInInfo, final MessageTbl messageTbl) {

        List<ContactIdentifier> contactIdentifierList = new ArrayList<ContactIdentifier>();
        List<ContactIdentifier> sender = getReplyToSender(loggedInInfo, messageTbl);
        List<ContactIdentifier> localCopyTo = getAllLocalReplyRecipients(loggedInInfo, messageTbl.getId());
        List<ContactIdentifier> remoteCopyTo = getAllRemoteReplyRecipients(loggedInInfo, messageTbl.getId());

        if (sender != null) {
            contactIdentifierList.addAll(sender);
        }

        if (localCopyTo != null) {
            contactIdentifierList.addAll(localCopyTo);
        }

        if (remoteCopyTo != null) {
            contactIdentifierList.addAll(remoteCopyTo);
        }

        return contactIdentifierList;
    }

    /**
     * A combined result of both the local reply recipients and recipients located in remote
     * facilities inluding the original sender.
     */
    public List<ContactIdentifier> getAllMessageReplyRecipients(LoggedInInfo loggedInInfo, int messageId) {
        MessageTbl messageTbl = getMessage(loggedInInfo, messageId);
        return getAllMessageReplyRecipients(loggedInInfo, messageTbl);
    }

    /**
     * Get the only the sender's identifier for a message reply.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<ContactIdentifier> getReplyToSender(LoggedInInfo loggedInInfo, int messageId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        MessageTbl messageTbl = getMessage(loggedInInfo, messageId);
        return getReplyToSender(loggedInInfo, messageTbl);
    }

    public List<ContactIdentifier> getReplyToSender(LoggedInInfo loggedInInfo, MessageTbl messageTbl) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }
        // add the primary sender
        List<ContactIdentifier> contactIdentifierList = new ArrayList<ContactIdentifier>();
        String providerNo = messageTbl.getSentByNo();
        int clinicLocationNo = messageTbl.getSentByLocation();

        ContactIdentifier contactIdentifier = new ContactIdentifier();
        contactIdentifier.setContactId(providerNo);
        contactIdentifier.setClinicLocationNo(clinicLocationNo);

        /*
         *  Kinda crazy, right? based on current design; the sentByLocation id is the only way
         *  to track the facility id for a reply to sender.
         */
        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()
                && clinicLocationNo != getCurrentLocationId()) {
            contactIdentifier.setFacilityId(clinicLocationNo);
            contactIdentifier.setClinicLocationNo(0);
        }

        contactIdentifierList.add(contactIdentifier);
        return contactIdentifierList;
    }


    /**
     * Recipients that were copied in on the message that have an origin in the client from where
     * the reply is originating.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<ContactIdentifier> getAllLocalReplyRecipients(LoggedInInfo loggedInInfo, int messageId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        List<MessageList> messageList = messageListDao.findAllByMessageNoAndLocationNo((long) messageId, getCurrentLocationId());
        List<ContactIdentifier> contactIdentifierList = new ArrayList<ContactIdentifier>();
        for (MessageList message : messageList) {
            if (!loggedInInfo.getLoggedInProviderNo().equals(message.getProviderNo())) {
                ContactIdentifier contactIdentifier = new ContactIdentifier();
                contactIdentifier.setClinicLocationNo(message.getRemoteLocation());
                contactIdentifier.setContactId(message.getProviderNo());
                contactIdentifier.setFacilityId(0);
                contactIdentifierList.add(contactIdentifier);
            }
        }
        return contactIdentifierList;
    }

    /**
     * Recipients that were copied in on the message but have an origin in one of the included
     * remote facilities.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<ContactIdentifier> getAllRemoteReplyRecipients(LoggedInInfo loggedInInfo, int messageId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        List<MessageList> messageList = messageListDao.findByMessage((long) messageId);
        List<ContactIdentifier> contactIdentifierList = new ArrayList<ContactIdentifier>();
        int currentLocationId = getCurrentLocationId();
        for (MessageList message : messageList) {
            if (message.getRemoteLocation() != currentLocationId) {
                ContactIdentifier contactIdentifier = new ContactIdentifier();
                contactIdentifier.setClinicLocationNo(message.getRemoteLocation());
                contactIdentifier.setContactId(message.getProviderNo());

                // assuming that this facility id is always 0
                if (message.getSourceFacilityId() > 0) {
                    contactIdentifier.setFacilityId(message.getSourceFacilityId());
                } else {
                    contactIdentifier.setFacilityId(message.getDestinationFacilityId());
                }

                contactIdentifierList.add(contactIdentifier);
            }
        }
        return contactIdentifierList;
    }

    /**
     * Save the message content.
     *
     * @param loggedInInfo
     * @param messageTbl
     * @return
     */
    public Integer saveMessage(LoggedInInfo loggedInInfo, MessageTbl messageTbl) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        messageTblDao.persist(messageTbl);
        return messageTbl.getId();
    }

    /**
     * Get the current address book location id for this clinic from the oscarcommlocations table.
     *
     * @return
     */
    public int getCurrentLocationId() {
        int currentLocationId = 0;
        List<OscarCommLocations> oscarCommLocations = oscarCommLocationsDao.findByCurrent1(1);
        Integer oscarCommLocationsID = null;

        if (oscarCommLocations != null) {
            oscarCommLocationsID = oscarCommLocations.get(0).getId();
        }

        if (oscarCommLocationsID != null) {
            currentLocationId = oscarCommLocationsID;
        }
        return currentLocationId;
    }

    // HELPER METHODS.

    /**
     * remove duplicate values from any string array.
     *
     * @param strarray
     * @return
     */
    public String[] removeDuplicates(String[] strarray) {
        List<String> arrayList = new ArrayList<String>(Arrays.asList(strarray));
        Set<String> hashSet = new HashSet<String>(arrayList);
        String[] outputArray = new String[hashSet.size()];
        return hashSet.toArray(outputArray);
    }

    private static final void sortByDate(List<MessageTbl> list) {
        Collections.sort(list, new Comparator<MessageTbl>() {
            public int compare(MessageTbl mt1, MessageTbl mt2) {
                java.util.Date datetime1 = combineDateTime(mt1.getDate(), mt1.getTime());
                java.util.Date datetime2 = combineDateTime(mt2.getDate(), mt2.getTime());
                return Long.valueOf(datetime2.getTime()).compareTo(datetime1.getTime());
            }
        });
    }

    /**
     * Combines the separate date and time columns of a Message into a single object.
     *
     * @param date
     * @param time
     * @return
     */
    private static final java.util.Date combineDateTime(final java.util.Date date,
                                                        final java.util.Date time) {

        Calendar calendar = Calendar.getInstance();
        Calendar calendarDate = Calendar.getInstance();
        Calendar timeMerge = Calendar.getInstance();
        calendarDate.clear();
        calendarDate.setTime(date);
        timeMerge.clear();
        timeMerge.setTime(time);
        calendar.clear();
        calendar.set(calendarDate.get(Calendar.YEAR), calendarDate.get(Calendar.MONTH), calendarDate.get(Calendar.DATE),
                timeMerge.get(Calendar.HOUR_OF_DAY), timeMerge.get(Calendar.MINUTE), timeMerge.get(Calendar.SECOND));

        return calendar.getTime();
    }

    /**
     * Checks a list of message recipients for any recipients that are in remote locations.
     *
     * @param loggedInInfo
     * @param msgProviderDataList
     * @return
     */
    public static boolean doesContainRemoteRecipient(LoggedInInfo loggedInInfo, final List<MsgProviderData> msgProviderDataList) {
        boolean remoterecipient = Boolean.FALSE;
        int thisfacilityid = loggedInInfo.getCurrentFacility().getId();

        if (msgProviderDataList != null) {
            for (MsgProviderData msgProviderData : msgProviderDataList) {
                ContactIdentifier contactIdentifier = msgProviderData.getId();
                if (contactIdentifier.getFacilityId() > 0 && contactIdentifier.getFacilityId() != thisfacilityid) {
                    remoterecipient = Boolean.TRUE;
                    break;
                }
            }
        }
        return remoterecipient;
    }

    public static final List<ContactIdentifier> createContactIdentifierList(final String[] compositeContactIdArray) {

        List<ContactIdentifier> contactIdentifierList = new ArrayList<ContactIdentifier>();

        if (compositeContactIdArray == null) {
            return contactIdentifierList;
        }

        for (String compositeContactId : compositeContactIdArray) {
            ContactIdentifier contactIdentifier = new ContactIdentifier(compositeContactId);
            contactIdentifierList.add(contactIdentifier);
        }

        return contactIdentifierList;
    }

}
