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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.*;
import io.github.carlos_emr.carlos.casemgmt.dao.*;
import io.github.carlos_emr.carlos.casemgmt.model.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** The receiving parser and native persister consume actual schema-validated exporter bytes. */
@Tag("unit")
@Tag("cds-export")
class CdsExportHistoryInteroperabilityUnitTest {
    @TempDir Path root;

    /** What the receiving CARLOS importer prepared and wrote for one exported occurrence. */
    record Imported(
            CaseManagementNote note,
            CdsHistoryNativeDto prepared,
            List<CdsClinicalDiagnosticDto> notices) {}

    /** Parses actual exporter bytes and runs the native history persister on the record. */
    Imported roundTrip(CdsExportNativeHistoryDto output, String tag) throws Exception {
        return roundTrip(output.category(), render(output), tag);
    }

    /** Parses actual exporter bytes of one category and runs the native history persister. */
    Imported roundTrip(CdsCategory category, byte[] xml, String tag) throws Exception {
        Path directory = Files.createTempDirectory(root, "roundtrip-");
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.write(directory.resolve("patient.xml"), xml),
                        directory.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        var record =
                records.stream()
                        .filter(r -> r.section().equals(category.wireName()))
                        .findFirst()
                        .orElseThrow();
        var fields = new CdsFieldParser();
        var preparation = mock(CdsNativePreparationService.class);
        var prepared = new AtomicReference<CdsHistoryNativeDto>();
        doAnswer(
                        call -> {
                            prepared.set(call.getArgument(5));
                            return null;
                        })
                .when(preparation)
                .store(anyString(), anyLong(), anyString(), anyString(), any(), any());
        when(preparation.load(
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        any(),
                        eq(CdsHistoryNativeDto.class)))
                .thenAnswer(call -> prepared.get());
        var notes = mock(CaseManagementNoteDAO.class);
        var written = new AtomicReference<CaseManagementNote>();
        doAnswer(
                        call -> {
                            CaseManagementNote note = call.getArgument(0);
                            note.setId(91L);
                            written.set(note);
                            return null;
                        })
                .when(notes)
                .saveNote(any());
        var issues = mock(IssueDAO.class);
        if (tag != null) {
            var definition = new Issue();
            definition.setId(51L);
            definition.setCode(tag);
            when(issues.findCandidatesByCode(tag)).thenReturn(List.of(definition));
        }
        var patientIssues = mock(CaseManagementIssueDAO.class);
        when(patientIssues.findCandidatesByDemographicAndIssue(anyInt(), anyLong()))
                .thenReturn(List.of());
        doAnswer(
                        call -> {
                            CaseManagementIssue issue = call.getArgument(0);
                            issue.setId(41L);
                            return null;
                        })
                .when(patientIssues)
                .saveIssue(any());
        var extensions = mock(CaseManagementNoteExtDAO.class);
        var extensionId = new java.util.concurrent.atomic.AtomicLong(101);
        doAnswer(
                        call -> {
                            CaseManagementNoteExt extension = call.getArgument(0);
                            extension.setId(extensionId.getAndIncrement());
                            return null;
                        })
                .when(extensions)
                .save(any());
        var properties = new Properties();
        properties.setProperty("cds.import.clinicalZone", "America/Toronto");
        // The receiving clinic knows the exporting fixture provider by its OHIP number.
        var resolver = mock(CdsProviderResolver.class);
        when(resolver.resolve(eq("001234"), any(), any(), any(), anyBoolean()))
                .thenReturn(
                        new CdsProviderMatchDto(
                                "17", CdsProviderMatchDto.Outcome.MATCHED_IDENTIFIERS));
        var writer =
                new CdsHistoryPersister(
                        fields,
                        new CdsClinicalNormalizationService(fields),
                        new CdsImportConfigurationService(properties),
                        resolver,
                        preparation,
                        notes,
                        patientIssues,
                        issues,
                        extensions);
        var command =
                new CdsImportCommand(
                        directory,
                        "roundtrip",
                        "999998",
                        1,
                        null,
                        "native-export",
                        "omd-5.2",
                        false,
                        false,
                        false,
                        0,
                        null,
                        false);
        writer.prepare("run", 1, "unit", record, command);
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();
        writer.write("run", 1, "unit", 28, record, command, notices::add);
        return new Imported(written.get(), prepared.get(), notices);
    }

    @Test
    void shouldImportTheDemographicPatientAlert_asTheExactReminderNoteText() throws Exception {
        // The Alert box of the demographic forms (demographiccust.cust3), with a carriage return
        // and repeated spaces, through the pinned schema, the receiving parser and persister.
        String text = "Latex allergy\r\nUses a wheelchair  (call ahead) café 😀";
        var values = new LinkedHashMap<String, String>();
        values.put("demographic_no", "27");
        values.put("cust3", text);
        var alert =
                CdsExportPatientAlertMapper.map(
                        27,
                        new CdsExportCapturedTextRowDto(
                                "demographiccust",
                                CdsExportPatientTableSourceReader.key(27),
                                values));
        var imported =
                roundTrip(
                        CdsCategory.ALERTS_AND_SPECIAL_NEEDS,
                        render(
                                CdsCategory.ALERTS_AND_SPECIAL_NEEDS,
                                xml -> CdsExportPatientAlertMapper.write(alert, xml)),
                        "Reminders");
        assertThat(imported.note().getNote()).isEqualTo(text);
    }

