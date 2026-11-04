/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryCodec.*;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict frozen M4 adapter. Portal transport states never establish cryptographic pickup. */
final class PortalMyCarlosDeliveryClient {
    private static final String ROOT = "/internal/carlos/mycarlos/";
    private static final Set<String> ENVELOPE = Set.of("sequence", "kind", "occurred_at", "recipient_id",
            "device_key_id", "delivery_id", "payload");
    private static final Set<String> COPY = Set.of("ciphertext_sha256", "size", "created_at", "expires_at");
    private final PatientPortalService portal;
    private final PortalJobAssertionSigner job;

    PortalMyCarlosDeliveryClient(PatientPortalService portal, PortalJobAssertionSigner job) {
        this.portal = Objects.requireNonNull(portal);
        if (job == null || job.job() != PortalJob.DELIVERY_SYNC) throw invalid();
        this.job = job;
    }

    boolean configuredFor(String clinic, String internalOrigin) {
        try {
            return portal.myCarlosClinicId().equals(clinic) && portal.myCarlosDeliveryTarget(1).equals(
                    java.net.URI.create(internalOrigin + "/internal/carlos/patients/1/mycarlos/deliveries"));
        } catch (IllegalArgumentException invalidConfiguration) { return false; }
    }

    String upload(int demographic, String deliveryId, String deviceKid, byte[] persistedBody) {
        if (demographic <= 0) throw invalid();
        uuid(deliveryId, true);
        PortalMyCarlosDeviceApprovalCodec.exact32(deviceKid);
        var response = portal.exchangeMyCarlosJob(job, PortalJob.PERMISSION_DELIVERY_UPLOAD, "POST",
                "/internal/carlos/patients/" + demographic + "/mycarlos/deliveries", persistedBody, Set.of(200, 201));
        JsonNode result = object(utf8(response.body()), 4096);
        fields(result, Set.of("delivery_id", "device_key_id", "status"));
        if (!deliveryId.equals(text(result, "delivery_id")) || !deviceKid.equals(text(result, "device_key_id"))) throw invalid();
        String status = text(result, "status");
        if (!Set.of("waiting", "picked_up", "expired", "removed").contains(status)
                || (response.statusCode() == 201 && !"waiting".equals(status))) throw invalid();
        return status;
    }

    Page events(long after) {
        return page(fetchEvents(after), after);
    }

    /** Raw bounded bytes are retained before interpreting any event or checkpoint. */
    byte[] fetchEvents(long after) {
        if (after < 0) throw invalid();
        String response = portal.exchangeJob(job, PortalJob.PERMISSION_DELIVERY_EVENTS_READ, "GET",
                ROOT + "delivery-events?after=" + after + "&limit=25", new byte[0], 200);
        return utf8(response);
    }

    /** Caller must commit verified events and contiguous checkpoint BEFORE invoking this method. */
    long acknowledge(long committedThrough) {
        if (committedThrough < 0) throw invalid();
        byte[] body = canonical(Map.of("through", Long.toString(committedThrough)));
        String response = portal.exchangeJob(job, PortalJob.PERMISSION_DELIVERY_EVENTS_ACK, "POST",
                ROOT + "delivery-events/ack", body, 200);
        var result = object(utf8(response), 1024);
        fields(result, Set.of("acked_through"));
        long acknowledged = cursor(text(result, "acked_through"));
        // A server with a later checkpoint belongs to a different writer/recovery state. Stop.
        if (acknowledged != committedThrough) throw invalid();
        return acknowledged;
    }

    /** Exact page bytes plus ordinal preserve the original event, including its signed proofs. */
    record Page(Bytes exactBytes, List<Event> events, long lastSequence, Long nextCursor) {
        Page { events = List.copyOf(events); }
        @Override public String toString() { return "DeliveryEventPage[REDACTED]"; }
    }
    record Event(int ordinal, long sequence, String kind, long occurredAt, String recipientId,
                 String deviceKid, String deliveryId, String ciphertextSha256, Long size,
                 Long createdAt, Long expiresAt, String receipt, String requestAssertion, String vaultHeadLink) {
        @Override public String toString() { return "DeliveryEvent[REDACTED]"; }
    }

