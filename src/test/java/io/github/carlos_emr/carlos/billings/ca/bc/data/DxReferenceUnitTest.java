/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.data;

import io.github.carlos_emr.carlos.billings.ca.bc.service.BillingDiagnosisService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import io.github.carlos_emr.carlos.test.logging.LogCapture;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("billing-bc")
class DxReferenceUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldPreserveLegacyNullDateOrderAndDescriptions_whenMappingTypedDiagnoses() {
        BillingDiagnosisService service = mock(BillingDiagnosisService.class);
        registerMock(BillingDiagnosisService.class, service);
        when(service.findRecentDiagnoses("42")).thenReturn(List.of(
                new BillingDiagnosisService.DiagnosisDto("20261001", "old", "Older"),
                new BillingDiagnosisService.DiagnosisDto("20261102", "new", "Newer"),
                new BillingDiagnosisService.DiagnosisDto(null, "null", null),
                new BillingDiagnosisService.DiagnosisDto("invalid", "invalid", null)));
        List<DxReference.DxCode> diagnoses = new DxReference().getLatestDxCodes("42");
        assertThat(diagnoses).extracting(DxReference.DxCode::getDx).containsExactly("null", "invalid", "new", "old");
        assertThat(diagnoses.get(2).getDate()).isEqualTo(UtilDateUtilities.StringToDate("20261102", "yyyyMMdd"));
        assertThat(diagnoses.get(2).getDesc()).isEqualTo("Newer");
        assertThat(diagnoses.get(0).getDesc()).isNull();
    }

    @Test
    void shouldReturnEmptyList_whenPersistenceReadFails() {
        BillingDiagnosisService service = mock(BillingDiagnosisService.class);
        registerMock(BillingDiagnosisService.class, service);
        when(service.findRecentDiagnoses("42")).thenThrow(new DataAccessResourceFailureException("synthetic failure"));
        assertThat(new DxReference().getLatestDxCodes("42")).isEmpty();
    }

    @Test
    void shouldReturnEmptyList_whenDatabaseTransactionCannotStart() {
        BillingDiagnosisService service = mock(BillingDiagnosisService.class);
        registerMock(BillingDiagnosisService.class, service);
        when(service.findRecentDiagnoses("42"))
                .thenThrow(new CannotCreateTransactionException("synthetic connection failure"));
        assertThat(new DxReference().getLatestDxCodes("42")).isEmpty();
    }

    @Test
    void shouldReturnEmptyList_whenDatabaseTransactionCannotComplete() {
        BillingDiagnosisService service = mock(BillingDiagnosisService.class);
        registerMock(BillingDiagnosisService.class, service);
        when(service.findRecentDiagnoses("42"))
                .thenThrow(new TransactionSystemException("synthetic completion failure"));
        assertThat(new DxReference().getLatestDxCodes("42")).isEmpty();
    }

    @Test
    void shouldLogCauseTypeWithoutSensitiveMessages_whenPersistenceReadFails() {
        BillingDiagnosisService service = mock(BillingDiagnosisService.class);
        registerMock(BillingDiagnosisService.class, service);
        when(service.findRecentDiagnoses("42")).thenThrow(new DataAccessResourceFailureException(
                "synthetic patient payload", new IllegalStateException("synthetic query payload")));
        try (LogCapture logs = LogCapture.forLogger(DxReference.class)) {
            assertThat(new DxReference().getLatestDxCodes("42")).isEmpty();
            assertThat(logs.messages()).anySatisfy(message -> assertThat(message)
                    .contains("DataAccessResourceFailureException", "cause=IllegalStateException"));
            assertThat(logs.messages()).allSatisfy(message -> assertThat(message)
                    .doesNotContain("synthetic patient payload", "synthetic query payload"));
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrown()).isNull());
        }
    }

}
