/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.messenger.data.ContactIdentifier;
import io.github.carlos_emr.carlos.messenger.data.MsgDemoMap;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MessagingManagerImpl} provider messaging business logic.
 *
 * <p>Tests security enforcement, inbox retrieval, message status management,
 * recipient routing, reply logic, and helper methods.</p>
 *
 * @since 2026-03-31
 * @see MessagingManagerImpl
 * @see MessagingManager
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MessagingManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("messaging")
class MessagingManagerUnitTest extends CarlosUnitTestBase {

    @Mock private MessageListDao mockMessageListDao;
    @Mock private MessageTblDao mockMessageTblDao;
    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private OscarCommLocationsDao mockOscarCommLocationsDao;
    @Mock private MessengerDemographicManager mockMessengerDemographicManager;

    private MessagingManagerImpl manager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        registerMock(MessageListDao.class, mockMessageListDao);
        registerMock(MessageTblDao.class, mockMessageTblDao);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(OscarCommLocationsDao.class, mockOscarCommLocationsDao);
        registerMock(MessengerDemographicManager.class, mockMessengerDemographicManager);
        // Register for SpringUtils.getBean calls in getLabRecallMsgSubjectPref/getLabRecallDelegatePref
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
        registerMock(ProviderDataDao.class, mock(ProviderDataDao.class));

        manager = new MessagingManagerImpl();
        injectDependency(manager, "messageListDao", mockMessageListDao);
        injectDependency(manager, "messageTblDao", mockMessageTblDao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(manager, "oscarCommLocationsDao", mockOscarCommLocationsDao);
        injectDependency(manager, "messengerDemographicManager", mockMessengerDemographicManager);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    private void grantMsgReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_msg"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
    }

