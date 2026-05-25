/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionReportDao;
import io.github.carlos_emr.carlos.commn.model.PreventionReport;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.EFormReportToolManager;
import io.github.carlos_emr.carlos.prev.reports.Report;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.EFormReportToolTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PreventionSearchTo1;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReportingService unit tests")
@Tag("unit")
@Tag("fast")
class ReportingServiceUnitTest extends CarlosUnitTestBase {

    @Mock
    private EFormReportToolManager mockEFormReportToolManager;

    @Mock
    private EFormDao mockEFormDao;

    @Mock
    private PreventionReportDao mockPreventionReportDao;

    private ReportingService service;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<CarlosProperties> carlosPropertiesMock;

    /**
     * Report returned by the {@code buildPreventionReport} seam. Set per test to drive the
     * success path ({@code new Report()}) or the no-result path ({@code null}); this lets the
     * tests exercise runPreventionReport without loading {@code ReportBuilder}, whose static
     * SpringUtils dependencies cannot initialize in a unit context.
     */
    private Report stubReport;

    @BeforeEach
    void setUp() throws Exception {
        registerMock(EFormReportToolManager.class, mockEFormReportToolManager);
        registerMock(EFormDao.class, mockEFormDao);

        carlosPropertiesMock = org.mockito.Mockito.mockStatic(CarlosProperties.class);
        carlosPropertiesMock.when(CarlosProperties::getBuildDate).thenReturn("2026-01-01");
        carlosPropertiesMock.when(CarlosProperties::getBuildTag).thenReturn("test");

        Provider provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("101");

        loggedInInfo = new LoggedInInfo();
        Field providerField = LoggedInInfo.class.getDeclaredField("loggedInProvider");
        providerField.setAccessible(true);
        providerField.set(loggedInInfo, provider);
        loggedInInfo.setIp("127.0.0.1");

        LoggedInInfo capturedInfo = loggedInInfo;
        service = new ReportingService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }

            @Override
            protected Report buildPreventionReport(LoggedInInfo info, String providerNo, PreventionSearchTo1 search) {
                return stubReport;
            }
        };

        injectDependency(service, "eformReportToolManager", mockEFormReportToolManager);
        injectDependency(service, "preventionReportDao", mockPreventionReportDao);
    }

    @AfterEach
    void tearDown() {
        if (carlosPropertiesMock != null) {
            carlosPropertiesMock.close();
        }
    }

    @Nested
    @DisplayName("addEFormReportTool")
    class AddEFormReportTool {

        @Test
        @DisplayName("should return error response when manager rejects invalid report tool name")
        void shouldReturnErrorResponse_whenManagerRejectsInvalidReportToolName() {
            EFormReportToolTo1 request = new EFormReportToolTo1();
            request.setName("foo /*");
            request.setEformId(1);

            doThrow(new IllegalArgumentException(
                    "Invalid report tool name: only letters, digits, and underscores are allowed."))
                    .when(mockEFormReportToolManager)
                    .addNew(any(), any());

            RestResponse<String> response = service.addEFormReportTool(request);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage())
                    .isEqualTo("Invalid report tool name: only letters, digits, and underscores are allowed.");
        }

        @Test
        @DisplayName("should return success response when report tool name is valid")
        void shouldReturnSuccessResponse_whenReportToolNameIsValid() {
            EFormReportToolTo1 request = new EFormReportToolTo1();
            request.setName("report_tool_2026");
            request.setEformId(1);

            RestResponse<String> response = service.addEFormReportTool(request);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            verify(mockEFormReportToolManager).addNew(any(), any());
        }
    }

    @Nested
    @DisplayName("prevention report lookup")
    class PreventionReportLookup {

        private JsonNode emptyJson() {
            return new ObjectMapper().createObjectNode();
        }

        // Stubs use Integer (not an int literal) so they target AbstractDao.find(Object),
        // which is the overload the production code resolves to for an Integer id.
        @Test
        @DisplayName("should return 404 when getPreventionReport finds no report (no NPE)")
        void shouldReturnNotFound_whenGetPreventionReportMissing() {
            when(mockPreventionReportDao.find(Integer.valueOf(999))).thenReturn(null);

            Response response = service.getPreventionReport(999, emptyJson());

            assertThat(response.getStatus()).isEqualTo(404);
        }

        @Test
        @DisplayName("should return 404 when runPreventionReport finds no report (no NPE)")
        void shouldReturnNotFound_whenRunPreventionReportMissing() {
            when(mockPreventionReportDao.find(Integer.valueOf(999))).thenReturn(null);

            Response response = service.runPreventionReport(999, emptyJson());

            assertThat(response.getStatus()).isEqualTo(404);
        }

        @Test
        @DisplayName("should return 268 when getPreventionReport JSON is malformed")
        void shouldReturn268_whenReportJsonInvalid() {
            PreventionReport pr = mock(PreventionReport.class);
            when(pr.getJson()).thenReturn("{ not valid json");
            when(mockPreventionReportDao.find(Integer.valueOf(7))).thenReturn(pr);

            Response response = service.getPreventionReport(7, emptyJson());

            assertThat(response.getStatus()).isEqualTo(268);
        }

        @Test
        @DisplayName("should return 268 when getPreventionReport JSON payload is null")
        void shouldReturn268_whenReportJsonNull() {
            PreventionReport pr = mock(PreventionReport.class);
            when(pr.getJson()).thenReturn(null);
            when(mockPreventionReportDao.find(Integer.valueOf(8))).thenReturn(pr);

            Response response = service.getPreventionReport(8, emptyJson());

            assertThat(response.getStatus()).isEqualTo(268);
        }

        @Test
        @DisplayName("should return 200 when getPreventionReport JSON is valid")
        void shouldReturnOk_whenReportJsonValid() {
            PreventionReport pr = mock(PreventionReport.class);
            when(pr.getJson()).thenReturn("{}");
            when(mockPreventionReportDao.find(Integer.valueOf(5))).thenReturn(pr);

            Response response = service.getPreventionReport(5, emptyJson());

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 268 when runPreventionReport JSON is malformed")
        void shouldReturn268_whenRunReportJsonInvalid() {
            PreventionReport pr = mock(PreventionReport.class);
            when(pr.getJson()).thenReturn("{ not valid json");
            when(mockPreventionReportDao.find(Integer.valueOf(6))).thenReturn(pr);

            Response response = service.runPreventionReport(6, emptyJson());

            assertThat(response.getStatus()).isEqualTo(268);
        }

        @Test
        @DisplayName("should return 200 when runPreventionReport builds a report")
        void shouldReturnOk_whenRunReportSucceeds() {
            PreventionReport pr = mock(PreventionReport.class);
            when(pr.getJson()).thenReturn("{}");
            when(pr.isActive()).thenReturn(true);
            when(mockPreventionReportDao.find(Integer.valueOf(10))).thenReturn(pr);
            stubReport = new Report();

            Response response = service.runPreventionReport(10, emptyJson());

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 268 when runPreventionReport builds no result")
        void shouldReturn268_whenRunReportBuildsNoResult() {
            PreventionReport pr = mock(PreventionReport.class);
            when(pr.getJson()).thenReturn("{}");
            // isActive() stubbed true so the null build result is not dereferenced by the
            // inactive-report branch; the assertion isolates "no result built -> 268".
            when(pr.isActive()).thenReturn(true);
            when(mockPreventionReportDao.find(Integer.valueOf(11))).thenReturn(pr);
            stubReport = null;

            Response response = service.runPreventionReport(11, emptyJson());

            assertThat(response.getStatus()).isEqualTo(268);
        }
    }
}
