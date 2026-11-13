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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.TimeZone;
import io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.LabPatientPhysicianInfoDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * The CML report-version chain ({@link MDSResultsData#getMatchingCMLLabs}) for issue #4568.
 *
 * <p>{@code LabPatientPhysicianInfoDao.findLabServiceDatesByLabId} selects {@code lpp.serviceDate},
 * a {@code String} property over {@code service_date varchar(10)} that holds the vendor's
 * {@code YYYYMMDD}. The lookup used to cast those columns to {@code Date}, threw a
 * {@code ClassCastException} on every CML view and acknowledgement, logged it at ERROR and fell back
 * to a one-lab chain, so a CML report never showed, filed or forwarded its other versions. Every row
 * in these tests starts with the real projection types: an {@code Integer} id and two
 * {@code String} dates, followed by source identity entities and print metadata. A mock that hands back {@code Date} objects is what let the defect through.</p>
 *
 * @since 2026-10-15
 */
@DisplayName("MDSResultsData CML version chain")
@Tag("unit")
@Tag("lab")
class MDSResultsDataCmlVersionChainUnitTest extends CarlosUnitTestBase {

    private LabPatientPhysicianInfoDao dao;

    @BeforeEach
    void setUp() {
        dao = mock(LabPatientPhysicianInfoDao.class);
        registerMock(ConsultDocsDao.class, mock(ConsultDocsDao.class));
        registerMock(ConsultResponseDocDao.class, mock(ConsultResponseDocDao.class));
        registerMock(LabPatientPhysicianInfoDao.class, dao);
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(EFormDocsDao.class, mock(EFormDocsDao.class));
    }

    /** One row as the DAO returns it: the candidate's id, its service date, and the requested lab's service date. */
    private static Object[] row(int id, String serviceDate, String requestedServiceDate) {
        return new Object[] {id, serviceDate, requestedServiceDate, patient(), patient(), "20260901", "09:30"};
    }

    private static LabPatientPhysicianInfo patient() {
        var patient = new LabPatientPhysicianInfo();
        patient.setPatientHin("TEST-HIN");
        patient.setPatientDob("19800101");
        patient.setPatientFirstName("Test");
        patient.setPatientLastName("Patient");
        return patient;
    }

    private void daoReturns(int requested, Object[]... rows) {
        when(dao.findLabServiceDatesByLabId(requested)).thenReturn(List.of(rows));
    }

    /** The ERROR events the capture holds, so "none" and "exactly one" are asserted on what matters, not on every event. */
    private static List<LogEvent> errorsLogged(LogCapture capture) {
        return capture.events().stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    @Test
    @DisplayName("should chain every version of the accession when the service dates are vendor YYYYMMDD strings")
    void shouldChainEveryVersion_whenServiceDatesAreVendorStrings() {
        daoReturns(42, row(40, "20260829", "20260901"), row(41, "20260831", "20260901"),
                row(42, "20260901", "20260901"));

        assertThat(new MDSResultsData().getMatchingCMLLabs("42")).isEqualTo("40,41,42");
    }

    @Test
    @DisplayName("should keep the order the lookup returned, which is print order, not numeric order")
    void shouldKeepLookupOrder_whenIdsAreNotAscending() {
        // The DAO orders by print date and time; a corrected report can carry a lower id than the report it supersedes.
        daoReturns(51, row(50, "20260801", "20260901"), row(49, "20260815", "20260901"),
                row(51, "20260901", "20260901"));

        assertThat(new MDSResultsData().getMatchingCMLLabs("51")).isEqualTo("50,49,51");
    }

    @Test
    @DisplayName("should include versions that arrived after the requested report")
    void shouldIncludeNewerVersions_whenResolvingFromTheOlderOne() {
        daoReturns(40, row(40, "20260829", "20260829"), row(41, "20260831", "20260829"),
                row(42, "20260901", "20260829"));

        assertThat(new MDSResultsData().getMatchingCMLLabs("40")).isEqualTo("40,41,42");
    }

    @Test
    @DisplayName("should leave out a report that recycled the accession number years ago")
    void shouldLeaveOutRecycledAccession_whenServiceDateIsYearsAway() {
        daoReturns(42, row(9, "20200901", "20260901"), row(41, "20260831", "20260901"),
                row(42, "20260901", "20260901"));

        assertThat(new MDSResultsData().getMatchingCMLLabs("42")).isEqualTo("41,42");
    }

    @ParameterizedTest(name = "candidate dated {0} against a requested report of 20260901: chained={1}")
    @CsvSource({
            "20260502, true",
            "20260501, false",
            "20261201, true",
            "20270101, false"
    })
    @DisplayName("should apply the four month window used by the other legacy lab chains")
    void shouldApplyFourMonthWindow_toTheServiceDateGap(String candidateDate, boolean chained) {
        daoReturns(42, row(7, candidateDate, "20260901"), row(42, "20260901", "20260901"));

        assertThat(new MDSResultsData().getMatchingCMLLabs("42")).isEqualTo(chained ? "7,42" : "42");
    }

    @Test
    @DisplayName("should accept ISO yyyy-MM-dd service dates as well as the vendor form")
    void shouldAcceptIsoDates_whenServiceDateIsHyphenated() {
        daoReturns(42, row(41, "2026-08-31", "2026-09-01"), row(42, "2026-09-01", "2026-09-01"));

        assertThat(new MDSResultsData().getMatchingCMLLabs("42")).isEqualTo("41,42");
    }

    @Test
    @DisplayName("should read service dates that carry surrounding spaces, as a padded or hand-entered row can")
    void shouldAcceptPaddedDates_whenServiceDateHasSurroundingSpaces() {
        // Strictness is about the calendar and about trailing text, not about whitespace around a real date.
        daoReturns(42, row(41, " 20260831 ", " 20260901"), row(42, "20260901 ", " 20260901"));

        assertThat(new MDSResultsData().getMatchingCMLLabs("42")).isEqualTo("41,42");
    }

    @Test
    @DisplayName("should leave out a report whose service date cannot be read instead of guessing it belongs")
    void shouldLeaveOutCandidate_whenItsServiceDateCannotBeRead() {
        // A report of the same accession whose date we cannot place may be an unrelated, recycled one:
        // filing it with this chain would hide a result nobody has read.
        daoReturns(42, row(7741, "not-a-date", "20260901"), row(7742, "20261301", "20260901"),
                row(7743, null, "20260901"), row(42, "20260901", "20260901"));

        assertThat(new MDSResultsData().getMatchingCMLLabs("42")).isEqualTo("42");
    }

    @Test
    @DisplayName("should refuse to resolve a chain for a mutation when a sibling's service date cannot be read")
    void shouldRefuseChain_whenResolvingForMutationAndASiblingDateCannotBeRead() {
        // The strict form exists so that an atomic mutation never proceeds on a chain it had to guess: leaving the
        // sibling out would match or route some versions of the order and not the others.
        daoReturns(42, row(41, "not-a-date", "20260901"), row(42, "20260901", "20260901"));

        MDSResultsData data = new MDSResultsData();
        assertThatThrownBy(() -> data.getMatchingCMLLabs("42", true))
                .isInstanceOf(IllegalStateException.class).hasMessage("Lab version chain could not be determined");
    }

    @ParameterizedTest(name = "requested report has service date [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "0000-00-00", "2026", "2026090", "20260230", "20260901xyz"})
    @DisplayName("should keep a report in its own chain when its own service date cannot be read")
    void shouldKeepRequestedReport_whenItsOwnServiceDateCannotBeRead(String requestedDate) {
        // No candidate can be placed against an unreadable date, so the chain is the report itself.
        daoReturns(42, row(41, "20260831", requestedDate), row(42, requestedDate, requestedDate));

        assertThat(new MDSResultsData().getMatchingCMLLabs("42")).isEqualTo("42");
        // ... but it cannot be placed against its siblings, so a mutation refuses rather than guesses.
        MDSResultsData data = new MDSResultsData();
        assertThatThrownBy(() -> data.getMatchingCMLLabs("42", true))
                .isInstanceOf(IllegalStateException.class).hasMessage("Lab version chain could not be determined");
    }

    @Test
    @DisplayName("should return the requested report as its own chain when the lookup finds no rows")
    void shouldReturnRequestedReport_whenTheLookupFindsNothing() {
        // A report with no accession number matches nothing, yet remains its own version:
        // the page renders its acknowledgement status from this chain, and an empty one hides it.
        daoReturns(42);

        assertThat(new MDSResultsData().getMatchingCMLLabs("42")).isEqualTo("42");
        assertThat(new MDSResultsData().getMatchingCMLLabs("42", true)).isEqualTo("42");
    }

    @Test
    @DisplayName("should return only the requested report when the lookup could not place it among its siblings")
    void shouldReturnRequestedReportAlone_whenTheLookupOmitsIt() {
        // Defensive guard against an inconsistent lookup: a sibling must never replace the selected report.
        // Missing headers are now retained by the DAO and validated separately.
        daoReturns(42, row(41, "20260831", "20260901"));

        assertThat(new MDSResultsData().getMatchingCMLLabs("42")).isEqualTo("42");
    }

    @Test
    @DisplayName("should refuse to resolve a chain for a mutation when the lookup could not place the requested report")
    void shouldRefuseChain_whenResolvingForMutationAndTheLookupOmitsTheRequestedReport() {
        // A sibling exists but the requested report was not placed: matching or routing the sibling alone would
        // act on another report than the one that was opened, and acting on the requested report alone would
        // leave the order's other versions behind.
        daoReturns(42, row(41, "20260831", "20260901"));

        MDSResultsData data = new MDSResultsData();
        assertThatThrownBy(() -> data.getMatchingCMLLabs("42", true))
                .isInstanceOf(IllegalStateException.class).hasMessage("Lab version chain could not be determined");
    }

    @Test
    @DisplayName("should resolve the chain for a mutation without raising the lookup failure")
    void shouldResolveChain_whenResolvingForMutation() {
        // The strict form is called by MrpRoutingService; it used to rethrow the cast as an IllegalStateException.
        daoReturns(42, row(41, "20260831", "20260901"), row(42, "20260901", "20260901"));

        assertThat(new MDSResultsData().getMatchingCMLLabs("42", true)).isEqualTo("41,42");
    }

    @Test
    @DisplayName("should not log an error when the versions resolve")
    void shouldNotLogError_whenVersionsResolve() {
        daoReturns(42, row(41, "20260831", "20260901"), row(42, "20260901", "20260901"));

        try (LogCapture capture = LogCapture.forLogger(MDSResultsData.class)) {
            new MDSResultsData().getMatchingCMLLabs("42");

            assertThat(errorsLogged(capture)).isEmpty();
        }
    }

    @Test
    @DisplayName("should warn once without naming a report or a date when candidates are left out for an unreadable date")
    void shouldWarnOnceWithoutIdentifiers_whenCandidatesAreLeftOut() {
        daoReturns(42, row(7741, "not-a-date", "20260901"), row(7742, "20261301", "20260901"),
                row(42, "20260901", "20260901"));

        try (LogCapture capture = LogCapture.forLogger(MDSResultsData.class)) {
            new MDSResultsData().getMatchingCMLLabs("42");

            assertThat(capture.events()).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(1);
            // None of the report numbers (left out or selected) and none of the dates (unreadable or selected).
            assertThat(capture.messages()).noneMatch(message -> message.contains("7741") || message.contains("7742")
                    || message.contains("42") || message.contains("not-a-date") || message.contains("20261301")
                    || message.contains("20260901"));
        }
    }

    @Test
    @DisplayName("should fall back to the requested report and log the failure when the lookup itself fails")
    void shouldFallBackToRequestedReport_whenLookupFails() {
        when(dao.findLabServiceDatesByLabId(42)).thenThrow(new IllegalStateException("fixture lookup failure"));

        try (LogCapture capture = LogCapture.forLogger(MDSResultsData.class)) {
            assertThat(new MDSResultsData().getMatchingCMLLabs("42")).isEqualTo("42");

            assertThat(errorsLogged(capture)).hasSize(1);
        }
    }

    @Test
    @DisplayName("should still propagate a lookup failure when resolving for a mutation")
    void shouldPropagateLookupFailure_whenResolvingForMutation() {
        when(dao.findLabServiceDatesByLabId(42)).thenThrow(new IllegalStateException("fixture lookup failure"));

        MDSResultsData data = new MDSResultsData();
        assertThatThrownBy(() -> data.getMatchingCMLLabs("42", true))
                .isInstanceOf(IllegalStateException.class).hasMessage("Lab version chain could not be determined");
    }
    @ParameterizedTest
    @CsvSource({"America/Havana,20260308,20260708", "America/Santiago,20260906,20270106", "UTC,20260131,20260528"})
    void shouldExcludeExactFourMonthBoundary_whenMidnightSkipsOrMonthEndsClamp(String zone, String first, String last) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            daoReturns(42, row(41, first, last), row(42, last, last));
            assertThat(new MDSResultsData().getMatchingCMLLabs("42", true)).isEqualTo("42");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void shouldRefuseAllMutations_whenSourcePatientIdentityConflicts() {
        Object[] sibling = row(41, "20260831", "20260901");
        ((LabPatientPhysicianInfo) sibling[3]).setPatientHin("OTHER-HIN");
        daoReturns(42, sibling, row(42, "20260901", "20260901"));
        var data = new MDSResultsData();
        assertThat(data.getCmlVersionChain("42")).isEqualTo(new MDSResultsData.CmlVersionChain("42", false));
        assertThatThrownBy(() -> data.getMatchingCMLLabs("42", true)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPermitNameOrDobCorrection_whenSourceHinAndOtherFieldMatch(boolean correctName) {
        Object[] sibling = row(41, "20260831", "20260901");
        if (correctName) ((LabPatientPhysicianInfo) sibling[3]).setPatientFirstName("Corrected");
        else ((LabPatientPhysicianInfo) sibling[3]).setPatientDob("19810101");
        daoReturns(42, sibling, row(42, "20260901", "20260901"));
        assertThat(new MDSResultsData().getMatchingCMLLabs("42", true)).isEqualTo("41,42");
    }

    @Test
    void shouldUseNameAndDob_whenSourceHinIsMissing() {
        Object[] sibling = row(41, "20260831", "20260901");
        ((LabPatientPhysicianInfo) sibling[3]).setPatientHin(null);
        ((LabPatientPhysicianInfo) sibling[3]).setPatientFirstName(" TEST ");
        daoReturns(42, sibling, row(42, "20260901", "20260901"));
        assertThat(new MDSResultsData().getMatchingCMLLabs("42", true)).isEqualTo("41,42");
        ((LabPatientPhysicianInfo) sibling[3]).setPatientDob(null);
        assertThatThrownBy(() -> new MDSResultsData().getMatchingCMLLabs("42", true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRefusePartialChain_whenCandidatePrintHeaderIsMissing() {
        Object[] sibling = row(41, "20260831", "20260901");
        sibling[5] = null;
        sibling[6] = null;
        daoReturns(42, sibling, row(42, "20260901", "20260901"));
        assertThat(new MDSResultsData().getCmlVersionChain("42").reliable()).isFalse();
        assertThatThrownBy(() -> new MDSResultsData().getMatchingCMLLabs("42", true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRefuseSharedPlaceholderHin_whenNamesAndDobDiffer() {
        Object[] sibling = row(41, "20260831", "20260901");
        ((LabPatientPhysicianInfo) sibling[3]).setPatientFirstName("Different");
        ((LabPatientPhysicianInfo) sibling[3]).setPatientDob("19810101");
        daoReturns(42, sibling, row(42, "20260901", "20260901"));
        assertThatThrownBy(() -> new MDSResultsData().getMatchingCMLLabs("42", true)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({"20260230,09:30", "2026-09-01,09:30", "20260901,24:00", "20260901,9:30"})
    void shouldRefuseUnreliablePrintOrder_whenHeaderValuesAreNotCanonicalDatesAndTimes(String date, String time) {
        Object[] sibling = row(41, "20260831", "20260901");
        sibling[5] = date;
        sibling[6] = time;
        daoReturns(42, sibling, row(42, "20260901", "20260901"));
        assertThat(new MDSResultsData().getCmlVersionChain("42").reliable()).isFalse();
        assertThatThrownBy(() -> new MDSResultsData().getMatchingCMLLabs("42", true)).isInstanceOf(IllegalStateException.class);
    }

}
