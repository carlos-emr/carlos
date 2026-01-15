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


import java.util.List;

import ca.openosp.openo.commn.model.MessageList;
import ca.openosp.openo.commn.model.MessageTbl;

import ca.openosp.openo.utility.LoggedInInfo;

import ca.openosp.openo.messenger.data.ContactIdentifier;
import ca.openosp.openo.messenger.data.MessengerSystemMessage;
import ca.openosp.openo.messenger.data.MsgDisplayMessage;


public interface MessagingManager {

    /*
     * PREFERRED METHOD
     * Get the entire inbox for the logged-in provider only.
     * Results can be reduced by specifying the message status and/or a result limit.
     * @param loggedInInfo
     * @param providerNo
     * @param messageStatus
     * @param offset
     * @param limit
     * @return
     */
    public List<MsgDisplayMessage> getInbox(LoggedInInfo loggedInInfo, String messageStatus, int offset, int limit);

    public MsgDisplayMessage getInboxMessage(LoggedInInfo loggedInInfo, int messageId);

    public MessageTbl getMessage(LoggedInInfo loggedInInfo, int messageId);

    public List<MessageList> getMyInboxMessages(LoggedInInfo loggedInInfo, String providerNo, int offset, int limit);

    public List<MessageList> getMyInboxMessages(LoggedInInfo loggedInInfo, String providerNo, String status, int offset, int limit);

    public List<MessageTbl> getMessagesLinkedToDemographic(LoggedInInfo loggedInInfo, int demographicNo);

    public Integer getMyInboxMessagesCount(LoggedInInfo loggedInInfo, String providerNo, String status);

    public int getMyInboxMessageCount(LoggedInInfo loggedInInfo, String providerNo, boolean onlyWithPatientAttached);

    /**
     * This is a short cut hack that returns a proper count in situations when multiple demographics are
     * attached to a single message. This is because Hibernate does not handle SQL methods such as GROUP BY and DISTINCT
     * This method can be eliminated if/when JPA is upgraded or joins are used in the DB model.
     *
     * @param loggedInInfo
     * @param providerNo
     * @return
     */
    public int getCountNewMessagesDemographicAttached(LoggedInInfo loggedInInfo, String providerNo);

    public Integer getMyInboxIntegratorMessagesCount(LoggedInInfo loggedInInfo, String providerNo);

    /**
     * Get the count of all messages attached to the given demographic Id.
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return
     */
    public int getInboxCountByDemographicNo(LoggedInInfo loggedInInfo, int demographicNo);

    /**
     * Change the status of a message (sent, read, del, new)
     *
     * @param loggedInInfo the logged in user information
     * @param messageList the message list to update
     * @param status the new status
     * @return the message ID
     */
    public Long setMessageStatus(LoggedInInfo loggedInInfo, MessageList messageList, String status);

    /**
     * Set the message opened by this local provider from new to read. Matching provider numbers from
     * remote locations will be ignored.
     *
     * @param loggedInInfo
     * @param messageId
     * @param providerNo
     */
    public void setMessageRead(LoggedInInfo loggedInInfo, Long messageId, String providerNo);

    public void deleteMessage(LoggedInInfo loggedInInfo, int messageId);

    public String getLabRecallMsgSubjectPref(LoggedInInfo loggedInInfo);

    public String getLabRecallDelegatePref(LoggedInInfo loggedInInfo);

    public String getDelegateName(String delegate);

    /**
     * Use this method for sending messages from the Oscar system to Oscar users.
     */
    public Integer sendSystemMessage(LoggedInInfo loggedInInfo, MessengerSystemMessage systemMessage);

    /**
     * Attach all providers that should receive this message.
     *
     * @param loggedInInfo the logged in user information
     * @param messageId the message ID
     * @param contactIdentifier array of contact identifiers
     * @param status the message status
     */
    public void addRecipientsToMessage(LoggedInInfo loggedInInfo, int messageId, ContactIdentifier[] contactIdentifier, String status);

    public void addRecipientsToMessage(LoggedInInfo loggedInInfo, int messageId, ContactIdentifier[] contactIdentifier);

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, ContactIdentifier contactIdentifier);

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, ContactIdentifier contactIdentifier, String status);

    public void addRecipientsToMessage(LoggedInInfo loggedInInfo, int messageId, String[] providerNoArray, int clinicLocationNo, int facilityId, int sourceFacilityId);

    public void addRecipientsToMessage(LoggedInInfo loggedInInfo, int messageId, String[] providerNoArray, int clinicLocationNo, int facilityId, int sourceFacilityId, String status);

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, String providerNo, int clinicLocationNo, int facilityId, int sourceFacilityId);

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, String providerNo, int clinicLocationNo, int facilityId, int sourceFacilityId, String status);

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, String providerNo, int clinicLocationNo, int facilityId);

    public void addRecipientToMessage(LoggedInInfo loggedInInfo, int messageId, String providerNo, int clinicLocationNo, int facilityId, String status);

    /**
     * A combined result of both the local reply recipients and recipients located in remote
     * facilities including the original sender.
     */
    public List<ContactIdentifier> getAllMessageReplyRecipients(LoggedInInfo loggedInInfo, final MessageTbl messageTbl);

    /**
     * A combined result of both the local reply recipients and recipients located in remote
     * facilities inluding the original sender.
     */
    public List<ContactIdentifier> getAllMessageReplyRecipients(LoggedInInfo loggedInInfo, int messageId);

    /**
     * Get the only the sender's identifier for a message reply.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<ContactIdentifier> getReplyToSender(LoggedInInfo loggedInInfo, int messageId);

    public List<ContactIdentifier> getReplyToSender(LoggedInInfo loggedInInfo, MessageTbl messageTbl);


    /**
     * Recipients that were copied in on the message that have an origin in the client from where
     * the reply is originating.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<ContactIdentifier> getAllLocalReplyRecipients(LoggedInInfo loggedInInfo, int messageId);

    /**
     * Recipients that were copied in on the message but have an origin in one of the included
     * remote facilities.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<ContactIdentifier> getAllRemoteReplyRecipients(LoggedInInfo loggedInInfo, int messageId);

    /**
     * Save the message content.
     *
     * @param loggedInInfo
     * @param messageTbl
     * @return
     */
    public Integer saveMessage(LoggedInInfo loggedInInfo, MessageTbl messageTbl);

    /**
     * Get the current address book location id for this clinic from the oscarcommlocations table.
     *
     * @return
     */
    public int getCurrentLocationId();
    // HELPER METHODS.

    /**
     * remove duplicate values from any string array.
     *
     * @param strarray
     * @return
     */
    public String[] removeDuplicates(String[] strarray);

}
