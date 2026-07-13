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
package io.github.carlos_emr.carlos.commn.model;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single source of truth for the closed set of billing-status string codes
 * recognised on {@link BillingONCHeader1#setStatus(String)} and
 * {@link BillingONItem#setStatus(String)}. The codes are stored as varchar
 * literals on the legacy {@code billing_on_cheader1.status} and
 * {@code billing_on_item.status} columns; an enum migration is the eventual
 * end state but is blocked by the {@code BillingClaimHeaderDto} String-status
 * passthrough and a handful of raw-JDBC report paths that bypass the entity.
 *
 * <p>Both entity classes re-export the individual constants through their
 * own surfaces (e.g. {@code BillingONItem.OPEN}) for backwards compatibility
 * with existing call sites; this class is the place to add a new code or
 * adjust the whitelist.</p>
 *
 * @since 2026-04-30
 */
public final class BillingStatus {

    private static final AtomicLong UNKNOWN_STATUS_WARNING_COUNT = new AtomicLong();

    private BillingStatus() {
        // Utility class — prevent instantiation.
    }

    /** Open / unbilled / draft. */
    public static final String OPEN = "O";
    /** Settled (RA-confirmed paid). */
    public static final String SETTLED = "S";
    /** Soft-deleted. */
    public static final String DELETED = "D";
    /** Billed but not yet settled. */
    public static final String BILLED = "B";
    /** Patient-billed (third-party payment expected). */
    public static final String PATIENT_BILLED = "P";
    /** No-charge / not billed. */
    public static final String NOT_BILLED = "N";
    /** Independent / BON billing. */
    public static final String INDEPENDENT = "I";
    /** WCB billing. */
    public static final String WCB = "W";
    /** Acknowledgement (legacy values seen in tests / DB). */
    public static final String ACKNOWLEDGED = "A";

    /**
     * Whitelist of recognised status values. {@link BillingONCHeader1} and
     * {@link BillingONItem} validate against this set at write-time; any
     * value not in the set throws {@link IllegalArgumentException} so drift
     * surfaces at the call site rather than as a downstream "unknown
     * status" rendering bug.
     */
    public static final Set<String> KNOWN = Set.of(
            OPEN, SETTLED, DELETED, BILLED, PATIENT_BILLED,
            NOT_BILLED, INDEPENDENT, WCB, ACKNOWLEDGED);

    /**
     * Record one lenient-setter acceptance of an unknown status during the
     * migration window. This gives tests and future metrics plumbing a single
     * hook without changing legacy setter return types.
     */
    public static void recordUnknownStatusWarning() {
        UNKNOWN_STATUS_WARNING_COUNT.incrementAndGet();
    }

    /**
     * @return count of unknown statuses accepted by lenient setters in this JVM
     */
    public static long unknownStatusWarningCount() {
        return UNKNOWN_STATUS_WARNING_COUNT.get();
    }
}
