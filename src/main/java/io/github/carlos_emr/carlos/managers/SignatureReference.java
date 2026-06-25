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
package io.github.carlos_emr.carlos.managers;

import org.apache.commons.lang3.StringUtils;

/**
 * Typed view of the single {@code signatureImg} form field used by the consultation request flow,
 * which is overloaded to mean one of three mutually exclusive things. Centralizing the
 * classification here removes the {@code \d{1,9}} regex that was previously duplicated across the
 * action and the service, and makes the three states explicit at call sites. The JSP keeps an
 * independent client-side copy of the rule ({@code isStoredSignatureId} in
 * {@code ConsultationFormRequest.jsp}); JavaScript cannot call this Java predicate, so that copy
 * must be kept in sync with {@link #STORED_ID_PATTERN} by hand.
 *
 * <ul>
 *   <li>{@link Kind#STORED} — a persisted {@code DigitalSignature} id (1-9 digit number).</li>
 *   <li>{@link Kind#MANUAL} — a signature-pad temp-file request id captured this submission.</li>
 *   <li>{@link Kind#STAMP}  — no stored id and not re-signing: the provider's stamp is applied.</li>
 * </ul>
 *
 * @param kind  which of the three reference kinds this value represents
 * @param value the underlying reference string ({@code ""} for {@link Kind#STAMP})
 */
public record SignatureReference(Kind kind, String value) {

    /** The mutually exclusive kinds a consultation signature reference can take. */
    public enum Kind { STORED, MANUAL, STAMP }

    /**
     * Validates the kind/value invariant so illegal combinations are unconstructable regardless of
     * which caller builds the record: a {@link Kind#STORED} value must be a stored id, and a
     * {@link Kind#STAMP} value must be empty. {@link Kind#MANUAL} may carry any (possibly empty)
     * request id. A {@code null} value is normalized to {@code ""}.
     */
    public SignatureReference {
        value = value == null ? "" : value;
        if (kind == Kind.STORED && !isStoredId(value)) {
            throw new IllegalArgumentException("STORED reference requires a 1-9 digit id");
        }
        if (kind == Kind.STAMP && !value.isEmpty()) {
            throw new IllegalArgumentException("STAMP reference must not carry a value");
        }
    }

    /**
     * Stored {@code DigitalSignature} ids are numeric and capped at 9 digits so the downstream
     * {@link Integer#parseInt(String)} can never overflow. This is intentionally narrower than the
     * unbounded {@code \d+} used to validate provider numbers (a different domain value that may
     * exceed 9 digits) — do not unify the two patterns.
     */
    private static final String STORED_ID_PATTERN = "\\d{1,9}";

    /**
     * Reports whether {@code value} is a persisted {@code DigitalSignature} id (a 1-9 digit number)
     * rather than a manual signature-pad request id or stamp marker.
     *
     * @param value the candidate signature reference (nullable)
     * @return {@code true} when {@code value} is a stored signature id, {@code false} otherwise
     */
    public static boolean isStoredId(String value) {
        return StringUtils.trimToEmpty(value).matches(STORED_ID_PATTERN);
    }

    /**
     * Classifies the submitted consultation signature form fields into a single typed reference.
     *
     * @param newSignature          whether the form is supplying a freshly captured signature
     * @param submittedSignatureImg the current {@code signatureImg} value (stored id or marker)
     * @param newSignatureImg       the manual signature-pad request id, when present
     * @return the typed reference; {@link Kind#MANUAL} when re-signing, otherwise {@link Kind#STORED}
     *         for a numeric id or {@link Kind#STAMP} when a provider stamp should be applied
     */
    public static SignatureReference parse(boolean newSignature, String submittedSignatureImg, String newSignatureImg) {
        String submitted = StringUtils.trimToEmpty(submittedSignatureImg);
        if (newSignature) {
            // Manual re-sign: prefer the submitted marker when it is not a stored id, else the
            // freshly captured signature-pad request id.
            String manualId = (!submitted.isEmpty() && !isStoredId(submitted))
                    ? submitted
                    : StringUtils.trimToEmpty(newSignatureImg);
            return new SignatureReference(Kind.MANUAL, manualId);
        }
        if (isStoredId(submitted)) {
            return new SignatureReference(Kind.STORED, submitted);
        }
        return new SignatureReference(Kind.STAMP, "");
    }

    public boolean isStored() {
        return kind == Kind.STORED;
    }

    public boolean isManual() {
        return kind == Kind.MANUAL;
    }

    public boolean isStamp() {
        return kind == Kind.STAMP;
    }
}
