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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;

/**
 * Unit tests for {@link TicklerWebService} bulk operations.
 *
 * <p>Regression coverage for the bulk {@code /complete} and {@code /delete}
 * endpoints. Iterating a Jackson {@code ArrayNode} yields {@code JsonNode}
 * elements, so the previous {@code (Integer) id} cast threw
 * {@link ClassCastException} (HTTP 500) for every non-empty {@code ticklers}
 * payload; these tests pin that the ids are now read with {@code asInt()} and
 * the correct tickler numbers reach {@link TicklerManager}.</p>
 *
 * @since 2026-06-21
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicklerWebService unit tests")
@Tag("unit")
@Tag("fast")
class TicklerWebServiceUnitTest extends CarlosUnitTestBase {

    @Mock
    private TicklerManager ticklerManager;

    @Mock
    private SecurityInfoManager securityInfoManager;

    private TicklerWebService service;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
        service = new TicklerWebService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        injectDependency(service, "ticklerManager", ticklerManager);
        injectDependency(service, "securityInfoManager", securityInfoManager);

        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_tickler"), eq("u"), any()))
                .thenReturn(true);
    }

    private JsonNode payload(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    @Test
    @Tag("update")
    @DisplayName("should complete each tickler when a valid id array is provided")
    void shouldCompleteEachTickler_whenValidIdArrayProvided() throws Exception {
        RestResponse<String> response = service.completeTicklers(payload("{\"ticklers\":[1,2,3]}"));

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
        verify(ticklerManager).completeTickler(eq(loggedInInfo), eq(1), any());
        verify(ticklerManager).completeTickler(eq(loggedInInfo), eq(2), any());
        verify(ticklerManager).completeTickler(eq(loggedInInfo), eq(3), any());
    }

    @Test
    @Tag("delete")
    @DisplayName("should delete each tickler when a valid id array is provided")
    void shouldDeleteEachTickler_whenValidIdArrayProvided() throws Exception {
        RestResponse<String> response = service.deleteTicklers(payload("{\"ticklers\":[4,5]}"));

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
        verify(ticklerManager).deleteTickler(eq(loggedInInfo), eq(4), any());
        verify(ticklerManager).deleteTickler(eq(loggedInInfo), eq(5), any());
    }

    @Test
    @Tag("update")
    @DisplayName("should not call the manager when the tickler array is empty")
    void shouldNotCallManager_whenTicklerArrayIsEmpty() throws Exception {
        RestResponse<String> response = service.completeTicklers(payload("{\"ticklers\":[]}"));

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
        verify(ticklerManager, never()).completeTickler(any(), any(), any());
    }

    @Test
    @Tag("update")
    @DisplayName("should deny completion when the caller lacks tickler update privilege")
    void shouldDenyCompletion_whenCallerLacksTicklerUpdatePrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_tickler"), eq("u"), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.completeTicklers(payload("{\"ticklers\":[1]}")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access Denied");
        verify(ticklerManager, never()).completeTickler(any(), any(), any());
    }

    @Test
    @Tag("update")
    @DisplayName("should return an error when the ticklers field is missing")
    void shouldReturnError_whenTicklersFieldMissing() throws Exception {
        RestResponse<String> response = service.completeTicklers(payload("{}"));

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
        verify(ticklerManager, never()).completeTickler(any(), any(), any());
    }

    @Test
    @Tag("update")
    @DisplayName("should return an error when the ticklers field is not an array")
    void shouldReturnError_whenTicklersIsNotArray() throws Exception {
        RestResponse<String> response = service.completeTicklers(payload("{\"ticklers\":5}"));

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
        verify(ticklerManager, never()).completeTickler(any(), any(), any());
    }

    @Test
    @Tag("update")
    @DisplayName("should return an error when a tickler id is not an integer")
    void shouldReturnError_whenTicklerIdIsNotInteger() throws Exception {
        RestResponse<String> response = service.completeTicklers(payload("{\"ticklers\":[\"abc\"]}"));

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
        verify(ticklerManager, never()).completeTickler(any(), any(), any());
    }
}
