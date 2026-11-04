/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Strict M3 job adapter. Portal routing remains untrusted until local patient authorization. */
final class PortalMyCarlosApprovalClient {
    private static final String ROOT = "/internal/carlos/mycarlos/";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Exchange exchange;

    @FunctionalInterface interface Exchange {
        String call(String permission, String method, String path, byte[] exactBody, int expectedStatus);
    }
    PortalMyCarlosApprovalClient(Exchange exchange) { this.exchange = Objects.requireNonNull(exchange); }

    record Request(long sequence, int demographicNo, String intent) {
        @Override public String toString() { return "ApprovalRequest[REDACTED]"; }
    }
    record Approval(long sequence, UUID requestId, String challengeId, String approval) {
        @Override public String toString() { return "ApprovalProof[REDACTED]"; }
    }
    record Page<T>(List<T> records, long lastSequence, Long nextCursor) {
        Page { records = List.copyOf(records); }
        @Override public String toString() { return "ApprovalPage[REDACTED]"; }
    }

    Page<Request> requests(long after) {
        String response = exchange.call(PortalJob.PERMISSION_APPROVAL_REQUESTS_READ, "GET",
                ROOT + "device-approval-requests?after=" + cursor(after) + "&limit=20", new byte[0], 200);
        return requests(response, after);
    }
    Page<Approval> approvals(long after) {
        String response = exchange.call(PortalJob.PERMISSION_APPROVAL_PROOFS_READ, "GET",
                ROOT + "device-approvals?after=" + cursor(after) + "&limit=20", new byte[0], 200);
        return approvals(response, after);
    }

    /** Body is the exact already committed outbox body, never a reminted signed object. */
    void publish(UUID request, String kind, byte[] exactBody) {
        uuid(request.toString());
        boolean challenge = "challenge".equals(kind);
        if (!challenge && !"decision".equals(kind)) throw invalid();
        int bound = challenge ? 32768 : 16384;
        if (exactBody == null || exactBody.length == 0 || exactBody.length > bound) throw invalid();
        JsonNode result = parse(exchange.call(challenge ? PortalJob.PERMISSION_APPROVAL_CHALLENGES_PUBLISH
                        : PortalJob.PERMISSION_APPROVAL_DECISIONS_PUBLISH,
                "PUT", ROOT + "device-approval-requests/" + request + "/" + kind, exactBody.clone(), 200));
        fields(result, "status");
        if (!"stored".equals(text(result, "status"))) throw invalid();
    }

    static Page<Request> requests(String response, long after) {
        JsonNode root = parse(response);
        fields(root, "requests", "last_sequence", "next_cursor");
        JsonNode rows = array(root, "requests");
        List<Request> result = new ArrayList<>();
        long previous = nonnegative(after);
        for (JsonNode row : rows) {
            fields(row, "sequence", "demographic_no", "intent");
            long sequence = decimal(text(row, "sequence"));
            if (sequence <= previous) throw invalid();
            JsonNode patient = row.get("demographic_no");
            if (!patient.isIntegralNumber() || !patient.canConvertToInt() || patient.intValue() <= 0) throw invalid();
            String intent = compact(row, "intent");
            result.add(new Request(sequence, patient.intValue(), intent));
            previous = sequence;
        }
        return page(root, result, previous, after);
    }
    static Page<Approval> approvals(String response, long after) {
        JsonNode root = parse(response);
        fields(root, "approvals", "last_sequence", "next_cursor");
        JsonNode rows = array(root, "approvals");
        List<Approval> result = new ArrayList<>();
        long previous = nonnegative(after);
        for (JsonNode row : rows) {
            fields(row, "sequence", "request_id", "challenge_id", "approval");
            long sequence = decimal(text(row, "sequence"));
            if (sequence <= previous) throw invalid();
            String challenge = text(row, "challenge_id");
            try { PortalMyCarlosDeviceApprovalCodec.exact32(challenge); }
            catch (IllegalArgumentException failure) { throw invalid(); }
            result.add(new Approval(sequence, uuid(text(row, "request_id")), challenge, compact(row, "approval")));
            previous = sequence;
        }
        return page(root, result, previous, after);
    }
    private static <T> Page<T> page(JsonNode root, List<T> records, long previous, long after) {
        long last = decimal(text(root, "last_sequence"));
        if (last != previous || (records.isEmpty() && last != after)) throw invalid();
        JsonNode next = root.get("next_cursor");
        Long cursor = next.isNull() ? null : decimal(text(root, "next_cursor"));
        if (cursor != null && (records.isEmpty() || cursor != last)) throw invalid();
        return new Page<>(records, last, cursor);
    }
    private static JsonNode array(JsonNode root, String name) {
        JsonNode rows = root.get(name);
        if (!rows.isArray() || rows.size() > 20) throw invalid();
        return rows;
    }
    private static JsonNode parse(String response) {
        if (response == null || response.length() > 1048576
                || response.getBytes(StandardCharsets.UTF_8).length > 1048576) throw invalid();
        try { return JSON.readTree(response); }
        catch (java.io.IOException failure) { throw invalid(); }
    }
    private static void fields(JsonNode node, String... names) {
        if (node == null || !node.isObject()) throw invalid();
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(Set.of(names))) throw invalid();
    }
    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual()) throw invalid();
        return value.textValue();
    }
    private static String compact(JsonNode node, String name) {
        String value = text(node, name);
        try { PortalMyCarlosDeviceApprovalCodec.envelope(value); }
        catch (IllegalArgumentException failure) { throw invalid(); }
        return value;
    }
    private static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (id.version() != 4 || id.variant() != 2 || !id.toString().equals(value)) throw invalid();
            return id;
        } catch (IllegalArgumentException failure) { throw invalid(); }
    }
    private static long decimal(String value) {
        if (!value.matches("0|[1-9][0-9]{0,18}")) throw invalid();
        try { return nonnegative(Long.parseLong(value)); }
        catch (NumberFormatException failure) { throw invalid(); }
    }
    private static String cursor(long value) { return Long.toString(nonnegative(value)); }
    private static long nonnegative(long value) { if (value < 0) throw invalid();
        return value; }
    private static PortalContractException invalid() {
        return new PortalContractException("portal device approval response is invalid");
    }
}
