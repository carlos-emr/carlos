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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrint;
import io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao;
import io.github.carlos_emr.carlos.commn.model.EncounterTemplate;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.PreferenceManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.EncounterTemplateResponse;

/**
 * CXF local-transport endpoint tests for {@link RecordUxService} using CXF local transport.
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

    /** Grants patient-scoped chart read, the right the PDF print requires (#2798). */
    private void grantChartRead() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), anyInt())).thenReturn(true);
    }

    @Test
    @DisplayName("print returns 403 and never builds the PDF without chart read")
    void shouldReturn403_whenChartReadDeniedForPrint() {
        try (var printers = mockConstruction(CaseManagementPrint.class)) {
            Response response = request().path("/recordUX/123/print").replaceHeader("Accept", "application/pdf")
                    .query("printOps", "{\"printType\":\"all\",\"cpp\":true,\"selectedList\":[]}").get();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(printers.constructed()).isEmpty();
        }
    }

    @Test
    @DisplayName("print checks chart read for the requested patient, not any patient")
    void shouldReturn403_whenChartReadGrantedOnlyForAnotherPatient() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), eq(456))).thenReturn(true);
        try (var printers = mockConstruction(CaseManagementPrint.class)) {
            Response response = request().path("/recordUX/123/print").replaceHeader("Accept", "application/pdf")
                    .query("printOps", "{\"printType\":\"selected\",\"selectedList\":[\"42\"]}").get();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(printers.constructed()).isEmpty();
        }
    }

    @Test
    @DisplayName("selected notes ignore an incomplete optional dates object")
    void shouldPrintSelectedNotes_withIncompleteOptionalDates() throws Exception {
        grantChartRead();
        try (var printers = mockConstruction(
                CaseManagementPrint.class)) {
            Response response = request().path("/recordUX/123/print").replaceHeader("Accept", "application/pdf")
                    .query("printOps", "{\"printType\":\"selected\",\"selectedList\":[\"42\"],\"dates\":{\"start\":\"2026-10-03T00:00:00Z\"}}")
                    .get();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(printers.constructed()).hasSize(1);
            verify(printers.constructed().getFirst()).doPrint(
                    any(), eq(123), eq(false), eq(new String[]{"42"}),
                    eq(false), eq(false), eq(false), eq(false), eq(false), eq(false),
                    isNull(), isNull(), any(), any());
        }
    }

    @Test
    @DisplayName("date mode passes both bounds and all-note selection to the PDF service")
    void shouldPrintDateRange_withCompleteBounds() throws Exception {
        grantChartRead();
        try (var printers = mockConstruction(CaseManagementPrint.class)) {
            Response response = request().path("/recordUX/123/print").replaceHeader("Accept", "application/pdf")
                    .query("printOps", "{\"printType\":\"dates\",\"selectedList\":[],\"dates\":{"
                            + "\"start\":\"2026-10-03T00:00:00Z\",\"end\":\"2026-10-03T00:00:00Z\"}}")
                    .get();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(printers.constructed()).hasSize(1);
            verify(printers.constructed().getFirst()).doPrint(any(), eq(123), eq(true), eq(new String[0]),
                    eq(false), eq(false), eq(false), eq(false), eq(false), eq(true),
                    any(java.util.Calendar.class), any(java.util.Calendar.class), any(), any());
        }
    }

    @Test
    @DisplayName("date printing rejects incomplete, invalid and reversed bounds before streaming")
    void shouldRejectDateRange_withInvalidBounds() {
        grantChartRead();
        String[] dates = {"{}", "{\"start\":\"2026-10-03T00:00:00Z\"}",
                "{\"start\":null,\"end\":\"2026-10-03T00:00:00Z\"}",
                "{\"start\":\"invalid\",\"end\":\"2026-10-03T00:00:00Z\"}",
                "{\"start\":\"2026-10-04T00:00:00Z\",\"end\":\"2026-10-03T00:00:00Z\"}"};
        try (var printers = mockConstruction(
                CaseManagementPrint.class)) {
            for (String bounds : dates) {
                Response response = request().path("/recordUX/123/print").replaceHeader("Accept", "application/pdf")
                        .query("printOps", "{\"printType\":\"dates\",\"selectedList\":[],\"dates\":" + bounds + "}")
                        .get();
                assertThat(response.getStatus()).isEqualTo(400);
            }
            assertThat(printers.constructed()).isEmpty();
        }
    }

    @Test
    @DisplayName("PDF generation errors produce HTTP failure instead of empty successful downloads")
    void shouldReturnServerError_whenChartPrintingFails() {
        grantChartRead();
        try (var _ = mockConstruction(
                CaseManagementPrint.class,
                (printer, context) -> doThrow(new IOException("synthetic failure"))
                        .when(printer).doPrint(any(), any(), anyBoolean(), any(),
                                anyBoolean(), anyBoolean(),
                                anyBoolean(), anyBoolean(),
                                anyBoolean(), anyBoolean(),
                                any(), any(), any(), any()))) {
            Response response = request().path("/recordUX/123/print").replaceHeader("Accept", "application/pdf")
                    .query("printOps", "{\"printType\":\"selected\",\"selectedList\":[\"42\"]}").get();
            assertThat(response.getStatus()).isEqualTo(500);
        }
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
            var wireJson = responseJson(response);
            assertThat(wireJson.isArray()).isTrue();
            assertThat(wireJson).hasSize(4);
            assertThat(wireJson.at("/0/id").intValue()).isEqualTo(0);
            assertThat(wireJson.at("/0/label").textValue()).isEqualTo("Details");
            assertThat(wireJson.at("/1/id").intValue()).isEqualTo(0);
            assertThat(wireJson.at("/1/label").textValue()).isEqualTo("Summary");
        }

        @Test
        @DisplayName("should return 200 with empty menu when user has no privileges")
        void shouldReturn200WithEmptyMenu_whenUserHasNoPrivileges() {
            when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), anyString(), anyString(), isNull()))
                .thenReturn(false);

            Response response = request().path("/recordUX/456/recordMenu").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.isArray()).isTrue();
            assertThat(wireJson).hasSize(0);
        }
    }

    /** Tests for POST /recordUX/searchTemplates endpoint. */
    @Nested
    @DisplayName("POST /recordUX/searchTemplates")
    class SearchTemplates {

        @BeforeEach
        void grantTemplateRead() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_newCasemgmt.templates"), eq("r"), isNull(String.class)))
                .thenReturn(true);
        }

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

        @Test
        @DisplayName("should return 403 without searching when template read is denied")
        void shouldReturn403_whenTemplateReadDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_newCasemgmt.templates"), eq("r"), isNull(String.class)))
                .thenReturn(false);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("name", "SOAP");

            Response search = request().path("/recordUX/searchTemplates").post(body);
            Response single = request().path("/recordUX/template").post(body);

            assertThat(search.getStatus()).isEqualTo(403);
            assertThat(single.getStatus()).isEqualTo(403);
            verifyNoInteractions(mockEncounterTemplateDao);
        }
    }
}
