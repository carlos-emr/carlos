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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable service-code grid for {@code billingON.jsp}. Replaces the
 * five-field cluster ({@code billingServiceCodesMap},
 * {@code listServiceType}, {@code titleMap}, {@code premiumCodes},
 * {@code dxCodesByServiceType}) the form view model previously carried as
 * flat fields.
 *
 * <p>The grid renders 3 columns of service codes per service type. Each
 * column is a {@code List<ServiceCodeEntry>} keyed by the service-type
 * code in {@link #codesByServiceType()}; the rendered title comes from
 * {@link #titlesByServiceType()}; {@link #premiumCodes()} flags codes that
 * carry a premium; {@link #dxCodesByServiceType()} feeds the dx-search
 * panel for each service type.</p>
 *
 * <p>Collections are immutable copies; nulls coalesce to empty.</p>
 *
 * @since 2026-04-26
 */
public record BillingServiceCodeGrid(
        List<String> serviceTypes,
        Map<String, List<BillingONFormViewModel.ServiceCodeEntry>> codesByServiceType,
        Map<String, String> titlesByServiceType,
        Set<String> premiumCodes,
        Map<String, List<BillingONFormViewModel.DxCodeEntry>> dxCodesByServiceType) {

    /** Empty grid (no codes, no service types). */
    public static final BillingServiceCodeGrid EMPTY = new BillingServiceCodeGrid(
            List.of(), Map.of(), Map.of(), Set.of(), Map.of());

    /** Compact constructor: defensively copies nested lists in nested-list maps. */
    public BillingServiceCodeGrid {
        serviceTypes = serviceTypes == null ? Collections.emptyList() : List.copyOf(serviceTypes);
        codesByServiceType = copyOfNestedListMap(codesByServiceType);
        titlesByServiceType = titlesByServiceType == null
                ? Collections.emptyMap() : Map.copyOf(titlesByServiceType);
        premiumCodes = premiumCodes == null ? Collections.emptySet() : Set.copyOf(premiumCodes);
        dxCodesByServiceType = copyOfNestedListMap(dxCodesByServiceType);
    }

    private static <K, V> Map<K, List<V>> copyOfNestedListMap(Map<K, List<V>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        java.util.LinkedHashMap<K, List<V>> copy = new java.util.LinkedHashMap<>(source.size());
        for (Map.Entry<K, List<V>> e : source.entrySet()) {
            List<V> list = e.getValue();
            copy.put(e.getKey(), list == null ? Collections.emptyList() : List.copyOf(list));
        }
        return Collections.unmodifiableMap(copy);
    }
}
