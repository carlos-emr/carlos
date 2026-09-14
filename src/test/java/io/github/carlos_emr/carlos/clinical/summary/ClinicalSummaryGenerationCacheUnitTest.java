/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ClinicalSummaryGenerationCacheUnitTest {
    private ClinicalSummaryArtifact chart;
    private ObjectNode output;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicLong clock = new AtomicLong();
    private String revision = "model-revision-1";
    private boolean replaceDuringGeneration;
    private MockedStatic<SyntheticSummaryScope> scope;
    private ClinicalSummaryGenerationCache cache;
    private ClinicalSummaryAgent agent;

    @BeforeEach
    void setUp() throws IOException {
        chart = new SyntheticClinicalSummaryProvider().load(null, ClinicalSummaryRequest.synthetic());
        ObjectNode fixture = JSON.valueToTree(chart.getView());
        output = JSON.createObjectNode();
        var sections = output.putArray("sections");
        sections.addObject().put("id", "clinical_overview").put("title", "Clinical overview")
                .putArray("claim_ids").add("claim-1");
        sections.addObject().put("id", "medications_allergies").put("title", "Medications and allergies")
                .putArray("claim_ids").add("claim-2").add("claim-3");
        output.set("claims", fixture.get("claims"));
        output.set("coverage", fixture.get("coverage"));
        cache = new ClinicalSummaryGenerationCache(2, 1024 * 1024, Duration.ofMinutes(15), clock::get);
        agent = new ClinicalSummaryAgent() {
            public String displayName() { return "Test model"; }
            public String cacheIdentity() { return revision; }
            public JsonNode generate(JsonNode request) {
                calls.incrementAndGet();
                if (replaceDuringGeneration) revision = "replaced";
                return output.deepCopy();
            }
        };
        scope = mockStatic(SyntheticSummaryScope.class);
        scope.when(() -> SyntheticSummaryScope.isEligible(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() { scope.close(); }

    private ClinicalSummaryArtifact generate(ClinicalSummaryArtifact input) throws Exception {
        // Actions construct new services on each request; cache reuse must survive that.
        return new ClinicalSummaryGenerationService(agent, cache).generate(input);
    }

    @Test
    void shouldReuseExactValidatedDraft_whenOnlySnapshotAssemblyMetadataChanges() throws Exception {
        var first = generate(chart);
        ObjectNode snapshot = JSON.valueToTree(chart.getView());
        snapshot.put("artifact_id", "fresh-read").put("generated_at", "2026-09-14T12:00:00Z");
        var second = generate(new ClinicalSummaryArtifact(snapshot));
        assertThat(calls.get()).isEqualTo(1);
        assertThat(second.getView()).isEqualTo(first.getView());
        second.getClaimsById().get("claim-1").put("text", "caller mutation");
        assertThat(generate(chart).getView()).isEqualTo(first.getView());
    }

    @Test
    void shouldRegenerate_whenSourcesPatientContextOrLedgerChanges() throws Exception {
        generate(chart);
        ObjectNode snapshot = JSON.valueToTree(chart.getView());
        ObjectNode source = (ObjectNode) snapshot.get("sources").get(0);
        source.put("text", source.get("text").asText() + " Updated source.");
        generate(new ClinicalSummaryArtifact(snapshot));
        ((ObjectNode) snapshot.get("patient_context")).put("label", "Changed patient context");
        generate(new ClinicalSummaryArtifact(snapshot));
        ((ObjectNode) snapshot.get("fact_ledger").get(0)).put("text", "Changed recorded fact");
        generate(new ClinicalSummaryArtifact(snapshot));
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test
    void shouldRejectCachedDraft_whenFixtureIsNoLongerEligible() throws Exception {
        generate(chart);
        scope.when(() -> SyntheticSummaryScope.isEligible(chart)).thenReturn(false);
        assertThatThrownBy(() -> generate(chart)).hasMessageContaining("No chart data was sent");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void shouldRegenerate_whenModelRevisionChangesOrExpires() throws Exception {
        generate(chart);
        revision = "model-revision-2";
        generate(chart);
        clock.addAndGet(Duration.ofMinutes(15).toNanos());
        generate(chart);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void shouldBypassCache_whenIdentityIsUnavailableOrCacheIsDisabled() throws Exception {
        revision = null;
        generate(chart);
        generate(chart);
        revision = "model-revision-1";
        new ClinicalSummaryGenerationService(agent, null).generate(chart);
        new ClinicalSummaryGenerationService(agent, null).generate(chart);
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test
    void shouldNotCacheRejectedOutput_orModelReplacedDuringInference() throws Exception {
        ObjectNode valid = output.deepCopy();
        ((ObjectNode) output.get("claims").get(0)).putArray("source_ids").add("unknown-source");
        assertThatThrownBy(() -> generate(chart)).hasMessageContaining("failed validation");
        output = valid;
        replaceDuringGeneration = true;
        generate(chart);
        replaceDuringGeneration = false;
        revision = "model-revision-1";
        generate(chart);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void shouldEvictLeastRecentlyUsedEntry_andRejectOversizedEntries() throws Exception {
        cache.put("a", chart);
        cache.put("b", chart);
        assertThat(cache.get("a")).isNotNull();
        cache.put("c", chart);
        assertThat(cache.get("b")).isNull();
        assertThat(cache.get("a")).isNotNull();
        var small = new ClinicalSummaryGenerationCache(2, 1, Duration.ofMinutes(15), clock::get);
        small.put("a", chart);
        assertThat(small.get("a")).isNull();
        int size = JSON.writeValueAsBytes(chart.getView()).length;
        var byteBound = new ClinicalSummaryGenerationCache(16, size, Duration.ofMinutes(15), clock::get);
        byteBound.put("a", chart);
        byteBound.put("b", chart);
        assertThat(byteBound.get("a")).isNull();
        assertThat(byteBound.get("b")).isNotNull();
    }

    @Test
    void shouldKeyAllEvidenceAndContractFields_butIgnoreObjectFieldOrder() throws IOException {
        ObjectNode snapshot = JSON.valueToTree(chart.getView());
        ObjectNode request = JSON.createObjectNode().put("instructions", "prompt-v1").put("request_id", "one");
        request.set("schema", JSON.createObjectNode().put("version", 1));
        String key = ClinicalSummaryGenerationCache.key(snapshot, request, "model-v1");
        ObjectNode reordered = JSON.createObjectNode();
        reordered.set("schema", request.get("schema"));
        reordered.put("request_id", "two").put("instructions", "prompt-v1");
        assertThat(ClinicalSummaryGenerationCache.key(snapshot, reordered, "model-v1")).isEqualTo(key);
        reordered.put("instructions", "prompt-v2");
        assertThat(ClinicalSummaryGenerationCache.key(snapshot, reordered, "model-v1")).isNotEqualTo(key);
        ((ObjectNode) request.get("schema")).put("version", 2);
        assertThat(ClinicalSummaryGenerationCache.key(snapshot, request, "model-v1")).isNotEqualTo(key);
    }
}
