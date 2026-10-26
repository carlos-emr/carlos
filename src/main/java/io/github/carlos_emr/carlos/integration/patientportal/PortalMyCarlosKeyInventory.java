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
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, explicitly supplied whole history of a clinic's public document-signing keys.
 *
 * <p>This is a local persistence format, not a rotation-signature or transport format. No key is
 * discovered from a current alias, generated, published, or trusted by constructing this value.
 * Existing replacement metadata stays byte-exact; supplied proofs and the full frozen v1 graph
 * are validated. An unsigned new key remains allowed under the client's plain-notice policy.
 */
public final class PortalMyCarlosKeyInventory {
    static final int MAX_STORED_BYTES = 32_768;
    private static final int SCHEMA_VERSION = 1;
    private static final String INVALID = "clinic public key catalogue is invalid";
    private static final Set<String> STORED_FIELDS =
            Set.of("schema_version", "clinic_id", "generation", "keys");
    private static final Set<String> KEY_FIELDS =
            Set.of("kid", "spki", "status", "replaces_kid", "replacement_signature");
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(8).maxStringLength(MAX_STORED_BYTES).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final String clinicId;
    private final long generation;
    private final PatientPortalClinicKeysPublishRequest inventory;

    private PortalMyCarlosKeyInventory(
            String clinicId, long generation, List<PatientPortalClinicKeysPublishRequest.Key> keys) {
        if (clinicId == null || clinicId.isBlank() || !clinicId.equals(clinicId.trim())
                || clinicId.length() > 128 || generation <= 0) {
            throw invalid();
        }
        this.clinicId = clinicId;
        this.generation = generation;
        // Validate the size, duplicates, canonical P-256 SPKI/kid and statuses before sorting.
        PatientPortalClinicKeysPublishRequest checked;
        try {
            checked = new PatientPortalClinicKeysPublishRequest(keys);
        } catch (PortalRequestPreparationException exception) {
            throw invalid();
        }
        this.inventory = new PatientPortalClinicKeysPublishRequest(checked.keys().stream()
                .sorted(Comparator.comparing(PatientPortalClinicKeysPublishRequest.Key::kid)).toList());
        Set<String> known = new HashSet<>();
        inventory.keys().forEach(key -> known.add(key.kid()));
        if (inventory.keys().stream().anyMatch(key ->
                key.replacesKid() != null && !known.contains(key.replacesKid()))) {
            throw invalid();
        }
        PortalMyCarlosKeyReplacementProofs.validateAll(clinicId, inventory);
        bounded(toStoredJson());
    }

    /** Only an explicit complete-inventory provisioning operation should call this factory. */
    public static PortalMyCarlosKeyInventory create(
            String clinicId, List<PatientPortalClinicKeysPublishRequest.Key> completeInventory) {
        return new PortalMyCarlosKeyInventory(clinicId, 1, completeInventory);
    }

    /**
     * Applies a complete replacement under an expected generation, retaining every historical key.
     * The caller must persist the result atomically; this value provides no database coordination.
     * Identical content is idempotent only with the current expected generation. After a lost
     * provisioning response the caller must reread and compare before making another explicit CAS.
     */
    public PortalMyCarlosKeyInventory replaceComplete(
            long expectedGeneration, List<PatientPortalClinicKeysPublishRequest.Key> completeInventory) {
        if (expectedGeneration != generation) {
            throw new IllegalStateException("clinic public key catalogue generation changed");
        }
        var next = new PortalMyCarlosKeyInventory(clinicId, generation, completeInventory);
        Map<String, PatientPortalClinicKeysPublishRequest.Key> nextKeys = new HashMap<>();
        next.inventory.keys().forEach(key -> nextKeys.put(key.kid(), key));
        for (var previous : inventory.keys()) {
            var replacement = nextKeys.get(previous.kid());
            if (replacement == null
                    || !previous.spki().equals(replacement.spki())
                    || !Objects.equals(previous.replacesKid(), replacement.replacesKid())
                    || !Objects.equals(previous.replacementSignature(), replacement.replacementSignature())
                    || ("revoked".equals(previous.status()) && !"revoked".equals(replacement.status()))) {
                throw invalid();
            }
        }
        PortalMyCarlosKeyReplacementProofs.validateNewEdges(this, next.inventory);
        if (inventory.equals(next.inventory)) {
            return this;
        }
        if (generation == Long.MAX_VALUE) {
            throw new IllegalStateException("clinic public key catalogue generation is exhausted");
        }
        return new PortalMyCarlosKeyInventory(clinicId, generation + 1, next.inventory.keys());
    }

    /** Strictly reads a bounded local record; malformed/unknown data must never be rewritten as empty. */
    public static PortalMyCarlosKeyInventory fromStoredJson(String stored) {
        bounded(stored);
        try {
            JsonNode root = JSON.readTree(stored);
            exactFields(root, STORED_FIELDS);
            if (PortalJson.requiredInt(root, "schema_version") != SCHEMA_VERSION) {
                throw invalid();
            }
            JsonNode entries = root.get("keys");
            if (!entries.isArray() || entries.isEmpty()
                    || entries.size() > PatientPortalClinicKeysPublishRequest.MAX_KEYS) {
                throw invalid();
            }
            List<PatientPortalClinicKeysPublishRequest.Key> keys = new ArrayList<>();
            for (JsonNode entry : entries) {
                exactFields(entry, KEY_FIELDS);
                keys.add(new PatientPortalClinicKeysPublishRequest.Key(
                        PortalJson.requiredText(entry, "kid"),
                        PortalJson.requiredText(entry, "spki"),
                        PortalJson.requiredText(entry, "status"),
                        PortalJson.nullableText(entry, "replaces_kid"),
                        PortalJson.nullableText(entry, "replacement_signature")));
            }
            return new PortalMyCarlosKeyInventory(PortalJson.requiredText(root, "clinic_id"),
                    PortalJson.positiveLong(root, "generation"), keys);
        } catch (IOException | RuntimeException exception) {
            // Parser/provider exception details can contain persisted input. Keep the failure bounded.
            throw invalid();
        }
    }

    public String clinicId() {
        return clinicId;
    }

    public long generation() {
        return generation;
    }

    public PatientPortalClinicKeysPublishRequest publication() {
        return inventory;
    }

    public String toStoredJson() {
        ObjectNode root = contentJson();
        root.put("generation", generation);
        return root.toString();
    }

    /**
     * Internal content binding for generation-bound publication acknowledgements.
     * Generation is deliberately separate; this digest is never a wire signature or proof.
     */
    public String contentDigest() {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(
                            contentJson().toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available");
        }
    }

    private ObjectNode contentJson() {
        ObjectNode root = JSON.createObjectNode();
        root.put("schema_version", SCHEMA_VERSION);
        root.put("clinic_id", clinicId);
        root.set("keys", inventory.toJson(JSON).get("keys"));
        return root;
    }

    private static void bounded(String stored) {
        if (stored == null || stored.length() > MAX_STORED_BYTES
                || stored.getBytes(StandardCharsets.UTF_8).length > MAX_STORED_BYTES) {
            throw invalid();
        }
    }

    private static void exactFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) {
            throw invalid();
        }
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(INVALID);
    }

    @Override
    public String toString() {
        return "ClinicPublicKeyCatalogue[details=REDACTED]";
    }
}
