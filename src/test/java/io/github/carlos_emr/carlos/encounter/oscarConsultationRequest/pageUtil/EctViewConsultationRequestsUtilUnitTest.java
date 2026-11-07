/**
 * Modifications by CARLOS Contributors, 2026.
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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExt;
import io.github.carlos_emr.carlos.consultation.dto.*;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.provider.dto.ProviderSummaryDTO;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Consultation row formatting, access checks and bounded lookup regression tests. */
@Tag("unit")
@Tag("consultation")
@DisplayName("Scalar consultation list rendering")
class EctViewConsultationRequestsUtilUnitTest extends CarlosUnitTestBase {
    @Test
    @DisplayName("should exclude missing and malformed patient identifiers from bulk ticklers")
    void shouldExcludeInvalidPatients_whenBuildingBulkTicklers() {
        assertThat(EctViewConsultationRequestsUtil.isTicklerDemographic(null)).isFalse();
        for (String value : List.of("", " ", "0", "-1", "unknown", "1&demo=2")) {
            assertThat(EctViewConsultationRequestsUtil.isTicklerDemographic(value)).as(value).isFalse();
        }
        assertThat(EctViewConsultationRequestsUtil.isTicklerDemographic("42")).isTrue();
    }

    @Test
    @DisplayName("should send the patient and suggested message under the names the Add Tickler form reads")
    void shouldBuildOverdueTicklerPath_withParametersTheFormReads() {
        String path = EctViewConsultationRequestsUtil.overdueTicklerPath("/carlos", "42");

        assertThat(path).startsWith("/carlos/tickler/ViewAddTickler?");
        Map<String, String> parameters = queryParameters(path);
        // #4999: the list used to send "demo" and "message", which ticklerAdd.jsp never reads, so
        // the form opened with no patient and an empty message. These two names are the form's own.
        assertThat(parameters)
                .containsOnlyKeys("demographic_no", "ticklerMessage")
                .containsEntry("demographic_no", "42")
                .containsEntry("ticklerMessage", EctViewConsultationRequestsUtil.OVERDUE_TICKLER_MESSAGE);
    }

    @Test
    @DisplayName("should keep the suggested message intact through URL encoding")
    void shouldRoundTripSuggestedMessage_throughUrlEncoding() {
        String path = EctViewConsultationRequestsUtil.overdueTicklerPath("", "7");

        // Quotes, spaces and the ampersand-free sentence must decode to exactly what was suggested.
        assertThat(queryParameters(path)).containsEntry("ticklerMessage",
                "Patient has Consultation Letter with a status of 'Nothing Done' for over one week");
        assertThat(path).doesNotContain(" ").doesNotContain("'");
    }

