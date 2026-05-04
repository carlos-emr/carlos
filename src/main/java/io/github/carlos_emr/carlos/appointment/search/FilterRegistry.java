/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.appointment.search;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import io.github.carlos_emr.carlos.appointment.search.filters.AvailableTimeSlotFilter;
import io.github.carlos_emr.carlos.appointment.search.filters.ExistingAppointmentFilter;
import io.github.carlos_emr.carlos.appointment.search.filters.FutureApptFilter;
import io.github.carlos_emr.carlos.appointment.search.filters.MultiUnitFilter;
import io.github.carlos_emr.carlos.appointment.search.filters.OpenAccessFilter;
import io.github.carlos_emr.carlos.appointment.search.filters.SufficientContiguousTimeFilter;

/**
 * Type-safe registry of known {@link AvailableTimeSlotFilter} implementations.
 *
 * <p>Replaces a previous {@code Class.forName(name).newInstance()} reflection sink in
 * {@code AppointmentSearchManagerImpl} that was flagged by code-scanning rules
 * {@code java/Reflection} / {@code unsafe-reflection}. Filter instances are now constructed
 * by hardcoded supplier lambdas keyed by a stable short name, giving compile-time type
 * safety and clear failure modes on typos.</p>
 *
 * <p>For backwards compatibility with existing {@code appointment_search} rows that
 * persisted fully-qualified class names, {@link #create(String)} also accepts legacy
 * FQCNs for the 5 known filter classes. Anything that is neither a registry key nor a
 * legacy FQCN is rejected with a descriptive {@link IllegalArgumentException}.</p>
 */
public final class FilterRegistry {

    /**
     * Stable short-name keys for the 5 known filter implementations. Clients persisting
     * new {@code SearchConfig} rows SHOULD use these keys; legacy rows using the FQCN
     * continue to resolve via {@link #LEGACY_FQCN_TO_KEY}.
     */
    public static final Map<String, Supplier<AvailableTimeSlotFilter>> REGISTRY;

    /**
     * Compatibility map from legacy fully-qualified class names of the 5 known filters
     * to their registry key. Used to resolve {@code appointment_search.file_contents}
     * rows that were persisted before the registry was introduced.
     */
    public static final Map<String, String> LEGACY_FQCN_TO_KEY;

    static {
        Map<String, Supplier<AvailableTimeSlotFilter>> registry = new HashMap<>();
        registry.put("SufficientContiguousTime", SufficientContiguousTimeFilter::new);
        registry.put("ExistingAppointment", ExistingAppointmentFilter::new);
        registry.put("MultiUnit", MultiUnitFilter::new);
        registry.put("OpenAccess", OpenAccessFilter::new);
        registry.put("FutureAppt", FutureApptFilter::new);
        REGISTRY = Collections.unmodifiableMap(registry);

        Map<String, String> legacy = new HashMap<>();
        legacy.put(SufficientContiguousTimeFilter.class.getName(), "SufficientContiguousTime");
        legacy.put(ExistingAppointmentFilter.class.getName(), "ExistingAppointment");
        legacy.put(MultiUnitFilter.class.getName(), "MultiUnit");
        legacy.put(OpenAccessFilter.class.getName(), "OpenAccess");
        legacy.put(FutureApptFilter.class.getName(), "FutureAppt");
        LEGACY_FQCN_TO_KEY = Collections.unmodifiableMap(legacy);
    }

    private FilterRegistry() {
        // utility class
    }

    /**
     * @param key registry short name or legacy fully-qualified class name
     * @return {@code true} if {@code key} resolves to a known filter
     */
    public static boolean isKnown(String key) {
        if (key == null) {
            return false;
        }
        return REGISTRY.containsKey(key) || LEGACY_FQCN_TO_KEY.containsKey(key);
    }

    /**
     * Resolve {@code key} to a short registry name, accepting either a native registry
     * key or a legacy fully-qualified class name of a known filter.
     *
     * @param key registry short name or legacy fully-qualified class name
     * @return the canonical registry key
     * @throws IllegalArgumentException if {@code key} is unknown
     */
    public static String resolveKey(String key) {
        if (key != null) {
            if (REGISTRY.containsKey(key)) {
                return key;
            }
            String mapped = LEGACY_FQCN_TO_KEY.get(key);
            if (mapped != null) {
                return mapped;
            }
        }
        throw new IllegalArgumentException("Unknown AvailableTimeSlotFilter key: " + key);
    }

    /**
     * Create a new {@link AvailableTimeSlotFilter} instance for the given key.
     *
     * @param key registry short name or legacy fully-qualified class name
     * @return a new filter instance
     * @throws IllegalArgumentException if {@code key} is unknown
     */
    public static AvailableTimeSlotFilter create(String key) {
        String canonical = resolveKey(key);
        return REGISTRY.get(canonical).get();
    }
}
