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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingOnNewReportPaidBillingRow;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingOnNewReportUnpaidRow;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;
import io.github.carlos_emr.carlos.commn.dao.projection.BillingOnNewReportPaidRaDetailRow;
import io.github.carlos_emr.carlos.commn.dao.projection.BillingOnNewReportUnbilledRow;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnNewReportViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.ReportProviderDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BillingOnNewReportViewModelAssembler} report-center view state assembly. */
@DisplayName("BillingOnNewReportViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingOnNewReportViewModelAssemblerUnitTest {

    @Test
    void shouldLeavePlainReportCellTextUnencoded_forJspContextEncoding() {
        BillingOnNewReportViewModel.ReportCell cell =
                BillingOnNewReportViewModelAssembler.textCell("<img src=x onerror=alert(1)>");

        assertThat(cell.text()).isEqualTo("<img src=x onerror=alert(1)>");
        assertThat(cell.popupUrl()).isEmpty();
    }

    @Test
    void shouldBuildBillingNoPopupCell_withEncodedUrlParameter() {
        BillingOnNewReportViewModel.ReportCell cell = BillingOnNewReportViewModelAssembler.buildBillingNoLinkWithTitle(
                "/ctx",
                "123\" onclick=\"alert(1)",
                "Bill <OHIP>");

        assertThat(cell.popupUrl()).contains("billing_no=123%22+onclick%3D%22alert%281%29");
        assertThat(cell.text()).isEqualTo("123\" onclick=\"alert(1)");
        assertThat(cell.title()).isEqualTo("Bill <OHIP>");
    }

    @Test
    void shouldRenderUnbilledRows_fromAppointmentDaoProjection() {
        ReportProviderDao reportProviderDao = mock(ReportProviderDao.class);
        SiteDao siteDao = mock(SiteDao.class);
        OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
        BillingONCHeader1Dao headerDao = mock(BillingONCHeader1Dao.class);
        BillingDao billingDao = mock(BillingDao.class);
        RaDetailDao raDetailDao = mock(RaDetailDao.class);
        when(reportProviderDao.search_reportprovider("billingreport")).thenReturn(Collections.emptyList());
        when(appointmentDao.findBillingOnNewReportUnbilledRows("999", "2026-04-01", "2026-04-30"))
                .thenReturn(List.of(new BillingOnNewReportUnbilledRow(
                        "77", "999", "2026-04-01", "09:30:00", "100",
                        "Patient <One>", "Needs <review>", "SITE-A")));

        BillingOnNewReportViewModelAssembler assembler = new BillingOnNewReportViewModelAssembler(
                reportProviderDao, siteDao, appointmentDao, headerDao, billingDao, raDetailDao);

        BillingOnNewReportViewModel model = assembler.assemble(reportRequest("unbilled"), null);

        assertThat(model.getColumnHeaders()).containsExactly(
                "SERVICE DATE", "TIME", "PATIENT", "DESCRIPTION", "COMMENTS");
        assertThat(model.getRows()).hasSize(1);
        assertThat(cellText(model, 0, "SERVICE DATE")).isEqualTo("2026-04-01");
        assertThat(cellText(model, 0, "TIME")).isEqualTo("09:30");
        assertThat(cellText(model, 0, "PATIENT")).isEqualTo("Patient <One>");
        assertThat(cellText(model, 0, "DESCRIPTION")).isEqualTo("Needs <review>");
        assertThat(cell(model, 0, "COMMENTS").popupUrl()).contains("appointment_no=77");
        verify(appointmentDao).findBillingOnNewReportUnbilledRows("999", "2026-04-01", "2026-04-30");
    }

    @Test
    void shouldRenderPaidRows_fromBillingAndRaDetailDaoProjections() {
        ReportProviderDao reportProviderDao = mock(ReportProviderDao.class);
        SiteDao siteDao = mock(SiteDao.class);
        OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
        BillingONCHeader1Dao headerDao = mock(BillingONCHeader1Dao.class);
        BillingDao billingDao = mock(BillingDao.class);
        RaDetailDao raDetailDao = mock(RaDetailDao.class);
        when(reportProviderDao.search_reportprovider("billingreport")).thenReturn(Collections.emptyList());
        when(billingDao.findBillingOnNewReportPaidBillings("999", "2026-04-01", "2026-04-30"))
                .thenReturn(List.of(new BillingOnNewReportPaidBillingRow("42", "100.00")));
        when(raDetailDao.findBillingOnNewReportPaidRaDetails(anyList()))
                .thenReturn(List.of(
                        new BillingOnNewReportPaidRaDetailRow("42", "80.00", "30.00", "HIN1", "20260401"),
                        new BillingOnNewReportPaidRaDetailRow("42", "20.00", "5.00", "HIN1", "20260401")));

        BillingOnNewReportViewModelAssembler assembler = new BillingOnNewReportViewModelAssembler(
                reportProviderDao, siteDao, appointmentDao, headerDao, billingDao, raDetailDao);

        BillingOnNewReportViewModel model = assembler.assemble(reportRequest("paid"), null);

        assertThat(model.getColumnHeaders()).containsExactly(
                "No", "Billing No", "HIN", "Claim", "Paid", "Billing Date");
        assertThat(model.getRows()).hasSize(1);
        assertThat(cell(model, 0, "Billing No").popupUrl()).contains("billing_no=42");
        assertThat(cellText(model, 0, "Claim")).isEqualTo("100.00");
        assertThat(cellText(model, 0, "Paid")).isEqualTo("35.00");
        assertThat(model.getTotalRow()).containsExactly("Total", "", "", "100.00", "35.00", "");
        verify(billingDao).findBillingOnNewReportPaidBillings("999", "2026-04-01", "2026-04-30");
        verify(raDetailDao).findBillingOnNewReportPaidRaDetails(List.of(42));
    }

    @Test
    void shouldTreatBlankUnpaidTotalAsZero_whenBuildingTotals() {
        ReportProviderDao reportProviderDao = mock(ReportProviderDao.class);
        SiteDao siteDao = mock(SiteDao.class);
        OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
        BillingONCHeader1Dao headerDao = mock(BillingONCHeader1Dao.class);
        BillingDao billingDao = mock(BillingDao.class);
        RaDetailDao raDetailDao = mock(RaDetailDao.class);
        when(reportProviderDao.search_reportprovider("billingreport")).thenReturn(Collections.emptyList());
        when(billingDao.findBillingOnNewReportUnpaidRows("999", "2026-04-01", "2026-04-30"))
                .thenReturn(List.of(new BillingOnNewReportUnpaidRow(
                        "42", "2026-04-01", "09:30", "Patient",
                        "O", "111", "999", "")));

        BillingOnNewReportViewModelAssembler assembler = new BillingOnNewReportViewModelAssembler(
                reportProviderDao, siteDao, appointmentDao, headerDao, billingDao, raDetailDao);

        BillingOnNewReportViewModel model = assembler.assemble(reportRequest("unpaid"), null);

        assertThat(model.getRows()).hasSize(1);
        assertThat(model.getTotalRow()).contains("0.00");
    }

    @Test
    void shouldThrowBillingDataLoadException_whenPaidAmountIsMalformed() {
        ReportProviderDao reportProviderDao = mock(ReportProviderDao.class);
        SiteDao siteDao = mock(SiteDao.class);
        OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
        BillingONCHeader1Dao headerDao = mock(BillingONCHeader1Dao.class);
        BillingDao billingDao = mock(BillingDao.class);
        RaDetailDao raDetailDao = mock(RaDetailDao.class);
        when(reportProviderDao.search_reportprovider("billingreport")).thenReturn(Collections.emptyList());
        when(billingDao.findBillingOnNewReportPaidBillings("999", "2026-04-01", "2026-04-30"))
                .thenReturn(List.of(new BillingOnNewReportPaidBillingRow("42", "100.00")));
        when(raDetailDao.findBillingOnNewReportPaidRaDetails(anyList()))
                .thenReturn(List.of(new BillingOnNewReportPaidRaDetailRow(
                        "42", "not-money", "30.00", "HIN1", "20260401")));

        BillingOnNewReportViewModelAssembler assembler = new BillingOnNewReportViewModelAssembler(
                reportProviderDao, siteDao, appointmentDao, headerDao, billingDao, raDetailDao);

        assertThatThrownBy(() -> assembler.assemble(reportRequest("paid"), null))
                .isInstanceOf(BillingDataLoadException.class)
                .hasCauseInstanceOf(NumberFormatException.class)
                .satisfies(ex -> {
                    BillingDataLoadException dataLoad = (BillingDataLoadException) ex;
                    assertThat(dataLoad.phase()).isEqualTo(BillingDataLoadException.Phase.DAO_QUERY);
                    assertThat(dataLoad.context())
                            .containsEntry("reportAction", "paid")
                            .containsEntry("providerview", "999");
                });
    }

    @Test
    void shouldThrowBillingDataLoadExceptionWithContext_whenReportDaoFails() {
        ReportProviderDao reportProviderDao = mock(ReportProviderDao.class);
        SiteDao siteDao = mock(SiteDao.class);
        OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
        BillingONCHeader1Dao headerDao = mock(BillingONCHeader1Dao.class);
        BillingDao billingDao = mock(BillingDao.class);
        RaDetailDao raDetailDao = mock(RaDetailDao.class);
        when(reportProviderDao.search_reportprovider("billingreport")).thenReturn(Collections.emptyList());
        when(appointmentDao.findBillingOnNewReportUnbilledRows("999", "2026-04-01", "2026-04-30"))
                .thenThrow(new RuntimeException("database unavailable"));

        BillingOnNewReportViewModelAssembler assembler = new BillingOnNewReportViewModelAssembler(
                reportProviderDao, siteDao, appointmentDao, headerDao, billingDao, raDetailDao);

        assertThatThrownBy(() -> assembler.assemble(reportRequest("unbilled"), null))
                .isInstanceOf(BillingDataLoadException.class)
                .hasMessageContaining("billingONNewReport")
                .satisfies(ex -> {
                    BillingDataLoadException dataLoad = (BillingDataLoadException) ex;
                    assertThat(dataLoad.phase()).isEqualTo(BillingDataLoadException.Phase.DAO_QUERY);
                    assertThat(dataLoad.context())
                            .containsEntry("reportAction", "unbilled")
                            .containsEntry("providerview", "999")
                            .containsEntry("xml_vdate", "2026-04-01")
                            .containsEntry("xml_appointment_date", "2026-04-30")
                            .containsEntry("site", "");
                });
    }

    private static HttpServletRequest reportRequest(String action) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContextPath()).thenReturn("/ctx");
        when(request.getParameter("providerview")).thenReturn("999");
        when(request.getParameter("xml_vdate")).thenReturn("2026-04-01");
        when(request.getParameter("xml_appointment_date")).thenReturn("2026-04-30");
        when(request.getParameter("site")).thenReturn("");
        when(request.getParameter("reportAction")).thenReturn(action);
        return request;
    }

    private static BillingOnNewReportViewModel.ReportCell cell(
            BillingOnNewReportViewModel model, int rowIndex, String header) {
        return model.getRows().get(rowIndex).cells().get(header);
    }

    private static String cellText(BillingOnNewReportViewModel model, int rowIndex, String header) {
        return cell(model, rowIndex, header).text();
    }
}
