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
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip coverage for the NRTF / FTLS neurological exam mapping in the OntarioMD CDS
 * export and import.
 *
 * <p>The CDS {@code DiabetesComplicationsScreening} element has a single neurological exam code
 * ({@code 67536-3}) and no free-text slot, so NRTF (128 Hz tuning fork) is exported as that code
 * plus a CARLOS {@code NewCategory} marker. These tests build records the way
 * {@link DemographicExportAction42Action} does, serialise and re-parse them through XMLBeans, and
 * resolve them the way {@link ImportDemographicDataAction42Action} does.</p>
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
        OmdCdsDocument doc = newDocument();
        PatientRecord patient = doc.getOmdCds().getPatientRecord();
        CareElements care = patient.addNewCareElements();
        NewCategory marker = exportNrtf(patient, care, null, date(2026, 3, 14), "No");
        assertThat(marker).isNotNull();

        List<ImportedExam> imported = importNeurologicalExams(reparse(doc));

        assertThat(imported).containsExactly(new ImportedExam("NRTF", "No"));
    }

    @Test
    @DisplayName("should keep importing FTLS as FTLS with Yes, exactly as before the NRTF split")
    void shouldImportFtlsUnchanged_whenNoNrtfMarkerIsPresent() throws Exception {
        OmdCdsDocument doc = newDocument();
        PatientRecord patient = doc.getOmdCds().getPatientRecord();
        CareElements care = patient.addNewCareElements();
        exportFtls(care, date(2026, 3, 14));

        OmdCdsDocument reparsed = reparse(doc);

        assertThat(reparsed.getOmdCds().getPatientRecord().getNewCategoryArray()).isEmpty();
        assertThat(importNeurologicalExams(reparsed)).containsExactly(new ImportedExam("FTLS", "Yes"));
    }

    @Test
    @DisplayName("should keep FTLS and NRTF apart when both were recorded on the same day")
    void shouldPreserveBothExams_whenFtlsAndNrtfShareADate() throws Exception {
        OmdCdsDocument doc = newDocument();
        PatientRecord patient = doc.getOmdCds().getPatientRecord();
        CareElements care = patient.addNewCareElements();
        Calendar day = date(2026, 5, 2);
        exportFtls(care, day);
        NewCategory marker = exportNrtf(patient, care, null, day, "Yes");
        exportNrtf(patient, care, marker, date(2025, 11, 20), "NA");

        OmdCdsDocument reparsed = reparse(doc);

        assertThat(reparsed.getOmdCds().getPatientRecord().getNewCategoryArray())
                .as("one marker category per patient, one residual block per NRTF reading")
                .hasSize(1)
                .allSatisfy(c -> assertThat(c.getResidualInfoArray()).hasSize(2));
        assertThat(importNeurologicalExams(reparsed)).containsExactlyInAnyOrder(
                new ImportedExam("FTLS", "Yes"),
                new ImportedExam("NRTF", "Yes"),
                new ImportedExam("NRTF", "NA"));
    }

    @Test
    @DisplayName("should export NRTF as a standard 67536-3 screening that validates against the CDS schema")
    void shouldProduceSchemaValidRecord_withNrtfMarker() throws Exception {
        OmdCdsDocument doc = newDocument();
        PatientRecord patient = doc.getOmdCds().getPatientRecord();
        CareElements care = patient.addNewCareElements();
        exportNrtf(patient, care, null, date(2026, 3, 14), "Yes");

        OmdCdsDocument reparsed = reparse(doc);
        DiabetesComplicationScreening[] screenings = reparsed.getOmdCds().getPatientRecord()
                .getCareElementsArray(0).getDiabetesComplicationsScreeningArray();

        assertThat(screenings).singleElement()
                .satisfies(s -> assertThat(s.getExamCode()).isEqualTo(ExamCode.X_67536_3));
        assertThat(validationErrors(reparsed.getOmdCds().getPatientRecord().getNewCategoryArray(0)))
                .as("the marker category must validate against the CDS schema")
                .isEmpty();
        assertThat(validationErrors(screenings[0])).isEmpty();
    }

    @Test
    @DisplayName("should ignore NewCategory entries that are not the CARLOS NRTF marker")
    void shouldImportFtls_whenForeignNewCategoryIsPresent() throws Exception {
        OmdCdsDocument doc = newDocument();
        PatientRecord patient = doc.getOmdCds().getPatientRecord();
        CareElements care = patient.addNewCareElements();
        exportFtls(care, date(2026, 3, 14));
        NewCategory other = patient.addNewNewCategory();
        other.setCategoryName("Other EMR Data");
        other.setCategoryDescription("Unrelated");
        cdsDt.ResidualInformation.DataElement element = other.addNewResidualInfo().addNewDataElement();
        element.setName("MeasurementType");
        element.setDataType("string");
        element.setContent("NRTF");

        OmdCdsDocument reparsed = reparse(doc);

        assertThat(CdsNeurologicalExam.isNrtfMarkerCategory(other)).isFalse();
        assertThat(importNeurologicalExams(reparsed)).containsExactly(new ImportedExam("FTLS", "Yes"));
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
                .contains("CdsNeurologicalExam.claimNrtfResult(nrtfMarkers, ds)")
                .contains("CdsNeurologicalExam.isNrtfMarkerCategory(ce)");
    }

    // --- helpers mirroring the action code ---

    private static OmdCdsDocument newDocument() {
        OmdCdsDocument doc = OmdCdsDocument.Factory.newInstance();
        doc.addNewOmdCds().addNewPatientRecord();
        return doc;
    }

    private static void exportFtls(CareElements care, Calendar date) {
        DiabetesComplicationScreening dcs = care.addNewDiabetesComplicationsScreening();
        dcs.setDate(date);
        dcs.setExamCode(ExamCode.X_67536_3);
    }

    private static NewCategory exportNrtf(PatientRecord patient, CareElements care, NewCategory marker,
                                          Calendar date, String result) {
        DiabetesComplicationScreening dcs = care.addNewDiabetesComplicationsScreening();
        dcs.setDate(date);
        dcs.setExamCode(ExamCode.X_67536_3);
        return CdsNeurologicalExam.addNrtfMarker(patient, marker, dcs, result);
    }

    /** Same decision the importer makes for every 67536-3 screening. */
    private static List<ImportedExam> importNeurologicalExams(OmdCdsDocument doc) {
        PatientRecord patient = doc.getOmdCds().getPatientRecord();
        Map<String, Deque<String>> markers = CdsNeurologicalExam.readNrtfMarkers(patient.getNewCategoryArray());
        List<ImportedExam> imported = new ArrayList<>();
        for (CareElements care : patient.getCareElementsArray()) {
            for (DiabetesComplicationScreening ds : care.getDiabetesComplicationsScreeningArray()) {
                if (!ExamCode.X_67536_3.equals(ds.getExamCode())) {
                    continue;
                }
                String nrtfResult = CdsNeurologicalExam.claimNrtfResult(markers, ds);
                if (nrtfResult != null) {
                    imported.add(new ImportedExam(CdsNeurologicalExam.NRTF, nrtfResult.isEmpty() ? "Yes" : nrtfResult));
                } else {
                    imported.add(new ImportedExam(CdsNeurologicalExam.FTLS, "Yes"));
                }
            }
        }
        return imported;
    }

    private static OmdCdsDocument reparse(OmdCdsDocument doc) throws Exception {
        XmlOptions options = new XmlOptions().setSavePrettyPrint();
        return OmdCdsDocument.Factory.parse(doc.xmlText(options));
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
