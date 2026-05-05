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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown when a billing-side read path (DAO query, date parse, etc.)
 * fails and silently returning an empty list would be a silent-failure
 * hazard — i.e., the operator would mistake "load failed" for "no
 * records exist" and act on incomplete data (e.g., generate a report
 * that's missing rows, mark a queue empty when it's actually not).
 *
 * <p>Carries a structured {@link Phase} discriminator and a string-keyed
 * {@code context} map so the operator-facing JSP can render
 * "Operation: load batch header / Context: bid=12345" without parsing
 * the message string. Throw sites supply only PHI-safe keys (bill ids,
 * date ranges, status filters — not HIN, demographic identity, etc.).</p>
 *
 * <p>Unchecked so legacy call sites surface the failure without a
 * checked exception signature change. Action layers catch this and
 * render an error banner so the operator knows the displayed list
 * is unreliable.</p>
 *
 * @since 2026-04-30
 */
public class BillingDataLoadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Coarse-grained discriminator for the failure category. */
    public enum Phase {
        DAO_QUERY,
        DATE_PARSE,
        BATCH_HEADER_LOOKUP,
        CLAIM_EXTRACT
    }

    private final Phase phase;
    private final Map<String, String> context;

    public BillingDataLoadException(String message) {
        this(message, null, Phase.DAO_QUERY, Collections.emptyMap());
    }

    public BillingDataLoadException(String message, Throwable cause) {
        this(message, cause, Phase.DAO_QUERY, Collections.emptyMap());
    }

    public BillingDataLoadException(String message, Phase phase, Map<String, String> context) {
        this(message, null, phase, context);
    }

    public BillingDataLoadException(String message, Throwable cause, Phase phase,
                                    Map<String, String> context) {
        super(message, cause);
        this.phase = phase == null ? Phase.DAO_QUERY : phase;
        this.context = context == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    public Phase phase() {
        return phase;
    }

    /**
     * Unmodifiable, insertion-ordered context map. Keys are PHI-safe
     * diagnostic identifiers (bill id, date range, status filter, etc.);
     * never raw demographic identity.
     */
    public Map<String, String> context() {
        return context;
    }
}
