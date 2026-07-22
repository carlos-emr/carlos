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

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.RaHeader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OnRaSummaryTotalsService}.
 *
 * <p>Pre-fix: {@code mergeTotals} silently returned on
 * {@code NumberFormatException(raNoStr)} or when the header lookup returned
 * null — the caller (ViewOnGenRaSummary2Action) trusted the merge succeeded
 * and the operator's grid silently drifted out of sync. Now both paths throw
 * {@link BillingValidationException} so the struts exception-mapping renders
 * the validation page.
 *
 * @since 2026-04-30
 */
@DisplayName("OnRaSummaryTotalsService")
@Tag("unit")
@Tag("billing")
class OnRaSummaryTotalsServiceUnitTest {

    private RaHeaderDao raHeaderDao;
    private OnRaSummaryTotalsService service;

    @BeforeEach
    void setUp() {
        raHeaderDao = mock(RaHeaderDao.class);
        service = new OnRaSummaryTotalsService(raHeaderDao);
    }

    @Test
    void shouldMergePersist_whenAllInputsValid() {
        RaHeader header = new RaHeader();
        header.setContent("<xml_transaction><row>existing</row></xml_transaction>"
                + "<xml_balancefwd><claimsAdjustment>1</claimsAdjustment></xml_balancefwd>");
        when(raHeaderDao.find(42)).thenReturn(header);

        service.mergeTotals("42",
                new BigDecimal("10.00"), new BigDecimal("20.00"),
                new BigDecimal("30.00"), new BigDecimal("40.00"),
                new BigDecimal("50.00"));

        ArgumentCaptor<RaHeader> captor = ArgumentCaptor.forClass(RaHeader.class);
        verify(raHeaderDao).merge(captor.capture());
        String content = captor.getValue().getContent();
        // Existing transaction + balanceFwd preserved
        assertThat(content).contains("<xml_transaction><row>existing</row></xml_transaction>");
        assertThat(content).contains("<xml_balancefwd><claimsAdjustment>1</claimsAdjustment></xml_balancefwd>");
        // New totals written
        assertThat(content).contains("<xml_local>10.00</xml_local>");
        assertThat(content).contains("<xml_total>20.00</xml_total>");
        assertThat(content).contains("<xml_other_total>30.00</xml_other_total>");
        assertThat(content).contains("<xml_ob_total>40.00</xml_ob_total>");
        assertThat(content).contains("<xml_co_total>50.00</xml_co_total>");
    }

    @Test
    void shouldCoalesceNullTotals_toZero() {
        RaHeader header = new RaHeader();
        header.setContent("");
        when(raHeaderDao.find(7)).thenReturn(header);

        service.mergeTotals("7", null, null, null, null, null);

        ArgumentCaptor<RaHeader> captor = ArgumentCaptor.forClass(RaHeader.class);
        verify(raHeaderDao).merge(captor.capture());
        assertThat(captor.getValue().getContent()).contains("<xml_local>0</xml_local>");
    }

    @Test
    void shouldThrow_whenRaNoNonNumeric() {
        // Pre-fix this returned silently; now surfaces as BillingValidationException.
        assertThatThrownBy(() ->
                service.mergeTotals("not-a-number",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("not-a-number")
                .hasMessageContaining("not a valid integer");

        verify(raHeaderDao, never()).merge(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldThrow_whenRaNoIsNull() {
        assertThatThrownBy(() ->
                service.mergeTotals((String) null,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("raNo is missing");

        verify(raHeaderDao, never()).merge(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldThrow_whenHeaderNotFound() {
        when(raHeaderDao.find(99)).thenReturn(null);

        assertThatThrownBy(() ->
                service.mergeTotals("99",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("no RaHeader")
                .hasMessageContaining("99");

        verify(raHeaderDao, never()).merge(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNoOp_whenViewModelIsNull() {
        // Distinct contract: the view-model overload tolerates null model
        // (callers may invoke after a no-op render); no exception, no merge.
        service.mergeTotals(null);
        verify(raHeaderDao, never()).find(org.mockito.ArgumentMatchers.anyInt());
        verify(raHeaderDao, never()).merge(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldThrowBillingValidationException_whenViewModelIsPartial() {
        // Round-5 contract: when the upstream RA summary excluded rows due
        // to amountUnreadable=true, the totals understate the true
        // reconciliation. mergeTotals must refuse to overwrite
        // RaHeader.content rather than persist the partial figure.
        io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnRaSummaryViewModel partial =
                io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnRaSummaryViewModel.builder()
                        .raNo("42")
                        .localTotal(new BigDecimal("100.00"))
                        .payTotal(new BigDecimal("50.00"))
                        .otherTotal(BigDecimal.ZERO)
                        .obTotal(BigDecimal.ZERO)
                        .coTotal(BigDecimal.ZERO)
                        .unreadableRowCount(3)  // <-- triggers isPartial() == true
                        .build();

        assertThatThrownBy(() -> service.mergeTotals(partial))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("3");  // count surfaced in the message

        // Critical: when partial, mergeTotals must NOT persist anything.
        verify(raHeaderDao, never()).find(org.mockito.ArgumentMatchers.anyInt());
        verify(raHeaderDao, never()).merge(org.mockito.ArgumentMatchers.any());
    }
}
