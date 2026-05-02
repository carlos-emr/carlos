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
package io.github.carlos_emr.carlos.billings.ca.on.command;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Shared coercion helpers for Ontario billing command objects.
 *
 * <p>The command layer sits between loose request/form inputs and the typed
 * services introduced in this PR. Keeping the normalization rules here avoids
 * re-encoding the same legacy null/blank/date/money handling in each command
 * type and makes validation failures uniform for callers and tests.</p>
 */
final class Commands {
    private Commands() {
    }

    /** Legacy form fields often omit optional values entirely; expose that as empty text. */
    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Parse an optional money field using the shared non-negative billing amount rules. */
    static BillingMoney optionalMoney(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return BillingMoney.parseNonNegative(value, fieldName);
    }

    /** Parse amounts already stored in the database's cents-style text format. */
    static BillingMoney requiredStoredCents(String value, String fieldName) {
        return BillingMoney.storedCents(value, fieldName);
    }

    /** Parse a non-negative quantity while preserving integer-looking values without trailing zeros. */
    static BigDecimal quantity(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            if (parsed.signum() < 0) {
                throw new BillingValidationException(
                        "Billing command: " + fieldName + " cannot be negative [" + value + "]");
            }
            return parsed.stripTrailingZeros();
        } catch (NumberFormatException e) {
            throw new BillingValidationException(
                    "Billing command: malformed " + fieldName + " [" + value + "]", e);
        }
    }

    /** Render quantities back into request-friendly plain text for redisplay or hidden fields. */
    static String quantityText(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /** Parse a required ISO date used by the newer typed command inputs. */
    static LocalDate isoDate(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BillingValidationException(
                    "Billing command: " + fieldName + " is null or blank");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BillingValidationException(
                    "Billing command: malformed " + fieldName + " [" + value + "]", e);
        }
    }

    /** Parse an optional ISO date, returning {@code null} for the legacy blank-field convention. */
    static LocalDate optionalIsoDate(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return isoDate(value, fieldName);
    }

    /**
     * Parse correction-screen dates, which still arrive as either ISO text or
     * the legacy basic OHIP form without separators.
     */
    static LocalDate optionalCorrectionDate(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 8 && trimmed.chars().allMatch(Character::isDigit)) {
                return LocalDate.parse(trimmed, DateTimeFormatter.BASIC_ISO_DATE);
            }
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new BillingValidationException(
                    "Billing command: malformed " + fieldName + " [" + value + "]", e);
        }
    }

    /** Render typed dates back into the ISO text format expected by newer form flows. */
    static String isoText(LocalDate value) {
        return value == null ? "" : value.toString();
    }
}
