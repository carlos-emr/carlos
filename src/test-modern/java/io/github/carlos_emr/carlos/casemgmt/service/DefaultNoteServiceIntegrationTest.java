/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.casemgmt.service;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.utility.ProgramAccessCache;
import io.github.carlos_emr.carlos.PMmodule.utility.RoleCache;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.impl.DefaultNoteService;
import io.github.carlos_emr.carlos.casemgmt.web.NoteDisplay;
import io.github.carlos_emr.carlos.commn.dao.utils.AuthUtils;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DefaultNoteService}, verifying note finding and
 * ordering behavior with a full Spring context and database.
 *
 * <p>Migrated from legacy JUnit 4 {@code DefaultNoteServiceTest}.</p>
 *
 * @see DefaultNoteService
 * @see NoteSelectionCriteria
 * @see NoteSelectionResult
 * @since 2006-10-05
 */
@Tag("integration")
@Tag("service")
@Tag("read")
@DisplayName("DefaultNoteService Integration Tests")
class DefaultNoteServiceIntegrationTest extends CarlosTestBase {

    private final NoteService service = SpringUtils.getBean(DefaultNoteService.class);
    private final CaseManagementManager caseManagementMgr = SpringUtils.getBean(CaseManagementManager.class);
    private final ProgramProviderDAO programProviderDao = SpringUtils.getBean(ProgramProviderDAO.class);

    /**
     * Helper to create and persist a case management note.
     */
    private void createNote(String noteText, Date obsDate, String demographicNo,
                            Provider provider, String providerNo, String programId) {
        CaseManagementNote cmn = new CaseManagementNote();
        cmn.setNote(noteText);
        cmn.setObservation_date(obsDate);
        cmn.setDemographic_no(demographicNo);
        cmn.setProvider(provider);
        cmn.setProviderNo(providerNo);
        cmn.setSigning_provider_no(providerNo);
        cmn.setProgram_no(programId);
        cmn.setReporter_caisi_role("1");
        cmn.setReporter_program_team("0");
        cmn.setHistory(noteText);
        caseManagementMgr.saveNoteSimple(cmn);
    }

    @Test
    @DisplayName("should return non-null result when finding notes with valid criteria")
    void shouldReturnNonNullResult_whenFindingNotesWithValidCriteria() {
        NoteSelectionCriteria c = new NoteSelectionCriteria();
        c.setDemographicId(1);
        c.setUserRole("doctor,admin");
        c.setUserName("999998");
        c.setProgramId("10016");

        LoggedInInfo loggedInInfo = AuthUtils.initLoginContext();

        NoteSelectionResult result = service.findNotes(loggedInInfo, c);
        assertThat(result).isNotNull();
        logger.info("Num results " + result.getNotes().size());
    }

    @Test
    @DisplayName("should return notes in correct order when slicing from end and start of list")
    void shouldReturnNotesInCorrectOrder_whenSlicingFromEndAndStartOfList() {
        LoggedInInfo loggedInInfo = AuthUtils.initLoginContext();

        String demographicNo = "1";
        String programId = "10016";
        Provider provider = loggedInInfo.getLoggedInProvider();
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // Add this provider to the program
        ProgramProvider pp = new ProgramProvider();
        pp.setProgramId((long) 10016);
        pp.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        pp.setRoleId((long) 1);
        programProviderDao.saveProgramProvider(pp);

        // These are used by the CaseManagementManager so they need to be initialized
        RoleCache.reload();
        ProgramAccessCache.setAccessMap(10016);

        // Add 40 notes to the same patient advancing the day by 1 for each note
        Calendar calendar = new GregorianCalendar(2011, 11, 9);
        int i = 0;

        for (i = 0; i < 40; i++) {
            String noteText = "note #" + i;
            Date obsDate = calendar.getTime();
            createNote(noteText, obsDate, demographicNo, provider, providerNo, programId);
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        int maxResultSize = 15;
        NoteSelectionCriteria c = new NoteSelectionCriteria();
        c.setDemographicId(1);
        c.setUserRole("doctor,admin");
        c.setUserName("999998");
        c.setProgramId("10016");
        c.setMaxResults(maxResultSize);

        NoteSelectionResult result = service.findNotes(loggedInInfo, c);

        List<NoteDisplay> list = result.getNotes();

        // The latest note should be "note #39". Slicing from end should have that as the last note.
        assertThat(list.get(0).getNote()).isEqualTo("note #" + (i - maxResultSize));
        assertThat(list.get(list.size() - 1).getNote()).isEqualTo("note #" + (i - 1));

        c.setSliceFromEndOfList(false);
        c.setNoteSort("observation_date_desc");
        result = service.findNotes(loggedInInfo, c);

        list = result.getNotes();

        // Slicing from start should have the latest note first.
        assertThat(list.get(0).getNote()).isEqualTo("note #" + (i - 1));
        assertThat(list.get(list.size() - 1).getNote()).isEqualTo("note #" + (i - maxResultSize));
    }
}
