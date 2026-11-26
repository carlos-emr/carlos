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
package io.github.carlos_emr.carlos.prevention.reports;

import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicArchive;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;

/**
 * Roster history ("as of") checks (#5877). A patient rostered to one provider during an archived
 * period and to another today must count for the archived period's provider on a date inside
 * that period, and not for today's provider. The prevention report uses
 * {@code wasEnrolledToThisProvider} (the roster's enrolled-to provider); the demographic "as of"
 * queries use {@code wasRosteredToThisProvider} (the patient's provider). Patients and provider
 * numbers are FAKE.
 */
@DisplayName("PreventionReportUtil roster history")
@Tag("unit")
@Tag("prevention")
class PreventionReportUtilUnitTest extends CarlosUnitTestBase {

    private static final Integer DEMOGRAPHIC_NO = 90577;
    private static final String ARCHIVED_ENROLLED_TO = "990101";
    private static final String ARCHIVED_PROVIDER = "990102";
    private static final String CURRENT_PROVIDER = "990103";
    private static final String THIRD_PROVIDER = "990104";
    /** Inside the archived roster period (2023-01-01 to 2024-12-31), before the current one. */
    private static final Date IN_ARCHIVED_PERIOD = day(2024, 6, 15);
    /** Inside the current, open roster period (from 2025-02-01). */
    private static final Date IN_CURRENT_PERIOD = day(2025, 6, 15);

    private final LoggedInInfo loggedInInfo = new LoggedInInfo();
    private DemographicArchiveDao archiveDao;
    private Demographic current;
    private DemographicArchive archived;

