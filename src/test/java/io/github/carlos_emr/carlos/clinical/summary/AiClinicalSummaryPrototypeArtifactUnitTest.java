/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class AiClinicalSummaryPrototypeArtifactUnitTest {
    private final SyntheticClinicalSummaryProvider provider = new SyntheticClinicalSummaryProvider();

    private ObjectNode fixture() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(SyntheticClinicalSummaryProvider.FIXTURE)) {
            return (ObjectNode) new ObjectMapper().readTree(input);
        }
    }

    @Test
    void loadsFixtureAndResolvesEveryCitation() throws Exception {
        ClinicalSummaryArtifact artifact = provider.load(null, ClinicalSummaryRequest.synthetic());
        assertThat(artifact.isRenderable()).isTrue();
        for (JsonNode claim : fixture().get("claims")) {
            assertThat(claim.get("source_ids").size()).isPositive();
            for (JsonNode source : claim.get("source_ids")) {
                assertThat(provider.resolve(null, ClinicalSummaryRequest.synthetic(), source.asText())).isPresent();
            }
        }
        assertThat(provider.resolve(null, ClinicalSummaryRequest.synthetic(), "missing")).isEmpty();
        assertThat(artifact.getClaimsById()).hasSize(3);
    }

    @Test
    void rejectsSchemaVersionOverflow() throws Exception {
        ObjectNode input = fixture();
        input.put("schema_version", 4294967297L);
        assertThatThrownBy(() -> new ClinicalSummaryArtifact(input)).hasMessage("Unsupported schema version");
    }

    @Test
    void refusesChartScopeAndArbitraryArtifactSelection() {
        assertThatThrownBy(() -> provider.load(null,
                new ClinicalSummaryRequest(1, null, "patient-overview", "synthetic-overview-001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.load(null,
                new ClinicalSummaryRequest(null, null, "patient-overview", "../../other")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingAndUnknownCitations() throws Exception {
        ObjectNode input = fixture();
        ((ObjectNode) input.get("claims").get(0)).putArray("source_ids");
        assertThatThrownBy(() -> new ClinicalSummaryArtifact(input)).hasMessage("Missing citation");
        ((ArrayNode) input.get("claims").get(0).get("source_ids")).add("other-patient-note");
        assertThatThrownBy(() -> new ClinicalSummaryArtifact(input)).hasMessage("Unknown or duplicate reference");
    }

    @Test
    void rejectsForeignPatientAndDuplicateSourceIds() throws Exception {
        ObjectNode input = fixture();
        ((ObjectNode) input.get("sources").get(0)).put("patient_id", "other");
        assertThatThrownBy(() -> new ClinicalSummaryArtifact(input)).hasMessage("Source belongs to another patient");
        ObjectNode duplicate = fixture();
        ((ArrayNode) duplicate.get("sources")).add(duplicate.get("sources").get(0).deepCopy());
        assertThatThrownBy(() -> new ClinicalSummaryArtifact(duplicate)).hasMessage("Duplicate identifier");
    }

    @Test
    void requiresCompleteConsistentCoverage() throws Exception {
        ObjectNode input = fixture();
        ((ArrayNode) input.get("coverage")).remove(3);
        assertThatThrownBy(() -> new ClinicalSummaryArtifact(input)).hasMessage("Coverage must account for every source");
        ObjectNode inconsistent = fixture();
        ((ObjectNode) inconsistent.get("coverage").get(0)).put("status", "excluded");
        assertThatThrownBy(() -> new ClinicalSummaryArtifact(inconsistent)).hasMessage("Coverage disagrees with citations");
    }

    @Test
    void requiresLedgerCitationsAndSectionMembership() throws Exception {
        ObjectNode input = fixture();
        ((ObjectNode) input.get("fact_ledger").get(0)).putArray("source_ids");
        assertThatThrownBy(() -> new ClinicalSummaryArtifact(input)).hasMessage("Missing citation");
        ObjectNode unplaced = fixture();
        ((ArrayNode) unplaced.get("sections")).remove(0);
        assertThatThrownBy(() -> new ClinicalSummaryArtifact(unplaced)).hasMessage("Every claim must appear in a section");
    }

    @Test
    void passAndWarningsRenderButErrorsWithholdSummary() throws Exception {
        ObjectNode input = fixture();
        assertThat(new ClinicalSummaryArtifact(input).isRenderable()).isTrue();
        ((ObjectNode) input.get("validation").get(0)).put("severity", "error");
        ClinicalSummaryArtifact artifact = new ClinicalSummaryArtifact(input);
        assertThat(artifact.isRenderable()).isFalse();
        assertThat(artifact.getView()).containsKeys("sources", "coverage", "validation");
        ((ObjectNode) input.get("validation").get(0)).put("severity", "unknown");
        assertThatThrownBy(() -> new ClinicalSummaryArtifact(input)).hasMessage("Invalid severity");
    }

    @Test
    void emptyClaimsAreValidAndSnapshotsCannotMutateArtifact() throws Exception {
        ObjectNode input = fixture();
        input.putArray("claims");
        input.putArray("sections");
        for (JsonNode row : input.get("coverage")) {
            ((ObjectNode) row).put("status", "reviewed_not_cited");
        }
        ClinicalSummaryArtifact artifact = new ClinicalSummaryArtifact(input);
        input.put("artifact_id", "changed");
        artifact.getView().put("artifact_id", "also-changed");
        assertThat(artifact.getView().get("artifact_id")).isEqualTo("synthetic-overview-001");
        assertThat(artifact.getClaimsById()).isEmpty();
    }
}
