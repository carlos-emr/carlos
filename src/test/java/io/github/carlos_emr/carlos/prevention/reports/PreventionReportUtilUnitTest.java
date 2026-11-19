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
        archived.setProviderNo(null);

        assertThat(PreventionReportUtil.wasRosteredToAnyProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, Set.of(ARCHIVED_PROVIDER))).isFalse();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, null)).isFalse();
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
    @DisplayName("should choose the later archive ID when provider revisions share a timestamp")
    void shouldUseLatestArchive_whenVersionsShareTheSameTimestamp() {
        current.setLastUpdateDate(day(2025, 2, 1));
        archived.setId(4L);
        archived.setLastUpdateDate(day(2023, 2, 1));
        var replacement = new DemographicArchive();
        replacement.setId(5L);
        replacement.setLastUpdateDate(archived.getLastUpdateDate());
        replacement.setRosterDate(archived.getRosterDate());
        replacement.setProviderNo(CURRENT_PROVIDER);
        when(archiveDao.findByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(List.of(replacement, archived));

        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, CURRENT_PROVIDER)).isTrue();
        assertThat(PreventionReportUtil.wasRosteredToThisProvider(
                loggedInInfo, DEMOGRAPHIC_NO, IN_ARCHIVED_PERIOD, ARCHIVED_PROVIDER)).isFalse();
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
