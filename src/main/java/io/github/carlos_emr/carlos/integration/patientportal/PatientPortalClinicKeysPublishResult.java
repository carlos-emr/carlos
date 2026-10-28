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

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A publication acknowledgment bound to the configured clinic and the entire sent inventory. */
public record PatientPortalClinicKeysPublishResult(String clinicId, boolean changed, List<Key> keys) {
    public PatientPortalClinicKeysPublishResult {
        keys = List.copyOf(keys);
    }

    public record Key(String kid, String status, Instant publishedAt, Instant revokedAt, String replacesKid) {
        @Override
        public String toString() {
            return "PublishedClinicKey[details=REDACTED]";
        }
    }

    static PatientPortalClinicKeysPublishResult fromJson(
            JsonNode node, String clinicId, PatientPortalClinicKeysPublishRequest sent) {
        exactFields(node, Set.of("clinic_id", "changed", "keys"));
        if (!clinicId.equals(PortalJson.requiredText(node, "clinic_id"))) {
            throw invalid();
        }
        boolean changed = PortalJson.requiredBool(node, "changed");
        JsonNode entries = node.get("keys");
        if (!entries.isArray() || entries.size() != sent.keys().size()) {
            throw invalid();
        }
        Map<String, PatientPortalClinicKeysPublishRequest.Key> expected = new HashMap<>();
        sent.keys().forEach(key -> expected.put(key.kid(), key));
        List<Key> keys = new ArrayList<>();
        for (JsonNode entry : entries) {
            exactFields(entry, Set.of("kid", "status", "published_at", "revoked_at", "replaces_kid"));
            String kid = PortalJson.requiredText(entry, "kid");
            var original = expected.remove(kid);
            String status = PortalJson.requiredText(entry, "status");
            String replaces = PortalJson.nullableText(entry, "replaces_kid");
            Instant published = PortalJson.requiredTimestamp(entry, "published_at");
            Instant revoked = PortalJson.nullableTimestamp(entry, "revoked_at");
            if (original == null || !original.status().equals(status)
                    || !Objects.equals(original.replacesKid(), replaces)
                    || ("revoked".equals(status) != (revoked != null))) {
                throw invalid();
            }
            keys.add(new Key(kid, status, published, revoked, replaces));
        }
        if (!expected.isEmpty()) {
            throw invalid();
        }
        return new PatientPortalClinicKeysPublishResult(clinicId, changed, keys);
    }

    private static void exactFields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) {
            throw invalid();
        }
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(allowed)) {
            throw invalid();
        }
    }

    private static PortalContractException invalid() {
        return new PortalContractException("portal did not confirm the clinic public key inventory");
    }

    @Override
    public String toString() {
        return "ClinicKeysPublication[details=REDACTED]";
    }
}