    @Test
    void shouldKeepNativeCppNarrativesAndArchiveState_afterExportAndReceivingImport()
            throws Exception {
        for (var category :
                Map.of(
                                CdsCategory.PERSONAL_HISTORY,
                                "SocHistory",
                                CdsCategory.FAMILY_HISTORY,
                                "FamHistory")
                        .entrySet())
            for (boolean archived : List.of(false, true)) {
                // Line breaks and repeated spaces typed into the CPP box must survive too.
                String narrative =
                        "Exact native " + category.getValue() + " café 😀\n  second   line";
                var mapped =
                        new CdsExportNativeHistoryMapper()
                                .map(
                                        27,
                                        category.getKey(),
                                        List.of(
                                                note(
                                                        91,
                                                        narrative,
                                                        category.getValue(),
                                                        List.of())),
                                        providers(),
                                        ZoneId.of("America/Toronto"));
                var value = mapped.value();
                var output =
                        new CdsExportNativeHistoryDto(
                                value.category(),
                                value.text(),
                                value.dates(),
                                value.code(),
                                value.participants(),
                                value.metadata(),
                                archived);
                var imported = roundTrip(output, category.getValue());
                assertThat(imported.note().getNote()).isEqualTo(narrative);
                assertThat(imported.note().isArchived()).isEqualTo(archived);
                assertThat(imported.prepared().archived()).isEqualTo(archived);
                assertThat(
                                imported.notices().stream()
                                        .anyMatch(
                                                n ->
                                                        n.code()
                                                                .equals(
                                                                        "CDS_HISTORY_ARCHIVE_STATE_VENDOR_EXTENSION")))
                        .isEqualTo(archived);
            }
    }

    @Test
    void shouldImportTheExactPersonalNarrative_andNeverItsPlainTextCopy() throws Exception {
        // The export adds a whitespace-collapsed copy, with the item's Social History dates, for
        // other EMRs; the receiving CARLOS restores the narrative from the exact carrier only.
        String narrative = "FAKE lives alone.\n  Walks   daily café 😀";
        var mapped =
                new CdsExportNativeHistoryMapper()
                        .map(
                                27,
                                CdsCategory.PERSONAL_HISTORY,
                                List.of(
                                        note(
                                                91,
                                                narrative,
                                                "SocHistory",
                                                List.of(
                                                        dated(
                                                                101,
                                                                "Start Date",
                                                                "YYYY",
                                                                "2010-01-01 00:00:00")))),
                                providers(),
                                ZoneId.of("America/Toronto"));
        byte[] xml = render(mapped.value());
        assertThat(residual(xml, CdsNativeExtensionContract.HISTORY_NARRATIVE_TEXT))
                .isEqualTo("FAKE lives alone. Walks daily café 😀 Start Date: 2010");
        var imported = roundTrip(CdsCategory.PERSONAL_HISTORY, xml, "SocHistory");
        assertThat(imported.note().getNote()).isEqualTo(narrative);
        assertThat(imported.prepared().diagnostics())
                .noneMatch(d -> d.code().equals("CDS_HISTORY_STRUCTURED_SOURCE_FIELDS"));
        // A file carrying only the copy has no narrative for CARLOS: the importer files its
        // placeholder, never the copy as the item's text.
        byte[] copyOnly =
                render(
                        CdsCategory.PERSONAL_HISTORY,
                        body -> {
                            body.start("cds", "ResidualInfo");
                            CdsExportNativeHistoryXmlWriter.residualElement(
                                    body,
                                    CdsNativeExtensionContract.HISTORY_NARRATIVE_TEXT,
                                    CdsNativeExtensionContract.STRING_TYPE,
                                    "FAKE plain copy");
                            body.end();
                        });
        var placeholder = roundTrip(CdsCategory.PERSONAL_HISTORY, copyOnly, "SocHistory");
        assertThat(placeholder.note().getNote()).doesNotContain("FAKE plain copy");
        assertThat(placeholder.prepared().diagnostics())
                .anyMatch(d -> d.code().equals("CDS_HISTORY_STRUCTURED_SOURCE_FIELDS"));
    }

