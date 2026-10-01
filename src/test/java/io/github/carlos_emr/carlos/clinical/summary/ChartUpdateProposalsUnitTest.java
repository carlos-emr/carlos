/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.clinical.summary;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChartUpdateProposalsUnitTest {
    private static final String SOURCE = "Suspected asthma. Review if symptoms persist.";

    @Test void shouldKeepExactSource_whenProposalRetainsQualification() throws Exception {
        var agent = mock(ClinicalSummaryAgent.class);
        when(agent.requestBytes()).thenReturn(16000);
        when(agent.generate(any())).thenAnswer(call -> {
            var request = (com.fasterxml.jackson.databind.JsonNode) call.getArgument(0);
            assertThat(request.get("workflow").asText()).isEqualTo("chart-update-proposals");
            assertThat(request.get("sources").get(0).get("text").asText()).isEqualTo(SOURCE);
            assertThat(request.has("patient_id")).isFalse();
            return JSON.readTree("{\"proposals\":[{\"kind\":\"history\",\"evidence\":\"Suspected asthma.\"}]}");
        });
        assertThat(new ChartUpdateProposals(agent).generate(SOURCE)).containsExactly(
                new ChartUpdateProposals.Proposal("history", "Suspected asthma."));
    }

    @Test void shouldRejectEvidence_whenInventedOrContainingWriteInstructions() throws Exception {
        for (String row : List.of("{\"kind\":\"history\",\"evidence\":\"Confirmed asthma\"}",
                "{\"kind\":\"prescription\",\"evidence\":\"Suspected asthma.\"}",
                "{\"kind\":\"history\",\"evidence\":\"Suspected asthma.\",\"patient_id\":42}")) {
            var output = JSON.readTree("{\"proposals\":[" + row + "]}");
            assertThatThrownBy(() -> ChartUpdateProposals.validate(output, SOURCE)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void shouldKeepFullChartDestinations_andRejectIncorrectRouting() throws Exception {
        String source = "Social History\n- Non-smoker\n\nAllergies\nNone";
        var output = JSON.readTree("{\"proposals\":[{\"kind\":\"history\",\"destination\":\"SocHistory\",\"evidence\":\"Social History\\n- Non-smoker\"},"
                + "{\"kind\":\"review\",\"destination\":\"Allergies\",\"evidence\":\"Allergies\\nNone\"}]}");
        assertThat(ChartUpdateProposals.validate(output, source)).containsExactly(
                new ChartUpdateProposals.Proposal("history", "Social History\n- Non-smoker", "SocHistory"),
                new ChartUpdateProposals.Proposal("review", "Allergies\nNone", "Allergies"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) output.path("proposals").get(1)).put("destination", "Concerns");
        assertThatThrownBy(() -> ChartUpdateProposals.validate(output, source)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void shouldRejectFragments_withOmittedQualification() {
        for (String[] pair : List.of(new String[]{"No asthma.", "asthma"},
                new String[]{"No\nasthma.", "asthma."},
                new String[]{"Asthma\nruled out.", "Asthma"},
                new String[]{"Asthma\n-ruled out.", "Asthma"},
                new String[]{"Asthma\n- ruled out.", "Asthma"},
                new String[]{"Review in four weeks\nif symptoms persist.", "Review in four weeks"},
                new String[]{"No asthma.", "asthma."},
                new String[]{"Asthma if confirmed.", "Asthma"},
                new String[]{"Review in two weeks if symptoms persist.", "Review in two weeks"},
                new String[]{"Dr. Smith advised review.", "Smith advised review."})) {
            assertThat(ChartUpdateProposals.completePassage(pair[0], pair[1])).isFalse();
        }
        assertThat(ChartUpdateProposals.completePassage("No asthma.\nAsthma.", "Asthma.")).isTrue();
        assertThat(ChartUpdateProposals.completePassage("Plan:\n- Review in two weeks.", "- Review in two weeks.")).isTrue();
    }

    @Test void shouldAcceptSavedReviewedQuotations_fromTenSyntheticTrialCases() throws Exception {
        var acceptance = JSON.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(
                "tools/ai-clinical-summary-draft/quality/2026-09-30/extraction-acceptance-results.json")));
        int cases = 0;
        int quotations = 0;
        for (var trial : acceptance.path("final").path("runs")) {
            String source = trial.path("source").asText();
            var output = trial.path("response").path("output");
            assertThatCode(() -> ChartUpdateProposals.validate(output, source))
                    .as(trial.path("label").asText()).doesNotThrowAnyException();
            cases++;
            quotations += output.path("proposals").size();
        }
        assertThat(cases).isEqualTo(10);
        assertThat(quotations).isEqualTo(21);
    }

    @Test void shouldAcceptBroadReviewedQuotations_fromLongAndShortSyntheticDocuments() throws Exception {
        var evidence = JSON.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(
                "tools/ai-clinical-summary-draft/quality/2026-10-01/broad-chart-results.json")));
        assertThat(evidence.path("runs").size()).isEqualTo(2);
        for (var trial : evidence.path("runs")) {
            var accepted = ChartUpdateProposals.validate(trial.path("output"), trial.path("source").asText());
            assertThat(accepted.size()).isGreaterThan(20).isLessThanOrEqualTo(ChartUpdateProposals.MAX_PROPOSALS);
            assertThat(accepted).anyMatch(row -> "review".equals(row.kind()));
            assertThat(accepted).anyMatch(row -> "SocHistory".equals(row.destination()));
            assertThat(accepted).anyMatch(row -> "FamHistory".equals(row.destination()));
        }
    }

    @Test void shouldAcceptCompletePassage_afterIndentedListMarker() {
        assertThat(ChartUpdateProposals.completePassage("Plan:\n  - Review in two weeks.", "Review in two weeks.")).isTrue();
        assertThat(ChartUpdateProposals.completePassage("Plan:\r\n  - Review in two weeks.", "Review in two weeks.")).isTrue();
    }

    @Test void shouldBoundWork_forRepeatedFragmentsInMaximumSizeSource() {
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(5), () -> {
            assertThat(ChartUpdateProposals.completePassage("x".repeat(60000), "x")).isFalse();
            assertThat(ChartUpdateProposals.completePassage("1".repeat(60000), "1")).isFalse();
            assertThat(ChartUpdateProposals.completePassage("No\nasthma.\n".repeat(5000), "asthma.")).isFalse();
            assertThat(ChartUpdateProposals.completePassage("No\nasthma.\n".repeat(4000) + "\nasthma.", "asthma.")).isTrue();
        });
    }

    @Test void shouldValidateProposals_whenEmptyOrDuplicated() throws Exception {
        assertThat(ChartUpdateProposals.validate(JSON.readTree("{\"proposals\":[]}"), SOURCE)).isEmpty();
        String row = "{\"kind\":\"history\",\"evidence\":\"Suspected asthma.\"}";
        var duplicate = JSON.readTree("{\"proposals\":[" + row + "," + row + "]}");
        assertThatThrownBy(() -> ChartUpdateProposals.validate(duplicate, SOURCE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void shouldMakeNoCall_whenSourceExceedsBudget() throws Exception {
        var agent = mock(ClinicalSummaryAgent.class);
        when(agent.requestBytes()).thenReturn(100);
        assertThatThrownBy(() -> new ChartUpdateProposals(agent).generate(SOURCE)).isInstanceOf(ClinicalSummaryGenerationException.class);
        verify(agent, never()).generate(any());
    }

    @Test void shouldReleaseCapacityAndHideUpstreamDetails_whenAgentFails() throws Exception {
        var agent = mock(ClinicalSummaryAgent.class);
        when(agent.requestBytes()).thenReturn(16000);
        when(agent.generate(any())).thenThrow(new IOException("private source and credentials"))
                .thenReturn(JSON.readTree("{\"proposals\":[]}"));
        var generator = new ChartUpdateProposals(agent);
        assertThatThrownBy(() -> generator.generate(SOURCE)).hasMessage("Proposals could not be generated or failed source validation. Nothing was saved.");
        assertThat(generator.generate(SOURCE)).isEmpty();
    }
}
