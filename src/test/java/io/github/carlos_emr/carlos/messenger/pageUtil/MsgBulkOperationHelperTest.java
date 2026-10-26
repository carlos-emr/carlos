/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.commn.model.OscarCommLocations;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MsgBulkOperationHelper}.
 *
 * <p>Verifies ID validation ({@code > 0L}), per-message error handling,
 * and failure count reporting for bulk message operations.</p>
 *
 * @since 2026-02-20
 */
@DisplayName("MsgBulkOperationHelper Unit Tests")
@Tag("unit")
@Tag("messenger")
class MsgBulkOperationHelperTest extends CarlosUnitTestBase {

    @Mock
    private MessageListDao mockDao;
    private OscarCommLocationsDao locations;

    private MockHttpServletRequest mockRequest;

    private static final String PROVIDER_NO = "999998";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registerMock(MessageListDao.class, mockDao);
        locations = createAndRegisterMock(OscarCommLocationsDao.class);
        mockRequest = new MockHttpServletRequest();
    }

    @Test
    @DisplayName("should skip invalid message ID when conversion returns zero")
    void shouldSkipInvalidMessageId_whenZero() {
        // Given - invalid IDs that ConversionUtils.fromLongString() converts to 0
        String[] messageIds = {"abc", "", "0"};

        // When
        MsgBulkOperationHelper.updateSelectedMessages(
                mockRequest, PROVIDER_NO, messageIds, msg -> msg.setDeleted(true));

        // Then - DAO should never be called since all IDs are invalid
        verify(mockDao, never()).findByProviderNoAndMessageNo(anyString(), anyLong());
        assertThat(mockRequest.getAttribute("updateFailureCount")).isEqualTo(3);
    }

    @Test
    @DisplayName("should continue processing when one message fails")
    void shouldContinueProcessing_whenOneMessageFails() {
        // Given - three valid IDs, but the second one throws
        String[] messageIds = {"100", "200", "300"};

        MessageList msg1 = mock(MessageList.class);
        MessageList msg3 = mock(MessageList.class);

        when(mockDao.findByProviderNoAndMessageNo(PROVIDER_NO, 100L))
                .thenReturn(List.of(msg1));
        when(mockDao.findByProviderNoAndMessageNo(PROVIDER_NO, 200L))
                .thenThrow(new org.springframework.dao.DataRetrievalFailureException("DB error"));
        when(mockDao.findByProviderNoAndMessageNo(PROVIDER_NO, 300L))
                .thenReturn(List.of(msg3));

        // When
        MsgBulkOperationHelper.updateSelectedMessages(
                mockRequest, PROVIDER_NO, messageIds, msg -> msg.setDeleted(true));

        // Then - first and third messages should be processed, second skipped
        verify(msg1).setDeleted(true);
        verify(msg3).setDeleted(true);
        verify(mockDao).merge(msg1);
        verify(mockDao).merge(msg3);
        assertThat(mockRequest.getAttribute("updateFailureCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("should set failure count attribute when partial failure occurs")
    void shouldSetFailureCountAttribute_whenPartialFailure() {
        // Given - mix of valid and invalid IDs
        String[] messageIds = {"100", "invalid", "300"};

        MessageList msg1 = mock(MessageList.class);
        when(mockDao.findByProviderNoAndMessageNo(PROVIDER_NO, 100L))
                .thenReturn(List.of(msg1));
        when(mockDao.findByProviderNoAndMessageNo(PROVIDER_NO, 300L))
                .thenReturn(Collections.emptyList());

        // When
        MsgBulkOperationHelper.updateSelectedMessages(
                mockRequest, PROVIDER_NO, messageIds,
                msg -> msg.setStatus(MessageList.STATUS_READ));

        // Then - "invalid" should be counted as failure
        assertThat(mockRequest.getAttribute("updateFailureCount")).isEqualTo(1);
        // msg1 should be processed
        verify(msg1).setStatus(MessageList.STATUS_READ);
        verify(mockDao).merge(msg1);
    }

    @Test
    @DisplayName("should not count as failure when DAO returns empty list")
    void shouldNotCountAsFailure_whenDaoReturnsEmptyList() {
        // Given - valid ID but no matching messages found
        String[] messageIds = {"100"};
        when(mockDao.findByProviderNoAndMessageNo(PROVIDER_NO, 100L))
                .thenReturn(Collections.emptyList());

        // When
        MsgBulkOperationHelper.updateSelectedMessages(
                mockRequest, PROVIDER_NO, messageIds, msg -> msg.setDeleted(true));

        // Then - no failure count since the ID was valid, just no matching rows
        assertThat(mockRequest.getAttribute(MsgBulkOperationHelper.ATTR_UPDATE_FAILURE_COUNT)).isNull();
        verify(mockDao, never()).merge(any());
    }

    @Test
    @DisplayName("should apply action to all valid messages")
    void shouldApplyAction_toAllValidMessages() {
        // Given - all valid IDs
        String[] messageIds = {"100", "200"};

        MessageList msg1 = mock(MessageList.class);
        MessageList msg2 = mock(MessageList.class);

        when(mockDao.findByProviderNoAndMessageNo(PROVIDER_NO, 100L))
                .thenReturn(List.of(msg1));
        when(mockDao.findByProviderNoAndMessageNo(PROVIDER_NO, 200L))
                .thenReturn(List.of(msg2));

        // When
        MsgBulkOperationHelper.updateSelectedMessages(
                mockRequest, PROVIDER_NO, messageIds,
                msg -> msg.setStatus(MessageList.STATUS_NEW));

        // Then - all messages processed, no failure count attribute
        verify(msg1).setStatus(MessageList.STATUS_NEW);
        verify(msg2).setStatus(MessageList.STATUS_NEW);
        verify(mockDao).merge(msg1);
        verify(mockDao).merge(msg2);
        assertThat(mockRequest.getAttribute("updateFailureCount")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "unread", "delete", "undelete"})
    void shouldOnlyChangeLocalRecipients_whenProviderNumberIsReusedRemotely(String operation) {
        OscarCommLocations firstClinic = new OscarCommLocations();
        firstClinic.setId(37);
        OscarCommLocations secondClinic = new OscarCommLocations();
        secondClinic.setId(38);
        when(locations.findByCurrent1(1)).thenReturn(List.of(firstClinic, secondClinic));
        String initialStatus = "unread".equals(operation) ? MessageList.STATUS_READ : MessageList.STATUS_NEW;
        MessageList legacy = recipient(0, 0, initialStatus);
        MessageList firstLocal = recipient(37, 0, initialStatus);
        MessageList secondLocal = recipient(38, -1, initialStatus);
        MessageList remoteClinic = recipient(99, 0, initialStatus);
        MessageList remoteFacility = recipient(37, 9, initialStatus);
        when(mockDao.findByProviderNoAndMessageNo(PROVIDER_NO, 100L))
                .thenReturn(List.of(legacy, firstLocal, remoteClinic, secondLocal, remoteFacility));

        MsgBulkOperationHelper.updateSelectedMessages(mockRequest, PROVIDER_NO, new String[]{"100"}, row -> {
            switch (operation) {
                case "read" -> row.setStatus(MessageList.STATUS_READ);
                case "unread" -> row.setStatus(MessageList.STATUS_NEW);
                case "delete" -> row.setDeleted(true);
                case "undelete" -> row.setDeleted(false);
                default -> throw new AssertionError(operation);
            }
        });

        String expectedStatus = switch (operation) {
            case "read" -> MessageList.STATUS_READ;
            case "unread" -> MessageList.STATUS_NEW;
            case "delete" -> MessageList.STATUS_DELETED;
            default -> null;
        };
        assertThat(legacy.getStatus()).isEqualTo(expectedStatus);
        assertThat(firstLocal.getStatus()).isEqualTo(expectedStatus);
        assertThat(secondLocal.getStatus()).isEqualTo(expectedStatus);
        assertThat(remoteClinic.getStatus()).isEqualTo(initialStatus);
        assertThat(remoteFacility.getStatus()).isEqualTo(initialStatus);
        verify(mockDao).merge(same(legacy));
        verify(mockDao).merge(same(firstLocal));
        verify(mockDao).merge(same(secondLocal));
        verify(mockDao, never()).merge(same(remoteClinic));
        verify(mockDao, never()).merge(same(remoteFacility));
    }

    @Test
    void shouldReportUnchangedSelection_whenOnlyRemoteRecipientsMatchProviderNumber() {
        MessageList remoteClinic = recipient(99, 0, MessageList.STATUS_NEW);
        MessageList remoteFacility = recipient(0, 9, MessageList.STATUS_NEW);
        when(mockDao.findByProviderNoAndMessageNo(PROVIDER_NO, 100L))
                .thenReturn(List.of(remoteClinic, remoteFacility));

        MsgBulkOperationHelper.updateSelectedMessages(mockRequest, PROVIDER_NO, new String[]{"100"},
                row -> row.setDeleted(true));

        assertThat(mockRequest.getAttribute(MsgBulkOperationHelper.ATTR_UPDATE_FAILURE_COUNT)).isEqualTo(1);
        assertThat(remoteClinic.getStatus()).isEqualTo(MessageList.STATUS_NEW);
        assertThat(remoteFacility.getStatus()).isEqualTo(MessageList.STATUS_NEW);
        verify(mockDao, never()).merge(any());
    }

    @Test
    void shouldLeaveAllRecipientsUnchanged_whenLocalLocationLookupFails() {
        when(locations.findByCurrent1(1))
                .thenThrow(new org.springframework.dao.DataRetrievalFailureException("location lookup failed"));

        assertThatThrownBy(() -> MsgBulkOperationHelper.updateSelectedMessages(
                mockRequest, PROVIDER_NO, new String[]{"100", "200"}, row -> row.setDeleted(true)))
                .isInstanceOf(org.springframework.dao.DataRetrievalFailureException.class);

        verifyNoInteractions(mockDao);
    }

    private MessageList recipient(int location, int facility, String status) {
        MessageList row = new MessageList();
        row.setProviderNo(PROVIDER_NO);
        row.setMessage(100);
        row.setRemoteLocation(location);
        row.setDestinationFacilityId(facility);
        row.setStatus(status);
        return row;
    }
}
