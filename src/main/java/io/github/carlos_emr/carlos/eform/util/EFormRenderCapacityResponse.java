/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;

/** A waiting page for a saved-form continuation refused before renderer admission. */
public final class EFormRenderCapacityResponse {
    private EFormRenderCapacityResponse() { }

    /**
     * Call only for a typed pre-admission capacity refusal. The continuation never repeats
     * AddEForm's clinical save, and fax preparation never queues or sends a fax.
     */
    public static String offer(HttpServletRequest request, HttpServletResponse response,
            EFormRenderApprovalService.Operation operation, Map<String, String> fields) {
        String route;
        Set<String> allowed;
        switch (operation) {
            case DOWNLOAD -> {
                route = "/eform/downloadEFormPdf";
                allowed = Set.of("fdid", "demographicNo", "renderApproval", "autoClose");
            }
            case EDOC -> {
                route = "/eform/saveEFormAsEDoc";
                allowed = Set.of("fdid", "demographicNo", "renderApproval", "autoClose");
            }
            case FAX -> {
                route = "/fax/faxAction";
                allowed = Set.of("method", "transactionType", "transactionId", "demographicNo",
                        "recipient", "recipientFaxNumber", "letterheadFax");
                if (!"prepareFax".equals(fields.get("method"))
                        || !"EFORM".equals(fields.get("transactionType"))) {
                    throw new IllegalArgumentException("Only eForm fax preparation can wait");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported waiting continuation");
        }
        if (!allowed.containsAll(fields.keySet()) || fields.values().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("Unexpected waiting continuation fields");
        }
        requirePositiveId(fields.get(operation == EFormRenderApprovalService.Operation.FAX ? "transactionId" : "fdid"));
        requirePositiveId(fields.get("demographicNo"));
        request.setAttribute("renderCapacityAction", request.getContextPath() + route);
        request.setAttribute("renderCapacityFields", Map.copyOf(fields));
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Retry-After", "2");
        response.setHeader("Cache-Control", "no-store");
        return "renderBusy";
    }

    private static void requirePositiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,9}") || Long.parseLong(value) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A saved form and patient are required to wait");
        }
    }
}