    @Test
    @DisplayName("should refuse to build a tickler path for a patient that cannot receive a tickler")
    void shouldRejectOverdueTicklerPath_forInvalidPatient() {
        for (String value : new String[] {null, "", "0", "-3", "abc", "1&demographic_no=2"}) {
            assertThatThrownBy(() -> EctViewConsultationRequestsUtil.overdueTicklerPath("/carlos", value))
                    .as(String.valueOf(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static Map<String, String> queryParameters(String path) {
        Map<String, String> parameters = new LinkedHashMap<>();
        for (String pair : path.substring(path.indexOf('?') + 1).split("&")) {
            int equals = pair.indexOf('=');
            parameters.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
        }
        return parameters;
    }


    private ConsultationRequestDao requests;
    private ConsultationRequestExtDao extensions;
    private DemographicManager demographics;
    private ProviderDao providers;
    private LoggedInInfo info;
    private EctViewConsultationRequestsUtil util;

    @BeforeEach
    void setUp() {
        requests = mock(ConsultationRequestDao.class);
        extensions = mock(ConsultationRequestExtDao.class);
        demographics = mock(DemographicManager.class);
        providers = mock(ProviderDao.class);
        info = mock(LoggedInInfo.class);
        registerMock(ConsultationRequestDao.class, requests);
        registerMock(ConsultationRequestExtDao.class, extensions);
        registerMock(DemographicManager.class, demographics);
        registerMock(ProviderDao.class, providers);
        util = new EctViewConsultationRequestsUtil();
    }

    private ConsultationListFilterDto filter() {
        return new ConsultationListFilterDto("team", true, null, null, "6", "1", "0", 0, 50, 8, "101");
    }

    private ConsultationListRow row(Integer id, Integer demographic, Integer service, String mrp, Integer specialist) {
        return new ConsultationListRow(id, "1", demographic, "alias", "201", service, service, null, specialist, " Smith ", null,
                mrp, "Lovelace", "Ada", null, "site", "team", null,
                java.sql.Date.valueOf("2097-03-14"), null, null, null);
    }

    @Test
    void shouldBatchLookups_forManyRowsWithRepeatedPatientAndProvider() {
        when(requests.getConsultationListRows(filter())).thenReturn(List.of(row(1, 42, 0, "101", null), row(2, 42, 0, "101", 8)));
        when(demographics.getDemographicFormattedNames(info, Set.of(42))).thenReturn(Map.of(42, "Doe, Jane"));
        ProviderSummaryDTO ordering = new ProviderSummaryDTO("201", "Order", "Pat", null, null, null);
        when(providers.getProviderSummariesByIds(Set.of("201"))).thenReturn(Map.of("201", ordering));
        ConsultationRequestExt service = new ConsultationRequestExt();
        service.setRequestId(1); service.setKey("ereferral_service"); service.setValue("Cardiology");
        ConsultationRequestExt specialist = new ConsultationRequestExt();
        specialist.setRequestId(1); specialist.setKey("ereferral_doctor"); specialist.setValue("Referral doctor");
        ConsultationRequestExt ref = new ConsultationRequestExt();
        ref.setRequestId(1); ref.setKey("ereferral_ref"); ref.setValue(null);
        when(extensions.getConsultationRequestExts(List.of(1, 2))).thenReturn(List.of(service, specialist, ref));

        assertThat(util.estConsultationVecByTeam(info, filter())).isTrue();
        assertThat(util.ids).containsExactly("1", "2");
        assertThat(util.patient).containsExactly("Doe, Jane (alias)", "Doe, Jane (alias)");
        assertThat(util.provider).containsExactly("Lovelace, Ada", "Lovelace, Ada");
        assertThat(util.providerNo).containsExactly("101", "101");
        assertThat(util.service).containsExactly("Cardiology", "");
        assertThat(util.vSpecialist).containsExactly("Referral doctor", "Smith");
        assertThat(util.eReferral).containsExactly(true, false);
        assertThat(util.apptDate).containsExactly("2097-03-14", "2097-03-14");
        assertThat(util.date).containsExactly("", "");
        assertThat(util.urgency).containsExactly("", "");
        assertThat(util.followUpDate).containsExactly("N/A", "N/A");
        assertThat(util.patientWillBook).containsExactly("false", "false");
        assertThat(util.consultProvider).containsExactly(ordering, ordering);
        verify(requests).getConsultationListRows(filter());
        verify(demographics).getDemographicFormattedNames(info, Set.of(42));
        verify(providers).getProviderSummariesByIds(Set.of("201"));
        verify(extensions).getConsultationRequestExts(List.of(1, 2));
        verifyNoMoreInteractions(requests, demographics, providers, extensions);
    }

    @Test
    void shouldKeepOrphanedRows_whenRelatedRecordsAreMissing() {
        when(requests.getConsultationListRows(filter())).thenReturn(List.of(row(7, null, null, null, null), row(8, 42, null, null, null)));
        assertThat(util.estConsultationVecByTeam(info, filter())).isTrue();
        assertThat(util.ids).containsExactly("7", "8");
        assertThat(util.demographicNo).containsExactly("", "42");
        assertThat(util.patient).containsExactly("", "");
        assertThat(util.provider).containsExactly("N/A", "N/A");
        assertThat(util.providerNo).containsExactly("-1", "-1");
        assertThat(util.service).containsExactly("", "");
        assertThat(util.vSpecialist).containsExactly("N/A", "N/A");
    }

    @Test
    void shouldKeepPatientChartFallbacks_whenServiceAndMrpAreMissing() {
        when(requests.getConsultationListRows(42)).thenReturn(List.of(row(7, 42, null, null, null)));
        assertThat(util.estConsultationVecByDemographic(info, "42")).isTrue();
        assertThat(util.ids).containsExactly("7");
        assertThat(util.service).containsExactly("unknown");
        assertThat(util.provider).containsExactly("N/A");
        assertThat(util.vSpecialist).containsExactly("N/A");
    }

    @Test
    void shouldPublishNoRows_whenPatientReadIsDenied() {
        when(requests.getConsultationListRows(filter())).thenReturn(List.of(row(7, 42, null, null, null)));
        when(demographics.getDemographicFormattedNames(info, Set.of(42))).thenThrow(new SecurityException("denied"));
        assertThat(util.estConsultationVecByTeam(info, filter())).isFalse();
        assertThat(util.ids).isEmpty();
        assertThat(util.patient).isEmpty();
        verifyNoInteractions(providers, extensions);
    }

    @Test
    void shouldSkipAllLookups_whenPageIsEmpty() {
        assertThat(util.estConsultationVecByTeam(info, filter())).isTrue();
        assertThat(util.ids).isEmpty();
        verifyNoInteractions(demographics, providers, extensions);
    }

    @Test
    void shouldPreserveFilters_forPositionalOverload() {
        util.estConsultationVecByTeam(info, "team", true, null, null, "2", "1", "1", 10, 20);
        verify(requests).getConsultationListRows(new ConsultationListFilterDto("team", true, null, null,
                "2", "1", "1", 10, 20, null, null));
    }
    @Test
    void shouldPreserveFirstAndLastExtensionSelection_forChartAndTeamLists() {
        var row = row(7, 42, 0, null, null);
        when(requests.getConsultationListRows(filter())).thenReturn(List.of(row));
        when(requests.getConsultationListRows(42)).thenReturn(List.of(row));
        var first = new ConsultationRequestExt();
        first.setRequestId(7); first.setKey("ereferral_service"); first.setValue("First service");
        var last = new ConsultationRequestExt();
        last.setRequestId(7); last.setKey("ereferral_service"); last.setValue("Last service");
        when(extensions.getConsultationRequestExts(List.of(7))).thenReturn(List.of(first, last));
        assertThat(util.estConsultationVecByTeam(info, filter())).isTrue();
        assertThat(util.service).containsExactly("Last service");
        assertThat(util.estConsultationVecByDemographic(info, "42")).isTrue();
        assertThat(util.service).containsExactly("First service");
    }

    @Test
    void shouldRetainNullDescription_whenServiceRecordExists() {
        when(requests.getConsultationListRows(filter())).thenReturn(List.of(row(7, 42, 5, null, null)));
        assertThat(util.estConsultationVecByTeam(info, filter())).isTrue();
        assertThat(util.service).containsExactly((String) null);
    }

    @ParameterizedTest(name = "[{index}] last={0}, first={1} -> \"{2}\"")
    @CsvSource(value = {
            "Smith     | Jane     | Smith, Jane",
            "Smith     | NULL     | Smith",
            "NULL      | Jane     | Jane",
            "NULL      | NULL     | ''",
            "'  '      | '  '     | ''",
            "' Smith ' | ' Jane ' | Smith, Jane"
    }, delimiter = '|', nullValues = "NULL")
    @DisplayName("should omit missing specialist name parts")
    void shouldOmitMissingParts_whenFormattingSpecialistName(String last, String first, String expected) {
        assertThat(
                EctViewConsultationRequestsUtil.formatSpecialistName(last, first)).isEqualTo(expected);
    }
}
