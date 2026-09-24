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

package io.github.carlos_emr.carlos.demographic.pageUtil;

import cds.CareElementsDocument.CareElements;
import cds.NewCategoryDocument.NewCategory;
import cds.OmdCdsDocument;
import cds.PatientRecordDocument.PatientRecord;
import cdsDt.DiabetesComplicationScreening;
import cdsDt.DiabetesComplicationScreening.ExamCode;
import cdsDt.ResidualInformation;

import org.apache.xmlbeans.XmlCalendar;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip coverage for the NRTF / FTLS neurological exam mapping in the OntarioMD CDS
 * export and import.
 *
 * <p>The CDS {@code DiabetesComplicationsScreening} element has a single neurological exam code
 * ({@code 67536-3}) and no free-text slot, so NRTF (128 Hz tuning fork) is exported as that code
 * plus a CARLOS {@code NewCategory} marker naming the screening's ordinal. These tests build
 * records the way {@link DemographicExportAction42Action} does, serialise and re-parse them through
 * XMLBeans, and resolve them the way {@link ImportDemographicDataAction42Action} does.</p>
 *
 * <p>Imported exams are compared <em>in screening order</em>, so a same-day FTLS/NRTF swap (the
 * import restoring the wrong screening as NRTF) fails the assertions even when the resulting set
 * of measurements looks the same.</p>
 *
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("fast")
@Tag("demographic")
class CdsNeurologicalExamUnitTest {

    private static final Path PAGE_UTIL = Path.of("src", "main", "java", "io", "github", "carlos_emr",
            "carlos", "demographic", "pageUtil");

    /** One exported screening: what the importer resolved it to, and the stored value. */
    private record ImportedExam(String type, String dataField) {
    }

    @Test
    @DisplayName("should restore an exported NRTF reading as NRTF with its recorded result")
    void shouldRestoreNrtf_whenExportedRecordIsReimported() throws Exception {
        Export export = new Export();
        export.nrtf(date(2026, 3, 14), "No");

        assertThat(importNeurologicalExams(export.reparse()))
                .containsExactly(new ImportedExam("NRTF", "No"));
    }

    @Test
    @DisplayName("should keep importing FTLS as FTLS with Yes, exactly as before the NRTF split")
    void shouldImportFtlsUnchanged_whenNoNrtfMarkerIsPresent() throws Exception {
        Export export = new Export();
        export.ftls(date(2026, 3, 14));

        OmdCdsDocument reparsed = export.reparse();

        assertThat(reparsed.getOmdCds().getPatientRecord().getNewCategoryArray()).isEmpty();
        assertThat(importNeurologicalExams(reparsed)).containsExactly(new ImportedExam("FTLS", "Yes"));
    }

    @Test
    @DisplayName("should restore the right screening as NRTF when FTLS is exported first on the same day")
    void shouldKeepSameDayExamsApart_whenFtlsIsExportedFirst() throws Exception {
        Export export = new Export();
        Calendar day = date(2026, 5, 2);
        export.ftls(day);
        export.nrtf(day, "No");

        assertThat(importNeurologicalExams(export.reparse())).containsExactly(
                new ImportedExam("FTLS", "Yes"),
                new ImportedExam("NRTF", "No"));
    }

    @Test
    @DisplayName("should restore the right screening as NRTF when NRTF is exported first on the same day")
    void shouldKeepSameDayExamsApart_whenNrtfIsExportedFirst() throws Exception {
        Export export = new Export();
        Calendar day = date(2026, 5, 2);
        export.nrtf(day, "No");
        export.ftls(day);

        assertThat(importNeurologicalExams(export.reparse())).containsExactly(
                new ImportedExam("NRTF", "No"),
                new ImportedExam("FTLS", "Yes"));
    }

