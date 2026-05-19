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
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.managers.LabManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.LabResponse;

/**
 * HTTP-level endpoint tests for {@link LabService} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-RS pipeline: path routing,
 * JSON serialization via Jackson, query parameter binding, and HTTP
 * status codes. Dependencies are mocked — no database required.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("LabService REST endpoint tests")
class LabServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private LabManager mockLabManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Override
    protected Object getServiceBean() {
        LabService service = new LabService();
        injectDependency(service, "labManager", mockLabManager);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        return service;
    }

    @BeforeEach
    void setUpSecurity() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), any(), any(), any()))
            .thenReturn(true);
    }

    private Hl7TextMessage createTestHl7Message(int id) {
        Hl7TextMessage msg = new Hl7TextMessage();
        msg.setType("HL7");
        msg.setServiceName("TestLab");
        return msg;
    }

    /** Tests for GET /labs/hl7LabsByDemographicNo endpoint. */
    @Nested
    @DisplayName("GET /labs/hl7LabsByDemographicNo")
    class GetHl7LabsByDemographicNo {

        @Test
        @DisplayName("should return 200 with lab messages as JSON")
        void shouldReturn200WithLabMessages_whenLabsExist() {
            Hl7TextMessage testMsg = createTestHl7Message(1);
            when(mockLabManager.getHl7Messages(any(LoggedInInfo.class), eq(123), eq(0), eq(10)))
                .thenReturn(List.of(testMsg));

            Response response = request().path("/labs/hl7LabsByDemographicNo")
                .query("demographicNo", 123)
                .query("offset", 0)
                .query("limit", 10)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            LabResponse body = response.readEntity(LabResponse.class);
            assertThat(body.getMessages()).isNotNull();
        }

        @Test
        @DisplayName("should return 200 with empty list when no labs exist")
        void shouldReturn200WithEmptyList_whenNoLabsExist() {
            when(mockLabManager.getHl7Messages(any(LoggedInInfo.class), eq(456), eq(0), eq(10)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/labs/hl7LabsByDemographicNo")
                .query("demographicNo", 456)
                .query("offset", 0)
                .query("limit", 10)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            LabResponse body = response.readEntity(LabResponse.class);
            assertThat(body.getMessages()).isEmpty();
        }

        @Test
        @DisplayName("should return multiple lab messages in JSON response")
        void shouldReturnMultipleMessages_whenMultipleLabsExist() {
            List<Hl7TextMessage> messages = List.of(
                createTestHl7Message(1),
                createTestHl7Message(2),
                createTestHl7Message(3)
            );
            when(mockLabManager.getHl7Messages(any(LoggedInInfo.class), eq(789), eq(0), eq(20)))
                .thenReturn(messages);

            Response response = request().path("/labs/hl7LabsByDemographicNo")
                .query("demographicNo", 789)
                .query("offset", 0)
                .query("limit", 20)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            LabResponse body = response.readEntity(LabResponse.class);
            assertThat(body.getMessages()).hasSize(3);
        }
    }
}
