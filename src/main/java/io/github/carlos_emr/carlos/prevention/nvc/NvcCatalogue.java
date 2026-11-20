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
package io.github.carlos_emr.carlos.prevention.nvc;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable snapshot of the parts of the Public Health Agency of Canada National Vaccine
 * Catalogue (NVC) v2 bundle that CARLOS stores locally.
 *
 * <p>Produced by {@link NvcBundleParser} before any database row is touched, so a download
 * that is incomplete or malformed never wipes the catalogue already in use. Only concepts
 * NVC marks {@code active} are carried: retired concepts share display terms with their
 * replacements and would otherwise surface as duplicate prevention types.
 *
 * @param version      the NVC bundle version stamp (Tradename ValueSet version), may be {@code null}
 * @param generics     generic vaccine concepts; each becomes a selectable prevention type
 * @param tradenames   brand-name vaccine concepts linked to their generic parent
 * @param products     one marketed product per tradename, carrying DIN, supplier and lot numbers
 * @param anatomicalSites administration sites for the {@code AnatomicalSite} lookup list
 * @param routes       routes of administration for the {@code RouteOfAdmin} lookup list
 * @since 2026-09-24
 */
public record NvcCatalogue(
        String version,
        List<Vaccine> generics,
        List<Vaccine> tradenames,
        List<Product> products,
        List<CodedValue> anatomicalSites,
        List<CodedValue> routes) {

    public NvcCatalogue {
        generics = List.copyOf(generics);
        tradenames = List.copyOf(tradenames);
        products = List.copyOf(products);
        anatomicalSites = List.copyOf(anatomicalSites);
        routes = List.copyOf(routes);
    }

    /**
     * A generic or tradename vaccine concept.
     *
     * @param snomedConceptId SNOMED CT (Canadian edition) concept id
     * @param displayName     text shown in lists and the tradename picker
     * @param picklistName    short unique name; for generics this is also the prevention type name
     * @param parentConceptId generic concept a tradename belongs to; {@code null} for generics
     */
    public record Vaccine(String snomedConceptId, String displayName, String picklistName,
                          String parentConceptId) {
    }

    /**
     * A marketed vaccine product (one per tradename concept).
     *
     * @param snomedCode   tradename SNOMED concept id (joins to {@link Vaccine#snomedConceptId()})
     * @param displayName  tradename display term
     * @param din          Health Canada Drug Identification Number, digits only, or {@code null}
     * @param manufacturer market authorization holder name, or {@code null}
     * @param status       comma-separated NVC product statuses (e.g. "Approved, Marketed"), or {@code null}
     * @param lots         manufacturer lot numbers NVC links to this tradename
     */
    public record Product(String snomedCode, String displayName, String din, String manufacturer,
                          String status, List<Lot> lots) {
        public Product {
            lots = List.copyOf(lots);
        }
    }

    /**
     * A manufacturer lot.
     *
     * @param lotNumber  lot number as printed on the vial/package
     * @param expiryDate current expiry (after any shelf-life extension), or {@code null}
     */
    public record Lot(String lotNumber, LocalDate expiryDate) {
    }

    /**
     * A coded lookup value (anatomical site or route).
     *
     * @param code  SNOMED concept id stored as the lookup item value
     * @param label English display term stored as the lookup item label
     */
    public record CodedValue(String code, String label) {
    }
}
