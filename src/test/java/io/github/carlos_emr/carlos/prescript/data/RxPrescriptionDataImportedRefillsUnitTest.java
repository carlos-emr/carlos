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
package io.github.carlos_emr.carlos.prescript.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrintPdf;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsTemporalReadService;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayRx2Action;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.encounter.pageUtil.NavBarDisplayDAO;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.provider.web.CppPreferencesUIBean;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.ByteArrayOutputStream;
import java.sql.Date;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** Optional source refill facts must survive both legacy loading paths without inventing zero. */
@Tag("unit")
@Tag("prescription")
class RxPrescriptionDataImportedRefillsUnitTest extends CarlosUnitTestBase {
    static Stream<Arguments> importedCases() {
        return Stream.of(true, false).flatMap(byId -> Stream.of("CURRENT", "HISTORICAL")
                .flatMap(state -> refillCases().map(values -> Arguments.of(byId, state,
                        values.get()[0], values.get()[1], values.get()[2]))));
    }

    static Stream<Arguments> refillCases() {
        return Stream.of(Arguments.of(30, null, "Refill: Qty:30 Duration:Unknown"),
                Arguments.of(null, 7, "Refill: Qty:Unknown Duration:7 Days"),
                Arguments.of(null, null, ""),
                Arguments.of(30, 7, "Refill: Qty:30 Duration:7 Days"));
    }

    @ParameterizedTest
    @MethodSource("importedCases")
    void shouldPreserveIndependentNullRefills_whenLoadingAndResyncingImportedMedication(
            boolean byId, String state, Integer quantity, Integer duration, String expected) {
        Drug source = medication(state, quantity, duration);
        DrugDao dao = createAndRegisterMock(DrugDao.class);
        when(dao.find(27)).thenReturn(source);
        var data = new RxPrescriptionData();
        var result = byId ? data.getPrescription(27) : data.toPrescription(source, 11);
        assertThat(result.getRefillQuantity()).isEqualTo(quantity);
        assertThat(result.getRefillDuration()).isEqualTo(duration);
        assertThat(result.getCdsCurrentness()).isEqualTo(state);
        assertThat(result.getFullOutLine()).contains("Source medicine one tablet daily");
        if (expected.isEmpty()) assertThat(result.getFullOutLine()).doesNotContain("Refill:");
        else assertThat(result.getFullOutLine()).contains(expected).doesNotContain("Duration:0 Days", "Qty:0");
        doAnswer(call -> { ((Drug) call.getArgument(0)).setId(28); return null; }).when(dao).persist(any(Drug.class));
        assertThat(result.Save()).isTrue();
        var written = org.mockito.ArgumentCaptor.forClass(Drug.class);
        verify(dao).persist(written.capture());
        assertThat(written.getValue().getRefillQuantity()).isEqualTo(quantity);
        assertThat(written.getValue().getRefillDuration()).isEqualTo(duration);
        assertThat(written.getValue().getCdsCurrentness()).isEqualTo(state);
    }

