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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

/**
 * Immutable triple of validation banner state shared across the ON billing
 * view models. Replaces the {@code (errorFlag, errorMessage, warningMessage)}
 * field cluster that was duplicated in 4 of the 5 page view models.
 *
 * <p>Field semantics:</p>
 * <ul>
 *   <li>{@code errorFlag} — string sentinel (legacy: empty or {@code "1"});
 *       a non-empty value means a hard validation failure that should
 *       prevent submission.</li>
 *   <li>{@code errorMessage} — pre-rendered HTML snippet shown in the red
 *       banner (assembled by the page's data assembler from per-field
 *       validation results).</li>
 *   <li>{@code warningMessage} — pre-rendered HTML snippet shown in the
 *       orange banner (non-blocking warnings such as missing HIN, malformed
 *       referral OHIP).</li>
 * </ul>
 *
 * <p>Every component is null-coalesced to empty so EL output via
 * {@code <carlos:encode>} doesn't render the literal 4-character word
 * {@code "null"}.</p>
 *
 * @since 2026-04-25
 */
public record BillingValidationMessages(
        String errorFlag,
        String errorMessage,
        String warningMessage) {

    /** Empty / no-validation-feedback default. */
    public static final BillingValidationMessages EMPTY =
            new BillingValidationMessages("", "", "");

    /**
     * Compact constructor coalesces nulls to empty so consumers don't render
     * the literal {@code "null"} string in error banners.
     */
    public BillingValidationMessages {
        errorFlag = errorFlag == null ? "" : errorFlag;
        errorMessage = errorMessage == null ? "" : errorMessage;
        warningMessage = warningMessage == null ? "" : warningMessage;
    }

    /** True when {@link #errorFlag()} has been set to a non-empty sentinel. */
    public boolean hasError() {
        return !errorFlag.isEmpty();
    }
}
