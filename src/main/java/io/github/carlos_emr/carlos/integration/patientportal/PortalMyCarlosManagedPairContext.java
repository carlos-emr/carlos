/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryCodec.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded local DATA representation of the reference pair contract and captured provenance.
 * Parsing this record authenticates nothing: not PDFs, approval, capabilities or a source capture.
 * It is not a payload/wire schema or a persisted Snapshot extension. No send permission is encoded.
 * payloadSha256 is plain SHA256 of exact canonical payload bytes for identity; it is distinct from
 * the reference contract's purpose-framed payload_digest and is not a verified signed attribute.
 */
record PortalMyCarlosManagedPairContext(String pairId, String clinicId, String recipientId,
        Member original, Member readingAid, String payloadSha256, Capture capture) {
    static final int MAX_CONTEXT_BYTES = 4096;
    static final int MAX_MANIFEST_BYTES = 8192;
    static final int MAX_ITEM_BYTES = MAX_CONTEXT_BYTES + MAX_MANIFEST_BYTES + 512;
    private static final String TYPE = "carlos-managed-pair-context";
    private static final Set<String> FIELDS = Set.of("v", "typ", "pair_id", "clinic_id", "recipient_id",
            "original", "reading_aid", "payload_sha256", "capture");
    private static final Set<String> MEMBER_FIELDS = Set.of("delivery_id", "document_id", "sha256", "size");
    private static final Set<String> CAPTURE_FIELDS = Set.of("raw_source_document_id", "source_fingerprint",
            "prepared_signer_kid", "capability_registry_version");
    private static final Set<String> ITEM_FIELDS = Set.of("v", "typ", "role", "core", "compact_manifest");

    enum MemberRole { ORIGINAL, READING_AID }

    /**
     * Own exact two-component transport manifest, not a JWT or a verified signature. This optional
     * local data carrier has no Snapshot/AEAD integration. The existing complete16KiB Snapshot
     * metadata limit must be checked separately if a later reviewed integration stores it.
     */
    record Item(MemberRole role, PortalMyCarlosManagedPairContext core, String compactManifest) {
        Item {
            if (role == null || core == null || compactManifest == null
                    || compactManifest.length() > MAX_MANIFEST_BYTES) throw invalid();
            // Existing format parser checks canonical base64url and a64-byte P1363 signature only.
            // It neither verifies that signature nor binds its claims to these represented fields.
            PortalMyCarlosDeviceApprovalCodec.envelope(compactManifest);
            itemBytes(role, core, compactManifest);
        }

        Member member() { return role == MemberRole.ORIGINAL ? core.original() : core.readingAid(); }

        byte[] encode() { return itemBytes(role, core, compactManifest); }

        static Item decode(byte[] exact) {
            JsonNode root = object(exact, MAX_ITEM_BYTES); fields(root, ITEM_FIELDS);
            if (epoch(root, "v") != 1 || !"carlos-managed-pair-item".equals(text(root, "typ"))) throw invalid();
            MemberRole role = switch (text(root, "role")) {
                case "original" -> MemberRole.ORIGINAL;
                case "reading_aid" -> MemberRole.READING_AID;
                default -> throw invalid();
            };
            Item item = new Item(role, fromNode(root.get("core")), text(root, "compact_manifest"));
            if (!Arrays.equals(exact, item.encode())) throw invalid();
            return item;
        }

        void requireSameIntent(Item attempted) {
            if (!Objects.equals(this, attempted)) throw new IllegalArgumentException("immutable managed pair item conflict");
        }

        @Override public String toString() { return "ManagedPairItem[REDACTED]"; }
    }

    /** Natural transport identity and final signed-PDF identity remain separate. */
    record Member(String deliveryId, String documentId, String sha256, long size) {
        Member {
            uuid(deliveryId, true);
            label(documentId, 256);
            hash(sha256);
            if (size <= 0 || size > MAX_SEALED_BYTES) throw invalid();
        }

        private Map<String, Object> values() {
            return Map.of("delivery_id", deliveryId, "document_id", documentId, "sha256", sha256, "size", size);
        }

        @Override public String toString() { return "ManagedPairMember[REDACTED]"; }
    }

    /**
     * Raw mapping is lookup provenance, not a revision. The fingerprint's producer must define
     * its raw-content/render-metadata/ownership coverage. A registry version is not capability proof.
     * Retaining a fingerprint does not choose whether later edits invalidate an approved queued pair.
     */
    record Capture(String rawSourceDocumentId, String sourceFingerprint, String preparedSignerKid,
            String capabilityRegistryVersion) {
        Capture {
            PortalMyCarlosDeviceApprovalCodec.exact32(rawSourceDocumentId);
            hash(sourceFingerprint);
            label(preparedSignerKid, 256);
            label(capabilityRegistryVersion, 256);
        }

        private Map<String, Object> values() {
            return Map.of("raw_source_document_id", rawSourceDocumentId, "source_fingerprint", sourceFingerprint,
                    "prepared_signer_kid", preparedSignerKid, "capability_registry_version", capabilityRegistryVersion);
        }

        @Override public String toString() { return "ManagedPairCapture[REDACTED]"; }
    }

    PortalMyCarlosManagedPairContext {
        uuid(pairId, true);
        label(clinicId, 64);
        recipient(recipientId);
        if (original == null || readingAid == null || capture == null
                || original.deliveryId().equals(readingAid.deliveryId())
                || original.documentId().equals(readingAid.documentId())) throw invalid();
        hash(payloadSha256);
        // Check aggregate byte bound during construction, not only serialization.
        bounded(values(pairId, clinicId, recipientId, original, readingAid, payloadSha256, capture));
    }

    byte[] encode() {
        return bounded(values(pairId, clinicId, recipientId, original, readingAid, payloadSha256, capture));
    }

    static PortalMyCarlosManagedPairContext decode(byte[] exact) {
        var context = fromNode(object(exact, MAX_CONTEXT_BYTES));
        if (!Arrays.equals(exact, context.encode())) throw invalid();
        return context;
    }

    private static PortalMyCarlosManagedPairContext fromNode(JsonNode root) {
        fields(root, FIELDS);
        if (epoch(root, "v") != 1 || !TYPE.equals(text(root, "typ"))) throw invalid();
        JsonNode provenance = root.get("capture");
        fields(provenance, CAPTURE_FIELDS);
        var context = new PortalMyCarlosManagedPairContext(text(root, "pair_id"), text(root, "clinic_id"),
                text(root, "recipient_id"), member(root.get("original")), member(root.get("reading_aid")),
                text(root, "payload_sha256"), new Capture(text(provenance, "raw_source_document_id"),
                    text(provenance, "source_fingerprint"), text(provenance, "prepared_signer_kid"),
                    text(provenance, "capability_registry_version")));
        return context;
    }

    /** Compare retained intent DATA only; do not rewrite a queued pair or infer its authenticity. */
    void requireSameIntent(PortalMyCarlosManagedPairContext attempted) {
        if (!Objects.equals(this, attempted)) throw new IllegalArgumentException("immutable managed pair context conflict");
    }

    private static Member member(JsonNode value) {
        fields(value, MEMBER_FIELDS);
        return new Member(text(value, "delivery_id"), text(value, "document_id"), text(value, "sha256"), epoch(value, "size"));
    }

    private static Map<String, Object> values(String pairId, String clinicId, String recipientId,
            Member original, Member readingAid, String payloadSha256, Capture capture) {
        return Map.of("v", 1L, "typ", TYPE, "pair_id", pairId, "clinic_id", clinicId, "recipient_id", recipientId,
                "original", original.values(), "reading_aid", readingAid.values(), "payload_sha256", payloadSha256,
                "capture", capture.values());
    }

    private static byte[] bounded(Map<String, Object> values) {
        byte[] exact = canonical(values);
        if (exact.length > MAX_CONTEXT_BYTES) throw invalid();
        return exact;
    }

    private static byte[] itemBytes(MemberRole role, PortalMyCarlosManagedPairContext core, String compactManifest) {
        Map<String, Object> common = values(core.pairId(), core.clinicId(), core.recipientId(), core.original(),
                core.readingAid(), core.payloadSha256(), core.capture());
        byte[] exact = canonical(Map.of("v", 1L, "typ", "carlos-managed-pair-item",
                "role", role == MemberRole.ORIGINAL ? "original" : "reading_aid", "core", common,
                "compact_manifest", compactManifest));
        if (exact.length > MAX_ITEM_BYTES) throw invalid();
        return exact;
    }

    private static void label(String value, int maximum) {
        if (value == null || value.length() > 2 * maximum
                || value.isBlank() || value.codePointCount(0, value.length()) > maximum
                || value.codePoints().anyMatch(c -> Character.getType(c) == Character.CONTROL)) throw invalid();
        text(value); // Strict UTF8 validation only after the cheap bounded character checks.
    }

    @Override public String toString() { return "ManagedPairContext[REDACTED]"; }
}