    @Test
    void shouldRestoreLongNativeCppNarratives_insteadOfAPlaceholder() throws Exception {
        var tags =
                Map.of(
                        CdsCategory.FAMILY_HISTORY, "FamHistory",
                        CdsCategory.PAST_HEALTH, "MedHistory",
                        CdsCategory.PROBLEM_LIST, "Concerns",
                        CdsCategory.RISK_FACTORS, "RiskFactors");
        for (var entry : tags.entrySet()) {
            // Longer than every description element (250, or 120 for RiskFactor), with the line
            // breaks and repeated spaces a clinician types into the CPP box.
            String narrative =
                    ("Line one of a long " + entry.getValue() + " item café 😀\n"
                                    + "  indented detail,   spaced\n")
                            .repeat(8);
            var mapped =
                    new CdsExportNativeHistoryMapper()
                            .map(
                                    27,
                                    entry.getKey(),
                                    List.of(note(91, narrative, entry.getValue(), List.of())),
                                    providers(),
                                    ZoneId.of("America/Toronto"));
            assertThat(mapped.value().text()).containsEntry("Notes", narrative);
            var imported = roundTrip(mapped.value(), entry.getValue());
            assertThat(imported.note().getNote()).as(entry.getKey().wireName()).isEqualTo(narrative);
            assertThat(imported.prepared().diagnostics())
                    .noneMatch(d -> d.code().equals("CDS_HISTORY_STRUCTURED_SOURCE_FIELDS"));
        }
    }

    @Test
    void shouldKeepTheAuthorOfARevisedNote_andLeaveTwoAuthorsUnresolved() throws Exception {
        var original = note(91, "Seen for cough.", null, List.of());
        var fields = new LinkedHashMap<>(original.note().value().fields());
        fields.put("note_id", "92");
        fields.put("note", "Seen for cough. Plan: fluids.");
        fields.put("update_date", "2026-07-11 09:00:00");
        var edit =
                new CdsExportHistoryFieldReconciler.Current(
                        row(
                                io.github.carlos_emr.carlos.commn.dao
                                        .CdsExportPatientTableReadSupport.Plan.CASE_NOTES,
                                92,
                                fields),
                        List.of(),
                        List.of(),
                        Map.of(),
                        Map.of());
        var mapper = new CdsExportNativeHistoryMapper();
        var zone = ZoneId.of("America/Toronto");
        var sameAuthor =
                roundTrip(
                        mapper.map(
                                        27,
                                        CdsCategory.CLINICAL_NOTES,
                                        List.of(original, edit),
                                        providers(),
                                        zone)
                                .value(),
                        null);
        assertThat(sameAuthor.note().getProviderNo()).isEqualTo("17");
        // A colleague's revision makes two genuine participants; the importer keeps its
        // multi-author policy (source-only, no single local author).
        fields.put("provider_no", "18");
        var colleague =
                new CdsExportHistoryFieldReconciler.Current(
                        row(
                                io.github.carlos_emr.carlos.commn.dao
                                        .CdsExportPatientTableReadSupport.Plan.CASE_NOTES,
                                92,
                                fields),
                        List.of(),
                        List.of(),
                        Map.of(),
                        Map.of());
        var providers = new HashMap<>(providers());
        var other = new LinkedHashMap<>(providers.get("17").fields());
        other.put("provider_no", "18");
        other.put("first_name", "Colleague");
        other.put("ohip_no", "004321");
        providers.put(
                "18",
                new CdsExportCapturedTextRowDto(
                        "provider", CdsExportPublicProviderSourceReader.key("18"), other));
        var twoAuthors =
                roundTrip(
                        mapper.map(
                                        27,
                                        CdsCategory.CLINICAL_NOTES,
                                        List.of(original, colleague),
                                        providers,
                                        zone)
                                .value(),
                        null);
        assertThat(twoAuthors.note().getProviderNo()).isEmpty();
        assertThat(twoAuthors.notices())
                .anyMatch(n -> n.code().equals("CDS_MULTIPLE_NOTE_PARTICIPANTS_SOURCE_ONLY"));
    }

    @Test
    void shouldReceiveAnArchivedEncounterNote_asAStatedArchivedNoteRatherThanALiveOne()
            throws Exception {
        var original = note(91, "Seen for cough. Plan: fluids.", null, List.of());
        var fields = new LinkedHashMap<>(original.note().value().fields());
        fields.put("note_id", "92");
        fields.put("archived", "1");
        fields.put("update_date", "2026-07-11 09:00:00");
        var archive =
                new CdsExportHistoryFieldReconciler.Current(
                        row(
                                io.github.carlos_emr.carlos.commn.dao
                                        .CdsExportPatientTableReadSupport.Plan.CASE_NOTES,
                                92,
                                fields),
                        List.of(),
                        List.of(),
                        Map.of(),
                        Map.of());
        var mapped =
                new CdsExportNativeHistoryMapper()
                        .map(
                                27,
                                CdsCategory.CLINICAL_NOTES,
                                List.of(original, archive),
                                providers(),
                                ZoneId.of("America/Toronto"));
        var imported = roundTrip(mapped.value(), null);
        // ClinicalNotes has no archive field; the receiver sees the statement first, never a
        // bare live note. The CARLOS importer therefore files it as a note that says so.
        assertThat(imported.note().getNote())
                .startsWith(CdsExportNativeHistoryMapper.ARCHIVED_CLINICAL_NOTE_STATEMENT)
                .contains("Seen for cough. Plan: fluids.");
    }
}
