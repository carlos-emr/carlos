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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import io.github.carlos_emr.carlos.managers.DemographicSetsManager;
import io.github.carlos_emr.carlos.managers.EFormReportToolManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
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

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private DemographicSetsManager mockDemographicSetsManager;

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
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "demographicSetsManager", mockDemographicSetsManager);

        // Default to granted so the existing behavioural tests exercise the business logic;
        // the authorization tests below re-stub specific (secobj, privilege) pairs as denied.
        when(mockSecurityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
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
        @DisplayName("should return 404 when deactivateReport finds no report (no NPE)")
        void shouldReturnNotFound_whenDeactivateReportMissing() {
            when(mockPreventionReportDao.find(Integer.valueOf(999))).thenReturn(null);

            Response response = service.getPreventionReport(999);

            assertThat(response.getStatus()).isEqualTo(404);
            verify(mockPreventionReportDao, never()).merge(any());
        }

        @Test
        @DisplayName("should return 200 and merge inactive report when deactivateReport succeeds")
        void shouldDeactivateReport_whenReportExists() {
            PreventionReport pr = mock(PreventionReport.class);
            when(mockPreventionReportDao.find(Integer.valueOf(12))).thenReturn(pr);

            Response response = service.getPreventionReport(12);

            assertThat(response.getStatus()).isEqualTo(200);
            verify(pr).setActive(false);
            verify(mockPreventionReportDao).merge(pr);
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

    /**
     * Authorization guards added for issue #2798. Each of the service's three sub-resources is
     * gated by a distinct security object: demographic sets by {@code _report}, the eForm report
     * tool by {@code _admin.eformreporttool}, and prevention reports by {@code _prevention}. These
     * tests verify the correct object/privilege is requested and that a denial short-circuits the
     * endpoint before any business logic runs.
     */
    @Nested
    @DisplayName("authorization guards")
    class AuthorizationGuards {

        private JsonNode emptyJson() {
            return new ObjectMapper().createObjectNode();
        }

        // --- demographicSets sub-resource: gated by _report read ---

        @Test
        @DisplayName("should throw SecurityException when demographicSets list lacks _report read")
        void shouldThrowSecurityException_whenDemographicSetsListLacksReportRead() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_report"), eq("r"), any())).thenReturn(false);

            assertThatThrownBy(() -> service.listDemographicSets())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_report)");
            verify(mockDemographicSetsManager, never()).getNames(any());
        }

        @Test
        @DisplayName("should throw SecurityException when demographicSet lookup lacks _report read")
        void shouldThrowSecurityException_whenDemographicSetByNameLacksReportRead() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_report"), eq("r"), any())).thenReturn(false);

            assertThatThrownBy(() -> service.getDemographicSetByName("set1"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_report)");
            verify(mockDemographicSetsManager, never()).getByName(any(), any());
        }

        @Test
        @DisplayName("should throw SecurityException when patientList lacks _report read")
        void shouldThrowSecurityException_whenPatientListLacksReportRead() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_report"), eq("r"), any())).thenReturn(false);

            assertThatThrownBy(() -> service.getAsPatientList(emptyJson()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_report)");
            verify(mockDemographicSetsManager, never()).getByName(any(), any());
        }

        // --- eformReportTool sub-resource: gated by _admin.eformreporttool (read on list, write on mutators) ---

        @Test
        @DisplayName("should throw SecurityException when eformReportTool list lacks _admin.eformreporttool read")
        void shouldThrowSecurityException_whenEformReportToolListLacksAdminRead() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.eformreporttool"), eq("r"), any()))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.eformReportToolList())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_admin.eformreporttool)");
            verify(mockEFormReportToolManager, never()).findAll(any(), any(), any());
        }

        @Test
        @DisplayName("should deny eformReportTool add when _admin.eformreporttool write is missing")
        void shouldDenyEformReportToolAdd_whenAdminWriteMissing() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.eformreporttool"), eq("w"), any()))
                    .thenReturn(false);

            EFormReportToolTo1 request = new EFormReportToolTo1();
            request.setName("report_tool_2026");
            request.setEformId(1);

            RestResponse<String> response = service.addEFormReportTool(request);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Access Denied");
            verify(mockEFormReportToolManager, never()).addNew(any(), any());
        }

        @Test
        @DisplayName("should deny eformReportTool populate when _admin.eformreporttool write is missing")
        void shouldDenyEformReportToolPopulate_whenAdminWriteMissing() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.eformreporttool"), eq("w"), any()))
                    .thenReturn(false);

            RestResponse<String> response = service.populateEFormReportTool(new EFormReportToolTo1());

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Access Denied");
            verify(mockEFormReportToolManager, never()).populateReportTable(any(), any());
        }

        @Test
        @DisplayName("should deny eformReportTool remove when _admin.eformreporttool write is missing")
        void shouldDenyEformReportToolRemove_whenAdminWriteMissing() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.eformreporttool"), eq("w"), any()))
                    .thenReturn(false);

            RestResponse<String> response = service.removeEFormReportTool(new EFormReportToolTo1());

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Access Denied");
            verify(mockEFormReportToolManager, never()).remove(any(), any());
        }

        @Test
        @DisplayName("should deny eformReportTool markLatest when _admin.eformreporttool write is missing")
        void shouldDenyEformReportToolMarkLatest_whenAdminWriteMissing() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.eformreporttool"), eq("w"), any()))
                    .thenReturn(false);

            RestResponse<String> response = service.markLatestEFormReportTool(new EFormReportToolTo1());

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Access Denied");
            verify(mockEFormReportToolManager, never()).markLatest(any(), any());
        }

        // --- preventionReport sub-resource: gated by _prevention (read on get/run, write on saveNew/deactivate) ---

        @Test
        @DisplayName("should deny prevention saveNew when _prevention write is missing")
        void shouldDenyPreventionSaveNew_whenPreventionWriteMissing() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_prevention"), eq("w"), any())).thenReturn(false);

            RestResponse<String> response = service.saveNewPreventionReport(new PreventionSearchTo1());

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Access Denied");
            verify(mockPreventionReportDao, never()).persist(any());
        }

        @Test
        @DisplayName("should throw SecurityException when prevention getList lacks _prevention read")
        void shouldThrowSecurityException_whenPreventionGetListLacksPreventionRead() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_prevention"), eq("r"), any())).thenReturn(false);

            assertThatThrownBy(() -> service.getPreventionReports())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_prevention)");
            verify(mockPreventionReportDao, never()).getPreventionReports();
        }

        @Test
        @DisplayName("should return 403 when runPreventionReport lacks _prevention read")
        void shouldReturnForbidden_whenRunPreventionReportLacksPreventionRead() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_prevention"), eq("r"), any())).thenReturn(false);

            Response response = service.runPreventionReport(1, emptyJson());

            assertThat(response.getStatus()).isEqualTo(403);
            verify(mockPreventionReportDao, never()).find(any());
        }

        @Test
        @DisplayName("should return 403 when getReport (read) lacks _prevention read")
        void shouldReturnForbidden_whenGetReportLacksPreventionRead() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_prevention"), eq("r"), any())).thenReturn(false);

            Response response = service.getPreventionReport(1, emptyJson());

            assertThat(response.getStatus()).isEqualTo(403);
            verify(mockPreventionReportDao, never()).find(any());
        }

        @Test
        @DisplayName("should return 403 when deactivateReport lacks _prevention write")
        void shouldReturnForbidden_whenDeactivateReportLacksPreventionWrite() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_prevention"), eq("w"), any())).thenReturn(false);

            // The single-arg getPreventionReport overload is the deactivate/merge path
            // (@Path("/preventionReport/dectivateReport/{id}")); the two-arg overload is the read path.
            Response response = service.getPreventionReport(1);

            assertThat(response.getStatus()).isEqualTo(403);
            verify(mockPreventionReportDao, never()).merge(any());
            verify(mockPreventionReportDao, never()).find(any());
        }
    }
}
