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

import java.time.LocalDate;
import java.util.Set;

/**
 * A payment request for the patient portal (billing through the portal, Phase 0).
 *
 * <p>Everything in it is a closed value or a number: an opaque invoice reference that only CARLOS
 * can map back to an invoice, the amount due now in whole cents with its tax portion, a category
 * from a fixed list, how the patient is told to pay, and a due date. There is no description and no
 * name, so nothing about the patient's care can travel in it. Callers keep {@code operationId}
 * unchanged across retries so a repeat returns the original request.
 *
 * @param operationId CARLOS's stable id for this send
 * @param invoiceRef opaque random reference (ASCII letters, digits, {@code -} and {@code _}); never the
 *     invoice number
 * @param category one of {@link #CATEGORIES}
 * @param amountCents the amount due now, {@value #MIN_AMOUNT_CENTS} to {@value #MAX_AMOUNT_CENTS}
 * @param taxCents the tax portion of {@code amountCents}, at most {@code amountCents}
 * @param dueOn the date the clinic asks for payment by
 */
public record PatientPortalPaymentRequestRequest(
        String operationId, String invoiceRef, String category, long amountCents, long taxCents,
        LocalDate dueOn) {
    /** Matches the portal's {@code PAYMENT_REQUEST_CATEGORIES}. */
    public static final Set<String> CATEGORIES =
            Set.of("uninsured_service", "form_or_note", "missed_appointment", "other");
    /** The only way a patient is told to pay in Phase 0; a closed vocabulary the portal checks too. */
    public static final String PAYMENT_METHOD = "clinic_instructions";
    /** Canadian dollars only. */
    public static final String CURRENCY = "CAD";
    public static final long MIN_AMOUNT_CENTS = 1;
    public static final long MAX_AMOUNT_CENTS = 10_000_000;
    public static final int MAX_INVOICE_REF_LENGTH = 64;

    public PatientPortalPaymentRequestRequest {
        if (operationId == null || !operationId.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new PortalRequestPreparationException("payment operation id is invalid");
        }
        if (invoiceRef == null || !invoiceRef.matches("[A-Za-z0-9_-]{1," + MAX_INVOICE_REF_LENGTH + "}")) {
            throw new PortalRequestPreparationException("payment invoice reference is invalid");
        }
        if (category == null || !CATEGORIES.contains(category)) {
            throw new PortalRequestPreparationException("payment category is invalid");
        }
        if (amountCents < MIN_AMOUNT_CENTS || amountCents > MAX_AMOUNT_CENTS
                || taxCents < 0 || taxCents > amountCents) {
            throw new PortalRequestPreparationException("payment amount is invalid");
        }
        if (dueOn == null) {
            throw new PortalRequestPreparationException("payment due date is required");
        }
    }

    /** Amounts, references and dates correlate to a patient's bill, so keep them out of logs. */
    @Override
    public String toString() {
        return "PatientPortalPaymentRequestRequest[details=REDACTED]";
    }
}
