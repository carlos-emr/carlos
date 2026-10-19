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
package io.github.carlos_emr.carlos.mds.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.LabPatientPhysicianInfoDao;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo;
import io.github.carlos_emr.carlos.commn.model.LabReportInformation;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * The CML report-version chain against the real lookup query and a real schema (issue #4568).
 *
 * <p>{@link MDSResultsDataCmlVersionChainUnitTest} fixes the parsing rules with a mocked DAO; this test
 * closes the gap that let the defect through, a consumer written against the wrong column type. Here the
 * rows come out of Hibernate, so a change to either side of the contract ({@code service_date} as a
 * {@code String}, the {@code Object[]} shape) fails the build instead of the clinician's screen.</p>
 *
 * @since 2026-10-15
 */
@DisplayName("MDSResultsData CML version chain integration")
@Tag("integration")
@Tag("lab")
@Transactional
class MDSResultsDataCmlVersionChainIntegrationTest extends CarlosTestBase {

    private static final String ACCESSION = "ACC-4568-I";

    @Autowired
    private LabPatientPhysicianInfoDao labPatientPhysicianInfoDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /** Persists one CML report: the print header that orders the chain and the patient/physician row. */
    private int version(String accession, String serviceDate, String printDate, String printTime) throws Exception {
        LabReportInformation header = new LabReportInformation();
        header.setLocationId("70");
        header.setPrintDate(printDate);
        header.setPrintTime(printTime);
        header.setTotalBType("1");
        header.setTotalCType("1");
        header.setTotalDType("0");
        entityManager.persist(header);
        entityManager.flush();

        LabPatientPhysicianInfo info = new LabPatientPhysicianInfo();
        EntityDataGenerator.generateTestDataForModelClass(info);
        info.setPatientHin("TEST-HIN");
        info.setPatientDob("19800101");
        info.setPatientFirstName("Test");
        info.setPatientLastName("Patient");
        info.setLabReportInfoId(header.getId());
        info.setAccessionNum(accession);
        info.setServiceDate(serviceDate);
        labPatientPhysicianInfoDao.persist(info);
        entityManager.flush();
        return info.getId();
    }

    @Test
    @DisplayName("should chain every version of the accession, oldest print first, from any one of them")
    void shouldChainEveryVersion_whenResolvedFromAnyOfThem() throws Exception {
        int preliminary = version(ACCESSION, "20260829", "20260829", "08:00");
        int finalReport = version(ACCESSION, "20260901", "20260901", "09:30");
        int corrected = version(ACCESSION, "20260903", "20260903", "10:45");
        String chain = preliminary + "," + finalReport + "," + corrected;

        for (int requested : new int[] {preliminary, finalReport, corrected}) {
            assertThat(new MDSResultsData().getMatchingCMLLabs(String.valueOf(requested))).isEqualTo(chain);
            assertThat(new MDSResultsData().getMatchingCMLLabs(String.valueOf(requested), true)).isEqualTo(chain);
        }
    }

    @Test
    @DisplayName("should leave out a report that recycled the accession number years ago")
    void shouldLeaveOutRecycledAccession_whenServiceDateIsYearsAway() throws Exception {
        int recycled = version(ACCESSION, "20200901", "20200901", "08:00");
        int preliminary = version(ACCESSION, "20260829", "20260829", "08:00");
        int finalReport = version(ACCESSION, "20260901", "20260901", "09:30");

        assertThat(new MDSResultsData().getMatchingCMLLabs(String.valueOf(finalReport)))
                .isEqualTo(preliminary + "," + finalReport);
        assertThat(new MDSResultsData().getMatchingCMLLabs(String.valueOf(recycled))).isEqualTo(String.valueOf(recycled));
    }

    @Test
    @DisplayName("should not mix in reports of another accession number")
    void shouldNotMixInOtherAccessions_whenResolvingTheChain() throws Exception {
        int requested = version(ACCESSION, "20260901", "20260901", "09:30");
        version("ACC-4568-OTHER", "20260901", "20260901", "09:30");

        assertThat(new MDSResultsData().getMatchingCMLLabs(String.valueOf(requested)))
                .isEqualTo(String.valueOf(requested));
    }

    @Test
    @DisplayName("should give a report without a print header a chain of its own, and keep it out of its siblings'")
    void shouldGiveReportWithoutPrintHeaderItsOwnChain_whenSiblingsHaveOne() throws Exception {
        // The lookup orders by the print header, so a report without one cannot be placed among its siblings.
        // Chaining the siblings to it anyway would forward or file a report other than the one that was opened.
        int sibling = version(ACCESSION, "20260901", "20260901", "09:30");
        LabPatientPhysicianInfo orphan = new LabPatientPhysicianInfo();
        EntityDataGenerator.generateTestDataForModelClass(orphan);
        orphan.setLabReportInfoId(0);
        orphan.setAccessionNum(ACCESSION);
        orphan.setServiceDate("20260901");
        labPatientPhysicianInfoDao.persist(orphan);
        entityManager.flush();

        assertThat(new MDSResultsData().getMatchingCMLLabs(String.valueOf(orphan.getId()))).isEqualTo(String.valueOf(orphan.getId()));
        assertThat(new MDSResultsData().getMatchingCMLLabs(String.valueOf(sibling))).isEqualTo(String.valueOf(sibling));
        assertThatThrownBy(() -> new MDSResultsData().getMatchingCMLLabs(String.valueOf(sibling), true))
                .isInstanceOf(IllegalStateException.class);
        // A mutation starting from the orphan refuses: its sibling exists, and it cannot be placed beside it.
        String orphanId = String.valueOf(orphan.getId());
        MDSResultsData data = new MDSResultsData();
        assertThatThrownBy(() -> data.getMatchingCMLLabs(orphanId, true))
                .isInstanceOf(IllegalStateException.class).hasMessage("Lab version chain could not be determined");
    }

    @Test
    @DisplayName("should order reports that share a print run by id, the order they were imported, when print date and time tie")
    void shouldOrderTiedReportsById_whenTheyShareAPrintRun() throws Exception {
        // One ABCD file is one print header, so every report in it ties on print date and time. Without a
        // tie-breaker the database may return them in any order, and acknowledging one files "the older ones".
        // H2 happens to return such ties in id order, so this passes with or without "lpp.id" in the ORDER BY: it
        // pins the behaviour the callers rely on, while LabPatientPhysicianInfoDaoImplUnitTest pins the clause itself.
        LabReportInformation header = new LabReportInformation();
        header.setLocationId("70");
        header.setPrintDate("20260901");
        header.setPrintTime("09:30");
        header.setTotalBType("3");
        header.setTotalCType("3");
        header.setTotalDType("0");
        entityManager.persist(header);
        entityManager.flush();
        List<Integer> importOrder = new ArrayList<>();
        for (String serviceDate : new String[] {"20260901", "20260902", "20260903"}) {
            LabPatientPhysicianInfo info = new LabPatientPhysicianInfo();
            EntityDataGenerator.generateTestDataForModelClass(info);
            info.setPatientHin("TEST-HIN");
            info.setPatientDob("19800101");
            info.setPatientFirstName("Test");
            info.setPatientLastName("Patient");
            info.setLabReportInfoId(header.getId());
            info.setAccessionNum(ACCESSION);
            info.setServiceDate(serviceDate);
            labPatientPhysicianInfoDao.persist(info);
            importOrder.add(info.getId());
        }
        entityManager.flush();
        String chain = importOrder.get(0) + "," + importOrder.get(1) + "," + importOrder.get(2);

        for (int requested : importOrder) {
            assertThat(new MDSResultsData().getMatchingCMLLabs(String.valueOf(requested))).isEqualTo(chain);
        }
    }

    @ParameterizedTest(name = "accession number [{0}]")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("should not link reports that have no accession number, which would otherwise match each other")
    void shouldNotLinkReports_whenTheyHaveNoAccessionNumber(String blankAccession) throws Exception {
        // Two unrelated reports, as two patients' would be: a blank accession equals every other blank one, and
        // acknowledging or forwarding one report files or routes every report in its chain. (NULL is never equal to
        // NULL in SQL, so the null case holds with or without the guard; the empty and whitespace values need it.)
        int first = version(blankAccession, "20260901", "20260901", "09:30");
        int second = version(blankAccession, "20260901", "20260901", "10:30");

        assertThat(new MDSResultsData().getMatchingCMLLabs(String.valueOf(first))).isEqualTo(String.valueOf(first));
        assertThat(new MDSResultsData().getMatchingCMLLabs(String.valueOf(second), true))
                .isEqualTo(String.valueOf(second));
    }

    @Test
    @DisplayName("should not log an error when the versions resolve")
    void shouldNotLogError_whenVersionsResolve() throws Exception {
        version(ACCESSION, "20260829", "20260829", "08:00");
        int finalReport = version(ACCESSION, "20260901", "20260901", "09:30");

        try (LogCapture capture = LogCapture.forLogger(MDSResultsData.class)) {
            new MDSResultsData().getMatchingCMLLabs(String.valueOf(finalReport));

            assertThat(capture.events().stream().filter(event -> event.getLevel() == Level.ERROR).map(LogEvent::getMessage).toList())
                    .isEmpty();
        }
    }
    @Test
    void shouldRefuseCrossPatientAccession_whenSourceIdentitiesDiffer() throws Exception {
        int first = version(ACCESSION, "20260901", "20260901", "08:00");
        int second = version(ACCESSION, "20260902", "20260902", "09:00");
        labPatientPhysicianInfoDao.find(first).setPatientHin("OTHER-HIN");
        entityManager.flush();
        var data = new MDSResultsData();
        assertThat(data.getCmlVersionChain(String.valueOf(second)).reliable()).isFalse();
        assertThatThrownBy(() -> data.getMatchingCMLLabs(String.valueOf(second), true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldKeepAllVersions_whenOneVersionWasRoutedToTheWrongChart() throws Exception {
        int first = version(ACCESSION, "20260901", "20260901", "08:00");
        int second = version(ACCESSION, "20260902", "20260902", "09:00");
        entityManager.persist(new io.github.carlos_emr.carlos.commn.model.PatientLabRouting(first, "CML", 100));
        entityManager.persist(new io.github.carlos_emr.carlos.commn.model.PatientLabRouting(second, "CML", 200));
        entityManager.flush();
        assertThat(new MDSResultsData().getMatchingCMLLabs(String.valueOf(second), true))
                .isEqualTo(first + "," + second);
    }

}
