/** Copyright (c) 2026 CARLOS Contributors. GPL v2 or later. */
package io.github.carlos_emr.carlos.consultation;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.ConsultAttachmentWarning;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static io.github.carlos_emr.carlos.consultation.ConsultAttachmentApprovalService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class ConsultAttachmentApprovalServiceUnitTest {
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-10-20T00:00:00Z");
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final List<ConsultAttachmentWarning> warnings = List.of(ConsultAttachmentWarning.notRendered(DocumentType.DOC, 80));
    private ConsultAttachmentApprovalService service;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(now);
        when(info.getLoggedInProviderNo()).thenReturn("provider1");
        request.setSession(new MockHttpSession());
        service = new ConsultAttachmentApprovalService(clock);
    }

    private void issue() {
        request.setParameter(TOKEN, service.issue(request, info, "9", "1", Operation.FAX, warnings));
        request.setParameter(CONFIRM, "true");
    }

    @Test
    void shouldConsumeOnlyOnce_whenProviderAcceptsExactWarnings() {
        issue();
        assertThat(service.consume(request, info, "9", "1", Operation.FAX, warnings)).isTrue();
        assertThat(service.consume(request, info, "9", "1", Operation.FAX, warnings)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"session", "provider", "patient", "consult", "operation", "reason", "id", "newIssue", "expired", "forged", "unchecked"})
    void shouldRefuseConsent_whenScopeOrIssuesChange(String change) {
        issue();
        String consult = "9", patient = "1";
        Operation operation = Operation.FAX;
        List<ConsultAttachmentWarning> current = warnings;
        switch (change) {
            case "session" -> request.setSession(new MockHttpSession());
            case "provider" -> when(info.getLoggedInProviderNo()).thenReturn("provider2");
            case "patient" -> patient = "2";
            case "consult" -> consult = "10";
            case "operation" -> operation = Operation.PRINT;
            case "reason" -> current = List.of(ConsultAttachmentWarning.unavailable(DocumentType.DOC, 80));
            case "id" -> current = List.of(ConsultAttachmentWarning.notRendered(DocumentType.DOC, 81));
            case "newIssue" -> current = List.of(warnings.getFirst(), ConsultAttachmentWarning.notRendered(DocumentType.EFORM, 12));
            case "expired" -> when(clock.instant()).thenReturn(now.plusSeconds(600));
            case "forged" -> request.setParameter(TOKEN, "unknown");
            case "unchecked" -> request.setParameter(CONFIRM, "false");
        }
        assertThat(service.consume(request, info, consult, patient, operation, current)).isFalse();
    }

    @Test
    void shouldOfferNewDecision_whenOldApprovalDoesNotCoverCurrentWarnings() {
        issue();
        String old = request.getParameter(TOKEN);
        List<ConsultAttachmentWarning> changed = List.of(ConsultAttachmentWarning.notRendered(DocumentType.EFORM, 12));
        assertThat(service.requiresReview(request, info, "9", "1", Operation.FAX, changed)).isTrue();
        assertThat(request.getAttribute(REVIEW_REQUIRED)).isEqualTo(true);
        assertThat(request.getAttribute(TOKEN)).isNotEqualTo(old);
        request.setParameter(TOKEN, (String) request.getAttribute(TOKEN));
        assertThat(service.consume(request, info, "9", "1", Operation.FAX, changed)).isTrue();
    }

    @Test
    void shouldWithholdWithoutResendInvitation_whenApprovalWasAlreadyUsed() {
        issue();
        assertThat(service.consume(request, info, "9", "1", Operation.FAX, warnings)).isTrue();
        assertThat(service.requiresReview(request, info, "9", "1", Operation.FAX, warnings)).isTrue();
        assertThat(request.getAttribute(REVIEW_REQUIRED)).isNull();
        assertThat(request.getAttribute(TOKEN)).isNull();
        assertThat(request.getAttribute("errorMessage")).asString().contains("already used", "duplicate");
    }

    @Test
    void shouldProceedDirectly_whenPacketHasNoOmissions() {
        assertThat(service.requiresReview(request, info, "9", "1", Operation.PREVIEW, List.of())).isFalse();
        assertThat(request.getAttribute(TOKEN)).isNull();
    }
}
