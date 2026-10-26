/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import java.util.Objects;
import java.util.Set;

/** Exact per-record processed/rejected evidence in the existing Property TEXT storage. */
final class PortalMyCarlosApprovalJournal {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> REASONS = Set.of("prepared", "invalid_proof", "candidate_changed", "binding_changed",
            "predecessor_not_accepted", "predecessor_removed", "stale_predecessor", "challenge_expired",
            "challenge_cancelled", "staff_required", "no_accepted_approver", "approval_conflict");
    private final PortalMyCarlosDeviceApprovalTransactions transactions;
    private final PropertyDao properties;
    PortalMyCarlosApprovalJournal(PortalMyCarlosDeviceApprovalTransactions transactions, PropertyDao properties) {
        this.transactions = Objects.requireNonNull(transactions);
        this.properties = Objects.requireNonNull(properties);
    }
    String record(PortalMyCarlosDeviceApprovalService.Current cfg, PortalMyCarlosApprovalClient.Request request,
            String suppliedReason) {
        if (suppliedReason != null && !REASONS.contains(suppliedReason)) throw new IllegalArgumentException("unknown approval outcome");
        String identity = PortalMyCarlosDeviceApprovalCrypto.sha256(PortalMyCarlosDeviceApprovalCrypto.frame(
                cfg.hash().copy(), PortalMyCarlosDeviceApprovalCodec.utf8(Integer.toString(request.demographicNo())),
                PortalMyCarlosDeviceApprovalCodec.utf8(Long.toString(request.sequence()))));
        return transactions.withApprovalState(() -> {
            String key = "mycarlos.approval.import." + identity;
            var rows = properties.findByNameForUpdate(key).stream()
                    .filter(row -> row.getProviderNo() == null || row.getProviderNo().isBlank()).toList();
            if (rows.size() > 1) throw new IllegalStateException("ambiguous approval import history");
            var expected = JSON.createObjectNode().put("v", 1).put("clinic", cfg.clinic)
                    .put("patient", request.demographicNo()).put("sequence", Long.toString(request.sequence()))
                    .put("intent", request.intent()).put("config", PortalMyCarlosDeviceApprovalCodec.base64(cfg.hash().copy()));
            if (!rows.isEmpty()) {
                try {
                    String raw = rows.getFirst().getValue();
                    if (raw == null || raw.length() > 32768) throw new IllegalStateException("invalid approval import history");
                    var prior = JSON.readTree(raw);
                    var reason = prior.get("reason");
                    if (reason == null || !reason.isTextual() || !REASONS.contains(reason.textValue())
                            || prior.size() != expected.size() + 1) throw new IllegalStateException("invalid approval import history");
                    expected.put("reason", reason.textValue());
                    if (!expected.equals(prior)) throw new IllegalStateException("approval import binding changed");
                    return reason.textValue();
                } catch (java.io.IOException failure) { throw new IllegalStateException("invalid approval import history"); }
            }
            if (suppliedReason == null) return null;
            expected.put("reason", suppliedReason);
            var row = new Property(key);
            row.setValue(expected.toString());
            properties.persist(row);
            return suppliedReason;
        });
    }
}
