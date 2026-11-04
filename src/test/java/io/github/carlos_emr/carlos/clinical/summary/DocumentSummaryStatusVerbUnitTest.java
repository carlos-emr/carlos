/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;
import static org.assertj.core.api.Assertions.*;

/**
 * The status-word provenance rule: a point may say prescribed, administered, given, started, taken,
 * scheduled, booked, arranged or instructed (or the French forms) only when one of its own excerpts
 * uses a word of that family; the overview only when the document does. Stub output exercises the
 * host rule, not the model.
 */
@Tag("unit")
class DocumentSummaryStatusVerbUnitTest {
    @Test
    void shouldAcceptStatusWordsTheEvidenceStatesInBothLanguages() throws Exception {
        for (String line : List.of("The clinician prescribed a blood test for Tuesday.",
                "The first dose was administered at 09:10.",
                "An eye review is scheduled for 2027-03-04.",
                "Aucun antibiotique n'est prescrit.",
                "La première dose a été administrée à 9 h 10.",
                "Aucun contrôle supplémentaire n'est prévu cette semaine.")) {
            assertAccepted(line, point(line, line));
        }
    }

    @Test
    void shouldAcceptAStatusWordWhenTheExcerptUsesAnotherFormOfTheSameFamily() throws Exception {
        assertAccepted("Prescription: metoprolol 12.5 mg twice daily.",
                point("Metoprolol is prescribed at 12.5 mg twice daily.", "Prescription: metoprolol 12.5 mg twice daily."));
        assertAccepted("Métoprolol prescrit : 12,5 mg deux fois par jour.",
                point("Le métoprolol est prescrit à 12,5 mg deux fois par jour.", "Métoprolol prescrit : 12,5 mg deux fois par jour."));
    }

    @Test
    void shouldRejectAPointThatTurnsARecordedRegimenIntoAPrescriptionOrAdministration() {
        String source = "Aucun antibiotique n'est prescrit.\nMétoprolol : 12,5 mg deux fois par jour.";
        assertRejected(source, point("Le métoprolol est prescrit à 12,5 mg deux fois par jour.", "Métoprolol : 12,5 mg deux fois par jour."),
                "prescrit");
        assertRejected(source, point("Le métoprolol est administré à 12,5 mg deux fois par jour.", "Métoprolol : 12,5 mg deux fois par jour."),
                "administré");
        String english = "Metoprolol: 12.5 mg twice daily.";
        assertRejected(english, point("Metoprolol is prescribed at 12.5 mg twice daily.", english), "prescribed");
        // "taken" is an everyday word (take care, taking into account) and is no longer a status family.
        assertThat(DocumentSummaryService.statusWordWithoutSupport("Metoprolol is taken at 12.5 mg twice daily.", english)).isNull();
    }

    @Test
    void shouldRejectAPointThatTurnsADatedImperativeIntoAnArrangement() {
        String source = "Review the wound on 2026-12-03.";
        assertRejected(source, point("A wound review is scheduled for 2026-12-03.", source), "scheduled");
        assertRejected(source, point("A wound review is booked for 2026-12-03.", source), "booked");
        assertRejected(source, point("A wound review was arranged for 2026-12-03.", source), "arranged");
    }

    @Test
    void shouldRejectAnOverviewThatAddsAStatusTheDocumentDoesNotState() {
        String source = "Aucun antibiotique n'est prescrit.\nMétoprolol : 12,5 mg deux fois par jour.";
        ObjectNode output = point("Aucun antibiotique n'est prescrit.", "Aucun antibiotique n'est prescrit.");
        output.put("overview", "Aucun antibiotique n'est prescrit et le métoprolol est administré à 12,5 mg deux fois par jour.");
        assertRejected(source, output, "administré");
    }

    @Test
    void shouldReportTheFirstUnsupportedFamilyAndNullWhenEverythingIsSupported() {
        assertThat(DocumentSummaryService.statusWordWithoutSupport("Metoprolol is prescribed and administered.",
                "Prescription: metoprolol")).isEqualTo("administered");
        assertThat(DocumentSummaryService.statusWordWithoutSupport("Aucun antibiotique n'est prescrit.",
                "Aucun antibiotique n'est prescrit.")).isNull();
        assertThat(DocumentSummaryService.statusWordWithoutSupport("The wound improves.", "The wound improves.")).isNull();
    }

    @Test
    void shouldAcceptEverydayGivenAndTakenPhrasesThatSayNothingAboutAMedication() throws Exception {
        for (String[] pair : java.util.List.of(
                new String[]{"Prise de sang le 2026-12-03.", "Une prise de sang est notée pour le 2026-12-03."},
                new String[]{"Prise en charge par la clinique de la douleur.", "Prise en charge notée par la clinique de la douleur."},
                new String[]{"Étant donné l'historique, un suivi est noté.", "Un suivi est noté étant donné l'historique."},
                new String[]{"Given the history, a follow-up is noted.", "A follow-up is noted given the history."},
                new String[]{"Taking into account the history, a review is noted.", "A review is noted, taking into account the history."},
                new String[]{"Take care when standing up.", "Advice to take care when standing up."})) {
            assertAccepted(pair[0], point(pair[1], pair[0]));
        }
    }

    @Test
    void shouldTreatPlannedAndInstructionAsDescriptionsNotStatuses() throws Exception {
        // "prévu" is the dated-plan paraphrase and an overview rightly calls an imperative an instruction;
        // both were the only false rejections on the eight exposed synthetic outputs.
        String imperative = "Revoir l'éruption le 2026-11-16.";
        assertAccepted(imperative, point("Il est prévu de revoir l'éruption le 2026-11-16.", imperative));
        ObjectNode output = point("Il est prévu de revoir l'éruption le 2026-11-16.", imperative);
        output.put("overview", "Le document indique une instruction de revoir l'éruption à une date future.");
        assertAccepted(imperative, output);
        String english = "Review the wound on 2026-12-03.";
        ObjectNode described = point("The wound is to be reviewed on 2026-12-03.", english);
        described.put("overview", "The document contains a single instruction to review a wound on 2026-12-03.");
        assertAccepted(english, described);
    }

    private static ObjectNode point(String text, String excerpt) {
        ObjectNode output = JSON.createObjectNode().put("overview", excerpt);
        output.putArray("points").addObject().put("text", text).putArray("evidence").add(excerpt);
        return output;
    }

    private static void assertAccepted(String source, JsonNode output) throws Exception {
        DocumentSummary result = new DocumentSummaryService(new StubAgent(output)).summarize(source);
        assertThat(result.points()).hasSize(1);
    }

    private static void assertRejected(String source, JsonNode output, String word) {
        assertThatThrownBy(() -> new DocumentSummaryService(new StubAgent(output)).summarize(source))
                .isInstanceOf(ClinicalSummaryGenerationException.class).hasMessageContaining("failed validation");
        assertThatThrownBy(() -> DocumentSummaryService.validate(output, source))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("status").hasMessageContaining(word);
    }

    private static final class StubAgent implements ClinicalSummaryAgent {
        private final JsonNode output;
        private StubAgent(JsonNode output) { this.output = output; }
        public String displayName() { return "synthetic offline boundary"; }
        public JsonNode generate(JsonNode request) { return output.deepCopy(); }
    }
}
