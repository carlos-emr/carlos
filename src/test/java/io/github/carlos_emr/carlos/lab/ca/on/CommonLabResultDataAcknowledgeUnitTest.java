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
package io.github.carlos_emr.carlos.lab.ca.on;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Covers which lab versions an acknowledgement files.
 *
 * <p>The inbox collapses a lab's version chain (versions sharing an accession number) to one row
 * for the newest version. Acknowledging without filing the older versions leaves them at status
 * {@code N}, the collapsed row re-appears pointing at the previous version, and the lab looks to
 * the clinician like it was never acknowledged — the alpha-tester report this covers.
 *
 * @since 2026-09-06
 */
@DisplayName("Lab acknowledgement version selection")
@Tag("unit")
@Tag("lab")
class CommonLabResultDataAcknowledgeUnitTest extends CarlosUnitTestBase {

    /**
     * CommonLabResultData and Hl7textResultsData both resolve DAOs in their static initializers;
     * register them so referencing either class does not blow up outside a Spring context.
     */
    private void registerStaticInitializerMocks() {
        registerMock(OscarLogDao.class, mock(OscarLogDao.class));
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(MeasurementsDeletedDao.class, mock(MeasurementsDeletedDao.class));
        registerMock(MeasurementDao.class, mock(MeasurementDao.class));
        registerMock(MeasurementsExtDao.class, mock(MeasurementsExtDao.class));
        registerMock(MeasurementMapDao.class, mock(MeasurementMapDao.class));
        registerMock(ConsultDocsDao.class, mock(ConsultDocsDao.class));
        registerMock(ConsultResponseDocDao.class, mock(ConsultResponseDocDao.class));
        registerMock(Hl7TextInfoDao.class, mock(Hl7TextInfoDao.class));
        registerMock(Hl7TextMessageDao.class, mock(Hl7TextMessageDao.class));
        registerMock(EFormDocsDao.class, mock(EFormDocsDao.class));
    }

