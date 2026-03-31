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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.inbox.InboxManagerQuery;
import io.github.carlos_emr.carlos.inbox.InboxManagerResponse;
import io.github.carlos_emr.carlos.managers.InboxManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * HTTP-level endpoint tests for {@link InboxService} using CXF local transport.
 *
 * <p>InboxService uses {@code SpringUtils.getBean(ProviderLabRoutingDao.class)}
 * directly, so this test registers the mock via {@code registerMock()}.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("InboxService REST endpoint tests")
class InboxServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private InboxManager mockInboxManager;

    @Mock
    private ProviderLabRoutingDao mockProviderLabRoutingDao;

    @Override
    protected Object getServiceBean() {
        InboxService service = new InboxService();
        injectDependency(service, "inboxManager", mockInboxManager);
        registerMock(ProviderLabRoutingDao.class, mockProviderLabRoutingDao);
        return service;
    }

    @BeforeEach
    void setUpProvider() {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    @Nested
    @DisplayName("GET /inbox/mine")
    class GetMyInbox {

        @Test
        @DisplayName("should return 200 with inbox items")
        void shouldReturn200_whenInboxItemsExist() {
            InboxManagerResponse inboxResponse = new InboxManagerResponse();
            inboxResponse.setLabdocs(Collections.emptyList());
            when(mockInboxManager.getInboxResults(any(LoggedInInfo.class), any(InboxManagerQuery.class)))
                .thenReturn(inboxResponse);
            when(mockProviderLabRoutingDao.findByProviderNo(eq("999998"), eq("N")))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/inbox/mine")
                .query("limit", 20)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /inbox/mine/count")
    class GetMyInboxCount {

        @Test
        @DisplayName("should return 200 with count")
        void shouldReturn200_withCount() {
            when(mockProviderLabRoutingDao.findByProviderNo(eq("999998"), eq("N")))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/inbox/mine/count").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
