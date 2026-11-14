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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.dao.DataAccessResourceFailureException;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;
import io.github.carlos_emr.carlos.prevention.dto.PreventionSourceUnavailableException;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionReportDisplay;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * A clinic-wide prevention report reads one chart after another. A chart whose preventions cannot
 * be read must stay in the report, marked as unreadable and counted, and every other patient must
 * still be listed: the report is neither aborted nor quietly shortened.
 */
@Tag("unit")
@DisplayName("Prevention reports with a patient whose preventions cannot be read")
class PreventionReportUnreadablePatientUnitTest extends CarlosUnitTestBase {

    private static final int UP_TO_DATE = 101;
    private static final int UNREADABLE = 102;
    private static final int NO_HISTORY = 103;

    private final LoggedInInfo user = new LoggedInInfo();
    private final PreventionDao preventionDao = mock(PreventionDao.class);
    private final PartialDateDao partialDateDao = mock(PartialDateDao.class);
    private final DemographicManager demographicManager = mock(DemographicManager.class);
    private final Demographic senior = senior();

    private Object savedPreventionDao;
    private Object savedPartialDateDao;
    private MockedConstruction<DemographicData> demographicData;
    private MockedConstruction<EctMeasurementsDataBeanHandler> followUps;

    @BeforeEach
    void setUp() throws Exception {
        // In case this test is the first to load PreventionData, which resolves its DAOs then.
        registerMock(PreventionDao.class, preventionDao);
        registerMock(PreventionExtDao.class, mock(PreventionExtDao.class));
        registerMock(PartialDateDao.class, partialDateDao);
        registerMock(DemographicManager.class, demographicManager);
        savedPreventionDao = swapPreventionDataField("preventionDao", preventionDao);
        savedPartialDateDao = swapPreventionDataField("partialDateDao", partialDateDao);
        when(demographicManager.getDemographic(eq(user), any(Integer.class))).thenReturn(senior);

        demographicData = mockConstruction(DemographicData.class,
                (data, context) -> when(data.getDemographic(eq(user), anyString())).thenReturn(senior));
        followUps = mockConstruction(EctMeasurementsDataBeanHandler.class,
                (handler, context) -> when(handler.getMeasurementsDataVector()).thenReturn(List.of()));
    }

    @AfterEach
    void restore() throws Exception {
        followUps.close();
        demographicData.close();
        swapPreventionDataField("preventionDao", savedPreventionDao);
        swapPreventionDataField("partialDateDao", savedPartialDateDao);
    }

    @Test
    @DisplayName("Flu report should mark and count the unreadable patient and still list the others")
    void shouldMarkAndCountTheUnreadablePatient_whileListingEveryOtherPatient() throws Exception {
        // The real readers run against a prevention table that fails for one chart only.
        Prevention shot = fluShot(9001, "2026-10-10");
        when(preventionDao.findByTypeAndDemoNo("Flu", UP_TO_DATE)).thenReturn(List.of(shot));
        when(preventionDao.findByTypeAndDemoNo("Flu", UNREADABLE))
                .thenThrow(new DataAccessResourceFailureException("synthetic source failure"));
        when(preventionDao.findByTypeAndDemoNo("Flu", NO_HISTORY)).thenReturn(List.of());

        Hashtable<String, Object> report = new FluReport().runReport(user, patients(), day("2026-11-01"));

        List<PreventionReportDisplay> rows = rows(report);
        assertThat(rows).extracting(row -> row.demographicNo)
                .containsExactlyInAnyOrder(UP_TO_DATE, UNREADABLE, NO_HISTORY);
        PreventionReportDisplay unreadable = row(rows, UNREADABLE);
        assertThat(unreadable.isUnreadable()).isTrue();
        assertThat(unreadable.state).isEqualTo(PreventionReportDisplay.STATE_UNREADABLE);
        // Nothing is known about this patient: no bonus, no bill, no recall letter.
        assertThat(unreadable.bonusStatus).isEqualTo("N");
        assertThat(unreadable.billStatus).isEqualTo("N");
        assertThat(unreadable.nextSuggestedProcedure).isEqualTo("----");
        assertThat(unreadable.lastDate).isEqualTo("------");
        // Sorted ahead of every computed state, so the row is the first thing on the page.
        assertThat(rows.get(0)).isSameAs(unreadable);

        assertThat(row(rows, UP_TO_DATE).state).isEqualTo("Up to date");
        assertThat(row(rows, UP_TO_DATE).isUnreadable()).isFalse();
        assertThat(row(rows, NO_HISTORY).state).isEqualTo("No Info");
        assertThat(row(rows, NO_HISTORY).nextSuggestedProcedure).isEqualTo("L1");

        assertThat(report)
                .containsEntry(PreventionReport.UNREADABLE_COUNT, "1")
                .containsEntry("up2date", "1")
                .containsEntry("inEligible", "0")
                // The unreadable patient stays in the denominator: counted as not up to date.
                .containsEntry("percent", "33");
    }

