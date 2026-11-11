/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrmVersionServiceUnitTest extends CarlosUnitTestBase {
    @Test void shouldOrderHistoryAndShowUnknownDates_withoutChangingTheInput() {
        HRMDocument first = new HRMDocument(); org.springframework.test.util.ReflectionTestUtils.setField(first, "id", 1); first.setReportDate(new Date(1000));
        HRMDocument second = new HRMDocument(); org.springframework.test.util.ReflectionTestUtils.setField(second, "id", 2); second.setReportDate(new Date(1000));
        HRMDocument unknown = new HRMDocument(); org.springframework.test.util.ReflectionTestUtils.setField(unknown, "id", 3);
        List<HRMDocument> input = List.of(first, unknown, second);
        var rows = HrmVersionService.format(input, Locale.ENGLISH);
        assertThat(rows).extracting(HrmVersionService.Version::id).containsExactly(2, 1, 3);
        assertThat(rows.getLast().label()).contains("Unknown date");
        assertThat(input).containsExactly(first, unknown, second);
    }
    @Test void shouldMarkMissingAndMalformedIdsIncomplete_withoutRenderingUnverifiedIds() {
        var docs = mock(HRMDocumentDao.class); var links = mock(HRMDocumentToDemographicDao.class);
        var security = mock(SecurityInfoManager.class); var info = mock(LoggedInInfo.class);
        when(security.hasPrivilege(info, "_hrm", "r", (String) null)).thenReturn(true);
        HRMDocument shown = new HRMDocument(); org.springframework.test.util.ReflectionTestUtils.setField(shown, "id", 1);
        var service = new HrmVersionService(docs, links, security);
        var result = service.history(info, shown, mock(HRMReport.class), "2,bad,-3,2", Locale.ENGLISH);
        assertThat(result.incomplete()).isTrue(); assertThat(result.versions()).isEmpty();
        verify(docs, times(1)).find(2);
    }
    @Test void shouldPropagateQueryFailure_insteadOfClaimingEmptyHistory() {
        var docs = mock(HRMDocumentDao.class); var links = mock(HRMDocumentToDemographicDao.class);
        var security = mock(SecurityInfoManager.class); var info = mock(LoggedInInfo.class);
        when(security.hasPrivilege(info, "_hrm", "r", (String) null)).thenReturn(true);
        when(docs.find(2)).thenThrow(new IllegalStateException("synthetic query failure"));
        HRMDocument shown = new HRMDocument(); org.springframework.test.util.ReflectionTestUtils.setField(shown, "id", 1);
        var service = new HrmVersionService(docs, links, security);
        assertThatThrownBy(() -> service.history(info, shown, mock(HRMReport.class), "2", Locale.ENGLISH))
                .isInstanceOf(IllegalStateException.class).hasMessage("synthetic query failure");
    }

    @Test void shouldRenderOnlySameGroupAndPatientHistory_withNullDatesAndStableTies() {
        var docs = mock(HRMDocumentDao.class); var links = mock(HRMDocumentToDemographicDao.class);
        var security = mock(SecurityInfoManager.class); var info = mock(LoggedInInfo.class);
        when(security.hasPrivilege(info, "_hrm", "r", (String) null)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(info, 5)).thenReturn(true);
        HRMDocument shown = document(1), old = document(2), tied = document(3), foreign = document(4), otherGroup = document(6);
        var patient = new io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic(); patient.setDemographicNo(5);
        var another = new io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic(); another.setDemographicNo(9);
        for (var row : List.of(shown, old, tied, otherGroup)) when(links.findByHrmDocumentId(row.getId())).thenReturn(List.of(patient));
        when(links.findByHrmDocumentId(4)).thenReturn(List.of(another));
        for (var row : List.of(old, tied, foreign, otherGroup)) when(docs.find(row.getId().intValue())).thenReturn(row);
        HRMReport group = mock(HRMReport.class), different = mock(HRMReport.class);
        when(group.getSendingFacilityId()).thenReturn("DEMO"); when(group.getSendingFacilityReportNo()).thenReturn("same-report");
        when(group.getDeliverToUserId()).thenReturn("7"); when(different.getSendingFacilityId()).thenReturn("OTHER");
        try (var parser = mockStatic(HRMReportParser.class)) {
            parser.when(() -> HRMReportParser.parseReport(info, "2.xml")).thenReturn(group);
            parser.when(() -> HRMReportParser.parseReport(info, "3.xml")).thenReturn(group);
            parser.when(() -> HRMReportParser.parseReport(info, "6.xml")).thenReturn(different);
            var result = new HrmVersionService(docs, links, security).history(info, shown, group, "2,4,6,3,2,bad,99", Locale.ENGLISH);
            assertThat(result.versions()).extracting(HrmVersionService.Version::id).containsExactly(3, 2);
            assertThat(result.versions()).allSatisfy(v -> assertThat(v.label()).contains("Unknown date"));
            assertThat(result.incomplete()).isTrue();
            parser.verify(() -> HRMReportParser.parseReport(info, "4.xml"), never());
        }
    }
    private HRMDocument document(int id) {
        var row = new HRMDocument(); org.springframework.test.util.ReflectionTestUtils.setField(row, "id", id);
        row.setReportFile(id + ".xml"); return row;
    }
    @Test void shouldMarkExcessiveHistoryHintsIncomplete_withoutLookingUpReports() {
        var docs = mock(HRMDocumentDao.class); var links = mock(HRMDocumentToDemographicDao.class);
        var security = mock(SecurityInfoManager.class); var info = mock(LoggedInInfo.class);
        when(security.hasPrivilege(info, "_hrm", "r", (String) null)).thenReturn(true);
        var result = new HrmVersionService(docs, links, security).history(info, document(1), mock(HRMReport.class),
                "2,".repeat(9000), Locale.ENGLISH);
        assertThat(result.incomplete()).isTrue(); assertThat(result.versions()).isEmpty();
        verifyNoInteractions(docs, links);
    }
}
