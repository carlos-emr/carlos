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

import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import io.github.carlos_emr.carlos.commn.model.RaHeader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioral tests for {@link OnRaSettlementService}, the mutation path
 * behind {@code onGenRAsettle.jsp} and {@code onGenRAsettle35.jsp}. Covers
 * both {@link OnRaSettlementService.Mode} variants plus the input-validation
 * pre-checks for the settle-vs-clear path.
 */
@DisplayName("OnRaSettlementService")
@Tag("unit")
@Tag("billing")
class OnRaSettlementServiceUnitTest {

    private RaHeaderDao raHeaderDao;
    private RaDetailDao raDetailDao;
    private BillingRaReportService billingRaReportService;
    private OnRaSettlementService service;

    @BeforeEach
    void setUp() {
        raHeaderDao = mock(RaHeaderDao.class);
        raDetailDao = mock(RaDetailDao.class);
        billingRaReportService = mock(BillingRaReportService.class);
        service = new OnRaSettlementService(raHeaderDao, raDetailDao, billingRaReportService);
    }

    @Test
    void shouldThrowAndDoNothing_whenRaNoIsNull() {
        // The legacy boolean-return contract was silently ignored by both
        // ViewOnGenRaSettle2Action and ViewOnGenRaSettle352Action, so a null
        // raNo produced "Settle complete" with no rows actually settled.
        // Throwing BillingValidationException routes the failure to the
        // action's exception mapping and renders the validation page.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.settle(null, OnRaSettlementService.Mode.STANDARD))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("rano");

        verify(raDetailDao, never()).search_raerror35(anyInt(), anyString(), anyString(), anyString());
        verify(billingRaReportService, never()).updateBillingStatus(anyString(), anyString());
    }