    @Test
    @DisplayName("Flu report should report no unreadable patients when every chart is read")
    void shouldCountNoUnreadablePatients_whenEveryChartIsRead() throws Exception {
        when(preventionDao.findByTypeAndDemoNo(eq("Flu"), any(Integer.class))).thenReturn(List.of());

        Hashtable<String, Object> report = new FluReport().runReport(user, patients(), day("2026-11-01"));

        assertThat(report).containsEntry(PreventionReport.UNREADABLE_COUNT, "0");
        assertThat(rows(report)).hasSize(3).noneMatch(PreventionReportDisplay::isUnreadable);
    }

    @Test
    @DisplayName("Flu report should still refuse a prevention date that needs review")
    void shouldPassTheDateReviewRefusalThrough_insteadOfMarkingThePatientUnreadable() {
        when(preventionDao.findByTypeAndDemoNo(eq("Flu"), any(Integer.class)))
                .thenThrow(new PreventionDatePrecisionException());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new FluReport().runReport(user, patients(), day("2026-11-01")))
                .isExactlyInstanceOf(PreventionDatePrecisionException.class);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "PAP,PAP",
        "Mammogram,MAM",
        "FOBT,FOBT",
        "FOBT,COLONOSCOPY",
        "ChildImmunizations,DTaP-IPV",
        "ChildImmunizations,MMRV"
    })
    @DisplayName("every clinic report should keep an unreadable patient, marked and counted")
    void shouldKeepTheUnreadablePatientInEveryReport_whenOneOfItsReadsFails(String reportName, String failingType)
            throws Exception {
        try (MockedStatic<PreventionData> data = mockStatic(PreventionData.class)) {
            data.when(() -> PreventionData.getExactPreventionData(eq(user), anyString(), any(Integer.class)))
                    .thenAnswer(read -> new ArrayList<Map<String, Object>>());
            data.when(() -> PreventionData.getExactPreventionData(user, failingType, UNREADABLE))
                    .thenThrow(new PreventionSourceUnavailableException(
                            new DataAccessResourceFailureException("synthetic source failure")));

            Hashtable<?, ?> report = PreventionReportFactory.getPreventionReport(reportName)
                    .runReport(user, patients(), day("2026-11-01"));

            @SuppressWarnings("unchecked")
            List<PreventionReportDisplay> rows = (List<PreventionReportDisplay>) report.get("returnReport");
            assertThat(rows).extracting(row -> row.demographicNo)
                    .containsExactlyInAnyOrder(UP_TO_DATE, UNREADABLE, NO_HISTORY);
            assertThat(row(rows, UNREADABLE).isUnreadable()).isTrue();
            assertThat(row(rows, UNREADABLE).nextSuggestedProcedure).isEqualTo("----");
            assertThat(rows.get(0)).isSameAs(row(rows, UNREADABLE));
            assertThat(row(rows, UP_TO_DATE).state).isEqualTo("No Info");
            assertThat(row(rows, NO_HISTORY).state).isEqualTo("No Info");
            assertThat(report.get(PreventionReport.UNREADABLE_COUNT)).isEqualTo("1");
        }
    }

    private static ArrayList<ArrayList<String>> patients() {
        ArrayList<ArrayList<String>> patients = new ArrayList<>();
        for (int demographicNo : new int[] {UP_TO_DATE, UNREADABLE, NO_HISTORY}) {
            patients.add(new ArrayList<>(List.of(String.valueOf(demographicNo))));
        }
        return patients;
    }

    @SuppressWarnings("unchecked")
    private static List<PreventionReportDisplay> rows(Hashtable<String, Object> report) {
        return (List<PreventionReportDisplay>) report.get("returnReport");
    }

    private static PreventionReportDisplay row(List<PreventionReportDisplay> rows, int demographicNo) {
        return rows.stream().filter(row -> row.demographicNo == demographicNo).findFirst().orElseThrow();
    }

    private static Date day(String isoDate) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd").parse(isoDate);
    }

    private static Prevention fluShot(int id, String isoDate) throws Exception {
        Prevention shot = mock(Prevention.class);
        when(shot.getId()).thenReturn(id);
        when(shot.getPreventionType()).thenReturn("Flu");
        when(shot.getPreventionDate()).thenReturn(day(isoDate));
        return shot;
    }

    private static Demographic senior() {
        Demographic demographic = new Demographic();
        demographic.setYearOfBirth("1940");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("01");
        return demographic;
    }

    private static Object swapPreventionDataField(String name, Object value) throws Exception {
        Field field = PreventionData.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }
}
