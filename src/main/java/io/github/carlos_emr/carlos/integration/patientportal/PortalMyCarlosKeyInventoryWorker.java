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
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable catalogue and publication state in one existing global Property TEXT value.
 *
 * <p>Every write first owns the existing InnoDB coordination row, including first insertion.
 * The owning transaction runner acquires coordination before any consistent read.
 * This internal worker never commits independently of a larger operation (including future M3).
 * Existing database migrations 21/22 and 79 are prerequisites; no schema is created here.
 */
@Service
class PortalMyCarlosKeyInventoryWorker {
    static final String PROPERTY_NAME = "mycarlos.clinic_public_key_inventory";
    // Clinical report ids are positive. MIN_VALUE belongs to the provider-linking switch;
    // MIN_VALUE+1 is reserved for this catalogue. Combined locks must retain ascending order.
    static final int COORDINATION_KEY = Integer.MIN_VALUE + 1;
    static final int MAX_STORED_BYTES = 40_960;
    private static final Set<String> ROOT_FIELDS = Set.of("schema_version", "catalogue", "publication");
    private static final Set<String> PUBLICATION_FIELDS = Set.of(
            "clinic_id", "target_uri", "generation", "content_digest", "published", "acknowledged_at",
            "lease_token", "lease_expires_at", "failures", "next_attempt_at");
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(10).maxStringLength(MAX_STORED_BYTES).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final PropertyDao properties;

    PortalMyCarlosKeyInventoryWorker(PropertyDao properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    private record Stored(
            Property row, PortalMyCarlosKeyInventory catalogue, PortalMyCarlosPublicationState publication) {}

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, readOnly = true)
    public Optional<PortalMyCarlosKeyInventory> read() {
        return load(false).map(Stored::catalogue);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, readOnly = true)
    public boolean isPublished(PortalMyCarlosPublicationState.Target effectiveTargetNow) {
        return load(false).map(stored -> stored.publication() != null
                && stored.publication().matches(stored.catalogue(), effectiveTargetNow)
                && stored.publication().published()).orElse(false);
    }

