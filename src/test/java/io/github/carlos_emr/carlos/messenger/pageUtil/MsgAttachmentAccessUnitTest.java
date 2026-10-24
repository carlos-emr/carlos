/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
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
