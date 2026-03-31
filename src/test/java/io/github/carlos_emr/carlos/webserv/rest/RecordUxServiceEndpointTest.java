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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao;
import io.github.carlos_emr.carlos.commn.model.EncounterTemplate;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.PreferenceManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.EncounterTemplateResponse;

/**
 * HTTP-level endpoint tests for {@link RecordUxService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, and HTTP status codes
 * for patient record UX REST endpoints. All dependencies are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("RecordUxService REST endpoint tests")
class RecordUxServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private ConsultationManager mockConsultationManager;

    @Mock
    private EncounterTemplateDao mockEncounterTemplateDao;

    @Mock
    private PreferenceManager mockPreferenceManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected Object getServiceBean() {
        RecordUxService service = new RecordUxService();
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "consultationManager", mockConsultationManager);
        injectDependency(service, "encounterTemplateDao", mockEncounterTemplateDao);
        injectDependency(service, "preferenceManager", mockPreferenceManager);
        return service;
    }

    /** Tests for GET /recordUX/{demographicNo}/recordMenu endpoint. */
    @Nested
    @DisplayName("GET /recordUX/{demographicNo}/recordMenu")
    class GetRecordMenu {

        @Test
        @DisplayName("should return 200 with menu items when user has privileges")
        void shouldReturn200WithMenuItems_whenUserHasPrivileges() {
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull()))
                .thenReturn(true);
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_eChart"), eq("r"), isNull()))
                .thenReturn(true);
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_newCasemgmt.prescriptions"), eq("r"), isNull()))
                .thenReturn(true);
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_newCasemgmt.consultations"), eq("r"), isNull()))
                .thenReturn(false);
            // Return false for the rest to keep menu simple
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_newCasemgmt.forms"), eq("r"), isNull()))
                .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_newCasemgmt.eforms"), eq("r"), isNull()))
                .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_newCasemgmt.viewTickler"), eq("r"), isNull()))
                .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_newCasemgmt.DxRegistry"), eq("r"), isNull()))
                .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_newCasemgmt.oscarMsg"), eq("r"), isNull()))
                .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_newCasemgmt.documents"), eq("r"), isNull()))
                .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_newCasemgmt.decisionSupportAlerts"), eq("r"), isNull()))
                .thenReturn(false);

            Response response = request().path("/recordUX/123/recordMenu").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty menu when user has no privileges")
        void shouldReturn200WithEmptyMenu_whenUserHasNoPrivileges() {
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), anyString(), anyString(), isNull()))
                .thenReturn(false);

            Response response = request().path("/recordUX/456/recordMenu").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    /** Tests for POST /recordUX/searchTemplates endpoint. */
    @Nested
    @DisplayName("POST /recordUX/searchTemplates")
    class SearchTemplates {

        @Test
        @DisplayName("should return 200 with templates when matching templates found")
        void shouldReturn200WithTemplates_whenMatchingTemplatesFound() {
            EncounterTemplate template = new EncounterTemplate();
            template.setEncounterTemplateName("SOAP Note");
            template.setEncounterTemplateValue("S:\nO:\nA:\nP:");

            when(mockEncounterTemplateDao.findByName(eq("SOAP%"), isNull(), isNull()))
                .thenReturn(List.of(template));

            ObjectNode body = objectMapper.createObjectNode();
            body.put("name", "SOAP");

            Response response = request().path("/recordUX/searchTemplates").post(body);

            assertThat(response.getStatus()).isEqualTo(200);
            EncounterTemplateResponse result = response.readEntity(EncounterTemplateResponse.class);
            assertThat(result.getTemplates()).hasSize(1);
        }

        @Test
        @DisplayName("should return 200 with empty list when no templates match")
        void shouldReturn200WithEmptyList_whenNoTemplatesMatch() {
            when(mockEncounterTemplateDao.findByName(eq("nonexistent%"), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

            ObjectNode body = objectMapper.createObjectNode();
            body.put("name", "nonexistent");

            Response response = request().path("/recordUX/searchTemplates").post(body);

            assertThat(response.getStatus()).isEqualTo(200);
            EncounterTemplateResponse result = response.readEntity(EncounterTemplateResponse.class);
            assertThat(result.getTemplates()).isEmpty();
        }
    }
}
