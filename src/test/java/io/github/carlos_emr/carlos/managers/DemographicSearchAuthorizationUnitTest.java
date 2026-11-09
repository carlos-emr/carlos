/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchResult;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class DemographicSearchAuthorizationUnitTest extends CarlosUnitTestBase {
    private DemographicDao dao;
    private SecurityInfoManager privileges;
    private DemographicManagerImpl manager;
    private final LoggedInInfo actor = new LoggedInInfo();
    private final Set<Integer> readable = new HashSet<>();

    @BeforeEach void setup() {
        dao = mock(DemographicDao.class); privileges = mock(SecurityInfoManager.class);
        registerMock(DemographicDao.class, dao); registerMock(SecurityInfoManager.class, privileges);
        manager = new DemographicManagerImpl();
        injectDependency(manager, "demographicDao", dao); injectDependency(manager, "securityInfoManager", privileges);
        when(privileges.hasPrivilege(actor, "_demographic", "r", null)).thenReturn(true);
        when(privileges.getReadableDemographicIds(eq(actor), anyCollection())).thenAnswer(call -> {
            var ids = new LinkedHashSet<>(call.<Collection<Integer>>getArgument(1)); ids.retainAll(readable); return ids;
        });
    }
    private static <T> List<T> page(List<T> rows, int offset, int limit) {
        return rows.subList(Math.min(offset, rows.size()), Math.min(Math.addExact(offset, limit), rows.size()));
    }
    private static List<Demographic> patients(int count) { return IntStream.rangeClosed(1, count).mapToObj(Demographic::new).toList(); }
    private static List<DemographicSearchResult> searchRows(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(id -> {
            var row = new DemographicSearchResult(); row.setDemographicNo(id); return row;
        }).toList();
    }
    @Test void shouldApplyNameOffsets_toPermittedRowsAndAuditOnlyReturnedPatients() {
        var rows = patients(8); readable.addAll(List.of(3, 6, 7));
        when(dao.searchDemographicByNameString(eq("Synthetic"), anyInt(), anyInt()))
                .thenAnswer(call -> page(rows, call.getArgument(1), call.getArgument(2)));
        assertThat(manager.searchDemographicByName(actor, "Synthetic", 1, 2)).extracting(Demographic::getId).containsExactly(6, 7);
        for (int id : List.of(6, 7)) logActionMock.verify(() -> LogAction.addLogSynchronous(actor,
                "DemographicManager.searchDemographicByName result", "demographicId=" + id));
        logActionMock.verifyNoMoreInteractions();
    }
    @Test void shouldContinuePastEntirelyBlockedNamePages_withoutAnEmptyExportTerminator() {
        var rows = patients(501); readable.add(501);
        when(dao.searchDemographicByNameString(eq("Synthetic"), anyInt(), anyInt()))
                .thenAnswer(call -> page(rows, call.getArgument(1), call.getArgument(2)));
        assertThat(manager.searchDemographicByName(actor, "Synthetic", 0, 500)).extracting(Demographic::getId).containsExactly(501);
        verify(dao).searchDemographicByNameString("Synthetic", 500, 500);
    }
    @Test void shouldApplyAttributeOffsets_toPermittedRows() {
        var rows = patients(8); readable.addAll(List.of(2, 5, 6));
        when(dao.findByAttributes(isNull(), eq("Synthetic"), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenAnswer(call -> page(rows, call.getArgument(10), call.getArgument(11)));
        assertThat(manager.searchDemographicsByAttributes(actor, null, "Synthetic", null, null, null, null, null,
                null, null, null, 1, 1)).extracting(Demographic::getId).containsExactly(5);
    }
    @Test void shouldUseTheSamePolicy_forRestSearchPagesAndCountsPastABlockedBatch() {
        var rows = searchRows(502); var request = new DemographicSearchRequest(); readable.addAll(List.of(501, 502));
        when(dao.searchPatients(eq(actor), eq(request), anyInt(), anyInt()))
                .thenAnswer(call -> page(rows, call.getArgument(2), call.getArgument(3)));
        assertThat(manager.searchPatients(actor, request, 1, 1)).extracting(DemographicSearchResult::getDemographicNo)
                .containsExactly(502);
        assertThat(manager.searchPatientsCount(actor, request)).isEqualTo(2);
        verify(dao, never()).searchPatientCount(any(), any());
    }
    @Test void shouldRefuseRoleDenial_beforeSearchingOrCounting() {
        when(privileges.hasPrivilege(actor, "_demographic", "r", null)).thenReturn(false);
        assertThatThrownBy(() -> manager.searchDemographicByName(actor, "Synthetic", 0, 10)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> manager.searchPatientsCount(actor, new DemographicSearchRequest())).isInstanceOf(SecurityException.class);
        verifyNoInteractions(dao);
    }
    @Test void shouldKeepZeroSizeSearchesEmpty_andRejectInvalidPagination() {
        assertThat(manager.searchDemographicByName(actor, "Synthetic", 0, 0)).isEmpty();
        assertThatThrownBy(() -> manager.searchDemographicByName(actor, "Synthetic", -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> manager.searchDemographicByName(actor, "Synthetic", 0, -1)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(dao);
    }
    @Test void shouldPropagatePolicyFailure_withoutReturningAnUnfilteredPage() {
        when(dao.searchDemographicByNameString("Synthetic", 0, 10)).thenReturn(patients(1));
        when(privileges.getReadableDemographicIds(eq(actor), anyCollection())).thenThrow(new IllegalStateException("Synthetic storage failure"));
        assertThatThrownBy(() -> manager.searchDemographicByName(actor, "Synthetic", 0, 10)).isInstanceOf(IllegalStateException.class);
        logActionMock.verifyNoInteractions();
    }
    @Test void shouldPreserveLargeLegacyNamePages_whileBoundingEachPolicyBatch() {
        var rows = patients(501); readable.addAll(IntStream.rangeClosed(1, 501).boxed().toList());
        when(dao.searchDemographicByNameString(eq("Synthetic"), anyInt(), anyInt()))
                .thenAnswer(call -> page(rows, call.getArgument(1), call.getArgument(2)));
        assertThat(manager.searchDemographicByName(actor, "Synthetic", 0, 501)).hasSize(501);
        verify(dao).searchDemographicByNameString("Synthetic", 0, 500);
        verify(dao).searchDemographicByNameString("Synthetic", 500, 500);
    }

    @Test
    void shouldApplyReadonlyPolicy_toAllUnpagedPatientDiscoveryPaths() {
        var rows = patients(502);
        rows.forEach(patient -> patient.setLastName("Synthetic"));
        readable.addAll(List.of(501, 502));
        var provider = new io.github.carlos_emr.carlos.commn.model.Provider(); provider.setProviderNo("41");
        when(dao.getDemographicByProvider("41")).thenReturn(rows);
        when(dao.getDemographicByProvider("41", true)).thenReturn(rows);
        when(dao.searchDemographic("Synthetic")).thenReturn(rows);
        when(dao.getActiveDemosByHealthCardNo("FAKE-HIN", "ON")).thenReturn(rows);
        when(dao.getClientsByChartNo("FAKE-CHART")).thenReturn(rows);
        when(dao.searchByHealthCard("FAKE-HIN")).thenReturn(rows);
        when(dao.getDemographicWithLastFirstDOB("Synthetic", "Patient", "1980", "01", "15")).thenReturn(rows);
        var calls = List.<java.util.function.Supplier<List<Demographic>>>of(
                () -> manager.getDemographicsNameRangeByProvider(actor, provider, "^[A-Z]"),
                () -> manager.getDemographicsByProvider(actor, provider),
                () -> manager.searchDemographic(actor, "Synthetic"),
                () -> manager.getActiveDemosByHealthCardNo(actor, "FAKE-HIN", "ON"),
                () -> manager.getDemosByChartNo(actor, "FAKE-CHART"),
                () -> manager.searchByHealthCard(actor, "FAKE-HIN"),
                () -> manager.getDemographicWithLastFirstDOB(actor, "Synthetic", "Patient", "1980", "01", "15"));
        for (var call : calls) assertThat(call.get()).extracting(Demographic::getId).containsExactly(501, 502);
        verify(privileges, atLeastOnce()).getReadableDemographicIds(actor,
                java.util.stream.IntStream.rangeClosed(1, 500).boxed().toList());
        verify(privileges, atLeastOnce()).getReadableDemographicIds(actor, List.of(501, 502));
        verify(privileges, never()).hasPrivilegeReadOnly(any(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldFilterAdmissionAndCareTeamIds_withTheSameBoundedPolicy() {
        var admissions = mock(io.github.carlos_emr.carlos.commn.dao.AdmissionDao.class);
        var extensions = mock(io.github.carlos_emr.carlos.commn.dao.DemographicExtDao.class);
        injectDependency(manager, "admissionDao", admissions); injectDependency(manager, "demographicExtDao", extensions);
        var ids = java.util.stream.IntStream.rangeClosed(1, 502).boxed().toList(); readable.addAll(List.of(501, 502));
        when(admissions.getAdmittedDemographicIdByProgramAndProvider(7, "41")).thenReturn(ids);
        when(extensions.getDemographicNumbersByDemographicExtKeyAndProviderNumberAndDemographicLastNameRegex(
                any(), eq("41"), eq("^[A-Z]"))).thenReturn(ids);
        assertThat(manager.getAdmittedDemographicIdsByProgramAndProvider(actor, 7, "41")).containsExactly(501, 502);
        assertThat(manager.getDemographicNumbersByMidwifeNumberAndDemographicLastNameRegex(actor, "41", "^[A-Z]"))
                .containsExactly(501, 502);
        assertThat(manager.getDemographicNumbersByNurseNumberAndDemographicLastNameRegex(actor, "41", "^[A-Z]"))
                .containsExactly(501, 502);
        assertThat(manager.getDemographicNumbersByResidentNumberAndDemographicLastNameRegex(actor, "41", "^[A-Z]"))
                .containsExactly(501, 502);
    }

    @Test
    void shouldRefuseGlobalDenial_beforeReadingAdmissions() {
        var admissions = mock(io.github.carlos_emr.carlos.commn.dao.AdmissionDao.class);
        injectDependency(manager, "admissionDao", admissions);
        when(privileges.hasPrivilege(actor, "_demographic", "r", null)).thenReturn(false);
        assertThatThrownBy(() -> manager.getAdmittedDemographicIdsByProgramAndProvider(actor, 7, "41"))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(admissions);
    }

    @Test
    void shouldPropagateDiscoveryFailures_withoutReturningTheUnfilteredRows() {
        when(dao.searchDemographic("Synthetic")).thenReturn(patients(2));
        when(privileges.getReadableDemographicIds(eq(actor), anyCollection()))
                .thenThrow(new IllegalStateException("policy unavailable"));
        assertThatThrownBy(() -> manager.searchDemographic(actor, "Synthetic")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectMalformedDiscoveryRows_insteadOfTreatingThemAsAuthorized() {
        when(dao.searchDemographic("Synthetic")).thenReturn(List.of(new Demographic()));
        assertThatThrownBy(() -> manager.searchDemographic(actor, "Synthetic")).isInstanceOf(IllegalStateException.class);
    }

}
