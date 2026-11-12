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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.commn.model.OscarCommLocations;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Protects remote recipients whose provider numbers collide with a local mailbox identity.
 *
 * @since 2026-10-24
 */
@Tag("unit")
@Tag("security")
@Tag("messenger")
class MessagingRecipientLocalityUnitTest extends CarlosUnitTestBase {
    private MessagingManagerImpl manager;
    private MessageListDao recipients;
    private OscarCommLocationsDao locations;
    private SecurityInfoManager security;
    private LoggedInInfo login;

    @BeforeEach
    void setUp() {
        manager = new MessagingManagerImpl();
        recipients = createAndRegisterMock(MessageListDao.class);
        locations = createAndRegisterMock(OscarCommLocationsDao.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_msg"), eq(SecurityInfoManager.UPDATE), isNull())).thenReturn(true);
        ReflectionTestUtils.setField(manager, "messageListDao", recipients);
        ReflectionTestUtils.setField(manager, "oscarCommLocationsDao", locations);
        ReflectionTestUtils.setField(manager, "securityInfoManager", security);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("101");
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "delete"})
    void shouldPreserveRemoteRecipientState_whenProviderNumberCollidesAcrossClinics(String operation) {
        OscarCommLocations firstClinic = new OscarCommLocations();
        firstClinic.setId(37);
        OscarCommLocations secondClinic = new OscarCommLocations();
        secondClinic.setId(38);
        when(locations.findByCurrent1(1)).thenReturn(List.of(firstClinic, secondClinic));
        MessageList legacy = recipient(0, 0);
        MessageList firstLocal = recipient(37, 0);
        MessageList secondLocal = recipient(38, -1);
        MessageList remoteClinic = recipient(99, 0);
        MessageList remoteFacility = recipient(37, 9);
        when(recipients.findByProviderNoAndMessageNo("101", 100L))
                .thenReturn(List.of(legacy, remoteClinic, firstLocal, remoteFacility, secondLocal));

        update(operation);

        String expected = "read".equals(operation) ? MessageList.STATUS_READ : MessageList.STATUS_DELETED;
        assertThat(legacy.getStatus()).isEqualTo(expected);
        assertThat(firstLocal.getStatus()).isEqualTo(expected);
        assertThat(secondLocal.getStatus()).isEqualTo(expected);
        assertThat(remoteClinic.getStatus()).isEqualTo(MessageList.STATUS_NEW);
        assertThat(remoteFacility.getStatus()).isEqualTo(MessageList.STATUS_NEW);
        verify(recipients).merge(same(legacy));
        verify(recipients).merge(same(firstLocal));
        verify(recipients).merge(same(secondLocal));
        verify(recipients, never()).merge(same(remoteClinic));
        verify(recipients, never()).merge(same(remoteFacility));
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "delete"})
    void shouldLeaveRecipientsUnchanged_whenLocalLocationLookupFails(String operation) {
        when(locations.findByCurrent1(1)).thenThrow(new DataRetrievalFailureException("location lookup failed"));

        assertThatThrownBy(() -> update(operation)).isInstanceOf(DataRetrievalFailureException.class);

        verifyNoInteractions(recipients);
    }

    @ParameterizedTest
    @CsvSource({"0,del", "0,DEL", "0,sent", "0,SENT", "37,del", "37,DEL", "37,sent", "37,SENT"})
    void shouldPreserveArchivedAndSentState_whenOpeningConfiguredLocalMessage(int location, String status) {
        OscarCommLocations clinic = new OscarCommLocations();
        clinic.setId(37);
        when(locations.findByCurrent1(1)).thenReturn(List.of(clinic));
        MessageList retained = recipient(location, 0);
        retained.setStatus(status);
        MessageList unread = recipient(location, 0);
        when(recipients.findByProviderNoAndMessageNo("101", 100L)).thenReturn(List.of(retained, unread));

        manager.setMessageRead(login, 100L, "101");

        assertThat(retained.getStatus()).isEqualTo(status);
        assertThat(unread.getStatus()).isEqualTo(MessageList.STATUS_READ);
        verify(recipients, never()).merge(same(retained));
        verify(recipients).merge(same(unread));
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "delete"})
    void shouldPreserveMailboxRows_whenUpdatePrivilegeIsDenied(String operation) {
        when(security.hasPrivilege(login, "_msg", SecurityInfoManager.UPDATE, (String) null)).thenReturn(false);
        MessageList first = recipient(0, 0);
        MessageList second = recipient(0, 0);
        when(recipients.findByProviderNoAndMessageNo("101", 100L)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> update(operation)).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_msg)");

        assertThat(first.getStatus()).isEqualTo(MessageList.STATUS_NEW);
        assertThat(second.getStatus()).isEqualTo(MessageList.STATUS_NEW);
        verify(recipients, never()).merge(any());
    }

    private void update(String operation) {
        if ("read".equals(operation)) {
            manager.setMessageRead(login, 100L, "101");
        } else {
            manager.deleteMessage(login, 100);
        }
    }

    private MessageList recipient(int location, int facility) {
        MessageList row = new MessageList();
        row.setProviderNo("101");
        row.setMessage(100);
        row.setRemoteLocation(location);
        row.setDestinationFacilityId(facility);
        row.setStatus(MessageList.STATUS_NEW);
        return row;
    }
}
