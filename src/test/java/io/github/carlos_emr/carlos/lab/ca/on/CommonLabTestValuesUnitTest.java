/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.on;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("lab")
class CommonLabTestValuesUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldPreserveSourceIdentityOrderAndUnits_whenMeasurementsHaveRepeatedValues() {
        var dao = createAndRegisterMock(MeasurementDao.class);
        when(dao.findMeasurementsWithIdentifiersByDemographicIdAndLocationCode(17, "FAKE-code"))
                .thenReturn(List.of(new Object[]{"1.2 café", "2026-01-02", 100, "N", "mmol/L"},
                                   new Object[]{"1.2 café", "2026-01-02", 200, "N", "mmol/L"}));
        var rows = CommonLabTestValues.findValuesByLoinc2("17", "FAKE-code");
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).containsEntry("lab_no", "100").containsEntry("result", "1.2 café")
                .containsEntry("units", "mmol/L").containsEntry("date", "2026-01-02");
        assertThat(rows.getLast()).containsEntry("lab_no", "200");
        verify(dao, times(1)).findMeasurementsWithIdentifiersByDemographicIdAndLocationCode(17, "FAKE-code");
    }

    @Test
    void shouldReturnNoInventedResult_whenNoMeasurementMatches() {
        var dao = createAndRegisterMock(MeasurementDao.class);
        when(dao.findMeasurementsWithIdentifiersByDemographicIdAndLocationCode(17, "FAKE-missing")).thenReturn(List.of());
        assertThat(CommonLabTestValues.findValuesByLoinc2("17", "FAKE-missing")).isEmpty();
    }
    @Test
    void shouldKeepMissingUnitsAndUndatedCmlResults_visibleWithoutFabricatingDates() {
        var dao = createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao.class);
        var routing = new io.github.carlos_emr.carlos.commn.model.PatientLabRouting(); routing.setLabNo(123);
        var test = new io.github.carlos_emr.carlos.commn.model.LabTestResults();
        test.setTestName("CREATININE"); test.setResult("80"); test.setUnits(null);
        var dated = new io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo();
        dated.setAccessionNum("same-accession"); dated.setCollectionDate("01-Jan-26");
        var undated = new io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo();
        undated.setAccessionNum("same-accession"); undated.setCollectionDate(null);
        when(dao.findRoutingsAndTests(17, "CML", "CREATININE")).thenReturn(List.of(
                new Object[]{routing, test, undated}, new Object[]{routing, test, dated}));
        var rows = CommonLabTestValues.findValuesForTest("CML", 17, "CREATININE");
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().get("collDateDate")).isNull();
        assertThat(rows.getFirst().get("units")).isNull();
        assertThat(rows.getFirst().get("result")).isEqualTo("80");
        assertThat(rows.getLast().get("collDateDate")).isInstanceOf(java.util.Date.class);
    }

    /**
     * One CML accession seen several times: a later collection date replaces the earlier row, an older one
     * is skipped, and an undated row after a dated one stays visible on its own, ahead of the dated row.
     */
    @Test
    void shouldKeepTheLatestDatedRowAndTheUndatedOne_whenAnAccessionHasSeveralCollectionDates() {
        var dao = createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao.class);
        var routing = new io.github.carlos_emr.carlos.commn.model.PatientLabRouting(); routing.setLabNo(321);
        when(dao.findRoutingsAndTests(17, "CML", "CREATININE")).thenReturn(List.of(
                cmlRow(routing, "70", "01-Jan-26"),
                cmlRow(routing, "75", "15-Jan-26"),
                cmlRow(routing, "90", "01-Dec-25"),
                cmlRow(routing, "60", null)));

        var rows = CommonLabTestValues.findValuesForTest("CML", 17, "CREATININE");

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).containsEntry("result", "60");
        assertThat(rows.getFirst().get("collDateDate")).isNull();
        assertThat(rows.getLast()).containsEntry("result", "75").containsEntry("collDate", "15-Jan-26");
    }

    /** A value history asked for without a patient reads patient 0's records for both lookups. */
    @Test
    void shouldLookUpPatientZero_whenTheHl7HistoryIsAskedForWithoutAPatient() {
        var dao = createAndRegisterMock(MeasurementDao.class);

        assertThat(CommonLabTestValues.findValuesForTest("HL7", null, "FAKE GLUCOSE", "FAKE-3007")).isEmpty();

        verify(dao).findTextResultPositions(0, "FAKE-3007");
        verify(dao).findLabNumbers(0, "FAKE-3007");
    }

    private static Object[] cmlRow(io.github.carlos_emr.carlos.commn.model.PatientLabRouting routing,
                                   String result, String collected) {
        var test = new io.github.carlos_emr.carlos.commn.model.LabTestResults();
        test.setTestName("CREATININE"); test.setResult(result);
        var info = new io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo();
        info.setAccessionNum("FAKE-accession"); info.setCollectionDate(collected);
        return new Object[]{routing, test, info};
    }


    /** #6122: a final word result is filed as text, and the grid, value history and Row Display label it. */
    @Test
    void shouldCarryTheTextMarker_fromBothLoincLookups() {
        var dao = createAndRegisterMock(MeasurementDao.class);
        when(dao.findMeasurementsByDemographicIdAndLocationCode(17, "FAKE-code")).thenReturn(List.of(
                new Object[]{"Pending", "2026-01-02", 100, "N", "text"},
                new Object[]{"5.7", "2026-01-01", 200, "N", null}));
        when(dao.findMeasurementsWithIdentifiersByDemographicIdAndLocationCode(17, "FAKE-code")).thenReturn(List.of(
                new Object[]{"Pending", "2026-01-02", 100, "N", "FAKE-3007", "text"},
                new Object[]{"5.7", "2026-01-01", 200, "N", "FAKE-3007", null}));

        var grid = CommonLabTestValues.findValuesByLoinc("17", "FAKE-code");
        var graph = CommonLabTestValues.findValuesByLoinc2("17", "FAKE-code");

        for (var rows : List.of(grid, graph)) {
            assertThat(rows.get(0)).containsEntry(CommonLabTestValues.VALUE_TYPE, "text");
            assertThat(CommonLabTestValues.isTextResult(rows.get(0))).isTrue();
            assertThat(rows.get(1)).doesNotContainKey(CommonLabTestValues.VALUE_TYPE);
            assertThat(CommonLabTestValues.isTextResult(rows.get(1))).isFalse();
        }
        assertThat(CommonLabTestValues.isTextResult(null)).isFalse();
    }

    @Test
    void shouldLabelOnlyTheReportFiledAsText_inTheHl7ValueHistory() {
        var dao = createAndRegisterMock(MeasurementDao.class);
        when(dao.findLabNumbers(17, "FAKE-3007")).thenReturn(List.of("100", "200"));
        when(dao.findTextResultPositions(17, "FAKE-3007")).thenReturn(List.<Object[]>of(new Object[]{"100", "0-0"}));
        MessageHandler text = handler("2026-01-02 10:00", "Pending");
        MessageHandler number = handler("2026-01-01 10:00", "5.7");

        try (MockedStatic<Factory> factory = mockStatic(Factory.class)) {
            factory.when(() -> Factory.getHandler("100")).thenReturn(text);
            factory.when(() -> Factory.getHandler("200")).thenReturn(number);

            var rows = CommonLabTestValues.findValuesForTest("HL7", 17, "FAKE GLUCOSE", "FAKE-3007");

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsEntry("result", "Pending").containsEntry("lab_no", "100");
            assertThat(CommonLabTestValues.isTextResult(rows.get(0))).isTrue();
            assertThat(rows.get(1)).containsEntry("result", "5.7");
            assertThat(CommonLabTestValues.isTextResult(rows.get(1))).isFalse();
        }
    }

    /**
     * The history shows the first result for the code in each report. When that report also holds another
     * result for the code, the label follows the result filed as text, not the report (#6122 review).
     */
    @Test
    void shouldLabelOnlyTheResultFiledAsText_whenItsReportHoldsTwoResultsForTheCode() {
        var dao = createAndRegisterMock(MeasurementDao.class);
        when(dao.findLabNumbers(17, "FAKE-3007")).thenReturn(List.of("100", "200"));
        // Report 100: a number first, then the text result. Report 200: the text result first, then a number.
        when(dao.findTextResultPositions(17, "FAKE-3007")).thenReturn(List.<Object[]>of(
                new Object[]{"100", "0-1"}, new Object[]{"200", "0-0"}));
        MessageHandler numberFirst = handler("2026-01-01 10:00", "5.7", "Not detected");
        MessageHandler textFirst = handler("2026-01-02 10:00", "Not detected", "5.9");

        try (MockedStatic<Factory> factory = mockStatic(Factory.class)) {
            factory.when(() -> Factory.getHandler("100")).thenReturn(numberFirst);
            factory.when(() -> Factory.getHandler("200")).thenReturn(textFirst);

            var rows = CommonLabTestValues.findValuesForTest("HL7", 17, "FAKE GLUCOSE", "FAKE-3007");

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsEntry("result", "Not detected").containsEntry("lab_no", "200");
            assertThat(CommonLabTestValues.isTextResult(rows.get(0))).isTrue();
            assertThat(rows.get(1)).containsEntry("result", "5.7").containsEntry("lab_no", "100");
            assertThat(CommonLabTestValues.isTextResult(rows.get(1))).as("the number in a report with a text result").isFalse();
        }
    }

    private static MessageHandler handler(String collected, String... results) {
        MessageHandler handler = mock(MessageHandler.class);
        when(handler.getOBRCount()).thenReturn(1);
        when(handler.getOBXCount(0)).thenReturn(results.length);
        for (int j = 0; j < results.length; j++) {
            when(handler.getOBXIdentifier(0, j)).thenReturn("FAKE-3007");
            when(handler.getOBXResult(0, j)).thenReturn(results[j]);
        }
        when(handler.getServiceDate()).thenReturn(collected);
        return handler;
    }

}