    @Test
    @DisplayName("should restore two NRTF readings on the same day with their own results")
    void shouldRestoreEachNrtf_whenTwoShareADate() throws Exception {
        Export export = new Export();
        Calendar day = date(2026, 5, 2);
        export.nrtf(day, "Yes");
        export.ftls(day);
        export.nrtf(day, "NA");
        export.nrtf(date(2025, 11, 20), "No");

        OmdCdsDocument reparsed = export.reparse();

        assertThat(reparsed.getOmdCds().getPatientRecord().getNewCategoryArray())
                .as("one marker category per patient, one residual block per NRTF reading")
                .hasSize(1)
                .allSatisfy(c -> assertThat(c.getResidualInfoArray()).hasSize(3));
        assertThat(importNeurologicalExams(reparsed)).containsExactly(
                new ImportedExam("NRTF", "Yes"),
                new ImportedExam("FTLS", "Yes"),
                new ImportedExam("NRTF", "NA"),
                new ImportedExam("NRTF", "No"));
    }

    @Test
    @DisplayName("should ignore markers that name no exported screening")
    void shouldIgnoreSurplusMarkers_whenMarkersOutnumberScreenings() throws Exception {
        Export export = new Export();
        Calendar day = date(2026, 5, 2);
        export.ftls(day);
        NewCategory marker = export.nrtf(day, "No");
        addRawMarker(marker, "7", "2026-05-02", "Yes");
        addRawMarker(marker, "not-a-number", "2026-05-02", "Yes");
        addRawMarker(marker, "-1", "2026-05-02", "Yes");

        assertThat(importNeurologicalExams(export.reparse())).containsExactly(
                new ImportedExam("FTLS", "Yes"),
                new ImportedExam("NRTF", "No"));
    }

    @Test
    @DisplayName("should fall back to FTLS when the screening at a marker's ordinal has another date")
    void shouldImportFtls_whenMarkerDateDoesNotMatchScreening() throws Exception {
        Export export = new Export();
        export.ftls(date(2026, 5, 2));
        NewCategory marker = export.nrtf(date(2026, 6, 1), "No");
        // Simulates another system dropping or reordering screenings: ordinal 0 is an FTLS dated
        // 2026-05-02, so a marker for ordinal 0 dated 2026-06-01 must not claim it.
        addRawMarker(marker, "0", "2026-06-01", "No");

        assertThat(importNeurologicalExams(export.reparse())).containsExactly(
                new ImportedExam("FTLS", "Yes"),
                new ImportedExam("NRTF", "No"));
    }

    @Test
    @DisplayName("should export NRTF as a standard 67536-3 screening that validates against the CDS schema")
    void shouldProduceSchemaValidRecord_withNrtfMarker() throws Exception {
        Export export = new Export();
        export.ftls(date(2026, 3, 14));
        export.nrtf(date(2026, 3, 14), "Yes");

        OmdCdsDocument reparsed = export.reparse();
        DiabetesComplicationScreening[] screenings = reparsed.getOmdCds().getPatientRecord()
                .getCareElementsArray(0).getDiabetesComplicationsScreeningArray();

        assertThat(screenings).hasSize(2)
                .allSatisfy(s -> assertThat(s.getExamCode()).isEqualTo(ExamCode.X_67536_3));
        assertThat(validationErrors(reparsed.getOmdCds().getPatientRecord().getNewCategoryArray(0)))
                .as("the marker category must validate against the CDS schema")
                .isEmpty();
        assertThat(validationErrors(screenings[1])).isEmpty();
    }

    @Test
    @DisplayName("should ignore NewCategory entries that are not the CARLOS NRTF marker")
    void shouldImportFtls_whenForeignNewCategoryIsPresent() throws Exception {
        Export export = new Export();
        export.ftls(date(2026, 3, 14));
        NewCategory other = export.patient.addNewNewCategory();
        other.setCategoryName("Other EMR Data");
        other.setCategoryDescription("Unrelated");
        addRawMarker(other, "0", "2026-03-14", "No");

        assertThat(CdsNeurologicalExam.isNrtfMarkerCategory(other)).isFalse();
        assertThat(importNeurologicalExams(export.reparse())).containsExactly(new ImportedExam("FTLS", "Yes"));
    }

