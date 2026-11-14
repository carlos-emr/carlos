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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsAppointmentSourceContract;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsSchemaModel;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsWireSchema;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportImmunizationDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The inventory of every CDS 5.2 element of the data-dictionary types {@code cds_dt} text1K,
 * text32K and text64K, and the rule each class that writes CARLOS text into one of them keeps
 * (CdsExportDictionaryLength).
 *
 * <p>Owner decision (#56): text CARLOS writes keeps the dictionary length (1,000, 32,000, 64,000)
 * by its writer's documented rule, a valid element plus the exact value in a CARLOS carrier; an
 * imported element retained unchanged is exported exactly as imported when the schema accepts it
 * (1,024, 32,768, 65,536); {@code CdsExportXmlValidator} holds the schema alone. Because the
 * validator no longer catches a writer that exceeds the dictionary length, this test fails when a
 * class of the exporter names one of these elements without being listed here with its rule.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportDictionaryLengthContractUnitTest {
    private static final Path SOURCE =
            Path.of("src/main/java/io/github/carlos_emr/carlos/demographic/cdsexport");

    /** One element (its path under PatientRecord), its type and the classes writing CARLOS text into it. */
    private record Element(String path, String type, Map<String, String> writers) {}

    private static final Map<String, String> HISTORY_NOTES =
            Map.of(
                    "CdsExportNativeHistoryMapper",
                    "Notes holds whole parts within notesLimit(category); a statement that does not"
                            + " fit is left out whole, exact in CARLOS.NativeHistory.v1, a narrative"
                            + " in CARLOS.History.Narrative.v1; room for the archive statement is kept",
                    "CdsExportNativeHistoryXmlWriter",
                    "writes the mapper's Notes and archive statement; its check is an integrity"
                            + " check the mapper's reserved room never trips",
                    "CdsExportHistoryNativeReconciler",
                    "Notes.fit(notesLimit): whole parts, imported Notes left out whole into"
                            + " importedNotes; imported Notes alone stay exactly as imported");

    private static final Map<String, String> MEDICATION =
            Map.of(
                    "CdsExportMedicationMapper",
                    "text(column, field.maximum()): a longer value is left out, exact in"
                            + " CARLOS.Medication.Source.v1; refill counts are whole numbers",
                    "CdsExportMedicationFieldReconciler",
                    "an unchanged group keeps the imported value, a changed one the native"
                            + " mapper's",
                    "CdsExportMedicationXmlWriter",
                    "gate: field.maximum() for CARLOS text, field.schemaMaximum() for an element"
                            + " kept as imported",
                    "CdsExportMedicationRetainedAssembler",
                    "an unchanged part keeps its imported element; only those fields reach the"
                            + " gate as imported");

    private static final List<Element> INVENTORY =
            List.of(
                    new Element(
                            "Demographics/NoteAboutPatient",
                            "text64K",
                            Map.of(
                                    "CdsExportPatientNoteMapper",
                                    "note(): a note longer than 64,000 characters is cut"
                                            + " before PATIENT_NOTE_SHORTENED_MARK, the exact"
                                            + " column in CARLOS.Demographic.v1",
                                    "CdsExportDemographicXmlWriter",
                                    "writes the mapper's text; refuses more (integrity check)",
                                    "CdsExportDemographicRetainedNoteReconciler",
                                    "an unchanged imported note is kept; an edited one is the"
                                            + " mapper's text")),
                    new Element("FamilyHistory/Notes", "text32K", HISTORY_NOTES),
                    new Element(
                            "PastHealth/Notes",
                            "text64K",
                            with(
                                    HISTORY_NOTES,
                                    "CdsExportScreeningMapper",
                                    "comments and moved details within NOTES_LIMIT (64,000); longer"
                                            + " text is left out whole, exact in"
                                            + " CARLOS.Prevention.Source.v1",
                                    "CdsExportScreeningXmlWriter",
                                    "writes the mapper's Notes; refuses more (integrity check)")),
                    new Element(
                            "ProblemList/Notes",
                            "text64K",
                            with(
                                    HISTORY_NOTES,
                                    "CdsExportProblemRegistryMapper",
                                    "registry notes within notesLimit (64,000), longer ones carried"
                                            + " in the residual")),
                    new Element("RiskFactors/Notes", "text64K", HISTORY_NOTES),
                    new Element(
                            "AllergiesAndAdverseReactions/Notes",
                            "text32K",
                            Map.of(
                                    "CdsExportAllergyMapper",
                                    "archive statement and long reaction within 32,000; a reaction"
                                            + " that does not fit is carried in"
                                            + " CARLOS.Allergy.Source.v1",
                                    "CdsExportAllergyFieldReconciler",
                                    "the archive statement or a changed long reaction is added to"
                                            + " imported Notes only when both fit 32,000; otherwise"
                                            + " the imported Notes stay as imported",
                                    "CdsExportAllergyXmlWriter",
                                    "gate: 32,000 for CARLOS text, 32,768 for the imported"
                                            + " element's own text",
                                    "CdsExportAllergyRetainedAssembler",
                                    "passes imported only for Notes equal to the import's")),
                    new Element("MedicationsAndTreatments/RefillDuration", "text1K", MEDICATION),
                    new Element("MedicationsAndTreatments/RefillQuantity", "text1K", MEDICATION),
                    new Element("MedicationsAndTreatments/Notes", "text32K", MEDICATION),
                    new Element(
                            "MedicationsAndTreatments/PrescriptionInstructions",
                            "text32K",
                            with(
                                    MEDICATION,
                                    "CdsExportNativeMedicationMapper",
                                    "the instructions, else the SIG, within 32,000; else left out,"
                                            + " the row in the residual")),
                    new Element(
                            "Immunizations/Notes",
                            "text32K",
                            Map.of(
                                    "CdsExportImmunizationMapper",
                                    "status line, moved values and comments as whole parts within"
                                            + " 32,000; a left-out part is exact in"
                                            + " CARLOS.Prevention.Source.v1",
                                    "CdsExportImmunizationFieldReconciler",
                                    "unchanged Notes keep the imported value, gated as imported",
                                    "CdsExportImmunizationXmlWriter",
                                    "gate: 32,000 for CARLOS text, 32,768 for the imported"
                                            + " element's own text",
                                    "CdsExportImmunizationRetainedAssembler",
                                    "passes imported only for Notes equal to the import's")),
                    new Element(
                            "LaboratoryResults/TestResultsInformationReportedByTheLab",
                            "text32K",
                            Map.of(
                                    "CdsExportNativeLaboratoryMapper",
                                    "put(..., 32,000): a longer value is left out whole, the"
                                            + " message exact in CARLOS.NativeLaboratory.v1")),
                    new Element(
                            "LaboratoryResults/NotesFromLab",
                            "text32K",
                            Map.of(
                                    "CdsExportNativeLaboratoryMapper",
                                    "put(..., 32,000): a longer value is left out whole, the"
                                            + " message exact in CARLOS.NativeLaboratory.v1")),
                    // CARLOS writes no PhysiciansNotes: an imported one is kept as imported.
                    new Element("LaboratoryResults/PhysiciansNotes", "text32K", Map.of()),
                    new Element(
                            "Appointments/AppointmentNotes",
                            "text32K",
                            Map.of(
                                    "CdsExportAppointmentMapper",
                                    "fitNotes: cut at 32,000 before SHORTENED_MARK, the complete"
                                            + " text in CARLOS.NativeAppointment.v1",
                                    "CdsExportAppointmentNativeMetadata",
                                    "booking-name and remarks lines, fitted with the notes",
                                    "CdsExportAppointmentFieldReconciler",
                                    "unchanged imported notes stay as imported; with a label or a"
                                            + " line added they are fitted",
                                    "CdsExportAppointmentXmlWriter",
                                    "gate: 32,000 for CARLOS text, 32,768 for the imported"
                                            + " element's own text",
                                    "CdsExportAppointmentRetainedAssembler",
                                    "passes imported only for notes the reconciler kept")),
                    new Element(
                            "Reports/Notes",
                            "text32K",
                            Map.of(
                                    "CdsExportReportContext",
                                    "the context within 32,000, else a pointer and a companion"
                                            + " record that holds it whole",
                                    "CdsExportNativeReportMapper",
                                    "CdsExportReportContext.notes with no lead",
                                    "CdsExportLaboratoryDocumentMapper",
                                    "CdsExportReportContext.notes after a short fixed lead",
                                    "CdsExportHospitalReportMapper",
                                    "CdsExportReportContext.notes after fixed statements",
                                    "CdsExportReportOccurrenceService",
                                    "imported reviews in Notes only when they fit 32,000 and the"
                                            + " report has none; otherwise a supplementary record",
                                    "CdsExportDataSummaryOccurrenceService",
                                    "fixed statements and an eForm subject of at most 4,000"
                                            + " characters")),
                    new Element(
                            "AlertsAndSpecialNeeds/AlertDescription",
                            "text1K",
                            Map.of(
                                    "CdsExportPatientAlertMapper",
                                    "MAXIMUM (1,000): a longer alert is left out, exact in"
                                            + " CARLOS.PatientAlert.v1",
                                    "CdsExportNativeHistoryMapper",
                                    "bodyMaximum (1,000): a longer narrative is left out, exact"
                                            + " in CARLOS.History.Narrative.v1",
                                    "CdsExportNativeHistoryXmlWriter",
                                    "writes the mapper's description",
                                    "CdsExportHistoryNativeReconciler",
                                    "writes the native mapper's description of an edited item",
                                    "CdsExportHistoryFieldReconciler",
                                    "a current narrative within 1,000 characters (maximum)")),
                    new Element("AlertsAndSpecialNeeds/Notes", "text1K", HISTORY_NOTES));

    /** Classes that name these elements but write no CARLOS text into them, and why. */
    private static final Map<String, String> READERS =
            Map.of(
                    "CdsExportMedicationDto",
                    "declares each field's dictionary and schema length",
                    "CdsExportPreparedMedicationMapper",
                    "the import's prepared values: the imported element's own text",
                    "CdsExportPatientNoteMappingDto",
                    "checks a decision's destination",
                    "CdsExportPatientNoteProjectionReader",
                    "reads the stored column, which holds 65,535 bytes",
                    "CdsExportAppointmentOriginalValues",
                    "reads the imported element, bounded by the schema's 32,768",
                    "CdsExportHistoryRetainedSource",
                    "verifies the imported description against its preparation",
                    "CdsExportLaboratoryRetainedSource",
                    "verifies the imported values against their preparation");

    private static Map<String, String> with(Map<String, String> base, String... more) {
        var result = new TreeMap<>(base);
        for (int i = 0; i < more.length; i += 2) result.put(more[i], more[i + 1]);
        return result;
    }

    @Test
    void shouldListEveryDictionaryTypedElement_ofThePinnedSchema() {
        var schema = new TreeMap<String, String>();
        var record =
                CdsWireSchema.schemaModel().element("cds", "OmdCds").child("cds", "PatientRecord");
        walk(record, "", schema, Collections.newSetFromMap(new IdentityHashMap<>()));
        var listed = new TreeMap<String, String>();
        for (var element : INVENTORY) listed.put(element.path(), element.type());
        assertThat(listed).hasSize(INVENTORY.size()).isEqualTo(schema);
        assertThat(schema).hasSize(18);
    }

    private static void walk(
            CdsSchemaModel.Type type,
            String path,
            Map<String, String> found,
            Set<CdsSchemaModel.Type> open) {
        if (!open.add(type)) return;
        for (var name : type.childNames()) {
            var child = type.child(name.namespace(), name.local());
            String at = path.isEmpty() ? name.local() : path + "/" + name.local();
            if ("cds_dt".equals(child.getTypeNamespace())
                    && Set.of("text1K", "text32K", "text64K").contains(child.getTypeName()))
                found.put(at, child.getTypeName());
            walk(child, at, found, open);
        }
        open.remove(type);
    }

    @Test
    void shouldNameAWriterRuleOrAReason_forEveryExporterClassThatNamesSuchAnElement()
            throws IOException {
        String names =
                "NoteAboutPatient|Notes|RefillDuration|RefillQuantity|PrescriptionInstructions"
                        + "|TestResultsInformationReportedByTheLab|NotesFromLab|PhysiciansNotes"
                        + "|AppointmentNotes|AlertDescription";
        var literal = Pattern.compile("\"(?:" + names + ")(?:\\[1\\])?\"");
        // The medication fields are written by the enum constant's name.
        var field =
                Pattern.compile(
                        "\\bField\\.(?:Notes|PrescriptionInstructions|RefillDuration|RefillQuantity)\\b"
                                + "|\\b(?:Notes|PrescriptionInstructions|RefillDuration|RefillQuantity)\\(\\d");
        var naming = new TreeSet<String>();
        var classes = new TreeSet<String>();
        try (var files = Files.walk(SOURCE)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                classes.add(name);
                String code =
                        Files.readString(file)
                                .replaceAll("(?s)/\\*.*?\\*/", "")
                                .replaceAll("//[^\\n]*", "");
                if (literal.matcher(code).find() || field.matcher(code).find()) naming.add(name);
            }
        }
        var classified = new TreeSet<>(READERS.keySet());
        for (var element : INVENTORY) classified.addAll(element.writers().keySet());
        assertThat(naming)
                .as("a class naming a dictionary-typed element needs a writer rule or a reason")
                .isSubsetOf(classified);
        assertThat(classified).as("every listed class exists").isSubsetOf(classes);
        for (var element : INVENTORY)
            element.writers().values().forEach(rule -> assertThat(rule).isNotBlank());
    }

    @Test
    void shouldStateTheDictionaryAndSchemaLengths_onceForEachType() {
        assertThat(CdsExportDictionaryLength.schema(CdsExportDictionaryLength.TEXT_1K))
                .isEqualTo(1_024);
        assertThat(CdsExportDictionaryLength.schema(CdsExportDictionaryLength.TEXT_32K))
                .isEqualTo(32_768);
        assertThat(CdsExportDictionaryLength.schema(CdsExportDictionaryLength.TEXT_64K))
                .isEqualTo(65_536);
        assertThat(CdsExportDictionaryLength.maximum(CdsExportDictionaryLength.TEXT_32K, false))
                .isEqualTo(32_000);
        assertThat(CdsExportDictionaryLength.maximum(CdsExportDictionaryLength.TEXT_32K, true))
                .isEqualTo(32_768);
        assertThatThrownBy(() -> CdsExportDictionaryLength.schema(250))
                .isInstanceOf(IllegalArgumentException.class);
        // Each writer's own constant is its element's dictionary length.
        var lengths = new LinkedHashMap<String, Integer>();
        for (var element : INVENTORY)
            lengths.put(
                    element.path(),
                    switch (element.type()) {
                        case "text1K" -> CdsExportDictionaryLength.TEXT_1K;
                        case "text32K" -> CdsExportDictionaryLength.TEXT_32K;
                        default -> CdsExportDictionaryLength.TEXT_64K;
                    });
        assertThat(CdsExportPatientNoteMapper.NOTE_LENGTH)
                .isEqualTo(lengths.get("Demographics/NoteAboutPatient"));
        for (var category :
                List.of(
                        CdsCategory.FAMILY_HISTORY,
                        CdsCategory.PAST_HEALTH,
                        CdsCategory.PROBLEM_LIST,
                        CdsCategory.RISK_FACTORS,
                        CdsCategory.ALERTS_AND_SPECIAL_NEEDS))
            assertThat(CdsExportNativeHistoryMapper.notesLimit(category))
                    .as(category.wireName())
                    .isEqualTo(lengths.get(category.wireName() + "/Notes"));
        assertThat(CdsExportScreeningMapper.NOTES_LIMIT).isEqualTo(lengths.get("PastHealth/Notes"));
        for (var medication :
                List.of(
                        Field.RefillDuration,
                        Field.RefillQuantity,
                        Field.Notes,
                        Field.PrescriptionInstructions)) {
            int dictionary = lengths.get("MedicationsAndTreatments/" + medication.name());
            assertThat(medication.maximum()).isEqualTo(dictionary);
            assertThat(medication.schemaMaximum())
                    .isEqualTo(CdsExportDictionaryLength.schema(dictionary));
        }
        assertThat(CdsAppointmentSourceContract.NOTES_MAXIMUM)
                .isEqualTo(lengths.get("Appointments/AppointmentNotes"));
        assertThat(CdsExportReportContext.NOTES_LENGTH).isEqualTo(lengths.get("Reports/Notes"));
        assertThat(CdsExportPatientAlertMapper.MAXIMUM)
                .isEqualTo(lengths.get("AlertsAndSpecialNeeds/AlertDescription"));
    }

    @Test
    void shouldKeepTheDictionaryLength_forTextCarlosWritesPastIt() throws Exception {
        // NoteAboutPatient: cut before the shortened mark to 64,000, the whole column carried.
        var note = CdsExportPatientNoteMapper.note("n".repeat(64_001));
        assertThat(note.text())
                .endsWith(CdsNativeExtensionContract.PATIENT_NOTE_SHORTENED_MARK)
                .hasSize(CdsExportDictionaryLength.TEXT_64K);
        assertThat(note.carried()).isTrue();
        // AppointmentNotes: cut before the mark, complete text in the source record.
        var fitted = CdsExportAppointmentMapper.fitNotes("n".repeat(32_768));
        assertThat(fitted.element())
                .hasSize(32_000)
                .endsWith(CdsExportAppointmentMapper.SHORTENED_MARK);
        assertThat(fitted.complete()).hasSize(32_768);
        // Reports/Notes: a pointer in Notes, the whole context in the companion record.
        var context = CdsExportReportContext.notes("", "c".repeat(40_000), "a".repeat(64));
        assertThat(context.notes().length()).isLessThanOrEqualTo(32_000);
        assertThat(context.companion()).contains("c".repeat(40_000));
        // AlertDescription: past 1,000 characters it is not the element.
        assertThat(CdsExportPatientAlertMapper.legal("a".repeat(1_000))).isTrue();
        assertThat(CdsExportPatientAlertMapper.legal("a".repeat(1_001))).isFalse();
        // Allergy Notes: the archive statement only beside imported Notes it fits with.
        assertThat(CdsExportAllergyFieldReconciler.notes("n".repeat(31_950), false, true))
                .isEqualTo("n".repeat(31_950));
        assertThat(CdsExportAllergyFieldReconciler.notes("Seen", false, true))
                .isEqualTo("Seen\n" + CdsExportAllergyMapper.ARCHIVED_NOTE);
        // Immunization Notes: CARLOS text is gated at the dictionary length, the imported
        // element's own text at the schema's.
        var date = new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);
        var vaccine =
                new CdsExportImmunizationDto(
                        "Synthetic vaccine", null, null, null, null, null, null, null, null, date,
                        false, null, "n".repeat(32_001));
        CdsExportImmunizationXmlWriter.validate(vaccine, true);
        assertThatThrownBy(() -> CdsExportImmunizationXmlWriter.validate(vaccine))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_VALUES_REQUIRES_REVIEW");
        // The inventory names writers for every type.
        var types = new ArrayList<String>();
        INVENTORY.forEach(element -> types.add(element.type()));
        assertThat(types.stream().collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("text1K", "text32K", "text64K");
    }
}