    private void denyMsgReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_msg"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(false);
    }

    private void grantMsgWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_msg"), eq(SecurityInfoManager.WRITE), isNull()))
                .thenReturn(true);
    }

    private void grantMsgUpdatePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_msg"), eq(SecurityInfoManager.UPDATE), isNull()))
                .thenReturn(true);
    }

    private MessageTbl createTestMessage(int id, String sentBy, String subject) {
        MessageTbl msg = new MessageTbl();
        msg.setId(id);
        msg.setSentBy(sentBy);
        msg.setSentByNo("999998");
        msg.setSentByLocation(1);
        msg.setSentTo("Dr. Jones");
        msg.setSubject(subject);
        msg.setMessage("Test message body");
        msg.setDate(new Date());
        msg.setTime(new Date());
        msg.setType(0);
        msg.setType_link("");
        msg.setActionStatus("N");
        return msg;
    }

    // -----------------------------------------------------------------------
    // getMessage
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getMessage")
    class GetMessage {

        @Test
        @DisplayName("should return message when privilege granted")
        void shouldReturnMessage_whenPrivilegeGranted() {
            grantMsgReadPrivilege();
            MessageTbl expected = createTestMessage(1, "Dr. Smith", "Lab Results");
            when(mockMessageTblDao.find(1)).thenReturn(expected);

            MessageTbl result = manager.getMessage(loggedInInfo, 1);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("should throw SecurityException when read privilege denied")
        void shouldThrowException_whenReadDenied() {
            denyMsgReadPrivilege();

            assertThatThrownBy(() -> manager.getMessage(loggedInInfo, 1))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_msg");
        }
    }

    // -----------------------------------------------------------------------
    // getMyInboxMessages
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getMyInboxMessages")
    class GetMyInboxMessages {

        @Test
        @DisplayName("should return inbox messages filtered by provider and status")
        void shouldReturnMessages_filteredByProviderAndStatus() {
            grantMsgReadPrivilege();
            MessageList ml = new MessageList();
            ml.setId(1L);
            ml.setMessage(100);
            when(mockMessageListDao.search("999998", "new", 0, 20)).thenReturn(List.of(ml));

            List<MessageList> result = manager.getMyInboxMessages(loggedInInfo, "999998", "new", 0, 20);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should pass null status when calling overloaded method")
        void shouldPassNullStatus_whenOverloadedMethodCalled() {
            grantMsgReadPrivilege();
            when(mockMessageListDao.search("999998", null, 0, 20)).thenReturn(Collections.emptyList());

            List<MessageList> result = manager.getMyInboxMessages(loggedInInfo, "999998", 0, 20);

            assertThat(result).isEmpty();
            verify(mockMessageListDao).search("999998", null, 0, 20);
        }

        @Test
        @DisplayName("should throw SecurityException when read privilege denied")
        void shouldThrowException_whenReadDenied() {
            denyMsgReadPrivilege();

            assertThatThrownBy(() -> manager.getMyInboxMessages(loggedInInfo, "999998", 0, 20))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // -----------------------------------------------------------------------
    // getMyInboxMessagesCount
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getMyInboxMessagesCount")
    class GetMyInboxMessagesCount {

        @Test
        @DisplayName("should return count from DAO")
        void shouldReturnCount_fromDao() {
            grantMsgReadPrivilege();
            when(mockMessageListDao.searchAndReturnTotal("999998", "new")).thenReturn(5);

            Integer result = manager.getMyInboxMessagesCount(loggedInInfo, "999998", "new");

            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("should throw SecurityException when privilege denied")
        void shouldThrowException_whenDenied() {
            denyMsgReadPrivilege();

            assertThatThrownBy(() -> manager.getMyInboxMessagesCount(loggedInInfo, "999998", "new"))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // -----------------------------------------------------------------------
    // getMyInboxMessageCount (with patient filter)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getMyInboxMessageCount")
    class GetMyInboxMessageCount {

        @Test
        @DisplayName("should return total new count when not filtering by patient")
        void shouldReturnNewCount_whenNotFilteringByPatient() {
            grantMsgReadPrivilege();
            when(mockMessageListDao.searchAndReturnTotal("999998", MessageList.STATUS_NEW)).thenReturn(3);

            int result = manager.getMyInboxMessageCount(loggedInInfo, "999998", false);

            assertThat(result).isEqualTo(3);
        }

        @Test
        @DisplayName("should count only messages with demographics when filtering by patient")
        void shouldCountWithDemographic_whenFilteringByPatient() {
            grantMsgReadPrivilege();
            MessageList ml1 = new MessageList();
            ml1.setMessage(100);
            MessageList ml2 = new MessageList();
            ml2.setMessage(200);
            when(mockMessageListDao.findUnreadByProvider("999998")).thenReturn(List.of(ml1, ml2));
            // ml1 has demographics, ml2 does not
            when(mockMessengerDemographicManager.getAttachedDemographicList(loggedInInfo, 100))
                    .thenReturn(List.of(new MsgDemoMap()));
            when(mockMessengerDemographicManager.getAttachedDemographicList(loggedInInfo, 200))
                    .thenReturn(Collections.emptyList());

            int result = manager.getMyInboxMessageCount(loggedInInfo, "999998", true);

            assertThat(result).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // getInboxCountByDemographicNo
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getInboxCountByDemographicNo")
    class GetInboxCountByDemographicNo {

        @Test
        @DisplayName("should return count of messages attached to demographic")
        void shouldReturnCount_forDemographic() {
            when(mockMessengerDemographicManager.getMessageMapByDemographicNo(loggedInInfo, 100))
                    .thenReturn(List.of(new MsgDemoMap(), new MsgDemoMap()));

            int result = manager.getInboxCountByDemographicNo(loggedInInfo, 100);

            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("should return zero when no messages for demographic")
        void shouldReturnZero_whenNoMessages() {
            when(mockMessengerDemographicManager.getMessageMapByDemographicNo(loggedInInfo, 100))
                    .thenReturn(null);

            int result = manager.getInboxCountByDemographicNo(loggedInInfo, 100);

            assertThat(result).isEqualTo(0);
        }
    }

    // -----------------------------------------------------------------------
    // setMessageStatus
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("setMessageStatus")
    class SetMessageStatus {

        @Test
        @DisplayName("should update status and merge message")
        void shouldUpdateStatusAndMerge() {
            grantMsgUpdatePrivilege();
            MessageList ml = new MessageList();
            ml.setMessage(100);

            Long result = manager.setMessageStatus(loggedInInfo, ml, MessageList.STATUS_READ);

            assertThat(result).isEqualTo(100L);
            assertThat(ml.getStatus()).isEqualTo(MessageList.STATUS_READ);
            verify(mockMessageListDao).merge(ml);
        }

        @Test
        @DisplayName("should throw SecurityException when update privilege denied")
        void shouldThrowException_whenUpdateDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_msg"), eq(SecurityInfoManager.UPDATE), isNull()))
                    .thenReturn(false);
            MessageList ml = new MessageList();

            assertThatThrownBy(() -> manager.setMessageStatus(loggedInInfo, ml, "read"))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // -----------------------------------------------------------------------
    // setMessageRead
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("setMessageRead")
    class SetMessageRead {

        @Test
        @DisplayName("should mark local new messages as read")
        void shouldMarkLocalNewMessages_asRead() {
            grantMsgUpdatePrivilege();
            MessageList newMsg = new MessageList();
            newMsg.setMessage(100);
            newMsg.setStatus(MessageList.STATUS_NEW);
            newMsg.setDestinationFacilityId(0);
            when(mockMessageListDao.findByProviderNoAndMessageNo("999998", 42L)).thenReturn(List.of(newMsg));

            manager.setMessageRead(loggedInInfo, 42L, "999998");

            assertThat(newMsg.getStatus()).isEqualTo(MessageList.STATUS_READ);
            verify(mockMessageListDao).merge(newMsg);
        }

        @Test
        @DisplayName("should not change status of deleted messages")
        void shouldNotChangeStatus_ofDeletedMessages() {
            grantMsgUpdatePrivilege();
            MessageList deletedMsg = new MessageList();
            deletedMsg.setMessage(100);
            deletedMsg.setStatus(MessageList.STATUS_DELETED);
            deletedMsg.setDestinationFacilityId(0);
            when(mockMessageListDao.findByProviderNoAndMessageNo("999998", 42L)).thenReturn(List.of(deletedMsg));

            manager.setMessageRead(loggedInInfo, 42L, "999998");

            assertThat(deletedMsg.getStatus()).isEqualTo(MessageList.STATUS_DELETED);
            verify(mockMessageListDao, never()).merge(any());
        }

        @Test
        @DisplayName("should not change status of remote facility messages")
        void shouldNotChangeStatus_ofRemoteMessages() {
            grantMsgUpdatePrivilege();
            MessageList remoteMsg = new MessageList();
            remoteMsg.setMessage(100);
            remoteMsg.setStatus(MessageList.STATUS_NEW);
            remoteMsg.setDestinationFacilityId(5); // remote facility
            when(mockMessageListDao.findByProviderNoAndMessageNo("999998", 42L)).thenReturn(List.of(remoteMsg));

            manager.setMessageRead(loggedInInfo, 42L, "999998");

            assertThat(remoteMsg.getStatus()).isEqualTo(MessageList.STATUS_NEW);
        }
    }

    // -----------------------------------------------------------------------
    // deleteMessage
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteMessage")
    class DeleteMessage {

        @Test
        @DisplayName("should set all matching messages to deleted status")
        void shouldSetDeletedStatus_forAllMatchingMessages() {
            grantMsgUpdatePrivilege();
            MessageList ml = new MessageList();
            ml.setMessage(100);
            ml.setStatus(MessageList.STATUS_NEW);
            when(mockMessageListDao.findByProviderNoAndMessageNo("999998", 42L)).thenReturn(List.of(ml));

            manager.deleteMessage(loggedInInfo, 42);

            assertThat(ml.getStatus()).isEqualTo(MessageList.STATUS_DELETED);
        }
    }

    // -----------------------------------------------------------------------
    // saveMessage
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("saveMessage")
    class SaveMessage {

        @Test
        @DisplayName("should persist message and return ID")
        void shouldPersistMessage_andReturnId() {
            grantMsgWritePrivilege();
            MessageTbl msg = createTestMessage(0, "System", "Alert");
            msg.setId(55);

            Integer result = manager.saveMessage(loggedInInfo, msg);

            assertThat(result).isEqualTo(55);
            verify(mockMessageTblDao).persist(msg);
        }

        @Test
        @DisplayName("should throw SecurityException when write privilege denied")
        void shouldThrowException_whenWriteDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_msg"), eq(SecurityInfoManager.WRITE), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.saveMessage(loggedInInfo, new MessageTbl()))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // -----------------------------------------------------------------------
    // addRecipientToMessage
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("addRecipientToMessage")
    class AddRecipientToMessage {

        @Test
        @DisplayName("should persist message list entry with all fields")
        void shouldPersistEntry_withAllFields() {
            grantMsgWritePrivilege();

            manager.addRecipientToMessage(loggedInInfo, 100, "222", 1, 5, 3, "new");

            verify(mockMessageListDao).persist(argThat(ml ->
                    ml.getMessage() == 100 &&
                    "222".equals(ml.getProviderNo()) &&
                    "new".equals(ml.getStatus()) &&
                    ml.getRemoteLocation() == 1 &&
                    ml.getDestinationFacilityId() == 5 &&
                    ml.getSourceFacilityId() == 3
            ));
        }

        @Test
        @DisplayName("should throw SecurityException when write privilege denied")
        void shouldThrowException_whenWriteDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_msg"), eq(SecurityInfoManager.WRITE), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.addRecipientToMessage(loggedInInfo, 100, "222", 1, 5, 3, "new"))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // -----------------------------------------------------------------------
    // addRecipientsToMessage (array overload)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("addRecipientsToMessage")
    class AddRecipientsToMessage {

        @Test
        @DisplayName("should persist one entry per provider")
        void shouldPersistOneEntryPerProvider() {
            grantMsgWritePrivilege();
            String[] providers = {"111", "222", "333"};

            manager.addRecipientsToMessage(loggedInInfo, 100, providers, 1, 5, 0);

            verify(mockMessageListDao, times(3)).persist(any(MessageList.class));
        }
    }

    // -----------------------------------------------------------------------
    // getReplyToSender
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getReplyToSender")
    class GetReplyToSender {

        @Test
        @DisplayName("should return sender contact identifier from message")
        void shouldReturnSenderContact_fromMessage() {
            grantMsgReadPrivilege();
            MessageTbl msg = createTestMessage(1, "Dr. Smith", "Lab");
            msg.setSentByNo("111");
            msg.setSentByLocation(2);

            List<ContactIdentifier> result = manager.getReplyToSender(loggedInInfo, msg);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getContactId()).isEqualTo("111");
            assertThat(result.get(0).getClinicLocationNo()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw SecurityException when privilege denied")
        void shouldThrowException_whenDenied() {
            denyMsgReadPrivilege();

            assertThatThrownBy(() -> manager.getReplyToSender(loggedInInfo, 1))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // -----------------------------------------------------------------------
    // getCurrentLocationId
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getCurrentLocationId")
    class GetCurrentLocationId {

        @Test
        @DisplayName("should return current location ID from oscarcommlocations")
        void shouldReturnLocationId() {
            OscarCommLocations loc = new OscarCommLocations();
            loc.setId(42);
            when(mockOscarCommLocationsDao.findByCurrent1(1)).thenReturn(List.of(loc));

            int result = manager.getCurrentLocationId();

            assertThat(result).isEqualTo(42);
        }

        @Test
        @DisplayName("should return 0 when no current location found")
        void shouldReturnZero_whenNoLocationFound() {
            when(mockOscarCommLocationsDao.findByCurrent1(1)).thenReturn(null);

            int result = manager.getCurrentLocationId();

            assertThat(result).isEqualTo(0);
        }
    }

    // -----------------------------------------------------------------------
    // removeDuplicates
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("removeDuplicates")
    class RemoveDuplicates {

        @Test
        @DisplayName("should remove duplicate strings from array")
        void shouldRemoveDuplicates() {
            String[] input = {"111", "222", "111", "333", "222"};

            String[] result = manager.removeDuplicates(input);

            assertThat(result).containsExactlyInAnyOrder("111", "222", "333");
        }

        @Test
        @DisplayName("should return same array when no duplicates")
        void shouldReturnSame_whenNoDuplicates() {
            String[] input = {"111", "222", "333"};

            String[] result = manager.removeDuplicates(input);

            assertThat(result).containsExactlyInAnyOrder("111", "222", "333");
        }
    }

    // -----------------------------------------------------------------------
    // createContactIdentifierList (static utility)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("createContactIdentifierList")
    class CreateContactIdentifierList {

        @Test
        @DisplayName("should return empty list for null input")
        void shouldReturnEmptyList_forNullInput() {
            List<ContactIdentifier> result = MessagingManagerImpl.createContactIdentifierList(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should create contact identifiers from composite IDs")
        void shouldCreateContacts_fromCompositeIds() {
            String[] ids = {"111|1|0", "222|2|5"};

            List<ContactIdentifier> result = MessagingManagerImpl.createContactIdentifierList(ids);

            assertThat(result).hasSize(2);
        }
    }
}
