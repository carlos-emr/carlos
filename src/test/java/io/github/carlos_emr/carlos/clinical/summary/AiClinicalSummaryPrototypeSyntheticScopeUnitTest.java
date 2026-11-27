/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class AiClinicalSummaryPrototypeSyntheticScopeUnitTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode chart() throws Exception {
        ObjectNode node = mapper.valueToTree(new SyntheticClinicalSummaryProvider()
                .load(null, ClinicalSummaryRequest.synthetic()).getView());
        node.putObject("patient_context").put("id", "demographic-42").put("label", "Test Patient")
                .put("synthetic", false).put("generation_fixture", "TEST");
        node.putArray("claims");
        node.putArray("sections");
        node.putArray("fact_ledger");
        node.putArray("sources").addObject().put("id", "note-1").put("patient_id", "demographic-42")
                .put("title", "Test note").put("date", "2026-01-01").put("text", "Verified synthetic note");
        node.putArray("coverage").addObject().put("source_id", "note-1").put("status", "reviewed_not_cited")
                .put("reason", "Source review");
        node.putArray("validation");
        return node;
    }

    private ArrayNode manifest() {
        ArrayNode manifest = mapper.createArrayNode();
        manifest.addObject().put("chart_no", "TEST").put("label", "Test Patient").putArray("notes")
                .addObject().put("sha256", SyntheticSummaryScope.sha256("Verified synthetic note")).put("date", "2026-01-01");
        return manifest;
    }

    @Test
    void acceptsVerifiedNoteContentAndRejectsChangedContent() throws Exception {
        ObjectNode chart = chart();
        assertThat(SyntheticSummaryScope.isEligible(new ClinicalSummaryArtifact(chart), manifest())).isTrue();
        ((ObjectNode) chart.get("sources").get(0)).put("text", "New unverified clinical information");
        assertThat(SyntheticSummaryScope.isEligible(new ClinicalSummaryArtifact(chart), manifest())).isFalse();
    }

    @Test
    void requiresMatchingIdentityAndDates() throws Exception {
        ObjectNode chart = chart();
        ((ObjectNode) chart.get("patient_context")).put("label", "Other patient");
        assertThat(SyntheticSummaryScope.isEligible(new ClinicalSummaryArtifact(chart), manifest())).isFalse();
        chart = chart();
        ((ObjectNode) chart.get("sources").get(0)).put("date", "2026-02-01");
        assertThat(SyntheticSummaryScope.isEligible(new ClinicalSummaryArtifact(chart), manifest())).isFalse();
    }

    @Test
    void refusesIncompleteFixtureAndNonNoteSources() throws Exception {
        ArrayNode manifest = manifest();
        ((ArrayNode) manifest.get(0).get("notes")).addObject().put("sha256", "missing").put("date", "2026-01-02");
        assertThat(SyntheticSummaryScope.isEligible(new ClinicalSummaryArtifact(chart()), manifest)).isFalse();
        ObjectNode chart = chart();
        ((ObjectNode) chart.get("sources").get(0)).put("id", "rx-1");
        ((ObjectNode) chart.get("coverage").get(0)).put("source_id", "rx-1");
        assertThat(SyntheticSummaryScope.isEligible(new ClinicalSummaryArtifact(chart), manifest())).isFalse();
    }

    @Test
    void demographicMustMatchAllSeedMarkersNotJustItsNumber() {
        Demographic demographic = mock(Demographic.class);
        when(demographic.getChartNo()).thenReturn("NHSSYN001");
        when(demographic.getAlias()).thenReturn("NHS synthetic 28570119-9cdc-4120-98c0-4edb76cf36a3");
        when(demographic.getDisplayName()).thenReturn("FAKE-NHS Wells, Judith Ada");
        assertThat(SyntheticSummaryScope.fixtureId(demographic)).isEqualTo("NHSSYN001");
        when(demographic.getAlias()).thenReturn("Different patient");
        assertThat(SyntheticSummaryScope.fixtureId(demographic)).isEmpty();
    }
}