    @Test
    @DisplayName("should wire the NRTF marker into the CDS export and import actions")
    void shouldUseNrtfMapping_inExportAndImportActions() throws Exception {
        String export = Files.readString(PAGE_UTIL.resolve("DemographicExportAction42Action.java"), StandardCharsets.UTF_8);
        String importer = Files.readString(PAGE_UTIL.resolve("ImportDemographicDataAction42Action.java"), StandardCharsets.UTF_8);

        assertThat(export)
                .contains("meas.getType().equals(\"FTLS\")")
                .contains("meas.getType().equals(CdsNeurologicalExam.NRTF)")
                .contains("CdsNeurologicalExam.addNrtfMarker(patientRec, nrtfMarkerCategory, dcs, meas.getDataField())");
        assertThat(importer)
                .contains("CdsNeurologicalExam.readNrtfMarkers(patientRec.getNewCategoryArray())")
                .contains("nrtfMarkers.claim(neurologicalExamOrdinal++, ds)")
                .contains("CdsNeurologicalExam.isNrtfMarkerCategory(ce)");
    }

    // --- helpers mirroring the action code ---

    /** Builds one patient record the way the export action's care-element loop does. */
    private static final class Export {
        private final OmdCdsDocument doc = OmdCdsDocument.Factory.newInstance();
        private final PatientRecord patient = doc.addNewOmdCds().addNewPatientRecord();
        private final CareElements care = patient.addNewCareElements();
        private NewCategory marker;

        void ftls(Calendar date) {
            DiabetesComplicationScreening dcs = care.addNewDiabetesComplicationsScreening();
            dcs.setDate(date);
            dcs.setExamCode(ExamCode.X_67536_3);
        }

        NewCategory nrtf(Calendar date, String result) {
            DiabetesComplicationScreening dcs = care.addNewDiabetesComplicationsScreening();
            dcs.setDate(date);
            dcs.setExamCode(ExamCode.X_67536_3);
            marker = CdsNeurologicalExam.addNrtfMarker(patient, marker, dcs, result);
            return marker;
        }

        OmdCdsDocument reparse() throws Exception {
            return OmdCdsDocument.Factory.parse(doc.xmlText(new XmlOptions().setSavePrettyPrint()));
        }
    }

    /** Same decision the importer makes for every 67536-3 screening, in document order. */
    private static List<ImportedExam> importNeurologicalExams(OmdCdsDocument doc) {
        PatientRecord patient = doc.getOmdCds().getPatientRecord();
        CdsNeurologicalExam.NrtfMarkers markers = CdsNeurologicalExam.readNrtfMarkers(patient.getNewCategoryArray());
        int ordinal = 0;
        List<ImportedExam> imported = new ArrayList<>();
        for (CareElements care : patient.getCareElementsArray()) {
            for (DiabetesComplicationScreening ds : care.getDiabetesComplicationsScreeningArray()) {
                if (!ExamCode.X_67536_3.equals(ds.getExamCode())) {
                    continue;
                }
                String nrtfResult = markers.claim(ordinal++, ds);
                if (nrtfResult != null) {
                    imported.add(new ImportedExam(CdsNeurologicalExam.NRTF, nrtfResult.isEmpty() ? "Yes" : nrtfResult));
                } else {
                    imported.add(new ImportedExam(CdsNeurologicalExam.FTLS, "Yes"));
                }
            }
        }
        return imported;
    }

    private static void addRawMarker(NewCategory category, String ordinal, String date, String result) {
        ResidualInformation ri = category.addNewResidualInfo();
        addElement(ri, "MeasurementType", "NRTF");
        addElement(ri, "ScreeningOrdinal", ordinal);
        addElement(ri, "ExamDate", date);
        addElement(ri, "Result", result);
    }

    private static void addElement(ResidualInformation ri, String name, String content) {
        ResidualInformation.DataElement element = ri.addNewDataElement();
        element.setName(name);
        element.setDataType("string");
        element.setContent(content);
    }

    private static List<String> validationErrors(XmlObject element) {
        List<XmlError> errors = new ArrayList<>();
        element.validate(new XmlOptions().setErrorListener(errors));
        List<String> messages = new ArrayList<>();
        for (XmlError error : errors) {
            messages.add(String.valueOf(error.getMessage()));
        }
        return messages;
    }

    /**
     * Same shape {@code Util.calDate(Date)} gives the exporter (local date-time, no zone). Built
     * directly because {@code Util}'s static initializer needs a Spring context.
     */
    private static Calendar date(int year, int month, int day) {
        return new XmlCalendar(String.format("%04d-%02d-%02dT09:30:00", year, month, day));
    }
}
