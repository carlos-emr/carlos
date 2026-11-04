/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The reading-aid artifact against the portal contract's public sender vectors
 * (src/test/resources/clinical/summary/reading-aid-pair-sender-vectors.json, FAKE data, no keys):
 * canonical bytes, the manifest claim set, the limits, and the two refusals (no approval, changed
 * original). Keys are generated at run time.
 */
@Tag("unit")
class ReadingAidArtifactUnitTest {
    private static final Instant GENERATED = Instant.ofEpochSecond(1_792_450_200L);
    private static final Instant REVIEWED = Instant.ofEpochSecond(1_792_450_680L);
    private static final DocumentSummaryReviewBinding.Scope SCOPE = new DocumentSummaryReviewBinding.Scope("clinic-a", 7, 11);
    private static final DocumentSummaryReviewBinding.Metadata META =
            new DocumentSummaryReviewBinding.Metadata("FAKE.txt", "text/plain", "FAKE chest X-ray report", 1234L);
    private static final JsonNode VECTORS = vectors();
    private static final String TEXT = "FAKE chest X-ray report. FAKE no acute change compared with the prior study. FAKE repeat imaging in 3 months.";
    private static final byte[] ORIGINAL = TEXT.getBytes(StandardCharsets.UTF_8);

    // --- canonical JSON ---------------------------------------------------------------------------
    @Test
    void canonicalBytesOfTheContractsReadingAidMatchTheVectorByteForByte() {
        // The vector's SHA-256 is over its exact canonical bytes, so equal hashes mean equal bytes.
        byte[] actual = ReadingAidArtifact.canonical(plain(VECTORS.get("reading_aid")));
        assertThat(ReadingAidArtifact.sha256Hex(actual)).isEqualTo(VECTORS.get("reading_aid_sha256").asText());
        assertThat(actual).hasSize(VECTORS.get("manifest_claims").get("reading_aid").get("size").asInt());
        assertThat(ReadingAidArtifact.canonical(plain(VECTORS.get("manifest_claims"))))
                .asString(StandardCharsets.UTF_8).startsWith("{\"approval\":{\"id\":\"apr-FAKE0001-9d2e\"")
                .contains("\"revised_at\":null").contains("\"created_at\":1792450800,\"delivery_id\":");
    }