    @ParameterizedTest
    @MethodSource("refillCases")
    void shouldRenderUnknownRefillCompanions_inActualCppAndPdfConsumers(
            Integer quantity, Integer duration, String expected) throws Exception {
        var medication = new RxPrescriptionData().toPrescription(medication("CURRENT", quantity, duration), 11);
        var temporal = new CdsTemporalReadService(mock(PartialDateDao.class));
        registerMock(CdsTemporalReadService.class, temporal);
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_rx"), eq("r"), isNull())).thenReturn(true);
        var user = new LoggedInInfo(); var provider = new Provider(); provider.setProviderNo("999998");
        user.setLoggedInProvider(provider);
        var request = new MockHttpServletRequest(); request.setContextPath("/carlos");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        var clinic = new Clinic(); clinic.setId(1); clinic.setClinicName("Synthetic clinic");
        when(createAndRegisterMock(ClinicDAO.class).getClinic()).thenReturn(clinic);
        try (var servlet = mockStatic(ServletActionContext.class);
                var prescriptions = mockConstruction(RxPrescriptionData.class, (mock, context) ->
                        when(mock.getUniquePrescriptionsByPatient(11)).thenReturn(new RxPrescriptionData.Prescription[]{medication}));
                var preferences = mockConstruction(CppPreferencesUIBean.class, (mock, context) -> {
                    when(mock.getEnable()).thenReturn("on");
                })) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            var action = spy(new EctDisplayRx2Action()); doReturn("Medications").when(action).getText(anyString());
            var bean = new EctSessionBean(); bean.setDemographicNo("11"); bean.providerNo = "999998";
            var display = new NavBarDisplayDAO(); assertThat(action.getInfo(bean, request, display)).isTrue();
            assertThat(display.numItems()).isEqualTo(1);
            String cpp = display.getItem(0).getLinkTitle();
            var output = new ByteArrayOutputStream();
            var pdf = new CaseManagementPrintPdf(request, output);
            pdf.printDocHeaderFooter(); pdf.printRx("11"); pdf.finish();
            try (var document = Loader.loadPDF(output.toByteArray())) {
                String text = new PDFTextStripper().getText(document);
                for (String rendered : new String[]{cpp, text}) {
                    assertThat(rendered).contains("Source medicine one tablet daily");
                    if (expected.isEmpty()) assertThat(rendered).doesNotContain("Refill:");
                    else assertThat(rendered).contains(expected).doesNotContain("Duration:0 Days", "Qty:0");
                }
            }
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldKeepNativeZeroDefaultsAndOutline_whenNoCdsProvenanceExists(boolean byId) {
        var source = medication(null, 30, null);
        when(createAndRegisterMock(DrugDao.class).find(27)).thenReturn(source);
        var data = new RxPrescriptionData();
        var result = byId ? data.getPrescription(27) : data.toPrescription(source, 11);
        assertThat(result.getRefillDuration()).isZero();
        assertThat(result.getFullOutLine()).contains("Refill: Qty:30 Duration:0 Days");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldKeepAnExplicitZeroAndSubstitutionAssertion_whenLoadingImportedRefills(boolean byId) {
        var source = medication("CURRENT", 30, 0);
        source.setNoSubs(true);
        when(createAndRegisterMock(DrugDao.class).find(27)).thenReturn(source);
        var data = new RxPrescriptionData();
        var result = byId ? data.getPrescription(27) : data.toPrescription(source, 11);
        assertThat(result.getRefillDuration()).isZero();
        assertThat(result.getFullOutLine()).contains("SUBSTITUTION NOT ALLOWED", "Refill: Qty:30 Duration:0 Days")
                .doesNotContain("Unknown");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"CURRENT,true", "CURRENT,false", "HISTORICAL,true", "HISTORICAL,false"})
    void shouldKeepImportedTreatmentEndIndependent_whenSavingDispenseDurationAndRefills(String state, boolean unknownEnd) {
        var source = medication(state, 30, 7);
        source.setDuration("10"); source.setDurUnit("D"); source.setRepeat(2);
        var expected = unknownEnd ? null : Date.valueOf("2024-08-09"); source.setEndDate(expected);
        var dao = createAndRegisterMock(DrugDao.class);
        doAnswer(call -> { ((Drug) call.getArgument(0)).setId(28); return null; }).when(dao).persist(any(Drug.class));
        var result = new RxPrescriptionData().toPrescription(source, 11);
        assertThat(result.Save()).isTrue();
        var written = org.mockito.ArgumentCaptor.forClass(Drug.class); verify(dao).persist(written.capture());
        assertThat(written.getValue().getEndDate()).isEqualTo(expected);
        assertThat(written.getValue().getDuration()).isEqualTo("10");
        assertThat(written.getValue().getRepeat()).isEqualTo(2);
        assertThat(written.getValue().getCdsCurrentness()).isEqualTo(state);
    }

    @org.junit.jupiter.api.Test
    void shouldKeepNativeEndCalculation_whenSavingANativePrescription() {
        var source = medication(null, 30, 7);
        source.setDuration("10"); source.setDurUnit("D"); source.setRepeat(2);
        var dao = createAndRegisterMock(DrugDao.class);
        doAnswer(call -> { ((Drug) call.getArgument(0)).setId(28); return null; }).when(dao).persist(any(Drug.class));
        var result = new RxPrescriptionData().toPrescription(source, 11);
        assertThat(result.Save()).isTrue();
        var written = org.mockito.ArgumentCaptor.forClass(Drug.class); verify(dao).persist(written.capture());
        assertThat(written.getValue().getEndDate()).isEqualTo(Date.valueOf("2024-04-03"));
    }

    private static Drug medication(String state, Integer quantity, Integer duration) {
        var drug = new Drug(); drug.setId(27); drug.setDemographicId(11); drug.setProviderNo("999998");
        drug.setCdsCurrentness(state); drug.setRxDate(Date.valueOf("2024-03-04"));
        drug.setSpecial("Source medicine one tablet daily"); drug.setCustomName("Source medicine");
        if (quantity != null) drug.setRefillQuantity(quantity);
        if (duration != null) drug.setRefillDuration(duration);
        return drug;
    }
}