    @BeforeEach
    void registerDependencies() {
        // PreventionReportUtil looks both up through SpringUtils on each use, so the base class's
        // per-test SpringUtils mock answers with these and nothing outlives the test.
        DemographicManager demographicManager = createAndRegisterMock(DemographicManager.class);
        archiveDao = createAndRegisterMock(DemographicArchiveDao.class);

        current = new Demographic();
        current.setRosterDate(day(2025, 2, 1));
        current.setRosterTerminationDate(null);
        current.setRosterEnrolledTo(CURRENT_PROVIDER);
        current.setProviderNo(CURRENT_PROVIDER);
        when(demographicManager.getDemographic(same(loggedInInfo), eq(DEMOGRAPHIC_NO))).thenReturn(current);

        archived = new DemographicArchive();
        archived.setRosterDate(day(2023, 1, 1));
        archived.setRosterTerminationDate(day(2024, 12, 31));
        archived.setRosterEnrolledTo(ARCHIVED_ENROLLED_TO);
        archived.setProviderNo(ARCHIVED_PROVIDER);
        when(archiveDao.findByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(List.of(archived));
    }

    private static Date day(int year, int month, int dayOfMonth) {
        return Date.from(LocalDate.of(year, month, dayOfMonth).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    @DisplayName("should count the patient for the archived period's enrolled-to provider")
    void shouldCountArchivedEnrolledToProvider_whenTheRosterHasSinceMoved() {
        assertThat(PreventionReportUtil.wasEnrolledToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_ENROLLED_TO)).isTrue();
    }

    @Test
    @DisplayName("should not count the patient for today's enrolled-to provider in an archived period")
    void shouldNotCountCurrentEnrolledToProvider_forADateInTheArchivedPeriod() {
        assertThat(PreventionReportUtil.wasEnrolledToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, CURRENT_PROVIDER)).isFalse();
    }

    @Test
    @DisplayName("should count the patient for the archived period's provider")
    void shouldCountArchivedProvider_whenThePatientHasSinceMoved() {
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_PROVIDER)).isTrue();
    }

    @Test
    @DisplayName("should check archived and current periods against the complete provider selection")
    void shouldAcceptAnySelectedProvider_inThePeriodCoveringTheDate() {
        Set<String> selected = Set.of(CURRENT_PROVIDER, ARCHIVED_PROVIDER);
        assertThat(PreventionReportUtil.wasRosteredToAnyProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, selected)).isTrue();
        assertThat(PreventionReportUtil.wasRosteredToAnyProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_CURRENT_PERIOD, selected)).isTrue();
        assertThat(PreventionReportUtil.wasRosteredToAnyProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, Set.of())).isFalse();
    }

    @Test
    @DisplayName("should leave periods without a provider out of the selection")
    void shouldIgnorePeriodsWithoutProviders() {
        current.setProviderNo(null);
        current.setLastUpdateDate(null);
        archived.setProviderNo(null);

        assertThat(PreventionReportUtil.wasRosteredToAnyProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, Set.of(ARCHIVED_PROVIDER))).isFalse();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, null)).isFalse();
        current.setLastUpdateDate(day(2025, 3, 1));
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_CURRENT_PERIOD, CURRENT_PROVIDER)).isFalse();
    }

    @Test
    @DisplayName("should use the assigned provider on either side of an MRP change without a roster termination")
    void shouldUseTheProviderVersionEffectiveOnTheReportDate() {
        Date change = day(2025, 2, 1);
        current.setRosterDate(archived.getRosterDate());
        current.setLastUpdateDate(change);
        archived.setLastUpdateDate(day(2023, 1, 1));
        archived.setRosterTerminationDate(null);

        assertThat(PreventionReportUtil.wasRosteredToAnyProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, Set.of(ARCHIVED_PROVIDER))).isTrue();
        assertThat(PreventionReportUtil.wasRosteredToAnyProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, Set.of(CURRENT_PROVIDER))).isFalse();
        assertThat(PreventionReportUtil.wasRosteredToAnyProvider(
                loggedInInfo, DEMOGRAPHIC_NO, change, Set.of(CURRENT_PROVIDER))).isTrue();
        assertThat(PreventionReportUtil.wasRosteredToAnyProvider(
                loggedInInfo, DEMOGRAPHIC_NO, change, Set.of(ARCHIVED_PROVIDER))).isFalse();
    }

    @Test
    @DisplayName("should omit conflicting provider assignments with the same revision and open roster")
    void shouldLeaveOutAmbiguousProviderChanges_whenLiveAndArchiveShareARevision() {
        Date revision = day(2023, 2, 1);
        current.setRosterDate(archived.getRosterDate());
        current.setLastUpdateDate(revision);
        archived.setLastUpdateDate(revision);
        archived.setRosterTerminationDate(null);

        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, CURRENT_PROVIDER)).isFalse();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_PROVIDER)).isFalse();

        archived.setRosterTerminationDate(day(2024, 1, 1));
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, CURRENT_PROVIDER)).isTrue();

        archived.setRosterTerminationDate(null);
        archived.setProviderNo(CURRENT_PROVIDER);
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, CURRENT_PROVIDER)).isTrue();

        archived.setProviderNo(ARCHIVED_PROVIDER);
        archived.setLastUpdateDate(null);
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, CURRENT_PROVIDER)).isTrue();
    }

    @Test
    @DisplayName("should leave the provider unresolved when archived versions with different providers share a timestamp")
    void shouldLeaveProviderUnresolved_whenArchivedVersionsShareATimestamp() {
        current.setLastUpdateDate(day(2025, 2, 1));
        archived.setId(4L);
        archived.setLastUpdateDate(day(2023, 2, 1));
        var replacement = new DemographicArchive();
        replacement.setId(5L);
        replacement.setLastUpdateDate(archived.getLastUpdateDate());
        replacement.setRosterDate(archived.getRosterDate());
        replacement.setProviderNo(CURRENT_PROVIDER);
        when(archiveDao.findByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(List.of(replacement, archived));

        // The archive ID orders archiving, not the unrecorded moment the provider changed.
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, CURRENT_PROVIDER)).isFalse();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_PROVIDER)).isFalse();
    }

    @Test
    @DisplayName("should credit neither earlier provider after a legacy A-to-B change and a stamped B-to-C change")
    void shouldLeaveLegacyChainUnresolved_whenAAndBShareTheirTimestamp() {
        // A -> B saved before reassignments were stamped, so B kept A's timestamp; B -> C was stamped.
        Date legacyRevision = day(2023, 2, 1);
        archived.setId(4L);
        archived.setRosterTerminationDate(null);
        archived.setLastUpdateDate(legacyRevision);
        var providerB = new DemographicArchive();
        providerB.setId(5L);
        providerB.setRosterDate(archived.getRosterDate());
        providerB.setLastUpdateDate(legacyRevision);
        providerB.setProviderNo(THIRD_PROVIDER);
        current.setRosterDate(archived.getRosterDate());
        current.setLastUpdateDate(day(2025, 2, 1));
        when(archiveDao.findByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(List.of(archived, providerB));

        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_PROVIDER)).as("A").isFalse();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, THIRD_PROVIDER)).as("B").isFalse();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, day(2025, 6, 15), CURRENT_PROVIDER)).as("C after its stamped change").isTrue();
    }

    @Test
    @DisplayName("should ignore undated, differently timed and non-covering archives when checking a timestamp tie")
    void shouldCreditProvider_whenNoOtherArchiveCompetesAtItsTimestamp() {
        current.setLastUpdateDate(day(2025, 2, 1));
        archived.setId(4L);
        archived.setLastUpdateDate(day(2023, 2, 1));
        var undated = new DemographicArchive();
        undated.setId(1L);
        undated.setRosterDate(archived.getRosterDate());
        undated.setProviderNo(THIRD_PROVIDER);
        var earlier = new DemographicArchive();
        earlier.setId(2L);
        earlier.setLastUpdateDate(day(2022, 1, 1));
        earlier.setRosterDate(archived.getRosterDate());
        earlier.setProviderNo(THIRD_PROVIDER);
        var sameTimeOtherPeriod = new DemographicArchive();
        sameTimeOtherPeriod.setId(5L);
        sameTimeOtherPeriod.setLastUpdateDate(archived.getLastUpdateDate());
        sameTimeOtherPeriod.setRosterDate(day(2020, 1, 1));
        sameTimeOtherPeriod.setRosterTerminationDate(day(2021, 12, 31));
        sameTimeOtherPeriod.setProviderNo(THIRD_PROVIDER);
        when(archiveDao.findByDemographicNo(DEMOGRAPHIC_NO))
                .thenReturn(List.of(undated, earlier, sameTimeOtherPeriod, archived));

        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_PROVIDER)).isTrue();

        archived.setRosterTerminationDate(day(2024, 1, 1));
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, THIRD_PROVIDER)).isFalse();
    }

    @Test
    @DisplayName("should still credit the provider when archived versions sharing a timestamp agree on it")
    void shouldCreditProvider_whenArchivedVersionsShareATimestampAndProvider() {
        current.setLastUpdateDate(day(2025, 2, 1));
        archived.setId(4L);
        archived.setLastUpdateDate(day(2023, 2, 1));
        var sameProvider = new DemographicArchive();
        sameProvider.setId(5L);
        sameProvider.setLastUpdateDate(archived.getLastUpdateDate());
        sameProvider.setRosterDate(archived.getRosterDate());
        sameProvider.setRosterTerminationDate(archived.getRosterTerminationDate());
        sameProvider.setProviderNo(ARCHIVED_PROVIDER);
        when(archiveDao.findByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(List.of(sameProvider, archived));

        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_PROVIDER)).isTrue();
    }

    @Test
    @DisplayName("should use an unambiguous undated open archive before a dated current revision")
    void shouldUseLegacyOpenArchive_whenItIdentifiesOnePriorProvider() {
        current.setLastUpdateDate(day(2025, 2, 1));
        archived.setRosterTerminationDate(null);

        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_PROVIDER)).isTrue();

        var another = new DemographicArchive();
        another.setRosterDate(archived.getRosterDate());
        another.setProviderNo(CURRENT_PROVIDER);
        when(archiveDao.findByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(List.of(archived, another));
        assertThat(PreventionReportUtil.wasRosteredToAnyProvider(loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD,
                Set.of(CURRENT_PROVIDER, ARCHIVED_PROVIDER))).isFalse();
    }

    @Test
    @DisplayName("should use an undated predecessor until the effective date of a later archived assignment")
    void shouldUseUndatedPredecessor_beforeALaterDatedArchive() {
        current.setLastUpdateDate(day(2025, 2, 1));
        archived.setRosterTerminationDate(null);
        var later = new DemographicArchive();
        later.setLastUpdateDate(day(2024, 9, 1));
        later.setProviderNo(THIRD_PROVIDER);
        later.setRosterDate(archived.getRosterDate());
        later.setRosterTerminationDate(day(2024, 12, 31));
        when(archiveDao.findByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(List.of(archived, later));

        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_PROVIDER)).isTrue();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, THIRD_PROVIDER)).isFalse();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, day(2024, 9, 1), THIRD_PROVIDER)).isTrue();
    }

    @Test
    @DisplayName("should omit an undated reassignment when its effective date cannot be determined")
    void shouldLeaveOutAmbiguousUnversionedOpenPeriods() {
        current.setRosterDate(archived.getRosterDate());
        current.setLastUpdateDate(null);
        archived.setRosterTerminationDate(null);

        assertThat(PreventionReportUtil.wasRosteredToAnyProvider(loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD,
                Set.of(CURRENT_PROVIDER, ARCHIVED_PROVIDER))).isFalse();
    }

    @Test
    @DisplayName("should use the recorded roster when there is no prior version history")
    void shouldUseCurrentRoster_whenNoArchivesExist() {
        current.setRosterDate(archived.getRosterDate());
        current.setLastUpdateDate(day(2025, 2, 1));
        when(archiveDao.findByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(List.of());

        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, CURRENT_PROVIDER)).isTrue();

        archived.setLastUpdateDate(day(2024, 9, 1));
        archived.setRosterTerminationDate(null);
        when(archiveDao.findByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(List.of(archived));
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, CURRENT_PROVIDER)).isFalse();
    }

    @Test
    @DisplayName("should accept a recorded roster only within its dates")
    void shouldRespectRosterBounds_forCurrentAndArchivedRecords() {
        Date beforeRoster = day(2022, 6, 15);
        assertThat(PreventionReportUtil.wasRostered(loggedInInfo, DEMOGRAPHIC_NO, IN_CURRENT_PERIOD)).isTrue();
        assertThat(PreventionReportUtil.wasRostered(loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD)).isTrue();
        assertThat(PreventionReportUtil.wasRostered(loggedInInfo, DEMOGRAPHIC_NO, beforeRoster)).isFalse();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, beforeRoster, CURRENT_PROVIDER)).isFalse();
        current.setLastUpdateDate(day(2022, 1, 1));
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, beforeRoster, CURRENT_PROVIDER)).isFalse();
    }

    @Test
    @DisplayName("should not count the patient for today's provider in an archived period")
    void shouldNotCountCurrentProvider_forADateInTheArchivedPeriod() {
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, CURRENT_PROVIDER)).isFalse();
    }

    @Test
    @DisplayName("should read each check's own archived field")
    void shouldKeepEnrolledToAndProviderApart_forTheArchivedPeriod() {
        assertThat(PreventionReportUtil.wasEnrolledToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_PROVIDER)).isFalse();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_ENROLLED_TO)).isFalse();
    }

    @Test
    @DisplayName("should still use today's record for a date in the current roster period")
    void shouldCountCurrentProvider_forADateInTheCurrentPeriod() {
        assertThat(PreventionReportUtil.wasEnrolledToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_CURRENT_PERIOD, CURRENT_PROVIDER)).isTrue();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_CURRENT_PERIOD, CURRENT_PROVIDER)).isTrue();
        assertThat(PreventionReportUtil.wasEnrolledToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_CURRENT_PERIOD, ARCHIVED_ENROLLED_TO)).isFalse();
    }
}
