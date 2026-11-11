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
import org.junit.jupiter.api.AfterEach;
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
    private DemographicManager originalManager;
    private DemographicArchiveDao originalArchiveDao;

    @BeforeEach
    void replaceDependencies() {
        DemographicManager demographicManager = createAndRegisterMock(DemographicManager.class);
        DemographicArchiveDao archiveDao = createAndRegisterMock(DemographicArchiveDao.class);
        // PreventionReportUtil resolves both in its static initializer, which may run here
        // (answered by the mocks above) or may already have run in another test.
        originalManager = PreventionReportUtil.demographicManager;
        originalArchiveDao = PreventionReportUtil.demographicArchiveDao;
        PreventionReportUtil.demographicManager = demographicManager;
        PreventionReportUtil.demographicArchiveDao = archiveDao;

        Demographic current = new Demographic();
        current.setRosterDate(day(2025, 2, 1));
        current.setRosterTerminationDate(null);
        current.setRosterEnrolledTo(CURRENT_PROVIDER);
        current.setProviderNo(CURRENT_PROVIDER);
        when(demographicManager.getDemographic(same(loggedInInfo), eq(DEMOGRAPHIC_NO))).thenReturn(current);

        DemographicArchive archived = new DemographicArchive();
        archived.setRosterDate(day(2023, 1, 1));
        archived.setRosterTerminationDate(day(2024, 12, 31));
        archived.setRosterEnrolledTo(ARCHIVED_ENROLLED_TO);
        archived.setProviderNo(ARCHIVED_PROVIDER);
        when(archiveDao.findByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(List.of(archived));
    }

    @AfterEach
    void restoreDependencies() {
        PreventionReportUtil.demographicManager = originalManager;
        PreventionReportUtil.demographicArchiveDao = originalArchiveDao;
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
