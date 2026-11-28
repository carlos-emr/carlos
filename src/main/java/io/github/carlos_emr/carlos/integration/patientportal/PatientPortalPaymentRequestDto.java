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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * A payment request as the portal reports it. {@code state} is one of {@link #STATES}; {@code
 * expired} is derived by the portal from an unpaid request past its expiry.
 */
public record PatientPortalPaymentRequestDto(
        long id, int demographicNo, String invoiceRef, String category, String paymentMethod,
        String currency, long amountCents, long taxCents, LocalDate dueOn, String state,
        String createdBy, Instant createdAt, Instant expiresAt, Instant readAt, Instant paidAt,
        Instant withdrawnAt, String withdrawnBy) {
    public static final Set<String> STATES = Set.of("sent", "read", "paid", "withdrawn", "expired");

    static PatientPortalPaymentRequestDto fromJson(JsonNode node) {
        String category = PortalJson.requiredText(node, "category");
        String state = PortalJson.requiredText(node, "state");
        String paymentMethod = PortalJson.requiredText(node, "payment_method");
        String currency = PortalJson.requiredText(node, "currency");
        if (!PatientPortalPaymentRequestRequest.CATEGORIES.contains(category)
                || !STATES.contains(state)
                || !PatientPortalPaymentRequestRequest.PAYMENT_METHOD.equals(paymentMethod)
                || !PatientPortalPaymentRequestRequest.CURRENCY.equals(currency)) {
            throw new PortalContractException("portal returned unknown payment vocabulary");
        }
        LocalDate dueOn;
        try {
            dueOn = LocalDate.parse(PortalJson.requiredText(node, "due_on"));
        } catch (DateTimeParseException invalid) {
            throw new PortalContractException("portal returned an invalid payment due date");
        }
        return new PatientPortalPaymentRequestDto(
                PortalJson.positiveLong(node, "id"), PortalJson.positiveInt(node, "demographic_no"),
                PortalJson.requiredText(node, "invoice_ref"), category, paymentMethod, currency,
                PortalJson.requiredLong(node, "amount_cents"), PortalJson.requiredLong(node, "tax_cents"),
                dueOn, state, PortalJson.requiredText(node, "created_by"),
                PortalJson.requiredTimestamp(node, "created_at"),
                PortalJson.requiredTimestamp(node, "expires_at"),
                PortalJson.nullableTimestamp(node, "read_at"),
                PortalJson.nullableTimestamp(node, "paid_at"),
                PortalJson.nullableTimestamp(node, "withdrawn_at"),
                PortalJson.nullableText(node, "withdrawn_by"));
    }

    /** The record carries a patient's bill and provider data, so suppress generated rendering. */
    @Override
    public String toString() {
        return "PatientPortalPaymentRequestDto[details=REDACTED]";
    }

    /** created=false is a confirmed retry, not evidence of another request. */
    public record Creation(PatientPortalPaymentRequestDto request, boolean created) {}
}