    @Test
    void shouldThrowAndDoNothing_whenRaNoIsBlank() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.settle("", OnRaSettlementService.Mode.STANDARD))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class);

        verify(raDetailDao, never()).search_raerror35(anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldThrowAndDoNothing_whenRaNoIsNonNumeric() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.settle("not-a-number", OnRaSettlementService.Mode.STANDARD))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class);

        verify(raDetailDao, never()).search_raerror35(anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldSettleAllNoErrorBillsAndMarkHeaderS_whenStandardMode() {
        // Two no-error bills, no I2/35 errors. Expect both bills settled and
        // RA header status flipped to "S".
        when(raDetailDao.search_raerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(raDetailDao.search_ranoerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(List.of(101, 102));
        RaHeader header = new RaHeader();
        when(raHeaderDao.find(Integer.valueOf(42))).thenReturn(header);

        boolean ran = service.settle("42", OnRaSettlementService.Mode.STANDARD);

        assertThat(ran).isTrue();
        verify(billingRaReportService).updateBillingStatus("101", "S");
        verify(billingRaReportService).updateBillingStatus("102", "S");
        verify(raHeaderDao).merge(header);
        assertThat(header.getStatus()).isEqualTo("S");
    }

    @Test
    void shouldSkipBillsWithErrors_whenStandardMode() {
        // Bill 101 has an I2/35 error — must NOT be settled. Bill 102 is
        // clean — must be settled.
        RaDetail err = new RaDetail();
        err.setBillingNo(101);
        err.setServiceCode("A001A");
        when(raDetailDao.search_raerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(List.of(err));
        when(raDetailDao.search_ranoerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(List.of(101, 102));
        when(raHeaderDao.find(Integer.valueOf(42))).thenReturn(new RaHeader());

        service.settle("42", OnRaSettlementService.Mode.STANDARD);

        verify(billingRaReportService, never()).updateBillingStatus(eq("101"), anyString());
        verify(billingRaReportService).updateBillingStatus("102", "S");
    }

    @Test
    void shouldMarkHeaderF_whenI235WithQcodesMode() {
        when(raDetailDao.search_raerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(raDetailDao.search_ranoerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(raDetailDao.search_ranoerrorQ(eq(42), anyString()))
                .thenReturn(Collections.emptyList());
        RaHeader header = new RaHeader();
        when(raHeaderDao.find(Integer.valueOf(42))).thenReturn(header);

        boolean ran = service.settle("42", OnRaSettlementService.Mode.I2_35_WITH_QCODES);

        assertThat(ran).isTrue();
        assertThat(header.getStatus()).isEqualTo("F");
    }

    @Test
    void shouldAlsoSettleBillsWithOnlyQcodeErrors_whenI235WithQcodesMode() {
        // Bill 101 has only a Q020A error — settles. Bill 102 has both a
        // Q-code AND a non-Q-code → in errorBillNoQ → must NOT settle.
        RaDetail q = new RaDetail();
        q.setBillingNo(101);
        q.setServiceCode("Q020A");
        RaDetail mixed1 = new RaDetail();
        mixed1.setBillingNo(102);
        mixed1.setServiceCode("Q020A");
        RaDetail mixed2 = new RaDetail();
        mixed2.setBillingNo(102);
        mixed2.setServiceCode("A007A");
        when(raDetailDao.search_raerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(List.of(q, mixed1, mixed2));
        when(raDetailDao.search_ranoerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(raDetailDao.search_ranoerrorQ(eq(42), anyString()))
                .thenReturn(List.of(101, 102));
        when(raHeaderDao.find(Integer.valueOf(42))).thenReturn(new RaHeader());

        service.settle("42", OnRaSettlementService.Mode.I2_35_WITH_QCODES);

        verify(billingRaReportService).updateBillingStatus("101", "S");
        verify(billingRaReportService, never()).updateBillingStatus(eq("102"), anyString());
    }

    @Test
    void shouldDedupeRepeatedSettleCalls_whenAccountAppearsInMultipleQueries() {
        // Bill 101 surfaces in both the no-error AND the no-error-Q queries.
        // The legacy ArrayList implementation issued two updateBillingStatus
        // round-trips for the same account; LinkedHashSet now dedupes.
        when(raDetailDao.search_raerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(raDetailDao.search_ranoerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(List.of(101));
        when(raDetailDao.search_ranoerrorQ(eq(42), anyString()))
                .thenReturn(List.of(101));
        when(raHeaderDao.find(Integer.valueOf(42))).thenReturn(new RaHeader());

        service.settle("42", OnRaSettlementService.Mode.I2_35_WITH_QCODES);

        verify(billingRaReportService, times(1)).updateBillingStatus("101", "S");
    }

    @Test
    void shouldStillReturnTrue_whenRaHeaderNotFound() {
        // Settle proceeds even if the header lookup misses; the audit-trail
        // bill writes still go through. The header.merge is just skipped.
        when(raDetailDao.search_raerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(raDetailDao.search_ranoerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(List.of(101));
        when(raHeaderDao.find(Integer.valueOf(42))).thenReturn(null);

        boolean ran = service.settle("42", OnRaSettlementService.Mode.STANDARD);

        assertThat(ran).isTrue();
        verify(raHeaderDao, never()).merge(org.mockito.ArgumentMatchers.any(RaHeader.class));
        verify(billingRaReportService).updateBillingStatus("101", "S");
    }

    @Test
    void shouldPassRequestedAccountStatusToReportService_whenSettling() {
        // Verify the literal "S" status string that the JSP-era code wrote.
        when(raDetailDao.search_raerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(raDetailDao.search_ranoerror35(eq(42), anyString(), anyString(), anyString()))
                .thenReturn(List.of(101));
        when(raHeaderDao.find(Integer.valueOf(42))).thenReturn(new RaHeader());

        service.settle("42", OnRaSettlementService.Mode.STANDARD);

        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(billingRaReportService).updateBillingStatus(eq("101"), status.capture());
        assertThat(status.getValue()).isEqualTo("S");
    }
}
