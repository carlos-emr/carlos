/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("billing-bc")
class BillingDiagnosisServiceUnitTest {
    @Test
    void shouldKeepFirstOccurrenceAndOriginalCode_whenBillsContainRepeatedOrBlankDiagnoses() {
        BillingmasterDAO bills = mock(BillingmasterDAO.class);
        DiagnosticCodeDao codes = mock(DiagnosticCodeDao.class);
        when(bills.findRecentDiagnosisRows("42")).thenReturn(List.of(
                new BillingmasterDAO.DiagnosisRow("250", " 250 ", null, "20261102"),
                new BillingmasterDAO.DiagnosisRow("250", "  ", "unknown", "20261001")));
        DiagnosticCode known = new DiagnosticCode();
        known.setDescription("Synthetic description");
        when(codes.findByCode("250")).thenReturn(known);

        assertThat(new BillingDiagnosisService(bills, codes).findRecentDiagnoses("42"))
                .containsExactly(new BillingDiagnosisService.DiagnosisDto("20261102", "250", "Synthetic description"),
                        new BillingDiagnosisService.DiagnosisDto("20261102", " 250 ", null),
                        new BillingDiagnosisService.DiagnosisDto("20261001", "unknown", null));
        verify(codes, times(1)).findByCode("250");
        verify(codes).findByCode(" 250 ");
        verify(codes).findByCode("unknown");
        verifyNoMoreInteractions(codes);
    }

    @Test
    void shouldReturnEmptyList_whenPatientHasNoBills() {
        BillingmasterDAO bills = mock(BillingmasterDAO.class);
        DiagnosticCodeDao codes = mock(DiagnosticCodeDao.class);
        when(bills.findRecentDiagnosisRows("42")).thenReturn(List.of());
        assertThat(new BillingDiagnosisService(bills, codes).findRecentDiagnoses("42")).isEmpty();
        verifyNoInteractions(codes);
    }
}
