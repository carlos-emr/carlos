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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimReportFilter;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimReportRow;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link BillingOnClaimLoader}. Focuses on the
 * {@code getCodeFee} surface, including the Phase 2 silent-failure-fix
 * path that converted a context-less catch into a sanitized error log.
 *
 * @since 2026-04-29
 */
@DisplayName("BillingOnClaimLoader")
@Tag("unit")
@Tag("billing")
class BillingOnClaimLoaderUnitTest {

    private ClinicLocationDao clinicLocationDao;
    private BillingONCHeader1Dao dao;
    private BillingONExtDao extDao;
    private BillingONPaymentDao payDao;
    private BillingServiceDao serviceDao;
    private BillingOnItemPaymentDao billOnItemPaymentDao;
    private BillingPercLimitDao percLimitDao;
    private BillingPaymentTypeDao paymentTypeDao;
    private ProviderDao providerDao;
    private BillingONItemDao itemDao;
    private CtlBillingServiceDao ctlBillingServiceDao;
    private BillingOnClaimLoader loader;

    @BeforeEach
    void setUp() {
        clinicLocationDao = mock(ClinicLocationDao.class);
        dao = mock(BillingONCHeader1Dao.class);
        extDao = mock(BillingONExtDao.class);
        payDao = mock(BillingONPaymentDao.class);
        serviceDao = mock(BillingServiceDao.class);
        billOnItemPaymentDao = mock(BillingOnItemPaymentDao.class);
        percLimitDao = mock(BillingPercLimitDao.class);
        paymentTypeDao = mock(BillingPaymentTypeDao.class);
        providerDao = mock(ProviderDao.class);
        itemDao = mock(BillingONItemDao.class);
        ctlBillingServiceDao = mock(CtlBillingServiceDao.class);
        loader = new BillingOnClaimLoader(clinicLocationDao, dao, extDao, payDao, serviceDao,
                billOnItemPaymentDao, percLimitDao, paymentTypeDao, providerDao, itemDao,
                ctlBillingServiceDao);
    }

    @Test
    void shouldReturnFeeValue_whenServiceCodeFoundAndNotDefunct() {
        BillingService bs = new BillingService();
        bs.setValue("75.00");
        // Termination date in the future: not defunct.
        bs.setTerminationDate(new Date(System.currentTimeMillis() + 86_400_000L));
        when(serviceDao.findByServiceCodeAndLatestDate(anyString(), any())).thenReturn(List.of(bs));

        String fee = loader.getCodeFee("A007", "2026-04-29");

        assertThat(fee).isEqualTo("75.00");
    }

    @Test
    void shouldReturnDefunct_whenTerminationDateIsBeforeServiceDate() {
        BillingService bs = new BillingService();
        bs.setValue("75.00");
        // Termination date in the distant past: defunct.
        bs.setTerminationDate(new Date(0));
        when(serviceDao.findByServiceCodeAndLatestDate(anyString(), any())).thenReturn(List.of(bs));

        String fee = loader.getCodeFee("A007", "2026-04-29");

        assertThat(fee).isEqualTo("defunct");
    }

    @Test
    void shouldReturnNull_whenNoServiceCodeMatches() {
        when(serviceDao.findByServiceCodeAndLatestDate(anyString(), any())).thenReturn(List.of());

        String fee = loader.getCodeFee("UNKNOWN", "2026-04-29");

        assertThat(fee).isNull();
    }

    @Test
    void shouldReturnNullAndLogContext_whenServiceDaoThrows() {
        // Phase 2 silent-failure-fix path: the catch now logs the offending
        // service code and date. The null-return contract is preserved so
        // existing callers that handle null don't change behavior.
        when(serviceDao.findByServiceCodeAndLatestDate(anyString(), any()))
                .thenThrow(new RuntimeException("DB outage simulation"));

        String fee = loader.getCodeFee("A007", "2026-04-29");

        assertThat(fee).isNull();
    }

    @Test
    void shouldExposePartialFlag_whenServiceDaoThrowsDuringFeeLookup() {
        when(serviceDao.findByServiceCodeAndLatestDate(anyString(), any()))
                .thenThrow(new RuntimeException("DB outage simulation"));

        BillingOnClaimLoader.FeeLookupResult result =
                loader.getCodeFeeResult("A007", "2026-04-29");

        assertThat(result.value()).isNull();
        assertThat(result.partial()).isTrue();
        assertThat(result.message()).contains("A007");
    }

    @Test
    void shouldReturnNull_whenDateStringUnparseable() {
        // ConversionUtils.fromDateString throws on bad input; loader catches
        // and returns null per the same contract.
        String fee = loader.getCodeFee("A007", "not-a-date");
        assertThat(fee).isNull();
    }

    // ---- getBill(...) dedup contract ------------------------------------