    @Test
    void canonicalWriterSortsByUtf16CodeUnitsEscapesOnlyWhatJsonRequiresAndRefusesOtherKinds() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("b", List.of("x", 2L));
        value.put("aé", "tab\there \"quoted\" back\\slash \u0001 é中");
        value.put("A", null);
        assertThat(new String(ReadingAidArtifact.canonical(value), StandardCharsets.UTF_8))
                .isEqualTo("{\"A\":null,\"aé\":\"tab\\there \\\"quoted\\\" back\\\\slash \\u0001 é中\",\"b\":[\"x\",2]}");
        for (Object bad : List.of(1.5, -1L, true, Map.of(1, "x"), "\ud800")) {
            assertThatThrownBy(() -> ReadingAidArtifact.canonical(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // --- the artifact from a real approval -------------------------------------------------------
    @Test
    void artifactOfAnApprovalIsTheContractsJsonWithCarlosOwnNoticeAndNoNames() {
        var approval = approval(summary(VECTORS.get("reading_aid")));
        var artifact = ReadingAidArtifact.of(approval, ORIGINAL, "apr-FAKE0001-9d2e");
        Map<String, Object> expected = plain(VECTORS.get("reading_aid"));
        @SuppressWarnings("unchecked") Map<String, Object> notice = new LinkedHashMap<>((Map<String, Object>) expected.get("notice"));
        notice.put("heading", DocumentSummaryReviewBinding.noticeHeading());
        notice.put("body", DocumentSummaryReviewBinding.noticeBody());
        notice.put("version", DocumentSummaryReviewBinding.NOTICE_VERSION);
        expected.put("notice", notice);
        assertThat(artifact.json()).isEqualTo(ReadingAidArtifact.canonical(expected));
        assertThat(artifact.sha256()).isEqualTo(ReadingAidArtifact.sha256Hex(artifact.json()));
        assertThat(artifact.size()).isEqualTo(artifact.json().length).isLessThanOrEqualTo(ReadingAidArtifact.MAX_BYTES);
        assertThat(artifact.language()).isEqualTo("en");
        assertThat(artifact.summaryVersion()).isEqualTo(approval.summaryVersion()).matches("[0-9a-f]{64}");
        assertThat(artifact.originalSha256()).isEqualTo(approval.sourceSha256()).isEqualTo(ReadingAidArtifact.sha256Hex(ORIGINAL));
        String json = new String(artifact.json(), StandardCharsets.UTF_8);
        assertThat(json).doesNotContain("FAKE-Reviewer", "FAKE-model", "provider", "4242");
        assertThat(json).startsWith("{\"language\":\"en\",\"notice\":{\"body\":");
    }

    @Test
    void manifestClaimsAreExactlyTheContractsFifteenWithTheAidsOwnSizeAndHash() {
        var approval = approval(summary(VECTORS.get("reading_aid")));
        var artifact = ReadingAidArtifact.of(approval, ORIGINAL, "apr-FAKE0001-9d2e");
        JsonNode v = VECTORS.get("manifest_claims");
        var pair = new ReadingAidArtifact.Pair(v.get("pair").get("pair_id").asText(), v.get("pair").get("original_delivery_id").asText());
        var routing = routing(v);
        Map<String, Object> claims = artifact.manifestClaims(pair, routing, v.get("created_at").asLong(), v.get("expires_at").asLong(), v.get("kid").asText());
        Map<String, Object> expected = plain(v);
        @SuppressWarnings("unchecked") Map<String, Object> aid = new TreeMap<>((Map<String, Object>) expected.get("reading_aid"));
        aid.put("size", (long) artifact.size());
        aid.put("sha256", artifact.sha256());
        aid.put("summary_version", artifact.summaryVersion());
        expected.put("reading_aid", aid);
        @SuppressWarnings("unchecked") Map<String, Object> pairClaim = new TreeMap<>((Map<String, Object>) expected.get("pair"));
        pairClaim.put("original_document_sha256", artifact.originalSha256());
        expected.put("pair", pairClaim);
        assertThat(ReadingAidArtifact.canonical(claims)).isEqualTo(ReadingAidArtifact.canonical(expected));
        assertThat(claims.keySet()).containsExactlyInAnyOrder("v", "typ", "delivery_id", "recipient_id", "device_key_id",
                "clinic_id", "clinic_name", "provider_name", "patient", "pair", "reading_aid", "approval", "created_at",
                "expires_at", "kid");
        @SuppressWarnings("unchecked") Map<String, Object> approvalClaim = (Map<String, Object>) claims.get("approval");
        assertThat(approvalClaim).containsOnlyKeys("id", "reviewer_role", "reviewed_at")
                .containsEntry("reviewer_role", "clinician").containsEntry("reviewed_at", REVIEWED.getEpochSecond());
        assertThat(new String(ReadingAidArtifact.canonical(claims), StandardCharsets.UTF_8))
                .doesNotContain("FAKE-Reviewer", "FAKE-model", "reviewer_name", "model", approval.reviewerId());
    }

    @Test
    void compactManifestIsTheSendersFormAndVerifiesWithTheRunTimeKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        var approval = approval(summary(VECTORS.get("reading_aid")));
        var artifact = ReadingAidArtifact.of(approval, ORIGINAL, "apr-FAKE0001-9d2e");
        JsonNode v = VECTORS.get("manifest_claims");
        var identity = new ReadingAidArtifact.Pair(v.get("pair").get("pair_id").asText(), v.get("pair").get("original_delivery_id").asText());
        List<String> kids = new ArrayList<>();
        ReadingAidArtifact.ClinicSigner signer = (payload, kid) -> {
            kids.add(kid);
            try {
                Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
                signature.initSign(pair.getPrivate());
                signature.update(payload);
                return signature.sign();
            } catch (java.security.GeneralSecurityException failure) {
                throw new java.io.IOException(failure);
            }
        };
        String compact = artifact.compactManifest(identity, routing(v), v.get("created_at").asLong(), v.get("expires_at").asLong(), "FAKE-kid", signer);
        String[] parts = compact.split("\\.");
        assertThat(parts).hasSize(2);
        byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
        byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
        assertThat(signature).hasSize(64);
        assertThat(payload).isEqualTo(artifact.manifestPayload(identity, routing(v), v.get("created_at").asLong(), v.get("expires_at").asLong(), "FAKE-kid"));
        assertThat(kids).containsExactly("FAKE-kid");
        Signature check = Signature.getInstance("SHA256withECDSAinP1363Format");
        check.initVerify(pair.getPublic());
        check.update(payload);
        assertThat(check.verify(signature)).isTrue();
        assertThat(compact).doesNotContain("=", "+", "/");
        ReadingAidArtifact.ClinicSigner shortSigner = (p, k) -> new byte[63];
        assertThatThrownBy(() -> artifact.compactManifest(identity, routing(v), v.get("created_at").asLong(), v.get("expires_at").asLong(), "FAKE-kid", shortSigner))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- refusals and limits ----------------------------------------------------------------------
    @Test
    void refusesWithoutAnApprovalAndWhenTheOriginalIsNotTheApprovedOne() {
        var approval = approval(summary(VECTORS.get("reading_aid")));
        assertThatThrownBy(() -> ReadingAidArtifact.of(null, ORIGINAL, "apr-1")).isInstanceOf(IllegalStateException.class);
        byte[] edited = (TEXT + " Edited.").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ReadingAidArtifact.of(approval, edited, "apr-1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ReadingAidArtifact.of(approval, null, "apr-1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ReadingAidArtifact.of(approval, ORIGINAL, " ")).isInstanceOf(IllegalArgumentException.class);
        // The approval id is opaque: one that carries the reviewer's provider number is refused.
        assertThatThrownBy(() -> ReadingAidArtifact.of(approval, ORIGINAL, "apr-" + approval.reviewerId() + "-9d2e"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ReadingAidArtifact.of(approval, ORIGINAL, "apr-FAKE0001-9d2e").size()).isPositive();
    }

    @Test
    void refusesALanguageWithoutAPatientNoticeAndALifetimeBeyondSevenDays() {
        // The notice version is carlos-5537-en-1, and the review binding already refuses a draft in a
        // language without a notice, so no French approval can exist yet; the artifact's own check of
        // the notice tag against the language is a guard for the day a second notice version lands.
        assertThatThrownBy(() -> approval(summary(VECTORS.get("reading_aid")), "fr"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("language");
        var artifact = ReadingAidArtifact.of(approval(summary(VECTORS.get("reading_aid"))), ORIGINAL, "apr-1");
        JsonNode v = VECTORS.get("manifest_claims");
        var routing = routing(v);
        var pair = new ReadingAidArtifact.Pair(v.get("pair").get("pair_id").asText(), v.get("pair").get("original_delivery_id").asText());
        long created = v.get("created_at").asLong();
        assertThat(artifact.manifestClaims(pair, routing, created, created + ReadingAidArtifact.MAX_LIFETIME_SECONDS, "k")).isNotEmpty();
        assertThatThrownBy(() -> artifact.manifestClaims(pair, routing, created, created + ReadingAidArtifact.MAX_LIFETIME_SECONDS + 1, "k"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesContentBeyondTheContractsLimits() {
        var tooManyPoints = ClinicalSummaryAgentProtocol.JSON.createObjectNode().put("overview", "FAKE overview of the report.");
        var points = tooManyPoints.putArray("points");
        // The review validator already caps points at 50 and evidence at 5; the artifact repeats those caps
        // and adds its own 64 KiB bound, which the validator's 2000-character points can exceed.
        for (int i = 0; i < 51; i++) points.addObject().put("text", "FAKE point " + i + " of the report.").putArray("evidence").add("X-ray report");
        var tooManyEvidence = ClinicalSummaryAgentProtocol.JSON.createObjectNode().put("overview", "FAKE overview of the report.");
        var evidence = tooManyEvidence.putArray("points").addObject().put("text", "FAKE point of the report.").putArray("evidence");
        for (int i = 0; i < 6; i++) evidence.add(TEXT.substring(i, i + 12));
        var oversize = ClinicalSummaryAgentProtocol.JSON.createObjectNode().put("overview", "FAKE overview of the report.");
        var big = oversize.putArray("points");
        for (int i = 0; i < 50; i++) big.addObject().put("text", "report " + i + " " + "x".repeat(1980)).putArray("evidence").add("X-ray report");
        for (ObjectNode output : List.of(tooManyPoints, tooManyEvidence, oversize)) {
            assertThatThrownBy(() -> ReadingAidArtifact.of(approval(new DocumentSummary(output)), ORIGINAL, "apr-1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var fifty = ClinicalSummaryAgentProtocol.JSON.createObjectNode().put("overview", "FAKE overview of the report.");
        var ok = fifty.putArray("points");
        for (int i = 0; i < 50; i++) ok.addObject().put("text", "FAKE point " + i + " of the report.").putArray("evidence").add("X-ray report");
        assertThat(ReadingAidArtifact.of(approval(new DocumentSummary(fifty)), ORIGINAL, "apr-1").size()).isLessThan(ReadingAidArtifact.MAX_BYTES);
    }

    @Test
    void manifestClaimsRefuseALifetimeBeforeTheReviewAndASelfPair() {
        var approval = approval(summary(VECTORS.get("reading_aid")));
        var artifact = ReadingAidArtifact.of(approval, ORIGINAL, "apr-1");
        JsonNode v = VECTORS.get("manifest_claims");
        var routing = routing(v);
        var pair = new ReadingAidArtifact.Pair(v.get("pair").get("pair_id").asText(), v.get("pair").get("original_delivery_id").asText());
        assertThatThrownBy(() -> artifact.manifestClaims(pair, routing, REVIEWED.getEpochSecond() - 1, v.get("expires_at").asLong(), "k"))
                .isInstanceOf(IllegalArgumentException.class);
        var self = new ReadingAidArtifact.Pair(v.get("pair").get("pair_id").asText(), routing.deliveryId());
        assertThatThrownBy(() -> artifact.manifestClaims(self, routing, v.get("created_at").asLong(), v.get("expires_at").asLong(), "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReadingAidArtifact.Pair("not-a-uuid", v.get("pair").get("original_delivery_id").asText()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers ---------------------------------------------------------------------------------
    private static JsonNode vectors() {
        try (var in = ReadingAidArtifactUnitTest.class.getResourceAsStream("/clinical/summary/reading-aid-pair-sender-vectors.json")) {
            return ClinicalSummaryAgentProtocol.JSON.readTree(in);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    private static DocumentSummary summary(JsonNode readingAid) {
        ObjectNode output = ClinicalSummaryAgentProtocol.JSON.createObjectNode().put("overview", readingAid.get("overview").asText());
        output.set("points", readingAid.get("points").deepCopy());
        return new DocumentSummary(output);
    }

    private static DocumentSummaryReviewBinding.Approval approval(DocumentSummary summary) { return approval(summary, "en"); }

    private static DocumentSummaryReviewBinding.Approval approval(DocumentSummary summary, String language) {
        var source = DocumentSummaryReviewBinding.capture(SCOPE, META, ORIGINAL);
        var draft = new DocumentSummaryReviewBinding(Clock.fixed(GENERATED, ZoneOffset.UTC)).prepare(source, summary, "FAKE-model", language);
        var provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("4242");
        when(provider.getFormattedName()).thenReturn("FAKE-Reviewer, Demo");
        var actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProvider()).thenReturn(provider);
        return new DocumentSummaryReviewBinding(Clock.fixed(REVIEWED, ZoneOffset.UTC)).approve(draft, draft.id(), source, actor, true);
    }

    private static ReadingAidArtifact.Routing routing(JsonNode v) {
        return new ReadingAidArtifact.Routing(v.get("delivery_id").asText(), v.get("recipient_id").asText(), v.get("device_key_id").asText(),
                v.get("clinic_id").asText(), v.get("clinic_name").asText(), v.get("provider_name").asText(),
                v.get("patient").get("name").asText(), v.get("patient").get("birth_date").asText());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> plain(JsonNode node) {
        return ClinicalSummaryAgentProtocol.JSON.convertValue(node, Map.class);
    }
}
