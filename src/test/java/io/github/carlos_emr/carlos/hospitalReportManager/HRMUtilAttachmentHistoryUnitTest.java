/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentSubClassDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class HRMUtilAttachmentHistoryUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldKeepCurrentSelectionAndDatedHistory_whenDuplicateReportsHaveDifferentClinicalDates() {
        var properties = mock(CarlosProperties.class);
        when(properties.isOntarioBillingRegion()).thenReturn(true);
        var info = mock(LoggedInInfo.class);
        when(info.getLocale()).thenReturn(Locale.ENGLISH);
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_hrm", "r", null)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(info, 5)).thenReturn(true);
        when(security.hasPrivilege(info, "_demographic", "r", "5")).thenReturn(true);
        when(security.hasPrivilege(info, "_hrm", "r", "5")).thenReturn(true);
        when(security.hasPrivilegeReadOnly(info, "_hrm", "r", "5")).thenReturn(true);
        when(security.hasPrivilegeReadOnly(info, "_demographic", "r", "5")).thenReturn(true);
        var documents = mock(HRMDocumentDao.class);
        var patients = mock(HRMDocumentToDemographicDao.class);
        var subclasses = mock(HRMDocumentSubClassDao.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(HRMDocumentDao.class, documents);
        registerMock(HRMDocumentToDemographicDao.class, patients);
        registerMock(HRMDocumentSubClassDao.class, subclasses);

        HRMDocument olderMessage = document(901, new Date(3000));
        HRMDocument current = document(902, new Date(1000));
        HRMDocument undated = document(903, null);
        HRMDocument anonymous = document(904, null);
        HRMDocument alsoAnonymous = document(905, null);
        HRMDocument shared = document(906, null);
        HRMDocument restricted = document(907, null);
        HRMDocument relinked = document(908, null);
        var other = link(906);
        other.setDemographicNo(6);
        shared.setMatchedDemographics(List.of(link(906), other));
        when(security.isAllowedAccessToPatientRecord(info, 6)).thenReturn(true);
        when(security.hasPrivilegeReadOnly(info, "_hrm", "r", "6")).thenReturn(true);
        when(security.hasPrivilegeReadOnly(info, "_demographic", "r", "6")).thenReturn(true);
        var denied = link(907);
        denied.setDemographicNo(7);
        restricted.setMatchedDemographics(List.of(link(907), denied));
        relinked.setMatchedDemographics(List.of(other));
        when(patients.findByDemographicNo("5")).thenReturn(List.of(link(901), link(902), link(903),
                link(901), link(904), link(905), link(906), link(907), link(908)));
        for (var row : List.of(olderMessage, current, undated, anonymous, alsoAnonymous, shared, restricted, relinked)) {
            when(documents.findById(row.getId())).thenReturn(List.of(row));
        }
        HRMReport first = report("20260101000000"), latest = report("20260103000000"), last = report("20260102000000");
        try (var config = mockStatic(CarlosProperties.class); var parser = mockStatic(HRMReportParser.class)) {
            config.when(CarlosProperties::getInstance).thenReturn(properties);
            parser.when(() -> HRMReportParser.parseReport(info, "901.xml")).thenReturn(first);
            parser.when(() -> HRMReportParser.parseReport(info, "902.xml")).thenReturn(latest);
            parser.when(() -> HRMReportParser.parseReport(info, "903.xml")).thenReturn(last);
            HRMReport missingNumber = report("20261101000000");
            when(missingNumber.getSendingFacilityReportNo()).thenReturn(" ");
            parser.when(() -> HRMReportParser.parseReport(info, "904.xml")).thenReturn(missingNumber);
            parser.when(() -> HRMReportParser.parseReport(info, "905.xml")).thenReturn(missingNumber);
            parser.when(() -> HRMReportParser.parseReport(info, "906.xml")).thenReturn(last);

            var rows = HRMUtil.listHRMDocuments(info, "report_date", true, "5", true);
            assertThat(rows.stream().map(row -> (Integer) row.get("id")).toList())
                    .containsExactlyInAnyOrder(902, 904, 905, 906);
            var latestRow = rows.stream().filter(row -> Integer.valueOf(902).equals(row.get("id"))).findFirst().orElseThrow();
            assertThat(latestRow.get("duplicateLabIds")).isEqualTo("901,903");
            assertThat(latestRow.get("time_received")).isEqualTo("");
            assertThat(rows.stream().filter(row -> row != latestRow)).allSatisfy(row ->
                    assertThat((List<?>) row.get("versions")).isEmpty());
            parser.verify(() -> HRMReportParser.parseReport(info, "907.xml"), never());
            parser.verify(() -> HRMReportParser.parseReport(info, "908.xml"), never());
            @SuppressWarnings("unchecked")
            var versions = (List<HrmVersionService.Version>) latestRow.get("versions");
            assertThat(versions).extracting(HrmVersionService.Version::id).containsExactly(901, 903);
            assertThat(versions.getFirst().label()).contains("Report:", "received:", "Unknown date");
            assertThat(versions.getLast().reportDate()).isEqualTo("Unknown date");
        }
    }

    private static HRMDocument document(int id, Date clinicalDate) {
        var row = new HRMDocument();
        ReflectionTestUtils.setField(row, "id", id);
        row.setReportFile(id + ".xml");
        row.setMatchedDemographics(List.of(link(id)));
        row.setReportDate(clinicalDate);
        row.setDescription("FAKE-History");
        row.setReportType("Medical Records Report");
        return row;
    }

    private static HRMDocumentToDemographic link(int id) {
        var row = new HRMDocumentToDemographic();
        row.setHrmDocumentId(id);
        row.setDemographicNo(5);
        return row;
    }

    private static HRMReport report(String messageDate) {
        var report = mock(HRMReport.class);
        when(report.getSendingFacilityId()).thenReturn("FAKE-Facility");
        when(report.getSendingFacilityReportNo()).thenReturn("FAKE-Report");
        when(report.getDeliverToUserId()).thenReturn("FAKE-Recipient");
        when(report.getFirstReportClass()).thenReturn("Medical Records Report");
        when(report.getMessageUniqueId()).thenReturn("FAKE^FAKE^FAKE^FAKE^FAKE^" + messageDate);
        return report;
    }
}
