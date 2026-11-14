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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDaoBaseIntegrationTest;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsHistoryNativeDto;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real note/issue/extension writes feed the existing chart reader after the persistence context is cleared. */
class CdsHistoryProjectionIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    @TempDir Path directory;

    @Autowired
    @Qualifier("CaseManagementNoteExtDAO")
    private CaseManagementNoteExtDAO extensions;

    @Test
    void shouldReadBackDatedFamilyHistory_withItsPrecisionAndSourceField() throws Exception {
        if (issueDAO.findCandidatesByCode("FamHistory").isEmpty())
            createIssue("FamHistory", "Family history fixture");
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
        Properties properties = new Properties();
        properties.setProperty("cds.import.clinicalZone", ZoneId.systemDefault().getId());
        var writer =
                new CdsHistoryPersister(
                        fields,
                        new CdsClinicalNormalizationService(fields),
                        new CdsImportConfigurationService(properties),
                        mock(CdsProviderResolver.class),
                        preparation,
                        caseManagementNoteDAO,
                        caseManagementIssueDAO,
                        issueDAO,
                        extensions);
        var record =
                CdsClinicalFixture.record(
                        directory,
                        "FamilyHistory",
                        java.util.Map.of(
                                "ProblemDiagnosisProcedureDescription",
                                "Synthetic family history",
                                "StartDate/FullDate",
                                "2026-02-03"));
        var command =
                new CdsImportCommand(
                        directory,
                        "fixture",
                        "999998",
                        1,
                        null,
                        "synthetic",
                        "omd-5.2",
                        false,
                        false,
                        false,
                        0,
                        null,
                        false);
        writer.prepare("run", 1, "unit", record, command);
        var receipt = writer.write("run", 1, "unit", 974234, record, command, notice -> {});
        hibernateTemplate.flush();
        entityManager.clear();
        var notes =
                caseManagementNoteDAO.findNotesByDemographicAndIssueCode(
                        974234, new String[] {"FamHistory"});
        assertThat(notes).hasSize(1);
        var note = notes.iterator().next();
        assertThat(note.getNote()).isEqualTo("Synthetic family history");
        assertThat(note.getObservation_date().toInstant())
                .isEqualTo(
                        LocalDate.of(2026, 2, 3).atStartOfDay(ZoneId.systemDefault()).toInstant());
        assertThat(extensions.getExtByNote(note.getId()))
                .extracting(CaseManagementNoteExt::getKeyVal, CaseManagementNoteExt::getValue)
                .containsExactlyInAnyOrder(
                        tuple("cdsDatePrecision", "FULL_DATE"),
                        tuple("cdsDateOriginal", "2026-02-03"),
                        tuple("cdsDateField", "StartDate"));
        assertThat(receipt)
                .containsKeys(
                        "casemgmt_note_ext#cdsDatePrecision",
                        "casemgmt_note_ext#cdsDateOriginal",
                        "casemgmt_note_ext#cdsDateField");
        // The note's issue link is returned with the rows the unit wrote, so the receipt names
        // that row rather than every link carrying the note's id.
        assertThat(receipt)
                .containsEntry(
                        "casemgmt_issue_notes",
                        receipt.get("casemgmt_note") + "_" + receipt.get("casemgmt_issue"));
        assertThat(
                        ((Number)
                                        entityManager
                                                .createNativeQuery(
                                                        "select count(*) from casemgmt_issue_notes"
                                                                + " where note_id=?1 and id=?2")
                                                .setParameter(
                                                        1,
                                                        Long.valueOf(receipt.get("casemgmt_note")))
                                                .setParameter(
                                                        2,
                                                        Long.valueOf(receipt.get("casemgmt_issue")))
                                                .getSingleResult())
                                .longValue())
                .isEqualTo(1);
    }

    /**
     * CARLOS to CARLOS: the document a CARLOS sender writes for an item its chart files under
     * three CPP sections is imported, and the receiving chart files the one note under the same
     * three sections, each link named in the unit's receipt for a re-export to prove.
     */
    @Test
    void shouldFileAnItemUnderEverySectionItsSenderDid_whenACarlosExportIsImported()
            throws Exception {
        var codes = java.util.List.of("FamHistory", "MedHistory", "Concerns");
        for (String code : codes)
            if (issueDAO.findCandidatesByCode(code).isEmpty())
                createIssue(code, "Section fixture " + code);
        var document =
                io.github.carlos_emr.carlos.demographic.cdsexport.service
                        .CdsExportNativeHistoryFixture.filedUnderDocument(
                                "Mother: type 2 diabetes", "Concerns", "MedHistory", "FamHistory");
        Path parsed = java.nio.file.Files.createTempDirectory(directory, "sections-");
        var records =
                new java.util.ArrayList<
                        io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        java.nio.file.Files.write(parsed.resolve("patient.xml"), document),
                        parsed.resolve("parsed"),
                        io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto
                                .defaults(),
                        () -> false,
                        records::add);
        var record =
                records.stream()
                        .filter(r -> r.section().equals("FamilyHistory"))
                        .findFirst()
                        .orElseThrow();
        var writer = persister();
        var command = command();
        var notices =
                new java.util.ArrayList<
                        io.github.carlos_emr.carlos.demographic.cdsimport.dto
                                .CdsClinicalDiagnosticDto>();
        writer.prepare("run", 1, "unit", record, command);
        var receipt = writer.write("run", 1, "unit", 974235, record, command, notices::add);
        hibernateTemplate.flush();
        entityManager.clear();
        Long noteId = Long.valueOf(receipt.get("casemgmt_note"));
        for (String code : codes)
            assertThat(caseManagementNoteDAO.findNotesByDemographicAndIssueCode(974235, new String[] {code}))
                    .as(code)
                    .singleElement()
                    .satisfies(
                            note -> {
                                assertThat(note.getId()).isEqualTo(noteId);
                                assertThat(note.getNote()).isEqualTo("Mother: type 2 diabetes");
                            });
        assertThat(
                        ((Number)
                                        entityManager
                                                .createNativeQuery(
                                                        "select count(*) from casemgmt_issue_notes"
                                                                + " where note_id=?1")
                                                .setParameter(1, noteId)
                                                .getSingleResult())
                                .longValue())
                .isEqualTo(3);
        assertThat(notices)
                .extracting(n -> n.code())
                .doesNotContain(
                        CdsHistoryPersister.SECTIONS_NOT_APPLIED,
                        CdsHistoryPersister.SECTION_NOT_RESTORED);
        // The record's own section first, as for an item of one section; each other section's
        // patient issue and link are named under its tag.
        for (String code : java.util.List.of("MedHistory", "Concerns"))
            assertThat(receipt)
                    .containsEntry(
                            "casemgmt_issue_notes#" + code,
                            noteId + "_" + receipt.get("casemgmt_issue#" + code));
        assertThat(receipt)
                .containsEntry(
                        "casemgmt_issue_notes", noteId + "_" + receipt.get("casemgmt_issue"))
                .doesNotContainKey("casemgmt_issue#FamHistory");
    }

    private CdsHistoryPersister persister() throws Exception {
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
        Properties properties = new Properties();
        properties.setProperty("cds.import.clinicalZone", ZoneId.systemDefault().getId());
        return new CdsHistoryPersister(
                fields,
                new CdsClinicalNormalizationService(fields),
                new CdsImportConfigurationService(properties),
                mock(CdsProviderResolver.class),
                preparation,
                caseManagementNoteDAO,
                caseManagementIssueDAO,
                issueDAO,
                extensions);
    }

    private CdsImportCommand command() {
        return new CdsImportCommand(
                directory, "fixture", "999998", 1, null, "synthetic", "omd-5.2", false, false,
                false, 0, null, false);
    }
}