    @Test
    @DisplayName("should mark the reviewed version acknowledged and file each earlier one")
    void shouldWriteEachVersionStatus_whenAcknowledgingReport() {
        registerStaticInitializerMocks();

        // The routine under test runs for real; only the per-row write is stubbed, so this
        // pins the statuses and the ids actually written — a reversed, truncated or wrongly
        // lettered filing loop fails here rather than reaching a clinician's inbox.
        try (MockedStatic<CommonLabResultData> commonLabResultData =
                     mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("171")).thenReturn("169,170,171");
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean())).thenReturn(true);
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any())).thenReturn(true);

            CommonLabResultData.acknowledgeReport(171, "999998", "Reviewed", "HL7", false, "169,170,171");

            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    171, "999998", 'A', "Reviewed", "HL7", false));
            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    169, "999998", 'F', "", "HL7"));
            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    170, "999998", 'F', "", "HL7"));
        }
    }

    @Test
    @DisplayName("should report one cleared routing row for every version it took out of NEW")
    void shouldReportClearedRowCount_forAMultiVersionChain() {
        registerStaticInitializerMocks();

        // The inbox counters count providerLabRouting rows — one per lab VERSION — while the
        // list collapses a chain to a single row. The browser cannot see the chain on the
        // macro path, so this number is the only thing that lets it move the badge by the
        // amount the next page load will compute for itself.
        try (MockedStatic<CommonLabResultData> commonLabResultData =
                     mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("171")).thenReturn("169,170,171");
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean())).thenReturn(true);
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any())).thenReturn(true);

            int cleared = CommonLabResultData.acknowledgeReport(
                    171, "999998", "Reviewed", "HL7", false, "169,170,171");

            assertThat(cleared).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("should report a single cleared routing row for a lab with no earlier version")
    void shouldReportOneClearedRow_forASingleVersionLab() {
        registerStaticInitializerMocks();

        try (MockedStatic<CommonLabResultData> commonLabResultData =
                     mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("170")).thenReturn("170");
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean())).thenReturn(true);

            int cleared = CommonLabResultData.acknowledgeReport(170, "999998", "", "HL7", true, "170");

            assertThat(cleared).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("should write nothing when the version chain cannot be resolved")
    void shouldWriteNothing_whenChainLookupFails() {
        registerStaticInitializerMocks();

        // Resolving the chain hits the database. If that fails after the reviewed row is
        // already stamped, the caller reports a failure while the acknowledgement is
        // half-applied — lab acknowledged, older versions still NEW, collapsed row back in
        // the inbox. Failing before the first write leaves nothing behind.
        try (MockedStatic<CommonLabResultData> commonLabResultData =
                     mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("171"))
                    .thenThrow(new IllegalStateException("database unavailable"));
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean())).thenReturn(true);

            assertThatThrownBy(() -> CommonLabResultData.acknowledgeReport(
                    171, "999998", "Reviewed", "HL7", false, "169,170,171"))
                    .isInstanceOf(IllegalStateException.class);

            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean()), never());
        }
    }

    @Test
    @DisplayName("should file nothing beyond the acknowledged version when it is the only one")
    void shouldWriteOnlyTheAcknowledgedVersion_forSingleVersionReport() {
        registerStaticInitializerMocks();

        try (MockedStatic<CommonLabResultData> commonLabResultData =
                     mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("170")).thenReturn("170");
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean())).thenReturn(true);

            CommonLabResultData.acknowledgeReport(170, "999998", "", "HL7", true, "170");

            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    170, "999998", 'A', "", "HL7", true));
            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), eq('F'), any(), any()), never());
        }
    }

    @Test
    @DisplayName("should file the earlier versions when the newest version of a lab is acknowledged")
    void shouldSelectEarlierVersions_whenAcknowledgingNewestVersion() {
        registerStaticInitializerMocks();

        try (MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("171")).thenReturn("169,170,171");

            assertThat(CommonLabResultData.olderVersionsOf(171, "HL7", "169,170,171"))
                    .containsExactly(169, 170);
        }
    }

    @Test
    @DisplayName("should leave a later corrected version alone when an earlier version is acknowledged")
    void shouldExcludeLaterVersions_whenAcknowledgingEarlierVersion() {
        registerStaticInitializerMocks();

        try (MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("170")).thenReturn("169,170,171");

            assertThat(CommonLabResultData.olderVersionsOf(170, "HL7", "169,170,171"))
                    .containsExactly(169);
        }
    }

    @Test
    @DisplayName("should derive the HL7 version chain server side and ignore the posted one")
    void shouldIgnorePostedChain_forHl7Labs() {
        registerStaticInitializerMocks();

        try (MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("170")).thenReturn("169,170");

            // Every id in the chain becomes a write to another lab's routing row, so for HL7 it
            // comes from the accession number, never from the browser. A macro that posted no
            // chain, and a forged one naming unrelated labs, both file this lab's own versions
            // and nothing else.
            assertThat(CommonLabResultData.olderVersionsOf(170, "HL7", null)).containsExactly(169);
            assertThat(CommonLabResultData.olderVersionsOf(170, "HL7", "900,901")).containsExactly(169);
            assertThat(CommonLabResultData.olderVersionsOf(170, "HL7", "900,901,170"))
                    .as("a forged chain must not get unrelated labs filed")
                    .containsExactly(169);
        }
    }

    @Test
    @DisplayName("should file nothing for a non-HL7 report whose posted chain does not describe it")
    void shouldFileNothing_forNonHl7ReportWithoutUsableChain() {
        registerStaticInitializerMocks();

        // Documents have no accession-number chain to derive, so they fall back to the posted
        // value; one that does not describe this document files nothing rather than guess —
        // and must not throw the way the old index walk did.
        assertThat(CommonLabResultData.olderVersionsOf(42, "DOC", null)).isEmpty();
        assertThat(CommonLabResultData.olderVersionsOf(42, "DOC", "900,901")).isEmpty();
    }

    @Test
    @DisplayName("should file nothing when the acknowledged lab is the only version in its chain")
    void shouldFileNothing_forSingleVersionLab() {
        registerStaticInitializerMocks();

        try (MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("170")).thenReturn("170");

            assertThat(CommonLabResultData.olderVersionsOf(170, "HL7", "170")).isEmpty();
        }
    }
}
