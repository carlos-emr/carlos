/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import java.util.List;
import org.junit.jupiter.api.Test;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChartUpdateCoverageUnitTest {
    private static final String SOURCE = "Pulse 80.\n\nAllergies: none.\n\nUnselected fact.";

    private com.fasterxml.jackson.databind.node.ObjectNode audit(String source) {
        var node = JSON.createObjectNode().put("version", 1);
        node.putArray("sections").addObject().put("start", 0).put("end", source.length());
        node.putArray("rejected");
        return node;
    }

    @Test void shouldDeriveGapsAndNativeHandoffs_fromValidatedSourceQuotations() {
        var coverage = ChartUpdateCoverage.parse(audit(SOURCE), SOURCE);
        var rows = coverage.sections(SOURCE, List.of(new ChartUpdateProposals.Proposal("history", "Pulse 80.", "Concerns"),
                new ChartUpdateProposals.Proposal("review", "Allergies: none.", "Allergies")));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("text")).isEqualTo(SOURCE);
        assertThat(rows.getFirst().get("gaps")).isEqualTo(List.of("\n\nUnselected fact."));
        assertThat(rows.getFirst().get("links").toString()).contains("nativeRecord=true", "nativeRecord=false", "destination=Allergies");
    }

    @Test void shouldKeepAllSourceTextAsGaps_whenNoCandidatesSurvive() {
        var node = audit(SOURCE);
        node.withArray("rejected").addObject().put("evidence", "Pulse 80.").put("reason", "AI explanation <script>");
        var coverage = ChartUpdateCoverage.parse(node, SOURCE);
        assertThat(coverage.sections(SOURCE, List.of()).getFirst().get("gaps")).isEqualTo(List.of(SOURCE));
        assertThat(coverage.getRejected()).containsExactly(new ChartUpdateCoverage.Rejected("Pulse 80.", "AI explanation <script>"));
    }

    @Test void shouldRejectAudit_whenPartitionHasGapsOverlapsOrInvalidOffsets() throws Exception {
        for (String sections : List.of("[]", "[{\"start\":1,\"end\":4}]", "[{\"start\":0,\"end\":3}]",
                "[{\"start\":0,\"end\":2},{\"start\":1,\"end\":4}]", "[{\"start\":0,\"end\":5}]",
                "[{\"start\":false,\"end\":4}]", "[{\"start\":0,\"end\":4294967300}]")) {
            var node = audit("Test");
            node.set("sections", JSON.readTree(sections));
            assertThatThrownBy(() -> ChartUpdateCoverage.parse(node, "Test")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void shouldRejectAudit_whenSurrogatePairIsSplit() {
        String source = "😀 fact.";
        var node = audit(source);
        node.putArray("sections").addObject().put("start", 0).put("end", 1);
        node.withArray("sections").addObject().put("start", 1).put("end", source.length());
        assertThatThrownBy(() -> ChartUpdateCoverage.parse(node, source)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ChartUpdateCoverage.parse(audit(source), source).sections(source, List.of()).getFirst().get("text")).isEqualTo(source);
    }

    @Test void shouldRejectAudit_whenRejectionsAreFabricatedOrClaimsUnsupported() {
        var node = audit(SOURCE);
        node.withArray("rejected").addObject().put("evidence", "Invented.").put("reason", "Rejected");
        assertThatThrownBy(() -> ChartUpdateCoverage.parse(node, SOURCE)).isInstanceOf(IllegalArgumentException.class);
        node.putArray("rejected");
        node.put("complete", true);
        assertThatThrownBy(() -> ChartUpdateCoverage.parse(node, SOURCE)).isInstanceOf(IllegalArgumentException.class);
        node.remove("complete");
        node.put("version", 4294967297L);
        assertThatThrownBy(() -> ChartUpdateCoverage.parse(node, SOURCE)).isInstanceOf(IllegalArgumentException.class);
        node.put("version", true);
        assertThatThrownBy(() -> ChartUpdateCoverage.parse(node, SOURCE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void shouldExposeAuditOnlyForNewResults_whenAgentSupportsCoverage() throws Exception {
        var agent = mock(ClinicalSummaryAgent.class);
        when(agent.requestBytes()).thenReturn(16000);
        when(agent.providesChartCoverageAudit()).thenReturn(true);
        var output = JSON.createObjectNode();
        output.putArray("proposals");
        output.set("coverage", audit(SOURCE));
        when(agent.generate(any())).thenReturn(output);
        var report = new ChartUpdateProposals(agent).generateReport(SOURCE);
        assertThat(report.coverage()).isNotNull();
        when(agent.providesChartCoverageAudit()).thenReturn(false);
        assertThat(new ChartUpdateProposals(agent).generateReport(SOURCE).coverage()).isNull();
        when(agent.providesChartCoverageAudit()).thenReturn(true);
        output.remove("coverage");
        assertThat(new ChartUpdateProposals(agent).generateReport(SOURCE).coverage()).isNull();
    }

    @Test void shouldAcceptAuditedSyntheticTrial_withAllRetainedQuotationsAndSections() throws Exception {
        var evidence = JSON.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(
                "tools/ai-clinical-summary-draft/quality/2026-10-02/section-coverage-results.json")));
        int quotations = 0;
        for (var run : evidence.path("runs")) {
            String source = run.path("source").asText();
            var output = run.path("output");
            var proposals = ChartUpdateProposals.validate(output, source);
            var coverage = ChartUpdateCoverage.parse(output.get("coverage"), source);
            var sections = coverage.sections(source, proposals);
            assertThat(sections.stream().map(row -> (String) row.get("text")).collect(java.util.stream.Collectors.joining()))
                    .isEqualTo(source);
            assertThat(proposals.size()).isGreaterThan(30);
            quotations += proposals.size();
        }
        assertThat(quotations).isEqualTo(211);
    }

    @Test void shouldCountRepeatedAndOverlappingQuotations_withoutHidingOtherSourceText() {
        String source = "Fact one. Fact two.\n\nFact one.\n\nOther.";
        var coverage = ChartUpdateCoverage.parse(audit(source), source);
        var rows = coverage.sections(source, List.of(new ChartUpdateProposals.Proposal("history", "Fact one."),
                new ChartUpdateProposals.Proposal("history", "Fact one. Fact two.")));
        assertThat(rows.getFirst().get("gaps")).isEqualTo(List.of("\n\nOther."));
    }
}
