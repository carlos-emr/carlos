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
    @Autowired @Qualifier("CaseManagementNoteExtDAO") private CaseManagementNoteExtDAO extensions;

    @Test
    void shouldReadBackDatedFamilyHistory_withItsPrecisionAndSourceField() throws Exception {
        if (issueDAO.findCandidatesByCode("FamHistory").isEmpty()) createIssue("FamHistory", "Family history fixture");
        var fields = new CdsFieldParser();
        var preparation = mock(CdsNativePreparationService.class);
        var prepared = new AtomicReference<CdsHistoryNativeDto>();
        doAnswer(call -> { prepared.set(call.getArgument(5)); return null; })
                .when(preparation).store(anyString(), anyLong(), anyString(), anyString(), any(), any());
        when(preparation.load(anyString(), anyLong(), anyString(), anyString(), any(), eq(CdsHistoryNativeDto.class)))
                .thenAnswer(call -> prepared.get());
        Properties properties = new Properties();
        properties.setProperty("cds.import.clinicalZone", ZoneId.systemDefault().getId());
        var writer = new CdsHistoryPersister(fields, new CdsClinicalNormalizationService(fields),
                new CdsImportConfigurationService(properties), mock(CdsProviderResolver.class), preparation,
                caseManagementNoteDAO, caseManagementIssueDAO, issueDAO, extensions);
        var record = CdsClinicalFixture.record(directory, "FamilyHistory", java.util.Map.of(
                "ProblemDiagnosisProcedureDescription", "Synthetic family history", "StartDate/FullDate", "2026-02-03"));
        var command = new CdsImportCommand(directory, "fixture", "999998", 1, null,
                "synthetic", "omd-5.2", false, false, false, 0, null, false);
        writer.prepare("run", 1, "unit", record, command);
        var receipt = writer.write("run", 1, "unit", 974234, record, command, notice -> {});
        hibernateTemplate.flush();
        entityManager.clear();
        var notes = caseManagementNoteDAO.findNotesByDemographicAndIssueCode(974234, new String[]{"FamHistory"});
        assertThat(notes).hasSize(1);
        var note = notes.iterator().next();
        assertThat(note.getNote()).isEqualTo("Synthetic family history");
        assertThat(note.getObservation_date().toInstant())
                .isEqualTo(LocalDate.of(2026, 2, 3).atStartOfDay(ZoneId.systemDefault()).toInstant());
        assertThat(extensions.getExtByNote(note.getId()))
                .extracting(CaseManagementNoteExt::getKeyVal, CaseManagementNoteExt::getValue)
                .containsExactlyInAnyOrder(tuple("cdsDatePrecision", "FULL_DATE"),
                        tuple("cdsDateOriginal", "2026-02-03"), tuple("cdsDateField", "StartDate"));
        assertThat(receipt).containsKeys("casemgmt_note_ext#cdsDatePrecision", "casemgmt_note_ext#cdsDateOriginal", "casemgmt_note_ext#cdsDateField");
    }
}
