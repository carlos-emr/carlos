/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Operator diagnostics must explain fixed refusals without exposing exception row data. @since 2026-11-09 */
@Tag("unit")
class TeleplanExportErrorsUnitTest {
    @Test
    void shouldRecognizeBusinessRefusals_whenWrappedByATransaction() {
        var failure = new IllegalStateException("transaction wrapper", new IllegalArgumentException("A provider has an invalid billing group"));
        assertThat(TeleplanExportErrors.describe(failure)).isEqualTo(
                new TeleplanExportErrors.Failure(400,"A provider has an invalid billing group"));
        assertThat(TeleplanExportErrors.describe(new IllegalStateException("Another Teleplan export is running; retry when it finishes")).status()).isEqualTo(409);
        assertThat(TeleplanExportErrors.describe(new java.io.IOException("Teleplan artifact changed; operator review is required")).status()).isEqualTo(503);
    }

    @Test
    void shouldPreserveDiagnosticLocationsAndCauseTypes_withoutPatientOrSqlText() {
        var failure = new IllegalStateException("FAKE-PATIENT",new java.sql.SQLException("SELECT FAKE-PATIENT"));
        failure.addSuppressed(new java.io.IOException("FAKE-PATIENT cleanup"));
        var safe = TeleplanExportErrors.diagnostic(failure);
        assertThat(TeleplanExportErrors.describe(failure).status()).isEqualTo(500);
        assertThat(TeleplanExportErrors.describe(failure).message()).doesNotContain("FAKE-PATIENT");
        assertThat(safe.getStackTrace()).containsExactly(failure.getStackTrace());
        assertThat(safe.getCause().getMessage()).contains("java.sql.SQLException").doesNotContain("SELECT","FAKE-PATIENT");
        assertThat(safe.getSuppressed()).hasSize(1);
        assertThat(safe.getSuppressed()[0].getMessage()).contains("java.io.IOException").doesNotContain("FAKE-PATIENT");
    }
}