    /**
     * Storage entrypoint for the authenticated provisioning service. The validator runs before any
     * write, on the actual locked previous/current pair; it must do no network or database mutation.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public PortalMyCarlosKeyInventory provision(
            String clinicId, long expectedGeneration,
            List<PatientPortalClinicKeysPublishRequest.Key> completeInventory,
            BiConsumer<PortalMyCarlosKeyInventory, PortalMyCarlosKeyInventory> validator) {
        return provisionPrepared(clinicId, expectedGeneration, previous -> completeInventory, validator);
    }

    /** Preparation (including one-time proof minting) runs only after the expected generation is locked. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public PortalMyCarlosKeyInventory provisionPrepared(
            String clinicId, long expectedGeneration,
            Function<PortalMyCarlosKeyInventory, List<PatientPortalClinicKeysPublishRequest.Key>> preparation,
            BiConsumer<PortalMyCarlosKeyInventory, PortalMyCarlosKeyInventory> validator) {
        Objects.requireNonNull(validator);
        Objects.requireNonNull(preparation);
        PortalMyCarlosInventoryTransactions.requireOwnedTransaction();
        Optional<Stored> old = load(true);
        PortalMyCarlosKeyInventory previous = old.map(Stored::catalogue).orElse(null);
        PortalMyCarlosKeyInventory next;
        if (previous == null) {
            if (expectedGeneration != 0) {
                throw changed();
            }
            next = PortalMyCarlosKeyInventory.create(clinicId, preparation.apply(null));
        } else {
            requireClinic(previous, clinicId);
            if (expectedGeneration != previous.generation()) {
                throw changed(); // A stale retry must not read a private key or mint another proof.
            }
            next = previous.replaceComplete(expectedGeneration, preparation.apply(previous));
        }
        validator.accept(previous, next);
        if (previous != next) {
            // Content changes invalidate every earlier target acknowledgement and in-flight lease.
            write(old.map(Stored::row).orElse(null), next, null);
        }
        return next;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<PortalMyCarlosPublicationState.Claim> claim(
            PortalMyCarlosPublicationState.Target target, String token, Instant now, Duration leaseDuration) {
        return claimCurrent(() -> target, token, () -> now, leaseDuration);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<PortalMyCarlosPublicationState.Claim> claimCurrent(
            Supplier<PortalMyCarlosPublicationState.Target> effectiveTarget, String token,
            Supplier<Instant> clock, Duration leaseDuration) {
        PortalMyCarlosInventoryTransactions.requireOwnedTransaction();
        Optional<Stored> loaded = load(true);
        if (loaded.isEmpty()) {
            return Optional.empty(); // Never infer a catalogue from a keystore alias.
        }
        Stored stored = loaded.get();
        PortalMyCarlosPublicationState.Target target = effectiveTarget.get();
        if (target == null) return Optional.empty();
        requireClinic(stored.catalogue(), target.clinicId());
        Instant now = clock.get(); // Fresh only AFTER every Property row lock/refresh has completed.
        PortalMyCarlosPublicationState state = stored.publication();
        if (state == null || !state.matches(stored.catalogue(), target)) {
            // Repointing A -> B makes the same content pending for B; A's lease cannot survive.
            state = PortalMyCarlosPublicationState.pending(stored.catalogue(), target);
        }
        if (!state.claimableAt(now)) {
            return Optional.empty();
        }
        state = state.leased(token, now, leaseDuration);
        write(stored.row(), stored.catalogue(), state);
        return Optional.of(new PortalMyCarlosPublicationState.Claim(
                stored.catalogue(), target, token, state.leaseExpiresAt(), state.failures()));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean acknowledge(
            PortalMyCarlosPublicationState.Claim claim,
            PortalMyCarlosPublicationState.Target effectiveTargetNow,
            PatientPortalClinicKeysPublishResult response, Instant now) {
        return acknowledgeCurrent(claim, () -> effectiveTargetNow, response, () -> now);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean acknowledgeCurrent(
            PortalMyCarlosPublicationState.Claim claim,
            Supplier<PortalMyCarlosPublicationState.Target> effectiveTarget,
            PatientPortalClinicKeysPublishResult response, Supplier<Instant> clock) {
        PortalMyCarlosInventoryTransactions.requireOwnedTransaction();
        Optional<Stored> loaded = load(true);
        if (loaded.isEmpty()) {
            return false;
        }
        Stored stored = loaded.get();
        PortalMyCarlosPublicationState.Target effectiveTargetNow = effectiveTarget.get();
        if (effectiveTargetNow == null) return false;
        Instant now = clock.get(); // Never decide expiry using a timestamp from before Property locking.
        PortalMyCarlosPublicationState state = stored.publication();
        if (state == null || !state.matches(stored.catalogue(), effectiveTargetNow)
                || !state.owns(claim, effectiveTargetNow, now) || !confirms(response, claim)) {
            return false;
        }
        write(stored.row(), stored.catalogue(), state.acknowledged(now));
        return true;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean failed(
            PortalMyCarlosPublicationState.Claim claim,
            PortalMyCarlosPublicationState.Target effectiveTargetNow, Instant now, Duration retryDelay) {
        return failedCurrent(claim, () -> effectiveTargetNow, () -> now, retryDelay);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean failedCurrent(
            PortalMyCarlosPublicationState.Claim claim,
            Supplier<PortalMyCarlosPublicationState.Target> effectiveTarget,
            Supplier<Instant> clock, Duration retryDelay) {
        PortalMyCarlosInventoryTransactions.requireOwnedTransaction();
        Optional<Stored> loaded = load(true);
        if (loaded.isEmpty()) {
            return false;
        }
        Stored stored = loaded.get();
        PortalMyCarlosPublicationState.Target effectiveTargetNow = effectiveTarget.get();
        if (effectiveTargetNow == null) return false;
        Instant now = clock.get(); // Never decide expiry using a timestamp from before Property locking.
        PortalMyCarlosPublicationState state = stored.publication();
        if (state == null || !state.matches(stored.catalogue(), effectiveTargetNow)
                || !state.owns(claim, effectiveTargetNow, now)) {
            return false;
        }
        write(stored.row(), stored.catalogue(), state.retryAfter(now, retryDelay));
        return true;
    }

    private Optional<Stored> load(boolean locking) {
        List<Property> globals = (locking
                ? properties.findByNameForUpdate(PROPERTY_NAME) : properties.findByName(PROPERTY_NAME))
                .stream().filter(row -> StringUtils.isBlank(row.getProviderNo())).toList();
        if (globals.size() > 1) {
            throw invalid(); // Never repair an ambiguous history automatically.
        }
        if (globals.isEmpty()) {
            return Optional.empty();
        }
        Property row = globals.getFirst();
        String value = row.getValue();
        if (value == null || value.length() > MAX_STORED_BYTES
                || value.getBytes(StandardCharsets.UTF_8).length > MAX_STORED_BYTES) {
            throw invalid();
        }
        try {
            JsonNode root = JSON.readTree(value);
            exactFields(root, ROOT_FIELDS);
            if (PortalJson.requiredInt(root, "schema_version") != 1) {
                throw invalid();
            }
            PortalMyCarlosKeyInventory catalogue =
                    PortalMyCarlosKeyInventory.fromStoredJson(root.get("catalogue").toString());
            JsonNode publication = root.get("publication");
            PortalMyCarlosPublicationState state =
                    publication.isNull() ? null : readPublication(publication);
            if (state != null && (!state.target().clinicId().equals(catalogue.clinicId())
                    || state.generation() != catalogue.generation()
                    || !state.contentDigest().equals(catalogue.contentDigest()))) {
                throw invalid();
            }
            return Optional.of(new Stored(row, catalogue, state));
        } catch (IOException | RuntimeException failure) {
            throw invalid();
        }
    }

    private void write(
            Property existing, PortalMyCarlosKeyInventory catalogue, PortalMyCarlosPublicationState state) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("schema_version", 1);
            root.set("catalogue", JSON.readTree(catalogue.toStoredJson()));
            root.set("publication", state == null ? JSON.getNodeFactory().nullNode() : publicationJson(state));
            String value = root.toString();
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_STORED_BYTES) {
                throw invalid();
            }
            Property row = existing == null ? new Property(PROPERTY_NAME) : existing;
            row.setValue(value);
            if (existing == null) {
                properties.persist(row);
            } else {
                properties.merge(row);
            }
        } catch (IOException failure) {
            throw invalid();
        }
    }

    private static PortalMyCarlosPublicationState readPublication(JsonNode node) {
        exactFields(node, PUBLICATION_FIELDS);
        var target = new PortalMyCarlosPublicationState.Target(
                PortalJson.requiredText(node, "clinic_id"),
                URI.create(PortalJson.requiredText(node, "target_uri")));
        return new PortalMyCarlosPublicationState(target, PortalJson.positiveLong(node, "generation"),
                PortalJson.requiredText(node, "content_digest"), PortalJson.requiredBool(node, "published"),
                PortalJson.nullableTimestamp(node, "acknowledged_at"),
                PortalJson.nullableText(node, "lease_token"),
                PortalJson.nullableTimestamp(node, "lease_expires_at"),
                PortalJson.nonnegativeInt(node, "failures"),
                PortalJson.nullableTimestamp(node, "next_attempt_at"));
    }

    private static ObjectNode publicationJson(PortalMyCarlosPublicationState state) {
        ObjectNode node = JSON.createObjectNode();
        node.put("clinic_id", state.target().clinicId());
        node.put("target_uri", state.target().endpoint().toASCIIString());
        node.put("generation", state.generation());
        node.put("content_digest", state.contentDigest());
        node.put("published", state.published());
        nullable(node, "acknowledged_at", state.acknowledgedAt());
        node.put("lease_token", state.leaseToken());
        nullable(node, "lease_expires_at", state.leaseExpiresAt());
        node.put("failures", state.failures());
        nullable(node, "next_attempt_at", state.nextAttemptAt());
        return node;
    }

    private static void nullable(ObjectNode node, String name, Instant value) {
        if (value == null) {
            node.putNull(name);
        } else {
            node.put(name, value.toString());
        }
    }

    private static boolean confirms(
            PatientPortalClinicKeysPublishResult response, PortalMyCarlosPublicationState.Claim claim) {
        if (response == null || !claim.target().clinicId().equals(response.clinicId())
                || response.keys() == null || response.keys().size() != claim.inventory().publication().keys().size()) {
            return false;
        }
        Map<String, PatientPortalClinicKeysPublishRequest.Key> expected = new HashMap<>();
        claim.inventory().publication().keys().forEach(key -> expected.put(key.kid(), key));
        for (var confirmed : response.keys()) {
            if (confirmed == null) {
                return false;
            }
            var original = expected.remove(confirmed.kid());
            if (original == null || !original.status().equals(confirmed.status())
                    || !Objects.equals(original.replacesKid(), confirmed.replacesKid())
                    || confirmed.publishedAt() == null
                    || ("revoked".equals(confirmed.status()) != (confirmed.revokedAt() != null))) {
                return false;
            }
        }
        return expected.isEmpty();
    }

    private static void requireClinic(PortalMyCarlosKeyInventory catalogue, String clinicId) {
        if (!catalogue.clinicId().equals(clinicId)) {
            throw invalid();
        }
    }

    private static void exactFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) {
            throw invalid();
        }
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(expected)) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("persisted clinic public key catalogue is invalid");
    }

    private static IllegalStateException changed() {
        return new IllegalStateException("clinic public key catalogue generation changed");
    }
}