    static Page page(byte[] exactBytes, long after) {
        if (after < 0) throw invalid();
        JsonNode root = object(exactBytes, 1048576);
        fields(root, Set.of("events", "last_sequence", "next_cursor"));
        var values = root.get("events");
        if (!values.isArray() || values.size() > 50) throw invalid();
        List<Event> events = new ArrayList<>();
        long previous = after;
        for (byte[] originalEvent : arrayObjects(exactBytes, "events", 16384)) {
            Event event = event(object(originalEvent, 16384), events.size());
            if (event.sequence() <= previous) throw invalid();
            events.add(event);
            previous = event.sequence();
        }
        long last = cursor(text(root, "last_sequence"));
        JsonNode nextNode = root.get("next_cursor");
        Long next = nextNode.isNull() ? null : cursor(text(root, "next_cursor"));
        if (last != previous || (next != null && (next != last || events.isEmpty()))) throw invalid();
        return new Page(new Bytes(exactBytes), events, last, next);
    }

    static Event event(byte[] exactBytes) {
        return event(object(exactBytes, 16384), 0);
    }

    private static Event event(JsonNode node, int ordinal) {
        fields(node, ENVELOPE);
        long sequence = cursor(text(node, "sequence"));
        if (sequence == 0) throw invalid();
        long occurred = epoch(node, "occurred_at");
        String kind = text(node, "kind");
        String recipient = text(node, "recipient_id");
        recipient(recipient);
        String kid = text(node, "device_key_id");
        PortalMyCarlosDeviceApprovalCodec.exact32(kid);
        boolean copy = Set.of("picked_up", "expired", "removed").contains(kind);
        String delivery = copy ? text(node, "delivery_id") : null;
        if (copy) uuid(delivery, true);
        else if (!node.get("delivery_id").isNull()) throw invalid();
        JsonNode payload = node.get("payload");
        String cipher = null, receipt = null, assertion = null, anchor = null;
        Long size = null, created = null, expires = null;
        if (copy) {
            fields(payload, "picked_up".equals(kind) ? Set.of("ciphertext_sha256", "size", "created_at",
                    "expires_at", "receipt", "request_assertion") : COPY);
            cipher = text(payload, "ciphertext_sha256");
            hash(cipher);
            size = epoch(payload, "size");
            created = epoch(payload, "created_at");
            expires = epoch(payload, "expires_at");
            if (size < 49 || size > MAX_SEALED_BYTES || expires <= created || expires - created > MAX_LIFETIME) throw invalid();
            if ("picked_up".equals(kind)) {
                receipt = compact(payload, "receipt");
                assertion = compact(payload, "request_assertion");
            }
        } else if ("device_removed".equals(kind)) {
            fields(payload, Set.of());
        } else if ("anchor".equals(kind)) {
            fields(payload, Set.of("vault_head_link", "request_assertion"));
            anchor = text(payload, "vault_head_link");
            hash(anchor);
            assertion = compact(payload, "request_assertion");
        } else throw invalid();
        return new Event(ordinal, sequence, kind, occurred, recipient, kid, delivery, cipher,
                size, created, expires, receipt, assertion, anchor);
    }

    static long cursor(String value) {
        return PortalMyCarlosDeviceApprovalCodec.decimal(value);
    }

    private static String compact(JsonNode object, String field) {
        String value = text(object, field);
        if (value.length() > 4096) throw invalid();
        PortalMyCarlosDeviceApprovalCodec.envelope(value);
        return value;
    }

    private static byte[] utf8(String value) {
        return PortalMyCarlosDeviceApprovalCodec.utf8(value);
    }
}
