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
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Verifies saved-message access for local senders and recipients, including configured
 * clinic locations and remote participants that share a provider number.
 *
 * @since 2026-10-24
 */
@Tag("unit")
class MsgAttachmentAccessUnitTest extends CarlosUnitTestBase {
    private OscarCommLocationsDao locations;
    private MessageListDao recipients;
    private MessageTblDao messages;
    private MessageTbl message;
    private LoggedInInfo login;

    @BeforeEach
    void prepare() {
        recipients = createAndRegisterMock(MessageListDao.class);
        messages = createAndRegisterMock(MessageTblDao.class);
        locations = createAndRegisterMock(OscarCommLocationsDao.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("101");
        message = new MessageTbl();
        message.setSentByNo("202");
        when(messages.find(1)).thenReturn(message);
    }

    @Test
    void shouldDenyAttachment_whenProviderIsNeitherSenderNorRecipient() {
        assertThat(MsgAttachmentAccess.find(login, 1)).isNull();
    }

    @Test
    void shouldAllowAttachment_whenProviderIsLocalSender() {
        message.setSentByNo("101");
        assertThat(MsgAttachmentAccess.find(login, 1)).isSameAs(message);
        verifyNoInteractions(recipients);
    }

    @Test
    void shouldAllowAttachment_whenClinicUsesConfiguredLocation() {
        var location = new io.github.carlos_emr.carlos.commn.model.OscarCommLocations();
        location.setId(7);
        when(locations.findByCurrent1(1)).thenReturn(List.of(location));
        message.setSentByNo("101");
        message.setSentByLocation(7);
        assertThat(MsgAttachmentAccess.find(login, 1)).isSameAs(message);
        message.setSentByNo("202");
        MessageList recipient = new MessageList();
        recipient.setRemoteLocation(7);
        when(recipients.findByProviderNoAndMessageNo("101", 1L)).thenReturn(List.of(recipient));
        assertThat(MsgAttachmentAccess.find(login, 1)).isSameAs(message);
    }

    @Test
    void shouldDenyAttachment_whenRemoteSenderSharesProviderNumber() {
        message.setSentByNo("101");
        message.setSentByLocation(8);
        assertThat(MsgAttachmentAccess.find(login, 1)).isNull();
    }

    @Test
    void shouldAllowAttachment_whenProviderIsLocalRecipient() {
        MessageList recipient = new MessageList();
        when(recipients.findByProviderNoAndMessageNo("101", 1L)).thenReturn(List.of(recipient));
        assertThat(MsgAttachmentAccess.find(login, 1)).isSameAs(message);
    }

    @Test
    void shouldDenyAttachment_whenRemoteRecipientSharesProviderNumber() {
        MessageList recipient = new MessageList();
        recipient.setRemoteLocation(8);
        when(recipients.findByProviderNoAndMessageNo("101", 1L)).thenReturn(List.of(recipient));
        assertThat(MsgAttachmentAccess.find(login, 1)).isNull();
        recipient.setRemoteLocation(0);
        recipient.setDestinationFacilityId(8);
        assertThat(MsgAttachmentAccess.find(login, 1)).isNull();
    }

    @Test
    void shouldAvoidLoadingAttachments_whenProviderIsNotAuthenticated() {
        assertThat(MsgAttachmentAccess.find(null, 1)).isNull();
        assertThat(MsgAttachmentAccess.find(login, 0)).isNull();
        verifyNoInteractions(messages, recipients);
    }
}
