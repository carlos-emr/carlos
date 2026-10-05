/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.lab.ca.on;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMReport;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMReportParser;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToProvider;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for HRM result query filters.
 *
 * @since 2026-09-16
 */
@Tag("unit")
class HRMResultsDataUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567890"})
    void shouldKeepMatchedReportVisible_withMissingOrPresentHealthNumber(String hin) {
        Demographic patient = new Demographic();
        patient.setFirstName("Test");
        patient.setLastName("Patient");
        patient.setHin(hin);
        LabResultData result = loadReport(patient, "Patient,Test");
        assertThat(result.isMatchedToPatient).isTrue();
        assertThat(result.patientName).isEqualTo("Patient,Test");
        assertThat(result.healthNumber).isEqualTo(hin);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Patient", "Patient,", "Patient,Test"})
    void shouldKeepUnmatchedReportVisible_withIncompletePatientName(String name) {
        LabResultData result = loadReport(null, name, true);
        assertThat(Boolean.TRUE.equals(result.isMatchedToPatient)).isFalse();
        assertThat(result.patientName).isEqualTo(name);
    }

    private LabResultData loadReport(Demographic patient, String reportName) {
        return loadReport(patient, reportName, false);
    }

    private LabResultData loadReport(Demographic patient, String reportName, boolean forceGivenNameFilter) {
        var providers = mock(HRMDocumentToProviderDao.class);
        var documents = mock(HRMDocumentDao.class);
        var matches = mock(HRMDocumentToDemographicDao.class);
        var demographics = mock(DemographicManager.class);
        registerMock(HRMDocumentToProviderDao.class, providers);
        registerMock(HRMDocumentDao.class, documents);
        registerMock(HRMDocumentToDemographicDao.class, matches);
        registerMock(DemographicManager.class, demographics);
        var info = mock(LoggedInInfo.class);
        var route = new HRMDocumentToProvider();
        route.setHrmDocumentId(42);
        when(providers.findByProviderNoLimit(anyString(), anyList(), anyBoolean(), any(), any(),
                anyInt(), anyInt(), anyBoolean(), anyInt(), anyInt())).thenReturn(List.of(route));
        var document = mock(HRMDocument.class);
        when(document.getId()).thenReturn(42);
        when(document.getTimeReceived()).thenReturn(new Date());
        when(document.getReportFile()).thenReturn("synthetic-report.xml");
        when(documents.findById(42)).thenReturn(List.of(document));
        if (patient != null) {
            var match = new HRMDocumentToDemographic();
            match.setDemographicNo(100);
            when(matches.findByHrmDocumentId(42)).thenReturn(List.of(match));
            when(demographics.getDemographic(info, 100)).thenReturn(patient);
        } else {
            when(matches.findByHrmDocumentId(42)).thenReturn(List.of());
        }
        var report = mock(HRMReport.class);
        when(report.getLegalName()).thenReturn(reportName);
        try (var parser = mockStatic(HRMReportParser.class)) {
            parser.when(() -> HRMReportParser.parseReport(info, "synthetic-report.xml")).thenReturn(report);
            // Nonmatching HIN/surname filters force evaluation of the given-name predicate.
            var results = forceGivenNameFilter
                    ? new HRMResultsData().populateHRMdocumentsResultsData(info, "999998", "", "NO-SURNAME", "NO-HIN",
                            null, "", null, null, false, 0, 100)
                    : new HRMResultsData().populateHRMdocumentsResultsData(info, "999998", "", null, null, false, 0, 100);
            assertThat(results).hasSize(1);
            return results.iterator().next();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"N,0", "A,1", "F,1", "'',2", "NULL,0"}, nullValues = "NULL")
    void shouldFilterBySignOffIndependentlyOfViewedState_whenReviewStatusSelected(String status, int signedOff) {
        HRMDocumentToProviderDao providers = mock(HRMDocumentToProviderDao.class);
        registerMock(HRMDocumentToProviderDao.class, providers);
        registerMock(HRMDocumentDao.class, mock(HRMDocumentDao.class));
        registerMock(HRMDocumentToDemographicDao.class, mock(HRMDocumentToDemographicDao.class));
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        assertThat(new HRMResultsData().populateHRMdocumentsResultsData(
                mock(LoggedInInfo.class), "999998", status, null, null, false, 0, 100)).isEmpty();
        verify(providers).findByProviderNoLimit(eq("999998"), eq(java.util.List.of()), eq(false),
                isNull(), isNull(), eq(2), eq(signedOff), eq(false), eq(0), eq(100));
    }
}
