/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpSessionBindingEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class DocumentSummaryReviewSessionUnitTest {
    private static final String TEXT = "FAKE document. Repeat blood work was planned.";
    private static final DocumentSummaryReviewBinding.Scope SCOPE = new DocumentSummaryReviewBinding.Scope("FAKE-clinic", 7, 11);
    private static final DocumentSummaryReviewBinding.Metadata META = new DocumentSummaryReviewBinding.Metadata("FAKE.txt", "text/plain", "FAKE document", 1234L);
    private final List<DocumentSummaryReviewSession> reviews = new ArrayList<>();
    private final MutableClock clock = new MutableClock();
    private final LoggedInInfo actor = actor("FAKE-provider", 71);

    @AfterEach void close() { reviews.forEach(DocumentSummaryReviewSession::close); }
    private static LoggedInInfo actor(String id, int login) {
        var actor = mock(LoggedInInfo.class);
        var provider = mock(Provider.class);
        var security = mock(Security.class);
        when(provider.getProviderNo()).thenReturn(id);
        when(provider.getFormattedName()).thenReturn("FAKE Clinician");
        when(security.getSecurityNo()).thenReturn(login);
        when(actor.getLoggedInProvider()).thenReturn(provider);
        when(actor.getLoggedInSecurity()).thenReturn(security);
        return actor;
    }
    private static DocumentSummary summary() {
        var value = ClinicalSummaryAgentProtocol.JSON.createObjectNode().put("overview", "FAKE document.");
        value.putArray("points").addObject().put("text", "Repeat blood work was planned.")
                .putArray("evidence").add("Repeat blood work was planned.");
        return new DocumentSummary(value);
    }
    private static byte[] bytes() { return TEXT.getBytes(StandardCharsets.UTF_8); }
    private static DocumentSummaryReviewBinding.Source source() { return DocumentSummaryReviewBinding.capture(SCOPE, META, bytes()); }
    private DocumentSummaryReviewSession generate(DocumentSummaryReviewSession.SourceLoader loader, DocumentSummaryService service) throws Exception {
        var review = DocumentSummaryReviewSession.generate(SCOPE, META, loader, service, "FAKE-model@r1", actor, clock);
        reviews.add(review);
        return review;
    }
    private DocumentSummaryReviewSession generate() throws Exception {
        var service = mock(DocumentSummaryService.class);
        when(service.summarize(TEXT)).thenReturn(summary());
        return generate(DocumentSummaryReviewSessionUnitTest::bytes, service);
    }
    private DocumentSummaryReviewSession.View view(DocumentSummaryReviewSession review) { return review.view(actor, SCOPE, source()); }
    private void approve(DocumentSummaryReviewSession review) { review.approve(actor, SCOPE, view(review).draftId(), source(), true); }

    @Test void previewNeverRecordsApprovalOrPermitsInclusion() throws Exception {
        var review = generate();
        var preview = view(review);
        assertThat(preview.approved()).isFalse();
        assertThat(preview.notice()).contains("Review status: Review not confirmed");
        assertThatThrownBy(() -> review.forDelivery(actor, SCOPE, preview.draftId(), source(), true)).isInstanceOf(IllegalStateException.class);
        assertThat(review.copyOriginalForReview(actor, SCOPE, preview.draftId(), source())).isEqualTo(bytes());
        assertThat(view(review).approved()).isFalse();
    }
    @Test void affirmativeReviewUsesServerActorClockAndExactNotice() throws Exception {
        var review = generate();
        var id = view(review).draftId();
        assertThatThrownBy(() -> review.approve(actor, SCOPE, id, source(), false)).isInstanceOf(IllegalStateException.class);
        assertThat(view(review).approved()).isFalse();
        clock.now = clock.now.plusSeconds(60);
        approve(review);
        assertThat(view(review).approved()).isTrue();
        assertThat(view(review).notice()).contains("Reviewed by clinician FAKE Clinician on " + clock.now,
                "Do not start, stop, change or delay treatment based only on this summary.");
    }
    @Test void approvalStillRequiresSeparateInclusionAndKeepsExactBytes() throws Exception {
        var review = generate(); approve(review);
        var id = view(review).draftId();
        assertThatThrownBy(() -> review.forDelivery(actor, SCOPE, id, source(), false)).isInstanceOf(IllegalStateException.class);
        try (var copy = review.forDelivery(actor, SCOPE, id, source(), true)) {
            byte[] escaped = copy.copyOriginal(); escaped[0] = 'X';
            assertThat(copy.copyOriginal()).isEqualTo(bytes());
            assertThat(copy.approval().sourceSha256()).isEqualTo(source().originalSha256());
            assertThat(copy.approval().patientNotice()).isEqualTo(view(review).notice());
            copy.close();
            assertThatThrownBy(copy::copyOriginal).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void changedOriginalInvalidatesApprovalAndDestroysSessionCustody() throws Exception {
        var review = generate(); approve(review);
        var id = view(review).draftId();
        var changed = DocumentSummaryReviewBinding.capture(SCOPE, META, (TEXT + " Changed.").getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> review.forDelivery(actor, SCOPE, id, changed, true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> review.copyOriginalForReview(actor, SCOPE, id, source())).isInstanceOf(IllegalStateException.class);
    }
    @Test void metadataChangeAlsoInvalidatesSameOriginalBytes() throws Exception {
        var review = generate();
        var changed = DocumentSummaryReviewBinding.capture(SCOPE,
                new DocumentSummaryReviewBinding.Metadata("FAKE.txt", "text/plain", "Changed title", 1234L), bytes());
        assertThatThrownBy(() -> review.view(actor, SCOPE, changed)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> view(review)).isInstanceOf(IllegalStateException.class);
    }
    @Test void differentLoginProviderOrPatientCannotUseOwnersReview() throws Exception {
        var review = generate(); approve(review);
        var id = view(review).draftId();
        for (var other : List.of(actor("FAKE-provider", 72), actor("other-provider", 71))) {
            assertThatThrownBy(() -> review.forDelivery(other, SCOPE, id, source(), true)).isInstanceOf(SecurityException.class);
        }
        var otherPatient = new DocumentSummaryReviewBinding.Scope("FAKE-clinic", 8, 11);
        assertThatThrownBy(() -> review.forDelivery(actor, otherPatient, id, source(), true)).isInstanceOf(SecurityException.class);
        assertThat(view(review).approved()).isTrue();
    }
    @Test void expiryDisposesCustodyRatherThanReturningOldApproval() throws Exception {
        var review = generate(); approve(review);
        clock.now = clock.now.plusSeconds(900);
        assertThatThrownBy(() -> view(review)).isInstanceOf(IllegalStateException.class).hasMessageContaining("expired");
        assertThatThrownBy(() -> view(review)).isInstanceOf(IllegalStateException.class);
    }
    @Test void processCapacityRejectsBeforeLoadingAndClosingReleasesSlot() throws Exception {
        int capacity = DocumentSummaryReviewSession.capacity();
        assertThat(capacity).isEqualTo(4); // the default, with no clinical.ai_document_summary.review.max_active set
        for (int i = 0; i < capacity; i++) generate();
        var loads = new AtomicInteger();
        var service = mock(DocumentSummaryService.class);
        when(service.summarize(TEXT)).thenReturn(summary());
        assertThatThrownBy(() -> generate(() -> { loads.incrementAndGet(); return bytes(); }, service)).isInstanceOf(IllegalStateException.class).hasMessageContaining("capacity (" + capacity + ")");
        assertThat(loads).hasValue(0);
        reviews.get(0).close();
        assertThat(generate(() -> { loads.incrementAndGet(); return bytes(); }, service)).isNotNull();
        assertThat(loads).hasValue(1);
    }
    @Test void failedGenerationClearsLoadedArrayAndReleasesReservation() throws Exception {
        byte[] loaded = bytes();
        var service = mock(DocumentSummaryService.class);
        when(service.summarize(TEXT)).thenThrow(new ClinicalSummaryGenerationException("FAKE model unavailable"));
        assertThatThrownBy(() -> generate(() -> loaded, service)).isInstanceOf(ClinicalSummaryGenerationException.class);
        assertThat(loaded).containsOnly((byte) 0);
        for (int i = 0; i < DocumentSummaryReviewSession.capacity(); i++) generate();
    }
    @Test void abandonedDraftsPastExpiryDoNotBlockTheNextClinician() throws Exception {
        for (int i = 0; i < DocumentSummaryReviewSession.capacity(); i++) generate();
        var abandoned = List.copyOf(reviews);
        clock.now = clock.now.plusSeconds(900);
        // The sweep runs on the generate attempt itself: no timer, no logout needed.
        var fresh = generate();
        assertThat(fresh).isNotNull();
        for (var review : abandoned) assertThatThrownBy(() -> view(review)).isInstanceOf(IllegalStateException.class);
        // One slot is taken by the fresh review, so capacity minus one more fit.
        for (int i = 1; i < DocumentSummaryReviewSession.capacity(); i++) generate();
        assertThatThrownBy(this::generate).isInstanceOf(IllegalStateException.class).hasMessageContaining("capacity");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "null,4", "'',4", "' ',4", "abc,4", "0,4", "-3,4", "33,4", "4.5,4",
            "1,1", "4,4", "' 8 ',8", "32,32"}, nullValues = "null")
    void capacityPropertyAcceptsOneToThirtyTwoAndDefaultsOtherwise(String raw, int expected) {
        assertThat(DocumentSummaryReviewSession.configuredCapacity(raw)).isEqualTo(expected);
    }
    @Test void failedLoaderReleasesReservationBeforeAnyModelCall() throws Exception {
        var service = mock(DocumentSummaryService.class);
        assertThatThrownBy(() -> generate(() -> { throw new IOException("FAKE source unavailable"); }, service)).isInstanceOf(IOException.class);
        verifyNoInteractions(service);
        for (int i = 0; i < 4; i++) generate();
    }
    @Test void duplicateApprovalRetainsFirstRecordedTime() throws Exception {
        var review = generate(); approve(review);
        String first = view(review).notice();
        clock.now = clock.now.plusSeconds(90); approve(review);
        assertThat(view(review).notice()).isEqualTo(first);
    }
    @Test void logoutUnbindingClosesReviewIdempotently() throws Exception {
        var review = generate(); approve(review);
        review.valueUnbound(mock(HttpSessionBindingEvent.class)); review.close();
        assertThatThrownBy(() -> view(review)).isInstanceOf(IllegalStateException.class);
    }
    @Test void replacementCannotReuseAnIdenticalDraftsOldToken() throws Exception {
        var old = generate(); approve(old);
        UUID oldId = view(old).draftId(); old.close();
        var fresh = generate();
        assertThat(view(fresh).draftId()).isNotEqualTo(oldId);
        assertThat(view(fresh).approved()).isFalse();
        assertThatThrownBy(() -> fresh.approve(actor, SCOPE, oldId, source(), true)).isInstanceOf(IllegalStateException.class);
    }
    @Test void emptyExtractionNeverCallsModelAndClearsLoadedArray() throws Exception {
        var service = mock(DocumentSummaryService.class);
        byte[] blank = "   ".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> generate(() -> blank, service)).isInstanceOf(IllegalStateException.class);
        assertThat(blank).containsOnly((byte) 0); verifyNoInteractions(service);
    }
    @Test void partialHtmlDisclosureAndRawBytesRemainAttached() throws Exception {
        byte[] html = ("<p>" + TEXT + "</p>").getBytes(StandardCharsets.UTF_8);
        var metadata = new DocumentSummaryReviewBinding.Metadata("FAKE.html", "text/html", "FAKE document", 1234L);
        var service = mock(DocumentSummaryService.class); when(service.summarize(TEXT)).thenReturn(summary());
        var review = DocumentSummaryReviewSession.generate(SCOPE, metadata, () -> html.clone(), service, "FAKE-model", actor, clock);
        reviews.add(review);
        var source = DocumentSummaryReviewBinding.capture(SCOPE, metadata, html);
        var view = review.view(actor, SCOPE, source);
        assertThat(view.extractionDisclosure()).contains("review the original form");
        review.approve(actor, SCOPE, view.draftId(), source, true);
        try (var copy = review.forDelivery(actor, SCOPE, view.draftId(), source, true)) {
            assertThat(copy.copyOriginal()).isEqualTo(html);
            assertThat(copy.approval().extractionComplete()).isFalse();
        }
    }
    @Test void generationSnapshotsOwnerBeforeTheModelCallback() throws Exception {
        var service = mock(DocumentSummaryService.class);
        when(service.summarize(TEXT)).thenAnswer(ignored -> {
            when(actor.getLoggedInSecurity().getSecurityNo()).thenReturn(72);
            return summary();
        });
        var review = generate(DocumentSummaryReviewSessionUnitTest::bytes, service);
        assertThatThrownBy(() -> view(review)).isInstanceOf(SecurityException.class);
    }
    @Test void correctionClearsApprovalAndRequiresConfirmationOfNewExactVersion() throws Exception {
        var review = generate(); approve(review);
        var old = view(review);
        clock.now = clock.now.plusSeconds(90);
        review.revise(actor, SCOPE, old.draftId(), source(), "FAKE blood work document.", List.of("Blood work was planned."), Set.of());
        var revised = view(review);
        assertThat(revised.approved()).isFalse();
        assertThat(revised.notice()).contains("Review not confirmed");
        assertThat(revised.draftId()).isNotEqualTo(old.draftId());
        assertThat(revised.version()).isNotEqualTo(old.version());
        assertThat(revised.originalSha256()).isEqualTo(old.originalSha256());
        assertThat(revised.generatedAt()).isEqualTo(old.generatedAt());
        assertThat(revised.revisedAt()).isEqualTo(clock.now);
        assertThat(review.copyOriginalForReview(actor, SCOPE, revised.draftId(), source())).isEqualTo(bytes());
        assertThatThrownBy(() -> review.forDelivery(actor, SCOPE, old.draftId(), source(), true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> review.forDelivery(actor, SCOPE, revised.draftId(), source(), true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> review.approve(actor, SCOPE, old.draftId(), source(), true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> review.approve(actor, SCOPE, revised.draftId(), source(), false)).isInstanceOf(IllegalStateException.class);
        approve(review);
        try (var copy = review.forDelivery(actor, SCOPE, revised.draftId(), source(), true)) {
            assertThat(copy.approval().summaryVersion()).isEqualTo(revised.version());
            assertThat(copy.approval().summary().overview()).isEqualTo("FAKE blood work document.");
            assertThat(copy.approval().generatedAt()).isEqualTo(old.generatedAt());
            assertThat(copy.approval().revisedAt()).isEqualTo(revised.revisedAt());
        }
    }
    @Test void staleOrForeignCorrectionCannotChangeCurrentApprovedDraft() throws Exception {
        var review = generate(); approve(review);
        var before = view(review);
        assertThatThrownBy(() -> review.revise(actor, SCOPE, UUID.randomUUID(), source(),
                "FAKE document.", List.of("Blood work planned."), Set.of())).isInstanceOf(IllegalStateException.class);
        for (var other : List.of(actor("FAKE-provider", 72), actor("other-provider", 71))) {
            assertThatThrownBy(() -> review.revise(other, SCOPE, before.draftId(), source(),
                    "FAKE document.", List.of("Blood work planned."), Set.of())).isInstanceOf(SecurityException.class);
        }
        var otherScope = new DocumentSummaryReviewBinding.Scope("FAKE-clinic", 8, 12);
        assertThatThrownBy(() -> review.revise(actor, otherScope, before.draftId(), source(),
                "FAKE document.", List.of("Blood work planned."), Set.of())).isInstanceOf(SecurityException.class);
        assertThat(view(review).version()).isEqualTo(before.version());
        assertThat(view(review).approved()).isTrue();
    }
    @Test void sourceChangeDuringCorrectionDisposesOldCustody() throws Exception {
        var review = generate(); approve(review); var before = view(review);
        var changed = DocumentSummaryReviewBinding.capture(SCOPE, META, (TEXT + " Changed.").getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> review.revise(actor, SCOPE, before.draftId(), changed,
                "FAKE document.", List.of("Blood work planned."), Set.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> view(review)).isInstanceOf(IllegalStateException.class);
    }
    @Test void correctionDoesNotRenewOriginalFifteenMinuteExpiry() throws Exception {
        var review = generate(); var before = view(review);
        clock.now = clock.now.plusSeconds(899);
        review.revise(actor, SCOPE, before.draftId(), source(), "FAKE document.", List.of("Blood work planned."), Set.of());
        clock.now = clock.now.plusSeconds(1);
        assertThatThrownBy(() -> view(review)).isInstanceOf(IllegalStateException.class);
    }
    @Test void invalidCorrectionCannotPartlyReplaceSummaryOrCarryApprovalToNewText() throws Exception {
        var review = generate(); approve(review); var before = view(review);
        assertThatThrownBy(() -> review.revise(actor, SCOPE, before.draftId(), source(),
                "FAKE document.", List.of("No lexical overlap."), Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(view(review).version()).isEqualTo(before.version());
        assertThat(view(review).approved()).isTrue();
        assertThat(view(review).revisedAt()).isNull();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-03T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
