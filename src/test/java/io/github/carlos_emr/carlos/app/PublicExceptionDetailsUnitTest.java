/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.app;

import java.util.Map;
import java.util.stream.Stream;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFileWriteException;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks mapped and direct error-page rendering without clinical exception details.
 *
 * @since 2026-09-27
 */
@Tag("unit")
@DisplayName("Public exception details")
class PublicExceptionDetailsUnitTest {
    static Stream<Exception> privateFailures() {
        return Stream.of(
                new BillingValidationException("PRIVATE_PATIENT"),
                new BillingFileWriteException("PRIVATE_MESSAGE", "PRIVATE_FILENAME", null),
                new BillingDataLoadException("PRIVATE_MESSAGE", BillingDataLoadException.Phase.CLAIM_EXTRACT,
                        Map.of("PRIVATE_KEY", "PRIVATE_VALUE")),
                new IllegalArgumentException("PRIVATE_MESSAGE"));
    }

    @ParameterizedTest
    @MethodSource("privateFailures")
    @DisplayName("should remove diagnostic data from view copies and direct JSP fallback messages")
    void shouldWithholdClinicalDetails_whenPreparingPublicFailure(Exception original) {
        // Some constructors initialize cause to null, so use suppressed failures to cover every type.
        original.addSuppressed(new IllegalStateException("PRIVATE_SUPPRESSED"));
        original.setStackTrace(new StackTraceElement[]{new StackTraceElement(
                "PRIVATE_CLASS", "PRIVATE_METHOD", "PRIVATE_FILE", 42)});

        Exception visible = PublicExceptionDetails.forView(original);

        assertThat(visible).isNotSameAs(original);
        assertThat(visible.getMessage()).isNotBlank().doesNotContain("PRIVATE_");
        assertThat(PublicExceptionDetails.message(original)).isNotBlank().doesNotContain("PRIVATE_");
        assertThat(visible.getCause()).isNull();
        assertThat(visible.getSuppressed()).isEmpty();
        assertThat(visible.getStackTrace()).isEmpty();
        if (visible instanceof BillingDataLoadException data) {
            assertThat(data.phase()).isEqualTo(BillingDataLoadException.Phase.CLAIM_EXTRACT);
            assertThat(data.context()).isEmpty();
        } else if (visible instanceof BillingFileWriteException file) {
            assertThat(file.filename()).isEmpty();
        }
        assertThat(original.getMessage()).contains("PRIVATE_");
        assertThat(original.getSuppressed()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(BillingFileWriteException.Reason.class)
    @DisplayName("should retain each fixed OHIP recovery instruction without its internal cause")
    void shouldPreserveOperationalGuidance_whenSanitizingFileFailure(BillingFileWriteException.Reason reason) {
        var cause = new IllegalStateException("PRIVATE_CAUSE");
        var original = BillingFileWriteException.forReason(reason, cause);
        var visible = (BillingFileWriteException) PublicExceptionDetails.forView(original);

        assertThat(original.getCause()).isSameAs(cause);
        assertThat(visible.reason()).isEqualTo(reason);
        assertThat(visible.getMessage()).isEqualTo(reason.publicMessage());
        assertThat(PublicExceptionDetails.message(original)).isEqualTo(reason.publicMessage());
        assertThat(visible.getCause()).isNull();
        assertThat(visible.getStackTrace()).isEmpty();
    }

    @Test
    @DisplayName("should not trust a raw message that resembles known public guidance")
    void shouldUseGeneralGuidance_whenUnclassifiedMessageClaimsBusyOutcome() {
        var failure = new BillingFileWriteException(
                BillingFileWriteException.Reason.BUSY.publicMessage() + " PRIVATE_PATIENT");
        assertThat(PublicExceptionDetails.message(failure))
                .isEqualTo(BillingFileWriteException.Reason.GENERAL.publicMessage());
        assertThat(PublicExceptionDetails.message(null)).isNotBlank();
    }
}
