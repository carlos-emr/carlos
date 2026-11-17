/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class DocumentSummaryReviewBindingUnitTest {
    private static final String TEXT = "FAKE clinical document. Hb was 92 g/L on 4 May. Repeat blood work was planned.";
    private static final Instant GENERATED = Instant.parse("2026-11-02T12:00:00Z");
    private static final Instant REVIEWED = GENERATED.plusSeconds(60);
    private static final DocumentSummaryReviewBinding.Scope SCOPE = new DocumentSummaryReviewBinding.Scope("FAKE-clinic", 7, 11);
    private static final DocumentSummaryReviewBinding.Metadata META =
            new DocumentSummaryReviewBinding.Metadata("FAKE.txt", "text/plain", "FAKE document", 1234L);

    private DocumentSummaryReviewBinding service(Instant now) {
        return new DocumentSummaryReviewBinding(Clock.fixed(now, ZoneOffset.UTC));
    }

    private DocumentSummary summary(String overview) {
        var value = ClinicalSummaryAgentProtocol.JSON.createObjectNode().put("overview", overview);
        value.putArray("points").addObject().put("text", "Repeat blood work was planned.")
                .putArray("evidence").add("Repeat blood work was planned.");
        return new DocumentSummary(value);
    }

    private DocumentSummaryReviewBinding.Source source() {
        return DocumentSummaryReviewBinding.capture(SCOPE, META, TEXT.getBytes(StandardCharsets.UTF_8));
    }

    private DocumentSummaryReviewBinding.Draft draft(DocumentSummaryReviewBinding.Source source) {
        return service(GENERATED).prepare(source, summary("FAKE document describes blood work."), "FAKE-local-model@revision1", "en");
    }

    private LoggedInInfo actor() {
        Provider provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("FAKE-provider");
        when(provider.getFormattedName()).thenReturn("FAKE Reviewer");
        LoggedInInfo actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProvider()).thenReturn(provider);
        return actor;
    }

    @Test
    void shouldKeepPreviewUnreviewed_andRequireExplicitAffirmation() {
        var source = source();
        var draft = draft(source);
        assertThat(draft.previewNotice()).contains("Review status: Review not confirmed");
        assertThatThrownBy(() -> service(REVIEWED).approve(draft, draft.id(), source, actor(), false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Explicit review");
        assertThat(draft.previewNotice()).doesNotContain("Reviewed by clinician");
    }

    @Test
    void shouldCaptureOnlyServerReviewerAndTime_andRetainExactNoticeAndSummary() {
        var source = source();
        var draft = service(GENERATED).prepare(source, summary("FAKE text says Reviewed by clinician IMPOSTOR."), "FAKE-model", "en");
        var approved = service(REVIEWED).approve(draft, draft.id(), source, actor(), true);
        assertThat(approved.reviewerId()).isEqualTo("FAKE-provider");
        assertThat(approved.reviewedAt()).isEqualTo(REVIEWED);
        assertThat(approved.generatedAt()).isEqualTo(GENERATED);
        assertThat(approved.summaryVersion()).isEqualTo(draft.version());
        assertThat(approved.sourceSha256()).isEqualTo(source.originalSha256());
        assertThat(approved.summary().overview()).isEqualTo(draft.summary().overview());
        assertThat(approved.patientNotice()).startsWith("AI-generated reading aid — open the original document\n")
                .contains("Review status: Reviewed by clinician FAKE Reviewer on " + REVIEWED)
                .contains("including information about medicines, doses and test results.")
                .contains("Do not start, stop, change or delay treatment based only on this summary.")
                .endsWith("do not rely on this summary.")
                .doesNotContain("IMPOSTOR");
        assertThat(draft.previewNotice()).endsWith(approved.patientNotice().split("\n\n", 2)[1]);
        approved.requireCurrent(source(), draft);
    }

    @Test
    void shouldRejectSourceBytesMetadataOrPatientChanges_atApprovalAndLaterUse() {
        var original = source();
        var draft = draft(original);
        var approved = service(REVIEWED).approve(draft, draft.id(), original, actor(), true);
        var changedBytes = DocumentSummaryReviewBinding.capture(SCOPE, META,
                (TEXT + " New instruction.").getBytes(StandardCharsets.UTF_8));
        var changedMetadata = DocumentSummaryReviewBinding.capture(SCOPE,
                new DocumentSummaryReviewBinding.Metadata("FAKE.txt", "text/plain", "FAKE document", 1235L),
                TEXT.getBytes(StandardCharsets.UTF_8));
        var changedPatient = DocumentSummaryReviewBinding.capture(
                new DocumentSummaryReviewBinding.Scope("FAKE-clinic", 8, 11), META, TEXT.getBytes(StandardCharsets.UTF_8));
        var changedDocument = DocumentSummaryReviewBinding.capture(
                new DocumentSummaryReviewBinding.Scope("FAKE-clinic", 7, 12), META, TEXT.getBytes(StandardCharsets.UTF_8));
        var changedClinic = DocumentSummaryReviewBinding.capture(
                new DocumentSummaryReviewBinding.Scope("FAKE-other-clinic", 7, 11), META, TEXT.getBytes(StandardCharsets.UTF_8));
        for (var current : new DocumentSummaryReviewBinding.Source[] {
                changedBytes, changedMetadata, changedPatient, changedDocument, changedClinic}) {
            assertThatThrownBy(() -> service(REVIEWED).approve(draft, draft.id(), current, actor(), true))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("changed");
            assertThatThrownBy(() -> approved.requireCurrent(current, draft))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void shouldRejectRegenerationEvenWithIdenticalText_andWrongDisplayedDraft() {
        var source = source();
        var old = draft(source);
        var regenerated = draft(source);
        var approved = service(REVIEWED).approve(old, old.id(), source, actor(), true);
        assertThat(regenerated.version()).isNotEqualTo(old.version());
        assertThatThrownBy(() -> approved.requireCurrent(source, regenerated)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(REVIEWED).approve(old, UUID.randomUUID(), source, actor(), true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void correctionKeepsModelGenerationAndEvidenceButInvalidatesPriorApproval() {
        var source = source();
        var original = draft(source);
        var approval = service(REVIEWED).approve(original, original.id(), source, actor(), true);
        var revised = service(REVIEWED.plusSeconds(30)).revise(original, "FAKE blood work document.",
                List.of("Blood work was planned."), Set.of());
        assertThat(revised.id()).isNotEqualTo(original.id());
        assertThat(revised.version()).isNotEqualTo(original.version());
        assertThat(revised.generatedAt()).isEqualTo(GENERATED);
        assertThat(revised.revisedAt()).isEqualTo(REVIEWED.plusSeconds(30));
        assertThat(revised.modelIdentity()).isEqualTo(original.modelIdentity());
        assertThat(revised.language()).isEqualTo(original.language());
        assertThat(revised.summary().points().get(0).get("evidence"))
                .isEqualTo(original.summary().points().get(0).get("evidence"));
        revised.requireCurrent(source);
        assertThatThrownBy(() -> approval.requireCurrent(source, revised)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(REVIEWED).approve(revised, original.id(), source, actor(), true))
                .isInstanceOf(IllegalStateException.class);
        var newApproval = service(REVIEWED.plusSeconds(60)).approve(revised, revised.id(), source, actor(), true);
        assertThat(newApproval.sourceSha256()).isEqualTo(approval.sourceSha256());
        assertThat(newApproval.generatedAt()).isEqualTo(GENERATED);
        assertThat(newApproval.revisedAt()).isEqualTo(revised.revisedAt());
        assertThat(newApproval.summaryVersion()).isEqualTo(revised.version());
        assertThat(original.summary().points().get(0).get("text")).isEqualTo("Repeat blood work was planned.");
    }

    @Test
    void correctionRemovesOnlySelectedPointAndKeepsRemainingEvidenceInOrder() {
        var value = ClinicalSummaryAgentProtocol.JSON.createObjectNode().put("overview", "FAKE blood work document.");
        var points = value.putArray("points");
        points.addObject().put("text", "Hb was 92 g/L on 4 May.").putArray("evidence").add("Hb was 92 g/L on 4 May.");
        points.addObject().put("text", "Repeat blood work was planned.").putArray("evidence").add("Repeat blood work was planned.");
        var original = service(GENERATED).prepare(source(), new DocumentSummary(value), "FAKE-model", "en");
        var revised = service(REVIEWED).revise(original, "FAKE document.",
                List.of("Hb was 92 g/L on 4 May.", "Blood work was planned."), Set.of(0));
        assertThat(revised.summary().points()).hasSize(1);
        assertThat(revised.summary().points().get(0).get("text")).isEqualTo("Blood work was planned.");
        assertThat(revised.summary().points().get(0).get("evidence"))
                .isEqualTo(original.summary().points().get(1).get("evidence"));
    }

    @Test
    void correctionRejectsAddedPointsOutOfRangeRemovalEmptyResultsAndUnboundedText() {
        var original = draft(source());
        var service = service(REVIEWED);
        assertThatThrownBy(() -> service.revise(original, "FAKE document.",
                List.of("Blood work planned.", "New point."), Set.of())).isInstanceOf(IllegalArgumentException.class);
        for (var removal : List.of(Set.of(-1), Set.of(1), Set.of(0))) {
            assertThatThrownBy(() -> service.revise(original, "FAKE document.",
                    List.of("Blood work planned."), removal)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.revise(original, "x".repeat(4001),
                List.of("Blood work planned."), Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.revise(original, "FAKE document.",
                List.of("x".repeat(2001)), Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.revise(original, "FAKE document.",
                List.of("Totally unrelated zebra."), Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void correctionRejectsCarriageReturnsAndBlankText() {
        var original = draft(source());
        var service = service(REVIEWED);
        for (var text : List.of("Blood work\rplanned.", "Blood work planned.\r\n", "", " ", "\n\t")) {
            assertThatThrownBy(() -> service.revise(original, "FAKE document.", List.of(text), Set.of()))
                    .as("point text %s", text).isInstanceOf(IllegalArgumentException.class);
        }
        for (var overview : List.of("FAKE\rdocument.", "", "   ")) {
            assertThatThrownBy(() -> service.revise(original, overview, List.of("Blood work planned."), Set.of()))
                    .as("overview %s", overview).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(service.revise(original, "FAKE document.", List.of("Blood work\nplanned."), Set.of())
                .summary().points().get(0).get("text")).isEqualTo("Blood work\nplanned.");
    }

    @Test
    void repeatedCorrectionKeepsOriginalGenerationAndRejectsBackwardsRevisionClock() {
        var original = draft(source());
        var revised = service(REVIEWED).revise(original, "FAKE document.", List.of("Blood work planned."), Set.of());
        var again = service(REVIEWED.plusSeconds(10)).revise(revised, "FAKE blood document.", List.of("Blood work planned."), Set.of());
        assertThat(again.generatedAt()).isEqualTo(GENERATED);
        assertThat(again.modelIdentity()).isEqualTo(original.modelIdentity());
        assertThat(again.id()).isNotEqualTo(revised.id());
        assertThatThrownBy(() -> service(REVIEWED).approve(again, again.id(), source(), actor(), true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(REVIEWED.minusSeconds(1)).revise(again,
                "FAKE document.", List.of("Blood work planned."), Set.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldSnapshotCallerBytes_withoutChangingOriginal_andHashExactExtractedText() throws Exception {
        byte[] callerBytes = TEXT.getBytes(StandardCharsets.UTF_8);
        byte[] originalBytes = callerBytes.clone();
        var source = DocumentSummaryReviewBinding.capture(SCOPE, META, callerBytes);
        assertThat(callerBytes).isEqualTo(originalBytes);
        callerBytes[0] = 'X';
        assertThat(source.originalSha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(originalBytes)));
        assertThat(source.extractedTextSha256()).isEqualTo(source.originalSha256());
        var draft = draft(source);
        var approved = service(REVIEWED).approve(draft, draft.id(), source, actor(), true);
        approved.requireCurrent(source, draft);
    }

    @Test
    void shouldBindOriginalHtmlBytes_evenWhenExtractedTextIsIdentical() {
        var metadata = new DocumentSummaryReviewBinding.Metadata("FAKE.html", "text/html", "", null);
        var original = DocumentSummaryReviewBinding.capture(SCOPE, metadata,
                ("<p>" + TEXT + "</p>").getBytes(StandardCharsets.UTF_8));
        var changed = DocumentSummaryReviewBinding.capture(SCOPE, metadata,
                ("<div>" + TEXT + "</div>").getBytes(StandardCharsets.UTF_8));
        assertThat(changed.extractedTextSha256()).isEqualTo(original.extractedTextSha256());
        assertThat(changed.originalSha256()).isNotEqualTo(original.originalSha256());
        var draft = draft(original);
        var approved = service(REVIEWED).approve(draft, draft.id(), original, actor(), true);
        assertThatThrownBy(() -> approved.requireCurrent(changed, draft)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldPreservePartialPdfDisclosure_withoutDecidingSendEligibility() throws Exception {
        byte[] pdf;
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
            document.addPage(new PDPage()); // Missing text remains visible in the disclosure.
            document.save(output);
            pdf = output.toByteArray();
        }
        var source = DocumentSummaryReviewBinding.capture(SCOPE,
                new DocumentSummaryReviewBinding.Metadata("FAKE.pdf", "application/pdf", "FAKE document", 1234L), pdf);
        var draft = draft(source);
        assertThat(draft.extractionComplete()).isFalse();
        assertThat(draft.extractionDisclosure()).contains("One or more pages have no readable text");
        var approved = service(REVIEWED).approve(draft, draft.id(), source, actor(), true);
        assertThat(approved.extractionComplete()).isFalse();
        assertThat(approved.extractionDisclosure()).isEqualTo(draft.extractionDisclosure());
    }

    @Test
    void shouldRejectBlankSourceUnauthenticatedReviewAndUnapprovedNoticeLanguage() {
        var blank = DocumentSummaryReviewBinding.capture(SCOPE, META, "  ".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> draft(blank)).isInstanceOf(IllegalStateException.class);
        var source = source();
        var draft = draft(source);
        assertThatThrownBy(() -> service(REVIEWED).approve(draft, draft.id(), source, null, true))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service(GENERATED.minusSeconds(1)).approve(draft, draft.id(), source, actor(), true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(GENERATED).prepare(source, summary("FAKE document."), "FAKE-model", "de"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("notice");
    }

    @Test
    void frenchRendersTheFrenchNoticeVersionWithoutMixingLanguages() {
        var source = source();
        var english = draft(source);
        var french = service(GENERATED).prepare(source, summary("FAKE document describes blood work."), "FAKE-local-model@revision1", "fr");
        assertThat(french.language()).isEqualTo("fr");
        assertThat(french.version()).isNotEqualTo(english.version());
        String preview = french.previewNotice();
        assertThat(preview).startsWith("Aide à la lecture générée par l’IA — ouvrez le document original\n")
                .contains("Statut de la révision : révision non confirmée", "résultats d’examens")
                .doesNotContain("Review status", "AI-generated", "healthcare team", "Reviewed by");
        var approved = service(REVIEWED).approve(french, french.id(), source, actor(), true);
        assertThat(approved.patientNotice())
                .startsWith("Aide à la lecture générée par l’IA — ouvrez le document original\n")
                .contains("Statut de la révision : révisé par le professionnel de la santé FAKE Reviewer le " + REVIEWED)
                .contains("ne vous fiez pas à ce résumé.")
                .doesNotContain("Review status", "Reviewed by clinician", "do not rely");
        assertThat(DocumentSummaryReviewBinding.NOTICE_VERSION_FR).isEqualTo("carlos-5537-fr-1");
        // The English notice is unchanged, and the French one never reaches an English draft.
        assertThat(english.previewNotice()).contains("Review status: Review not confirmed").doesNotContain("Statut");
        assertThat(DocumentSummaryReviewBinding.unreviewedNotice()).isEqualTo(english.previewNotice());
        assertThat(DocumentSummaryReviewBinding.unreviewedNotice("fr")).isEqualTo(preview);
        assertThat(DocumentSummaryReviewBinding.unreviewedNotice("de")).isEqualTo(english.previewNotice());
    }

    @Test
    void spanishPolishAndPortugueseRenderTheirOwnNoticeVersionWithoutMixingLanguages() {
        var source = source();
        var english = draft(source);
        record Case(String language, String version, String heading, String status, String unconfirmed, String reviewed, String closing) {}
        for (var c : new Case[]{
                new Case("es", "carlos-5537-es-1", "Ayuda de lectura generada por IA — abra el documento original\n",
                        "Estado de la revisión: ", "revisión sin confirmar",
                        "revisado por el profesional de la salud FAKE Reviewer el " + REVIEWED, "no confíe en este resumen."),
                new Case("pl", "carlos-5537-pl-1", "Pomoc w czytaniu wygenerowana przez AI — proszę otworzyć oryginalny dokument\n",
                        "Status weryfikacji: ", "weryfikacja niepotwierdzona",
                        "zweryfikowane przez pracownika ochrony zdrowia FAKE Reviewer w dniu " + REVIEWED,
                        "nie należy polegać na tym streszczeniu."),
                new Case("pt-BR", "carlos-5537-pt-BR-1", "Auxílio de leitura gerado por IA — abra o documento original\n",
                        "Situação da revisão: ", "revisão não confirmada",
                        "revisado pelo profissional de saúde FAKE Reviewer em " + REVIEWED, "não confie neste resumo.")}) {
            var draft = service(GENERATED).prepare(source, summary("FAKE document describes blood work."), "FAKE-local-model@revision1", c.language());
            assertThat(draft.language()).isEqualTo(c.language());
            assertThat(draft.version()).isNotEqualTo(english.version());
            assertThat(DocumentSummaryReviewBinding.noticeVersion(c.language())).isEqualTo(c.version());
            assertThat(draft.previewNotice()).as(c.language()).startsWith(c.heading())
                    .contains(c.status() + c.unconfirmed(), c.closing())
                    .doesNotContain("Review status", "Statut de la", "AI-generated", "Reviewed by");
            var approved = service(REVIEWED).approve(draft, draft.id(), source, actor(), true);
            assertThat(approved.patientNotice()).as(c.language()).startsWith(c.heading())
                    .contains(c.status() + c.reviewed(), c.closing())
                    .doesNotContain("Review status", "Statut de la", "Reviewed by clinician");
            assertThat(DocumentSummaryReviewBinding.unreviewedNotice(c.language())).isEqualTo(draft.previewNotice());
        }
        // Polish says przychodnia for the clinic, never klinika; the three versions are distinct.
        assertThat(DocumentSummaryReviewBinding.unreviewedNotice("pl")).contains("przychodni").doesNotContainIgnoringCase("klinik");
        // The wording fixes of 10 Nov 2026.
        assertThat(DocumentSummaryReviewBinding.unreviewedNotice("es"))
                .contains("ni a las indicaciones", "servicios de urgencias o emergencias correspondientes");
        assertThat(DocumentSummaryReviewBinding.unreviewedNotice("pl"))
                .contains("W razie nagłego problemu zdrowotnego proszę skontaktować się")
                .doesNotContain("W nagłych problemach");
        assertThat(java.util.Set.of(DocumentSummaryReviewBinding.NOTICE_VERSION, DocumentSummaryReviewBinding.NOTICE_VERSION_FR,
                DocumentSummaryReviewBinding.NOTICE_VERSION_ES, DocumentSummaryReviewBinding.NOTICE_VERSION_PL,
                DocumentSummaryReviewBinding.NOTICE_VERSION_PT_BR)).hasSize(5);
        assertThat(DocumentSummaryReviewBinding.NOTICE_LANGUAGES).containsExactlyInAnyOrder("en", "fr", "es", "pl", "pt-BR");
        // Anything else is still refused, and an unknown language renders English.
        assertThatThrownBy(() -> service(GENERATED).prepare(source, summary("FAKE document."), "FAKE-model", "pt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(DocumentSummaryReviewBinding.unreviewedNotice("de")).isEqualTo(english.previewNotice());
    }

    @Test
    void noticeLanguageFollowsTheRecordNotTheBrowserAndFrenchWinsOverASpokenLanguage() {
        // Spoken language gives Spanish, Polish and Portuguese, by exact token.
        for (String value : new String[]{"Spanish", "spanish; castilian", "Spanish; Castilian", " SPA ", "es", "es-MX", "es_ES", "Español", "espanol", "Castellano"})
            assertThat(DocumentSummaryReviewBinding.noticeLanguage("English", value)).as(value).isEqualTo("es");
        for (String value : new String[]{"Polish", "POLISH", "pl", "pol", "Pol", "pl-PL", "Polski", "^Polish"})
            assertThat(DocumentSummaryReviewBinding.noticeLanguage("English", value)).as(value).isEqualTo("pl");
        for (String value : new String[]{"Portuguese", "por", "pt", "pt-BR", "pt_PT", "Português", "portugues", "Brazilian Portuguese"})
            assertThat(DocumentSummaryReviewBinding.noticeLanguage(null, value)).as(value).isEqualTo("pt-BR");
        // A decomposed accent (a combining mark) reads the same as the precomposed letter.
        assertThat(DocumentSummaryReviewBinding.noticeLanguage("English", "Espan\u0303ol")).isEqualTo("es");
        assertThat(DocumentSummaryReviewBinding.noticeLanguage("Franc\u0327ais", null)).isEqualTo("fr");
        assertThat(DocumentSummaryReviewBinding.noticeLanguage("English", "Portugue\u0302s")).isEqualTo("pt-BR");
        // An unknown, empty or merely similar spoken language is English: never a prefix match.
        for (String value : new String[]{"Cree", "Mandarin", "English", "French", "Spanglish", "Polynesian", "Portuguese Creole", "polish-ish", "spa-x", "es-", " ", "", "unknown", "Polo", null})
            assertThat(DocumentSummaryReviewBinding.noticeLanguage("English", value)).as(String.valueOf(value)).isEqualTo("en");
        // The official language French wins over a spoken language of another notice language.
        assertThat(DocumentSummaryReviewBinding.noticeLanguage("French", "Spanish")).isEqualTo("fr");
        assertThat(DocumentSummaryReviewBinding.noticeLanguage("fr-CA", "Polish")).isEqualTo("fr");
        assertThat(DocumentSummaryReviewBinding.noticeLanguage("Français", "Portuguese")).isEqualTo("fr");
        // A spoken French alone does not give French (French comes from the official language), and the
        // one-argument form is unchanged.
        assertThat(DocumentSummaryReviewBinding.noticeLanguage("English", "French")).isEqualTo("en");
        assertThat(DocumentSummaryReviewBinding.noticeLanguage("Spanish")).isEqualTo("en");
        assertThat(DocumentSummaryReviewBinding.noticeLanguage("French")).isEqualTo("fr");
    }

    @Test
    void noticeLanguageFollowsThePatientsOfficialLanguageAndDefaultsToEnglish() {
        for (String value : new String[]{"French", "french", " FRENCH ", "fr", "fr-CA", "fr_CA", "FRE", "fra", "Français", "francais"})
            assertThat(DocumentSummaryReviewBinding.noticeLanguage(value)).as(value).isEqualTo("fr");
        for (String value : new String[]{"English", "ENG", "en", "en-CA", "", " ", "Spanish", "Cree", "unknown", "Frisian", "free text", "fra-x", "fr-", "French Canadian", null})
            assertThat(DocumentSummaryReviewBinding.noticeLanguage(value)).as(String.valueOf(value)).isEqualTo("en");
    }
}
