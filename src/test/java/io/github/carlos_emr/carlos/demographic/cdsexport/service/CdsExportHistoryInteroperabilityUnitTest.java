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
                String narrative = "Exact native " + category.getValue() + " café 😀";
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
                Path directory = Files.createTempDirectory(root, "roundtrip-");
                var records = new ArrayList<CdsRecordDto>();
                new CdsXmlParser()
                        .parse(
                                Files.write(directory.resolve("patient.xml"), render(output)),
                                directory.resolve("parsed"),
                                CdsInputLimitsDto.defaults(),
                                () -> false,
                                records::add);
                var record =
                        records.stream()
                                .filter(r -> r.section().equals(category.getKey().wireName()))
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
                var definition = new Issue();
                definition.setId(51L);
                definition.setCode(category.getValue());
                when(issues.findCandidatesByCode(category.getValue()))
                        .thenReturn(List.of(definition));
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
                var writer =
                        new CdsHistoryPersister(
                                fields,
                                new CdsClinicalNormalizationService(fields),
                                new CdsImportConfigurationService(properties),
                                mock(CdsProviderResolver.class),
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
                assertThat(written.get().getNote()).isEqualTo(narrative);
                assertThat(written.get().isArchived()).isEqualTo(archived);
                assertThat(prepared.get().archived()).isEqualTo(archived);
                assertThat(
                                notices.stream()
                                        .anyMatch(
                                                n ->
                                                        n.code()
                                                                .equals(
                                                                        "CDS_HISTORY_ARCHIVE_STATE_VENDOR_EXTENSION")))
                        .isEqualTo(archived);
            }
    }
}
