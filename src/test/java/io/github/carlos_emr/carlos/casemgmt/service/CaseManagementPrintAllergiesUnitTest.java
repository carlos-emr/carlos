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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Exercises the chart-print entry point before the allergy list reaches the PDF renderer. */
@Tag("unit")
class CaseManagementPrintAllergiesUnitTest extends CarlosUnitTestBase {
    private static final int PATIENT = 900001;

    @TempDir
    Path documentDirectory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPrintOnlyActiveAllergies_whenPrintingSelectedOrAllNotes(boolean allNotes) throws Exception {
        Allergy severe = allergy(1, "Active severe", false, "3");
        Allergy mild = allergy(2, "Active mild", false, "1");
        Allergy archived = allergy(3, "Removed", true, "3");
        List<Allergy> stored = new ArrayList<>(List.of(severe, mild, archived));
        printAndVerify(stored, List.of(severe, mild), true, allNotes);
        assertThat(stored).containsExactly(severe, mild, archived);
        assertThat(archived.getArchived()).isTrue();
    }

    @Test
    void shouldPrintEmptyAllergySection_whenAllAllergiesAreArchived() throws Exception {
        printAndVerify(List.of(allergy(3, "Removed", true, "3")), List.of(), true, false);
    }

    @Test
    void shouldPrintEmptyAllergySection_whenPatientHasNoAllergies() throws Exception {
        printAndVerify(List.of(), List.of(), true, false);
    }

    @Test
    void shouldSkipAllergyLookup_whenAllergySectionIsNotSelected() throws Exception {
        printAndVerify(List.of(), List.of(), false, false);
    }

    private void printAndVerify(List<Allergy> stored, List<Allergy> expected,
                                boolean includeAllergies, boolean allNotes) throws Exception {
        AllergyDao dao = createAndRegisterMock(AllergyDao.class);
        createAndRegisterMock(CaseManagementManager.class);
        createAndRegisterMock(NoteService.class);
        createAndRegisterMock(ProgramManager2.class);
        createAndRegisterMock(ProgramManager.class);
        createAndRegisterMock(PreventionManager.class);
        CarlosProperties properties = mock(CarlosProperties.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(properties.getProperty("CMESort", "")).thenReturn("");
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());
        when(dao.findAllergies(PATIENT)).thenReturn(stored);
        try (var configuration = mockStatic(CarlosProperties.class);
             var pdfs = mockConstruction(CaseManagementPrintPdf.class);
             var _ = mockStatic(ConcatPDF.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            new CaseManagementPrint().doPrint(loggedInInfo, PATIENT, allNotes, new String[0],
                    false, false, false, false, includeAllergies, false, null, null,
                    new MockHttpServletRequest(), new ByteArrayOutputStream());
            assertThat(pdfs.constructed()).hasSize(1);
            CaseManagementPrintPdf pdf = pdfs.constructed().getFirst();
            if (includeAllergies) {
                verify(dao).findAllergies(PATIENT);
                verify(pdf).printAllergies(expected);
            } else {
                verifyNoInteractions(dao);
                verify(pdf, never()).printAllergies(org.mockito.ArgumentMatchers.anyList());
            }
            verify(pdf).finish();
        }
    }

    private static Allergy allergy(int id, String description, boolean archived, String severity) {
        Allergy allergy = new Allergy();
        allergy.setId(id);
        allergy.setDemographicNo(PATIENT);
        allergy.setDescription(description);
        allergy.setArchived(archived);
        allergy.setSeverityOfReaction(severity);
        return allergy;
    }
}