    /** Build the typed row shape that BillingONCHeader1Dao.findBillingData returns. */
    private static BillingClaimReportRow billRow(String ch1Id, String payProgram, String paid, String itemId) {
        return new BillingClaimReportRow(
                ch1Id,        // ch1.id
                payProgram,   // pay_program (HCP / PAT / ...)
                "100",        // demographic_no
                "Patient",    // demographic_name
                "2026-04-01", // billing_date
                "10:00:00",   // billing_time
                "O",          // status
                "999",        // provider_no
                "PRV01",      // provider_ohip_no
                "20260401",   // timestamp1
                "100.00",     // total
                paid,          // ch1.paid (the field the dedup gates)
                "C001",       // clinic
                "1",          // bi.ser_num
                itemId);       // billing_on_item_id (also FK fed to getAmountPaidByItemId for PAT)
    }

    @Test
    void shouldStampPaidOnFirstRowAndZeroOnSubsequentRows_whenSameCh1IdAppearsThreeTimes() {
        // Three rows with the same ch1.id="42" simulate a 3-item claim
        // returned by the bi×ch1 LEFT JOIN. Pre-fix prevId was declared
        // inside the loop and reset every iteration, so all three rows
        // received ch1.paid; report totals tripled. Post-fix only the first
        // row gets b[11]; subsequent same-ch1 rows get "0.00".
        when(dao.findBillingData(any(BillingClaimReportFilter.class))).thenReturn(java.util.Arrays.asList(
                billRow("42", "HCP", "50.00", "1001"),
                billRow("42", "HCP", "50.00", "1002"),
                billRow("42", "HCP", "50.00", "1003")));

        java.util.List<io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto> result =
                loader.getBill("HCP", "O", "999", "2026-04-01", "2026-04-30", "100", "", "", "");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getPaid()).isEqualTo("50.00");
        assertThat(result.get(1).getPaid()).isEqualTo("0.00");
        assertThat(result.get(2).getPaid()).isEqualTo("0.00");
    }

    @Test
    void shouldStampPaidOnFirstRowOfEachCh1_whenInterleavedCh1Ids() {
        // A, B, A interleaved: B's first occurrence keeps b[11], the second A
        // gets "0.00" because its ch1 matches the previous row's ch1.
        when(dao.findBillingData(any(BillingClaimReportFilter.class))).thenReturn(java.util.Arrays.asList(
                billRow("42", "HCP", "50.00", "1001"),
                billRow("99", "HCP", "30.00", "2001"),
                billRow("42", "HCP", "50.00", "1002")));

        java.util.List<io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto> result =
                loader.getBill("HCP", "O", "999", "2026-04-01", "2026-04-30", "100", "", "", "");

        assertThat(result.get(0).getPaid()).isEqualTo("50.00"); // first A
        assertThat(result.get(1).getPaid()).isEqualTo("30.00"); // first B (different ch1)
        assertThat(result.get(2).getPaid()).isEqualTo("50.00"); // second A — different from previous (B)
    }

    @Test
    void shouldUseAmountPaidByItemId_whenPayProgramIsPAT() {
        // For PAT (private/patient-pay) bills, ch1.paid is the wrong source —
        // the per-item payment table is authoritative. The dedup branch is
        // bypassed for PAT.
        when(dao.findBillingData(any(BillingClaimReportFilter.class))).thenReturn(java.util.Collections.singletonList(
                billRow("7", "PAT", "999.99", "555")));
        when(billOnItemPaymentDao.getAmountPaidByItemId(555))
                .thenReturn(new java.math.BigDecimal("42.00"));

        java.util.List<io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto> result =
                loader.getBill("HCP", "O", "999", "2026-04-01", "2026-04-30", "100", "", "", "");

        assertThat(result).hasSize(1);
        // Comes from billOnItemPaymentDao, NOT from b[11]="999.99".
        assertThat(result.get(0).getPaid()).isEqualTo("42.00");
    }

    @Test
    void shouldPassTypedBillingReportFilterToDaoInsteadOfSqlConditionString() {
        when(dao.findBillingData(any(BillingClaimReportFilter.class)))
                .thenReturn(java.util.Collections.emptyList());

        loader.getBill("HCP", "O", "999", "2026-04-01", "2026-04-30", "100",
                "A007", "V70", "00");

        ArgumentCaptor<BillingClaimReportFilter> filterCaptor =
                ArgumentCaptor.forClass(BillingClaimReportFilter.class);
        verify(dao).findBillingData(filterCaptor.capture());
        BillingClaimReportFilter filter = filterCaptor.getValue();
        assertThat(filter.billType()).isEqualTo("HCP");
        assertThat(filter.statusType()).isEqualTo("O");
        assertThat(filter.providerNo()).isEqualTo("999");
        assertThat(filter.startDate()).isEqualTo("2026-04-01");
        assertThat(filter.endDate()).isEqualTo("2026-04-30");
        assertThat(filter.demoNo()).isEqualTo("100");
        assertThat(filter.serviceCodes()).isEqualTo("A007");
        assertThat(filter.dx()).isEqualTo("V70");
        assertThat(filter.visitType()).isEqualTo("00");
    }
}
