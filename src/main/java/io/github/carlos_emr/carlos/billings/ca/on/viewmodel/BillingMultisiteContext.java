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

/**
 * Immutable multisite + RMA + clinic-nbr context shared across the ON billing
 * page view models:
 *
 * <ul>
 *   <li>{@code multisiteEnabled} / {@code multisiteSites} / {@code defaultSelectedSite}
 *       / {@code defaultXmlProvider} / {@code selectedXmlProvider} /
 *       {@code multisiteProviderHtml}</li>
 *   <li>{@code rmaEnabled} / {@code clinicNbrs} / {@code selectedClinicNbrPrefix}</li>
 * </ul>
 *
 * <p>Each component is null-coalesced so EL output via {@code <carlos:encode>}
 * doesn't render the literal {@code "null"} string. Collections are immutable
 * copies; constructors accept {@code null} and substitute empty collections.</p>
 *
 * @since 2026-04-26
 */
public record BillingMultisiteContext(
        boolean enabled,
        List<MultisiteSite> sites,
        String defaultSelectedSite,
        String defaultXmlProvider,
        String selectedXmlProvider,
        Map<String, String> multisiteProviderHtml,
        boolean rmaEnabled,
        List<ClinicNbrEntry> clinicNbrs,
        String selectedClinicNbrPrefix) {

    /** Empty / no-multisite default. */
    public static final BillingMultisiteContext EMPTY = new BillingMultisiteContext(
            false, List.of(), "", "", "", Map.of(), false, List.of(), "");

    /** Compact constructor coalesces nulls and locks collections immutable. */
    public BillingMultisiteContext {
        sites = sites == null ? Collections.emptyList() : List.copyOf(sites);
        defaultSelectedSite = defaultSelectedSite == null ? "" : defaultSelectedSite;
        defaultXmlProvider = defaultXmlProvider == null ? "" : defaultXmlProvider;
        selectedXmlProvider = selectedXmlProvider == null ? "" : selectedXmlProvider;
        multisiteProviderHtml = multisiteProviderHtml == null
                ? Collections.emptyMap() : Map.copyOf(multisiteProviderHtml);
        clinicNbrs = clinicNbrs == null ? Collections.emptyList() : List.copyOf(clinicNbrs);
        selectedClinicNbrPrefix = selectedClinicNbrPrefix == null ? "" : selectedClinicNbrPrefix;
    }

    /** A multisite site entry (name, bg color, providers attached). */
    public record MultisiteSite(String name, String bgColor, List<MultisiteProvider> providers) {
        public MultisiteSite {
            name = name == null ? "" : name;
            bgColor = bgColor == null ? "" : bgColor;
            providers = providers == null ? Collections.emptyList() : List.copyOf(providers);
        }
    }

    /** A provider attached to a multisite site (for the per-site picker). */
    public record MultisiteProvider(String providerNo, String ohipNo, String lastName, String firstName) {
        public MultisiteProvider {
            providerNo = providerNo == null ? "" : providerNo;
            ohipNo = ohipNo == null ? "" : ohipNo;
            lastName = lastName == null ? "" : lastName;
            firstName = firstName == null ? "" : firstName;
        }
    }

    /** Clinic-number entry for the xml_visittype dropdown when {@link #rmaEnabled} is true. */
    public record ClinicNbrEntry(String nbrValue, String displayLabel) {
        public ClinicNbrEntry {
            nbrValue = nbrValue == null ? "" : nbrValue;
            displayLabel = displayLabel == null ? "" : displayLabel;
        }
    }
}
