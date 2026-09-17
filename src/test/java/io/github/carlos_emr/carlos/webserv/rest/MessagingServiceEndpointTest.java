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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.MessagingManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * CXF local-transport endpoint tests for {@link MessagingService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("MessagingService REST endpoint tests")
class MessagingServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private MessagingManager mockMessagingManager;

    @Mock
    private Provider mockProvider;

    @Test
    void shouldReturnXmlCount_whenCountClientRequestsIt() throws Exception {
        when(mockMessagingManager.getMyInboxMessageCount(any(LoggedInInfo.class), eq("999998"), anyBoolean()))
                .thenReturn(5);
        try (Response response = request().path("/messaging/count")
                .replaceHeader("Accept", "application/xml").get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getMediaType().toString()).startsWith("application/xml");
            var xml = io.github.carlos_emr.carlos.utility.XmlUtils.toDocument(response.readEntity(String.class));
            assertThat(xml.getDocumentElement().getTagName()).isEqualTo("count");
            assertThat(xml.getDocumentElement().getTextContent()).isEqualTo("5");
        }
    }

    @Override
    protected Object getServiceBean() {
        MessagingService service = new MessagingService();
        injectDependency(service, "messagingManager", mockMessagingManager);
        return service;
    }

    @BeforeEach
    void setUpProvider() {
        registerMock(io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao.class));
        registerMock(io.github.carlos_emr.carlos.commn.dao.DemographicDao.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.DemographicDao.class));
        var messages = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.MessageTblDao.class);
        registerMock(io.github.carlos_emr.carlos.commn.dao.MessageTblDao.class, messages);
        var message = new io.github.carlos_emr.carlos.commn.model.MessageTbl();
        message.setId(17);
        message.setDate(java.sql.Date.valueOf("2026-01-02"));
        message.setTime(java.sql.Time.valueOf("09:00:00"));
        message.setSubject("Synthetic message subject");
        message.setMessage("Synthetic message body");
        when(messages.find(17)).thenReturn(message);
        when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(mockProvider);
        when(mockProvider.getProviderNo()).thenReturn("999998");
    }

    @Nested
    @DisplayName("GET /messaging/unread")
    class GetUnreadMessages {

        @Test

        @DisplayName("should return 200 with unread messages")
        void shouldReturn200_whenUnreadMessagesExist() {
            MessageList msg = new MessageList();
            msg.setMessage(17);
            when(mockMessagingManager.getMyInboxMessages(any(LoggedInInfo.class), eq("999998"), eq(MessageList.STATUS_NEW), eq(0), eq(20)))
                .thenReturn(List.of(msg));

            Response response = request().path("/messaging/unread")
                .query("startIndex", 0)
                .query("limit", 20)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(String.class)).contains("Synthetic message subject", "Synthetic message body");
        }

        @Test
        @DisplayName("should return 200 with empty list when no unread messages")
        void shouldReturn200WithEmptyList_whenNoUnreadMessages() {
            when(mockMessagingManager.getMyInboxMessages(any(LoggedInInfo.class), eq("999998"), eq(MessageList.STATUS_NEW), eq(0), eq(20)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/messaging/unread")
                .query("startIndex", 0)
                .query("limit", 20)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/total").intValue()).isEqualTo(0);
            assertThat(wireJson.at("/messages").isArray()).isTrue();
            assertThat(wireJson.at("/messages")).hasSize(0);
        }
    }

    @Nested
    @DisplayName("GET /messaging/count")
    class GetMessageCount {

        @Test
        @DisplayName("should return 200 with message count")
        void shouldReturn200_withMessageCount() {
            when(mockMessagingManager.getMyInboxMessageCount(any(LoggedInInfo.class), eq("999998"), anyBoolean()))
                .thenReturn(5);

            Response response = request().path("/messaging/count").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.intValue()).isEqualTo(5);
        }
    }
}
