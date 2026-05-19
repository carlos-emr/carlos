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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
 * HTTP-level endpoint tests for {@link MessagingService} using CXF local transport.
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

    @Override
    protected Object getServiceBean() {
        MessagingService service = new MessagingService();
        injectDependency(service, "messagingManager", mockMessagingManager);
        return service;
    }

    @BeforeEach
    void setUpProvider() {
        when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(mockProvider);
        when(mockProvider.getProviderNo()).thenReturn("999998");
    }

    @Nested
    @DisplayName("GET /messaging/unread")
    class GetUnreadMessages {

        @Test
        @Disabled("TODO: Requires mock setup for MessagingConverter internal SpringUtils.getBean() calls on CXF thread")
        @DisplayName("should return 200 with unread messages")
        void shouldReturn200_whenUnreadMessagesExist() {
            MessageList msg = new MessageList();
            when(mockMessagingManager.getMyInboxMessages(any(LoggedInInfo.class), eq("999998"), eq(MessageList.STATUS_NEW), eq(0), eq(20)))
                .thenReturn(List.of(msg));

            Response response = request().path("/messaging/unread")
                .query("startIndex", 0)
                .query("limit", 20)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
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

            Response response = request().replaceHeader("Accept", "*/*").path("/messaging/count").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
