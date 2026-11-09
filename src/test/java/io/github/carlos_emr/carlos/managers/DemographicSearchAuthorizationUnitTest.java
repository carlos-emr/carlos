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

}
