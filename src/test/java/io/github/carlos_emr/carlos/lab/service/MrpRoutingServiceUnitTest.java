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
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.hospitalReportManager.service.HrmProviderRoutingService;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@Tag("lab")
@DisplayName("MrpRoutingService")
class MrpRoutingServiceUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 42;
    private static final String MRP = "101";

    private ProviderLinkingRulesService rules;
    private DemographicDao demographicDao;
    private ProviderDao providerDao;
    private HrmProviderRoutingService hrmRouting;
    private MockedStatic<CommonLabResultData> labResults;
    private MrpRoutingService service;

    @BeforeEach
    void setUp() {
        // CommonLabResultData resolves these in its static initializer.
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(QueueDocumentLinkDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        labResults = mockStatic(CommonLabResultData.class);

        rules = mock(ProviderLinkingRulesService.class);
        demographicDao = mock(DemographicDao.class);
        providerDao = mock(ProviderDao.class);
        hrmRouting = mock(HrmProviderRoutingService.class);
        service = new MrpRoutingService(rules, demographicDao, providerDao, hrmRouting);
    }

    @AfterEach
    void tearDown() {
        labResults.close();
    }

    private void givenPatientWithMrp(String providerNo) {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(PATIENT);
        demographic.setProviderNo(providerNo);
        when(demographicDao.getDemographicById(PATIENT)).thenReturn(demographic);
    }

    private void givenProvider(String providerNo, String status) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        provider.setStatus(status);
        when(providerDao.getProvider(providerNo)).thenReturn(provider);
    }

    @Nested
    @DisplayName("HL7 upload decision")
    class UploadDecision {

        @Test
        @DisplayName("should not route to the MRP when the rules are off")
        void shouldNotRoute_whenRulesAreOff() {
            givenProvider(MRP, "1");
            assertThat(service.shouldRouteUploadToMrp(MRP)).isFalse();
            verifyNoInteractions(providerDao);
        }

        @Test
        @DisplayName("should route to an active MRP when the rules are on")
        void shouldRoute_whenRulesAreOnAndMrpIsActive() {
            when(rules.isEnabled()).thenReturn(true);
            givenProvider(MRP, "1");
            assertThat(service.shouldRouteUploadToMrp(" 101 ")).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"0", "-1", "  "})
        @DisplayName("should skip a patient with no MRP")
        void shouldNotRoute_whenThereIsNoMrp(String mrp) {
            when(rules.isEnabled()).thenReturn(true);
            assertThat(service.shouldRouteUploadToMrp(mrp)).isFalse();
            verifyNoInteractions(providerDao);
        }

        @Test
        @DisplayName("should skip an inactive or unknown MRP")
        void shouldNotRoute_whenMrpIsInactiveOrUnknown() {
            when(rules.isEnabled()).thenReturn(true);
            givenProvider(MRP, "0");
            assertThat(service.shouldRouteUploadToMrp(MRP)).isFalse();
            assertThat(service.shouldRouteUploadToMrp("999")).isFalse();
        }

        @Test
        @DisplayName("should audit an upload routing without report content")
        void shouldAuditUploadRouting_withIdentifiersOnly() {
            service.recordUploadRouting("555", " 101 ", "999998");
            logActionMock.verify(() -> LogAction.addLog(eq("999998"), eq(MrpRoutingService.AUDIT_ACTION),
                    eq(MrpRoutingService.AUDIT_CONTENT), eq("HL7:555"), isNull(), isNull(), eq("mrp=101")));
        }
    }

    @Nested
    @DisplayName("manual lab match")
    class LabMatch {

        @Test
        @DisplayName("should do nothing when the rules are off")
        void shouldDoNothing_whenRulesAreOff() {
            assertThat(service.routeMatchedLabToMrp("555", "HL7", PATIENT, "999998")).isFalse();
            labResults.verifyNoInteractions();
            verifyNoInteractions(demographicDao);
            logActionMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should route every version of the lab to the MRP and audit it")
        void shouldRouteLabToMrp_whenRulesAreOn() {
            when(rules.isEnabled()).thenReturn(true);
            givenPatientWithMrp(MRP);
            givenProvider(MRP, "1");
            labResults.when(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), eq(MRP)))
                    .thenReturn(true);

            assertThat(service.routeMatchedLabToMrp("555", "HL7", PATIENT, "999998")).isTrue();

            labResults.verify(() -> CommonLabResultData.updateLabRouting(
                    org.mockito.ArgumentMatchers.<ArrayList<String[]>>argThat(labs -> labs.size() == 1
                            && "555".equals(labs.get(0)[0]) && "HL7".equals(labs.get(0)[1])),
                    eq(MRP)));
            logActionMock.verify(() -> LogAction.addLog(eq("999998"), eq(MrpRoutingService.AUDIT_ACTION),
                    eq(MrpRoutingService.AUDIT_CONTENT), eq("HL7:555"), isNull(), eq("42"), eq("mrp=101")));
        }

        @Test
        @DisplayName("should not audit a routing that failed")
        void shouldReportFailure_whenRoutingFails() {
            when(rules.isEnabled()).thenReturn(true);
            givenPatientWithMrp(MRP);
            givenProvider(MRP, "1");
            labResults.when(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), anyString()))
                    .thenReturn(false);

            assertThat(service.routeMatchedLabToMrp("555", "HL7", PATIENT, "999998")).isFalse();
            logActionMock.verifyNoInteractions();
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1"})
        @NullAndEmptySource
        @DisplayName("should skip a patient whose MRP is missing")
        void shouldSkip_whenPatientHasNoMrp(String mrp) {
            when(rules.isEnabled()).thenReturn(true);
            givenPatientWithMrp(mrp);

            assertThat(service.routeMatchedLabToMrp("555", "HL7", PATIENT, "999998")).isFalse();
            labResults.verifyNoInteractions();
        }

        @Test
        @DisplayName("should skip a patient whose MRP is no longer active")
        void shouldSkip_whenMrpIsInactive() {
            when(rules.isEnabled()).thenReturn(true);
            givenPatientWithMrp(MRP);
            givenProvider(MRP, "0");

            assertThat(service.routeMatchedLabToMrp("555", "HL7", PATIENT, "999998")).isFalse();
            labResults.verifyNoInteractions();
        }

        @Test
        @DisplayName("should skip a patient that does not exist")
        void shouldSkip_whenPatientIsUnknown() {
            when(rules.isEnabled()).thenReturn(true);
            assertThat(service.routeMatchedLabToMrp("555", "HL7", PATIENT, "999998")).isFalse();
            assertThat(service.routeMatchedLabToMrp("555", "HL7", null, "999998")).isFalse();
            labResults.verifyNoInteractions();
        }

        @ParameterizedTest
        @ValueSource(strings = {"DOC", "HRM"})
        @DisplayName("should leave eDocuments and HRM reports to their own paths")
        void shouldIgnore_forNonLabTypes(String labType) {
            when(rules.isEnabled()).thenReturn(true);
            givenPatientWithMrp(MRP);
            givenProvider(MRP, "1");

            assertThat(service.routeMatchedLabToMrp("555", labType, PATIENT, "999998")).isFalse();
            labResults.verifyNoInteractions();
        }
    }

    @Nested
    @DisplayName("manual HRM match")
    class HrmMatch {

        @Test
        @DisplayName("should do nothing when the rules are off")
        void shouldDoNothing_whenRulesAreOff() {
            assertThat(service.routeMatchedHrmToMrp(7, PATIENT, "999998")).isFalse();
            verifyNoInteractions(hrmRouting, demographicDao);
        }

        @Test
        @DisplayName("should assign the report to the MRP through the HRM routing service")
        void shouldAssignReportToMrp_whenRulesAreOn() {
            when(rules.isEnabled()).thenReturn(true);
            givenPatientWithMrp(MRP);
            givenProvider(MRP, "1");

            assertThat(service.routeMatchedHrmToMrp(7, PATIENT, "999998")).isTrue();

            verify(hrmRouting).assignProvider(7, MRP);
            logActionMock.verify(() -> LogAction.addLog(eq("999998"), eq(MrpRoutingService.AUDIT_ACTION),
                    eq(MrpRoutingService.AUDIT_CONTENT), eq("HRM:7"), isNull(), eq("42"), eq("mrp=101")));
        }

        @Test
        @DisplayName("should not route an HRM report to an MRP of 0")
        void shouldSkip_whenMrpIsZero() {
            when(rules.isEnabled()).thenReturn(true);
            givenPatientWithMrp("0");

            assertThat(service.routeMatchedHrmToMrp(7, PATIENT, "999998")).isFalse();
            verify(hrmRouting, never()).assignProvider(anyInt(), anyString());
        }
    }
}
