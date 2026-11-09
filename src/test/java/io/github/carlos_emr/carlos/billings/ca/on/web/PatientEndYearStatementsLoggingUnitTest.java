/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.service.PatientEndYearStatementService;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class PatientEndYearStatementsLoggingUnitTest {
    @ParameterizedTest
    @EnumSource(value = PatientEndYearStatementService.Reason.class, names = {"DATABASE_ERROR", "IO_ERROR", "PDF_ERROR"})
    void shouldRetainDiagnosticTypes_withoutLoggingSqlOrPatientValues(PatientEndYearStatementService.Reason reason) {
        var cause = new java.sql.SQLException("SELECT sensitive_clinical_value FROM synthetic_secret_table");
        cause.addSuppressed(new IllegalStateException("synthetic_suppressed_patient_value"));
        try (var logs = LogCapture.forLogger(PatientEndYearStatements.class)) {
            PatientEndYearStatements.logFailure(new PatientEndYearStatementService.Failure(reason, cause), "5692");
            assertThat(logs.events()).hasSize(1);
            assertThat(logs.events().getFirst().getThrown()).isNull();
            assertThat(logs.messages().getFirst()).contains(reason.name(), "SQLException", "IllegalStateException")
                    .doesNotContain("sensitive_clinical_value", "synthetic_secret_table", "synthetic_suppressed_patient_value");
        }
    }
}
