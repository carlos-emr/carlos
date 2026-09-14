/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.LongSupplier;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;

/** Bounded process-memory cache; no chart text, keys or drafts are written to disk or logs. */
final class ClinicalSummaryGenerationCache {
    private record Entry(byte[] artifact, long created) { }
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final int maxEntries;
    private final int maxBytes;
    private final long ttlNanos;
    private final LongSupplier clock;
    private int bytes;

    ClinicalSummaryGenerationCache() {
        this(128, 16 * 1024 * 1024, Duration.ofMinutes(15), System::nanoTime);
    }

    ClinicalSummaryGenerationCache(int maxEntries, int maxBytes, Duration ttl, LongSupplier clock) {
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
        this.ttlNanos = ttl.toNanos();
        this.clock = clock;
    }

    static String key(ObjectNode snapshot, ObjectNode request, String identity) throws IOException {
        ObjectNode chart = snapshot.deepCopy();
        // These identify assembly of the same authorized snapshot, not a change in evidence.
        chart.remove(java.util.List.of("artifact_id", "generated_at"));
        ObjectNode contract = request.deepCopy();
        contract.remove("request_id");
        ObjectNode material = JSON.createObjectNode().put("cache_contract", 2).put("agent", identity);
        material.set("chart", chart);
        material.set("request", contract);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.writeValueAsBytes(canonical(material))));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            TreeSet<String> names = new TreeSet<>();
            node.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> result.set(name, canonical(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            var result = JSON.createArrayNode();
            node.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return node;
    }

    synchronized ClinicalSummaryArtifact get(String key) throws IOException {
        expire();
        Entry entry = entries.get(key);
        // Reparse and revalidate a private copy; callers never share mutable cached state.
        return entry == null ? null : new ClinicalSummaryArtifact(JSON.readTree(entry.artifact()));
    }

    synchronized void put(String key, ClinicalSummaryArtifact artifact) throws IOException {
        expire();
        if (!artifact.isRenderable()) return;
        byte[] value = JSON.writeValueAsBytes(artifact.getView());
        if (value.length > maxBytes) return;
        Entry previous = entries.remove(key);
        if (previous != null) bytes -= previous.artifact().length;
        entries.put(key, new Entry(value, clock.getAsLong()));
        bytes += value.length;
        var oldest = entries.entrySet().iterator();
        while ((entries.size() > maxEntries || bytes > maxBytes) && oldest.hasNext()) {
            bytes -= oldest.next().getValue().artifact().length;
            oldest.remove();
        }
    }

    private void expire() {
        long now = clock.getAsLong();
        var iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (now - entry.created() >= ttlNanos) {
                bytes -= entry.artifact().length;
                iterator.remove();
            }
        }
    }
}
