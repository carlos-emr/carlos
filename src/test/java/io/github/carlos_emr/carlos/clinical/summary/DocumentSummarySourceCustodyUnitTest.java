/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class DocumentSummarySourceCustodyUnitTest {
    private static final String TEXT = "FAKE document. Synthetic review evidence.";
    private static final Instant GENERATED = Instant.parse("2026-11-02T12:00:00Z");
    private static final DocumentSummaryReviewBinding.Scope SCOPE =
            new DocumentSummaryReviewBinding.Scope("FAKE-clinic", 7, 11);
    private static final DocumentSummaryReviewBinding.Metadata TEXT_META =
            new DocumentSummaryReviewBinding.Metadata("FAKE.txt", "text/plain", "FAKE document", 1234L);

    private DocumentSummaryReviewBinding service(Instant time) {
        return new DocumentSummaryReviewBinding(Clock.fixed(time, ZoneOffset.UTC));
    }

    private DocumentSummary summary() {
        var value = ClinicalSummaryAgentProtocol.JSON.createObjectNode().put("overview", "FAKE document.");
        value.putArray("points").addObject().put("text", "Synthetic review evidence.")
                .putArray("evidence").add("Synthetic review evidence.");
        return new DocumentSummary(value);
    }

    private LoggedInInfo actor() {
        Provider provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("FAKE-provider");
        when(provider.getFormattedName()).thenReturn("FAKE Reviewer");
        LoggedInInfo actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProvider()).thenReturn(provider);
        return actor;
    }

    private byte[] pdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.newLineAtOffset(20, 700);
                content.showText(TEXT);
                content.endText();
            }
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void shouldHandOffExactReviewedPdf_withoutCallerOrReturnedCopyMutation() throws Exception {
        byte[] input = pdf();
        byte[] original = input.clone();
        var metadata = new DocumentSummaryReviewBinding.Metadata("FAKE.pdf", "application/pdf", "FAKE document", 1234L);
        try (var captured = DocumentSummaryReviewBinding.captureOwned(SCOPE, metadata, input)) {
            input[0] ^= 1;
            var draft = service(GENERATED).prepare(captured.source(), summary(), "FAKE-local-test", "en");
            var approved = service(GENERATED.plusSeconds(60))
                    .approve(draft, draft.id(), captured.source(), actor(), true);
            byte[] handoff = captured.copyBytes();
            assertThat(handoff).isEqualTo(original);
            assertThat(hash(handoff)).isEqualTo(approved.sourceSha256());
            approved.requireCurrent(DocumentSummaryReviewBinding.capture(SCOPE, metadata, handoff), draft);
            assertThat(approved.extractionComplete()).isFalse();
            assertThat(approved.extractionDisclosure()).contains("One or more pages have no readable text");
            handoff[0] ^= 1;
            assertThat(captured.copyBytes()).isEqualTo(original);
            assertThat(captured.source().originalSha256()).isEqualTo(hash(original));
        }
    }

    @Test
    void shouldRetainDifferentOriginalBytes_evenWhenExtractedTextIsIdentical() {
        var metadata = new DocumentSummaryReviewBinding.Metadata("FAKE.html", "text/html", "FAKE document", 1234L);
        byte[] first = ("<p>" + TEXT + "</p>").getBytes(StandardCharsets.UTF_8);
        byte[] second = ("<div>" + TEXT + "</div>").getBytes(StandardCharsets.UTF_8);
        try (var a = DocumentSummaryReviewBinding.captureOwned(SCOPE, metadata, first);
                var b = DocumentSummaryReviewBinding.captureOwned(SCOPE, metadata, second)) {
            assertThat(a.source().extractedTextSha256()).isEqualTo(b.source().extractedTextSha256());
            assertThat(a.source().originalSha256()).isNotEqualTo(b.source().originalSha256());
            assertThat(a.copyBytes()).isEqualTo(first);
            assertThat(b.copyBytes()).isEqualTo(second);
            var draft = service(GENERATED).prepare(a.source(), summary(), "FAKE-local-test", "en");
            var approved = service(GENERATED.plusSeconds(60)).approve(draft, draft.id(), a.source(), actor(), true);
            assertThatThrownBy(() -> approved.requireCurrent(b.source(), draft)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void shouldDisposeOwnedArrayAndRejectNewAccess_withoutRevokingEscapedEvidence() throws Exception {
        byte[] input = TEXT.getBytes(StandardCharsets.UTF_8);
        AtomicReference<byte[]> owned = new AtomicReference<>();
        try (var extractor = mockStatic(ClinicalSummaryTextExtractor.class)) {
            extractor.when(() -> ClinicalSummaryTextExtractor.bytes(any(byte[].class), eq("text/plain")))
                    .thenAnswer(call -> {
                        owned.set(call.getArgument(0));
                        return new ClinicalSummaryTextExtractor.Extract(TEXT, true, "Complete stored plain text.");
                    });
            var captured = DocumentSummaryReviewBinding.captureOwned(SCOPE, TEXT_META, input);
            var escapedSource = captured.source();
            byte[] escapedCopy = captured.copyBytes();
            try {
                assertThat(owned.get()).isNotSameAs(input).isEqualTo(input);
                assertThat(captured.toString()).isEqualTo("CapturedSummarySource[REDACTED]");
                captured.close();
                captured.close();
                assertThat(owned.get()).isEqualTo(new byte[input.length]);
                assertThatThrownBy(captured::source).isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
                assertThatThrownBy(captured::copyBytes).isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
                assertThat(escapedCopy).isEqualTo(input);
                assertThat(escapedSource.originalSha256()).isEqualTo(hash(input));
                assertThat(captured.toString()).isEqualTo("CapturedSummarySource[REDACTED]");
            } finally {
                captured.close();
            }
        }
    }

    @Test
    void shouldRejectInvalidBounds_andClearFailedExtractionWithoutChangingCaller() throws Exception {
        byte[] input = TEXT.getBytes(StandardCharsets.UTF_8);
        AtomicReference<byte[]> attempted = new AtomicReference<>();
        try (var extractor = mockStatic(ClinicalSummaryTextExtractor.class)) {
            assertThatThrownBy(() -> DocumentSummaryReviewBinding.captureOwned(SCOPE, TEXT_META, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DocumentSummaryReviewBinding.captureOwned(SCOPE, TEXT_META, new byte[0]))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DocumentSummaryReviewBinding.captureOwned(SCOPE, TEXT_META, new byte[20 * 1024 * 1024 + 1]))
                    .isInstanceOf(IllegalArgumentException.class);
            extractor.verifyNoInteractions();
            extractor.when(() -> ClinicalSummaryTextExtractor.bytes(any(byte[].class), eq("text/plain")))
                    .thenAnswer(call -> {
                        attempted.set(call.getArgument(0));
                        throw new IOException("FAKE extraction failure");
                    }).thenAnswer(call -> {
                        attempted.set(call.getArgument(0));
                        throw new IllegalStateException("FAKE runtime failure");
                    });
            assertThatThrownBy(() -> DocumentSummaryReviewBinding.captureOwned(SCOPE, TEXT_META, input))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Source text is unavailable.");
            assertThat(attempted.get()).isNotSameAs(input).isEqualTo(new byte[input.length]);
            attempted.set(null);
            assertThatThrownBy(() -> DocumentSummaryReviewBinding.captureOwned(SCOPE, TEXT_META, input))
                    .isInstanceOf(IllegalStateException.class).hasMessage("FAKE runtime failure");
            assertThat(attempted.get()).isNotSameAs(input).isEqualTo(new byte[input.length]);
            assertThat(input).isEqualTo(TEXT.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void shouldReturnWholeCopyOrClosed_whenCopyAndCloseAreConcurrent() throws Exception {
        byte[] input = new byte[128 * 1024];
        Arrays.fill(input, (byte) 'A');
        var captured = DocumentSummaryReviewBinding.captureOwned(SCOPE, TEXT_META, input);
        var workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var copying = workers.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("FAKE start timeout");
                try {
                    return captured.copyBytes();
                } catch (IllegalStateException closed) {
                    assertThat(closed).hasMessageContaining("closed");
                    return null;
                }
            });
            var closing = workers.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("FAKE start timeout");
                captured.close();
                return null;
            });
            start.countDown();
            byte[] copy = copying.get(5, TimeUnit.SECONDS);
            closing.get(5, TimeUnit.SECONDS);
            if (copy != null) assertThat(copy).isEqualTo(input);
            assertThatThrownBy(captured::copyBytes).isInstanceOf(IllegalStateException.class);
        } finally {
            start.countDown();
            captured.close();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
