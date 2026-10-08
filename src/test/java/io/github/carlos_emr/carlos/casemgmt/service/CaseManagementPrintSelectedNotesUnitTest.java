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
package io.github.carlos_emr.carlos.casemgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The chart print is authorized for one patient, but its note ids come from the request
 * (the REST {@code selectedList}, the classic encounter's {@code notes2print}). A selected id
 * that belongs to another patient must not reach the PDF (#2798).
 *
 * @since 2026-10-08
 */
@Tag("unit")
@DisplayName("Chart print keeps selected notes to the printed patient")
class CaseManagementPrintSelectedNotesUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 900001;
    private static final int OTHER_PATIENT = 900002;

    @TempDir
    Path documentDirectory;

    @Test
    @DisplayName("should print only the selected notes that belong to the printed patient")
    void shouldDropSelectedNote_whenItBelongsToAnotherPatient() throws Exception {
        CaseManagementManager notes = createAndRegisterMock(CaseManagementManager.class);
        createAndRegisterMock(AllergyDao.class);
        createAndRegisterMock(NoteService.class);
        createAndRegisterMock(ProgramManager2.class);
        createAndRegisterMock(ProgramManager.class);
        createAndRegisterMock(PreventionManager.class);
        CaseManagementNote own = note(11L, PATIENT);
        CaseManagementNote foreign = note(12L, OTHER_PATIENT);
        when(notes.getNote("11")).thenReturn(own);
        when(notes.getNote("12")).thenReturn(foreign);
        CarlosProperties properties = mock(CarlosProperties.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(properties.getProperty("CMESort", "")).thenReturn("");
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());

        try (var configuration = mockStatic(CarlosProperties.class);
             var pdfs = mockConstruction(CaseManagementPrintPdf.class);
             var _ = mockStatic(ConcatPDF.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);

            new CaseManagementPrint().doPrint(loggedInInfo, PATIENT, false, new String[]{"11", "12"},
                    false, false, false, false, false, false, null, null,
                    new MockHttpServletRequest(), new ByteArrayOutputStream());

            assertThat(pdfs.constructed()).hasSize(1);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CaseManagementNote>> printed = ArgumentCaptor.forClass(List.class);
            verify(pdfs.constructed().getFirst()).printNotes(printed.capture());
            assertThat(printed.getValue()).containsExactly(own);
        }
    }

    private static CaseManagementNote note(long id, int demographicNo) {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(id);
        note.setDemographic_no(String.valueOf(demographicNo));
        note.setProviderNo("999998");
        note.setObservation_date(new Date());
        note.setNote("synthetic note " + id);
        return note;
    }
}
