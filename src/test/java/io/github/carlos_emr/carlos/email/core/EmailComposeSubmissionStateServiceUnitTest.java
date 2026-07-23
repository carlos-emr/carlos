package io.github.carlos_emr.carlos.email.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;

@Tag("unit")
@Tag("security")
@DisplayName("EmailComposeSubmissionStateService")
class EmailComposeSubmissionStateServiceUnitTest {
    private static final String TOKEN_PARAMETER_NAME = "emailPDFPasswordToken";

    @Test
    @DisplayName("should expire pending compose submission states")
    void shouldExpirePendingComposeSubmissionStates_whenMaxAgeExceeded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-23T12:00:00Z"));
        EmailComposeSubmissionStateService service = new EmailComposeSubmissionStateService(clock);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        try {
            String expiredToken = service.store(
                    request.getSession(),
                    "example-expired-value",
                    "delivery instruction",
                    List.of());
            clock.advanceMillis(EmailComposeSubmissionStateService.PENDING_EMAIL_COMPOSE_STATE_MAX_AGE_MILLIS + 1);
            request.setParameter(TOKEN_PARAMETER_NAME, expiredToken);

            assertThat(service.consume(request, TOKEN_PARAMETER_NAME)).isNull();
        } finally {
            service.shutdown();
        }
    }

    @Test
    @DisplayName("should snapshot attachment values when storing compose submission state")
    void shouldSnapshotAttachmentValues_whenStoringComposeSubmissionState() {
        EmailComposeSubmissionStateService service = new EmailComposeSubmissionStateService();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        EmailAttachment attachment = new EmailAttachment(
                "original.pdf",
                "/tmp/original.pdf",
                DocumentType.DOC,
                42,
                2048);
        try {
            String token = service.store(
                    request.getSession(),
                    "example-snapshot-value",
                    "delivery instruction",
                    List.of(attachment),
                    EmailComposeSubmissionStateService.EmailComposeSubmissionContext.eform(
                            "123",
                            "456",
                            true,
                            false));
            attachment.setFileName("changed.pdf");
            attachment.setFilePath("/tmp/changed.pdf");
            attachment.setDocumentType(DocumentType.LAB);
            attachment.setDocumentId(99);
            attachment.setFileSize(4096);
            request.setParameter(TOKEN_PARAMETER_NAME, token);

            EmailComposeSubmissionStateService.EmailComposeSubmissionState state =
                    service.consume(request, TOKEN_PARAMETER_NAME);

            EmailAttachment snapshot = state.emailAttachmentList().get(0);
            assertThat(snapshot.getFileName()).isEqualTo("original.pdf");
            assertThat(snapshot.getFilePath()).isEqualTo("/tmp/original.pdf");
            assertThat(snapshot.getDocumentType()).isEqualTo(DocumentType.DOC);
            assertThat(snapshot.getDocumentId()).isEqualTo(42);
            assertThat(snapshot.getFileSize()).isEqualTo(2048);
            assertThat(state.context().demographicId()).isEqualTo("123");
            assertThat(state.context().fdid()).isEqualTo("456");
            assertThat(state.context().transactionType()).isEqualTo(TransactionType.EFORM);
            assertThat(state.context().openEFormAfterEmail()).isTrue();
            assertThat(state.context().deleteEFormAfterEmail()).isFalse();
        } finally {
            service.shutdown();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
