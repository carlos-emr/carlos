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
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.encounter.data.EctProviderData;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The chart print's "MRP:" header line names the printed patient's own most responsible provider
 * (issue #4673). It used to come from the login-wide {@code EctSessionBean}, which names whichever
 * chart was entered last in any window, so printing A's notes after B's chart was opened put B's
 * family doctor on A's printed record.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("Chart print MRP line")
class CaseManagementPrintMrpUnitTest extends CarlosUnitTestBase {
    private static final int PRINTED = 900001;
    private static final String PRINTED_MRP = "900101";
    private static final String OTHER_MRP = "900202";

    @TempDir
    Path documentDirectory;

    private CaseManagementManager manager;
    private MockHttpServletRequest request;

    @BeforeEach
    void enterAnotherPatientsChartLast() {
        manager = createAndRegisterMock(CaseManagementManager.class);
        createAndRegisterMock(AllergyDao.class);
        createAndRegisterMock(NoteService.class);
        createAndRegisterMock(ProgramManager2.class);
        createAndRegisterMock(ProgramManager.class);
        createAndRegisterMock(PreventionManager.class);
        request = new MockHttpServletRequest();
        // The login-wide bean belongs to another patient, entered after the printed one.
        EctSessionBean other = new EctSessionBean();
        other.demographicNo = "900002";
        other.familyDoctorNo = OTHER_MRP;
        request.getSession().setAttribute("EctSessionBean", other);
    }

    @Test
    @DisplayName("should name the printed patient's MRP when another chart was entered last")
    void shouldPrintPrintedPatientsMrp_whenAnotherChartWasEnteredLast() throws Exception {
        when(manager.getDemoProviderNo(String.valueOf(PRINTED))).thenReturn(PRINTED_MRP);

        assertThat(printedMrp(Map.of(PRINTED_MRP, provider(PRINTED_MRP, "Dr. Alma", "Printed"),
                OTHER_MRP, provider(OTHER_MRP, "Dr. Bert", "Other")))).isEqualTo("Dr. Alma Printed");
    }

    @Test
    @DisplayName("should leave the MRP blank when the printed patient has none, whatever the session bean holds")
    void shouldPrintBlankMrp_whenPrintedPatientHasNoMrp() throws Exception {
        when(manager.getDemoProviderNo(String.valueOf(PRINTED))).thenReturn("");
        EctSessionBean samePatient = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");
        samePatient.demographicNo = String.valueOf(PRINTED);

        assertThat(printedMrp(Map.of(OTHER_MRP, provider(OTHER_MRP, "Dr. Bert", "Other")))).isEmpty();
    }

    @Test
    @DisplayName("should leave the MRP blank when the patient's MRP has no provider record")
    void shouldPrintBlankMrp_whenMrpProviderRecordIsMissing() throws Exception {
        when(manager.getDemoProviderNo(String.valueOf(PRINTED))).thenReturn(PRINTED_MRP);

        assertThat(printedMrp(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("should print the patient's MRP when no chart is open in this login")
    void shouldPrintPrintedPatientsMrp_whenNoChartIsOpen() throws Exception {
        request = new MockHttpServletRequest();
        when(manager.getDemoProviderNo(String.valueOf(PRINTED))).thenReturn(PRINTED_MRP);

        assertThat(printedMrp(Map.of(PRINTED_MRP, provider(PRINTED_MRP, "Dr. Alma", "Printed"))))
                .isEqualTo("Dr. Alma Printed");
    }

    /** Runs the chart-print entry point and returns the MRP it hands the PDF header. */
    private String printedMrp(Map<String, EctProviderData.Provider> providers) throws Exception {
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("CMESort", "")).thenReturn("");
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        try (var configuration = mockStatic(CarlosProperties.class);
             var _ = mockConstruction(EctProviderData.class, (data, context) ->
                     when(data.getProvider(anyString())).thenAnswer(call -> providers.get(call.getArgument(0, String.class))));
             var pdfs = mockConstruction(CaseManagementPrintPdf.class);
             var _ = mockStatic(ConcatPDF.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            new CaseManagementPrint().doPrint(loggedInInfo, PRINTED, false, new String[0],
                    false, false, false, false, false, false, null, null, request, new ByteArrayOutputStream());
            assertThat(pdfs.constructed()).hasSize(1);
        }
        return (String) request.getAttribute("mrp");
    }

    private static EctProviderData.Provider provider(String providerNo, String firstName, String surname) {
        return new EctProviderData().new Provider(providerNo, surname, firstName, "", "", "", "", "", "");
    }
}
