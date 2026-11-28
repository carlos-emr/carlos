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
package io.github.carlos_emr.carlos.integration.patientportal.payment;

/**
 * A payment request CARLOS refused before or after asking the portal. The reason has a fixed code
 * and message that are safe to show staff and to log: none carries an invoice number, an amount or
 * a patient.
 */
public class PortalPaymentRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Why a request was refused. */
    public enum Reason {
        INVOICE_NOT_FOUND("invoice_not_found", "The invoice was not found for this patient."),
        NOTHING_OWING("nothing_owing", "This invoice has nothing owing, so there is nothing to request."),
        AMOUNT_OUT_OF_RANGE("amount_out_of_range", "The amount owing cannot be requested through the portal."),
        DUE_DATE_INVALID("due_date_invalid", "The due date must be from today to one year ahead."),
        OPEN_REQUEST_DIFFERS("open_request_differs",
                "A different request is already open for this invoice. Withdraw it first."),
        REQUEST_NOT_FOUND("request_not_found", "The payment request was not found for this invoice."),
        REQUEST_UNCONFIRMED("request_unconfirmed",
                "The portal has not confirmed this request. Retry sending it before withdrawing it."),
        REQUEST_PAID("request_paid", "This payment request has been paid and cannot be changed.");

        private final String code;
        private final String message;

        Reason(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String code() { return code; }
        public String message() { return message; }
    }

    private final Reason reason;

    public PortalPaymentRequestException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
