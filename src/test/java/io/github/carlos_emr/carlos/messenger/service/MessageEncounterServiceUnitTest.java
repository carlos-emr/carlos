/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.messenger.service;

import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.commn.model.MsgDemoMap;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Guards chart-local message loading without relying on the shared encounter bean. */
@Tag("unit")
class MessageEncounterServiceUnitTest {
    private final MessageTblDao messages = mock(MessageTblDao.class);
    private final MsgDemoMapDao links = mock(MsgDemoMapDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo caller = mock(LoggedInInfo.class);
    private final MessageEncounterService service = new MessageEncounterService(messages, links, security);

    private void allow() {
        when(security.hasPrivilege(caller, "_msg", "r", null)).thenReturn(true);
        when(security.hasPrivilege(caller, "_eChart", "r", "42")).thenReturn(true);
    }

    private void link(int demographic) {
        var link = new MsgDemoMap();
        link.setDemographic_no(demographic);
        when(links.findByMessageId(7)).thenReturn(List.of(link));
    }

    @Test
    void shouldRejectBeforeReading_whenMessagePermissionMissing() {
        assertThatThrownBy(() -> service.load(caller, 7, 42)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(links, messages);
    }

    @Test
    void shouldRejectBeforeReading_whenChartPermissionMissing() {
        when(security.hasPrivilege(caller, "_msg", "r", null)).thenReturn(true);
        assertThatThrownBy(() -> service.load(caller, 7, 42)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(links, messages);
    }

    @Test
    void shouldRejectBeforeReading_whenLinkedToAnotherPatient() {
        allow(); link(43);
        assertThatThrownBy(() -> service.load(caller, 7, 42)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(messages);
    }

    @Test
    void shouldReportMissingMessage_whenDeletedAfterLinking() {
        allow(); link(42);
        assertThatThrownBy(() -> service.load(caller, 7, 42))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Message no longer exists");
    }

    @Test
    void shouldReturnCompletePlainText_whenAccessAndPatientLinkMatch() {
        allow(); link(42);
        var message = new MessageTbl();
        message.setSentBy("Sender"); message.setSentTo("Recipient");
        message.setDate(java.sql.Date.valueOf("2026-10-03"));
        message.setTime(java.sql.Time.valueOf("12:34:56"));
        message.setSubject("Subject <>&"); message.setMessage("line one\nline two </script>");
        when(messages.find(7)).thenReturn(message);
        assertThat(service.load(caller, 7, 42)).startsWith("From: Sender\nTo: Recipient\nDate: ")
                .contains("12:34:56", "\nSubject: Subject <>&\nline one\nline two </script>");
        verify(messages, never()).merge(any());
        verify(messages, never()).persist(any());
    }
}
