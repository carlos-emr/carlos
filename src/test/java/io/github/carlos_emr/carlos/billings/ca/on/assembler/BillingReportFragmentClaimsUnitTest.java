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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReportFragmentViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The Ontario Billing Report Center's Billed, Unsettled and OB lists are built from Ontario claims
 * ({@code billing_on_cheader1} and its items), not the retired {@code billing} table (#5061).
 *
 * @since 2026-10-19
 */
@DisplayName("BillingReportFragmentViewModelAssembler Ontario claim lists")
@Tag("unit")
@Tag("billing")
class BillingReportFragmentClaimsUnitTest {

    private static final String PROVIDER = "999998";

    private BillingDao billingDao;
    private BillingONCHeader1Dao claimDao;
    private BillingONItemDao itemDao;
    private BillingReportFragmentViewModelAssembler assembler;

    @BeforeEach
    void setUp() {
        billingDao = mock(BillingDao.class);
        claimDao = mock(BillingONCHeader1Dao.class);
        itemDao = mock(BillingONItemDao.class);
        assembler = new BillingReportFragmentViewModelAssembler(billingDao, claimDao, itemDao, mock(OscarAppointmentDao.class));
    }

    @Test
    void shouldListOpenClaims_forTheBilledReport() {
        when(claimDao.findReportCenterOpenClaims(eq(PROVIDER), any(Date.class), any(Date.class)))
                .thenReturn(List.of(claim(7, "O", PROVIDER, "12.00")));

        BillingReportFragmentViewModel model = assembler.assemble(request(), null, "billed");

        assertThat(model.getBilledRows()).singleElement().satisfies(row -> {
            assertThat(row.billingId()).isEqualTo(7);
            assertThat(row.apptDate()).isEqualTo("2026-01-10");
            assertThat(row.apptTime()).isEqualTo("09:30:00");
            assertThat(row.demoName()).isEqualTo("FAKE-Claim, Patient");
            assertThat(row.reasonText()).isEqualTo("Bill OHIP ");
            assertThat(row.note()).isEqualTo("With Appt. Doctor");
        });
        verifyNoInteractions(billingDao);
    }

    @Test
    void shouldListSubmittedClaims_forTheUnsettledReport() {
        when(claimDao.findReportCenterSubmittedClaims(eq(PROVIDER), any(Date.class), any(Date.class)))
                .thenReturn(List.of(claim(8, "B", "none", "30.00")));

        BillingReportFragmentViewModel model = assembler.assemble(request(), null, "unsettled");

        assertThat(model.getUnsettledRows()).singleElement().satisfies(row -> {
            assertThat(row.billingId()).isEqualTo(8);
            assertThat(row.reasonCode()).isEqualTo("B");
            assertThat(row.note()).isEqualTo("No Appt / INR");
        });
        verifyNoInteractions(billingDao);
    }

    @Test
    void shouldListObstetricClaims_withTheirItemCodesAndTheSettledTotal() {
        when(claimDao.findReportCenterClaimsWithServiceCodes(eq(PROVIDER), any(Date.class), any(Date.class),
                eq(BillingReportFragmentViewModelAssembler.OB_SERVICE_CODES)))
                .thenReturn(List.of(claim(9, "S", PROVIDER, "120.5"), claim(10, "B", PROVIDER, "30.00")));
        when(itemDao.findByCh1IdsExcludingDeleted(List.of(9, 10)))
                .thenReturn(List.of(item(9, "P006A"), item(9, "A007A"), item(10, "P011A")));

        BillingReportFragmentViewModel model = assembler.assemble(request(), null, "billob");

        assertThat(model.getBillobRows()).hasSize(2);
        assertThat(model.getBillobRows().get(0).serviceCodes())
                .containsExactly("P006A", "A007A", "", "", "", "", "", "", "", "");
        assertThat(model.getBillobRows().get(0).total()).isEqualTo("120.50");
        assertThat(model.getBillobRows().get(1).serviceCodes()).startsWith("P011A", "");
        assertThat(model.getBillobTotal()).as("only the settled claim counts as paid").isEqualTo("120.50");
        verifyNoInteractions(billingDao);
    }

    private static BillingONCHeader1 claim(int id, String status, String apptProviderNo, String total) {
        BillingONCHeader1 claim = new BillingONCHeader1();
        ReflectionTestUtils.setField(claim, "id", id);
        claim.setProviderNo(PROVIDER);
        claim.setApptProviderNo(apptProviderNo);
        claim.setStatus(status);
        claim.setDemographicName("FAKE-Claim, Patient");
        claim.setBillingDate(ConversionUtils.fromDateString("2026-01-10"));
        claim.setBillingTime(ConversionUtils.fromTimeStringNoSeconds("09:30"));
        claim.setTotal(new BigDecimal(total));
        return claim;
    }

    private static BillingONItem item(int claimId, String serviceCode) {
        BillingONItem item = new BillingONItem();
        item.setCh1Id(claimId);
        item.setServiceCode(serviceCode);
        return item;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("providerview", PROVIDER);
        request.setParameter("xml_vdate", "2026-01-01");
        request.setParameter("xml_appointment_date", "2026-01-31");
        return request;
    }
}
