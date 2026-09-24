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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceDesignationComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;

/**
 * Parses the NVC v2 FHIR R4 {@code Bundle/NVC} download into an {@link NvcCatalogue}.
 *
 * <p>The bundle is a {@code collection} of ValueSets and CodeSystems, one per NVC subset.
 * The mapping below was derived from the published bundle (version 1.130, September 2026),
 * not from the NVC v1/CVC resource model the previous implementation targeted:
 * <ul>
 *   <li>{@code ValueSet/Generic}, {@code ValueSet/Tradename} — vaccine concepts. Every concept
 *       sits in its own {@code compose.include}; the English "Synonym" designation is NVC's
 *       unique display term (e.g. {@code [Inf] Influenza trivalent vaccine}), the
 *       {@code enPublicPicklist} designation is the plain-language name.</li>
 *   <li>{@code nvc-linked-generic-concept} links a tradename to its generic. Its coding system
 *       is the SNOMED CT Canadian edition URI, not the Generic ValueSet URL.</li>
 *   <li>{@code nvc-din}, {@code nvc-linked-to-market-authorization-holder} and
 *       {@code nvc-product-status} describe the marketed product.</li>
 *   <li>{@code CodeSystem/nvc-vaccine-lot-id} holds the lots. Each lot concept carries
 *       {@code lotNumber}/{@code expiryDate} properties and links back to its tradename(s);
 *       the per-tradename {@code nvc-lot} codings do not resolve to these concept codes, so the
 *       CodeSystem is the only reliable source of lot expiry dates.</li>
 * </ul>
 *
 * <p>Stateless and thread-safe: the shared {@link FhirContext} is expensive to build and safe
 * to reuse.
 *
 * @since 2026-09-24
 */
public final class NvcBundleParser {

    /** Canonical base NVC stamps on every extension and CodeSystem URL in the bundle. */
    static final String NVC_CANONICAL_BASE = "https://nvc-cnv.canada.ca/fhir/v2";

    private static final String EXT = NVC_CANONICAL_BASE + "/StructureDefinition/";
    static final String EXT_CONCEPT_STATUS = EXT + "nvc-concept-status";
    static final String EXT_LINKED_GENERIC = EXT + "nvc-linked-generic-concept";
    static final String EXT_LINKED_TRADENAME = EXT + "nvc-linked-tradename-concept";
    static final String EXT_MARKET_AUTH_HOLDER = EXT + "nvc-linked-to-market-authorization-holder";
    static final String EXT_PRODUCT_STATUS = EXT + "nvc-product-status";
    static final String EXT_DIN = EXT + "nvc-din";

    private static final String NVC_BUNDLE_ID = "NVC";
    private static final String LOT_CODE_SYSTEM_ID = "nvc-vaccine-lot-id";
    private static final String SNOMED_SYNONYM = "900000000000013009";
    private static final String NVC_PUBLIC_PICKLIST = "enPublicPicklist";
    private static final String NVC_DISPLAY_TERM = "enDisplayTerm";

    /** CVCMedication.status is VARCHAR(40). */
    private static final int MAX_STATUS_LENGTH = 40;
    /** CVCMedication.din is INT(11); Health Canada DINs are 8 digits. */
    private static final int MAX_DIN_DIGITS = 9;

    private static final FhirContext FHIR_R4 = FhirContext.forR4();

    private NvcBundleParser() {
    }

    /**
     * Parses a raw NVC bundle.
     *
     * @param json the {@code Bundle/NVC} response body
     * @return the catalogue snapshot
     * @throws NvcBundleException if the JSON is not a FHIR R4 Bundle, or lacks the Generic or
     *                            Tradename subsets (treated as a bad download, never as "empty")
     */
    public static NvcCatalogue parse(String json) throws NvcBundleException {
        if (json == null || json.isBlank()) {
            throw new NvcBundleException("NVC bundle is empty");
        }
        Bundle bundle;
        try {
            bundle = FHIR_R4.newJsonParser().parseResource(Bundle.class, json);
        } catch (DataFormatException | ClassCastException e) {
            throw new NvcBundleException("NVC response is not a FHIR R4 Bundle", e);
        }
        return parse(bundle);
    }

    /**
     * Maps an already-parsed bundle; see {@link #parse(String)}.
     */
    static NvcCatalogue parse(Bundle bundle) throws NvcBundleException {
        // Only the NVC collection itself may replace the catalogue; a misconfigured mirror that
        // serves some other bundle with look-alike subsets must not.
        if (bundle.getType() != Bundle.BundleType.COLLECTION
                || !NVC_BUNDLE_ID.equals(bundle.getIdElement().getIdPart())) {
            throw new NvcBundleException("Response is not the NVC Bundle/NVC collection");
        }
        Map<String, Resource> subsets = new HashMap<>();
        for (BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource != null && resource.getIdElement().getIdPart() != null) {
                subsets.put(resource.fhirType() + "/" + resource.getIdElement().getIdPart(), resource);
            }
        }

        ValueSet genericSet = valueSet(subsets, "Generic");
        ValueSet tradenameSet = valueSet(subsets, "Tradename");
        if (genericSet == null || tradenameSet == null) {
            throw new NvcBundleException("NVC bundle is missing the Generic or Tradename ValueSet");
        }

        List<NvcCatalogue.Vaccine> generics = new ArrayList<>();
        for (ConceptReferenceComponent concept : activeConcepts(genericSet)) {
            String synonym = firstNonBlank(designation(concept, SNOMED_SYNONYM), concept.getDisplay());
            generics.add(new NvcCatalogue.Vaccine(
                    concept.getCode(),
                    firstNonBlank(designation(concept, NVC_PUBLIC_PICKLIST), synonym),
                    // The generic picklist name becomes the prevention type key in
                    // PreventionDisplayConfig, so it must be unique: NVC's synonym is, the
                    // public picklist term is not (eleven generics are "Influenza (flu) vaccine").
                    synonym,
                    null));
        }
        if (generics.isEmpty()) {
            throw new NvcBundleException("NVC Generic ValueSet contains no active concepts");
        }

        List<NvcCatalogue.Vaccine> tradenames = new ArrayList<>();
        Map<String, ProductBuilder> products = new LinkedHashMap<>();
        for (ConceptReferenceComponent concept : activeConcepts(tradenameSet)) {
            String synonym = firstNonBlank(designation(concept, SNOMED_SYNONYM), concept.getDisplay());
            tradenames.add(new NvcCatalogue.Vaccine(
                    concept.getCode(),
                    synonym,
                    firstNonBlank(designation(concept, NVC_PUBLIC_PICKLIST), synonym),
                    firstCode(concept.getExtension(), EXT_LINKED_GENERIC)));

            ProductBuilder product = new ProductBuilder(concept.getCode(), synonym);
            product.din = din(firstCode(concept.getExtension(), EXT_DIN));
            product.manufacturer = supplierName(firstDisplay(concept.getExtension(), EXT_MARKET_AUTH_HOLDER));
            product.status = productStatus(concept.getExtension());
            products.put(concept.getCode(), product);
        }
        if (tradenames.isEmpty()) {
            // Replacing with no brands would also delete every product and lot.
            throw new NvcBundleException("NVC Tradename ValueSet contains no active concepts");
        }

        CodeSystem lotSystem = subsets.get("CodeSystem/" + LOT_CODE_SYSTEM_ID) instanceof CodeSystem cs ? cs : null;
        if (lotSystem != null) {
            for (ConceptDefinitionComponent lotConcept : lotSystem.getConcept()) {
                String lotNumber = stringProperty(lotConcept, "lotNumber");
                if (isBlank(lotNumber)) {
                    continue;
                }
                NvcCatalogue.Lot lot = new NvcCatalogue.Lot(lotNumber.trim(), dateProperty(lotConcept, "expiryDate"));
                for (String tradename : codes(lotConcept.getExtension(), EXT_LINKED_TRADENAME)) {
                    ProductBuilder product = products.get(tradename);
                    if (product != null) {
                        product.lots.put(lot.lotNumber(), lot);
                    }
                }
            }
        }

        List<NvcCatalogue.Product> productList = new ArrayList<>(products.size());
        for (ProductBuilder product : products.values()) {
            productList.add(product.build());
        }

        return new NvcCatalogue(
                tradenameSet.getVersion(),
                generics,
                tradenames,
                productList,
                codedValues(valueSet(subsets, "AnatomicalSite")),
                codedValues(valueSet(subsets, "RouteOfAdmin")));
    }

    private static ValueSet valueSet(Map<String, Resource> subsets, String id) {
        return subsets.get("ValueSet/" + id) instanceof ValueSet vs ? vs : null;
    }

    private static List<ConceptReferenceComponent> activeConcepts(ValueSet valueSet) {
        List<ConceptReferenceComponent> active = new ArrayList<>();
        if (valueSet == null || !valueSet.hasCompose()) {
            return active;
        }
        for (ConceptSetComponent include : valueSet.getCompose().getInclude()) {
            for (ConceptReferenceComponent concept : include.getConcept()) {
                if (isBlank(concept.getCode())) {
                    continue;
                }
                String status = firstString(concept.getExtension(), EXT_CONCEPT_STATUS);
                // A missing status is treated as active: the flag is informational in the NVC
                // profile, and dropping unflagged concepts would silently shrink the catalogue.
                // NVC publishes the status lower-case; compare exactly rather than case-folding.
                if (status == null || "active".equals(status)) {
                    active.add(concept);
                }
            }
        }
        return active;
    }

    private static List<NvcCatalogue.CodedValue> codedValues(ValueSet valueSet) {
        List<NvcCatalogue.CodedValue> values = new ArrayList<>();
        for (ConceptReferenceComponent concept : activeConcepts(valueSet)) {
            values.add(new NvcCatalogue.CodedValue(concept.getCode(),
                    firstNonBlank(designation(concept, NVC_DISPLAY_TERM), concept.getDisplay(), concept.getCode())));
        }
        return values;
    }

    /** English designation value whose {@code use.code} matches, or {@code null}. */
    private static String designation(ConceptReferenceComponent concept, String useCode) {
        for (ConceptReferenceDesignationComponent designation : concept.getDesignation()) {
            Coding use = designation.getUse();
            boolean english = !designation.hasLanguage() || "en".equals(designation.getLanguage());
            if (english && use != null && useCode.equals(use.getCode()) && !isBlank(designation.getValue())) {
                return designation.getValue().trim();
            }
        }
        return null;
    }

    private static String firstString(List<Extension> extensions, String url) {
        for (Extension extension : extensions) {
            if (url.equals(extension.getUrl()) && extension.hasValue() && extension.getValue().isPrimitive()) {
                return extension.getValue().primitiveValue();
            }
        }
        return null;
    }

    private static List<Coding> codings(List<Extension> extensions, String url) {
        List<Coding> codings = new ArrayList<>();
        for (Extension extension : extensions) {
            if (!url.equals(extension.getUrl())) {
                continue;
            }
            if (extension.getValue() instanceof CodeableConcept concept) {
                codings.addAll(concept.getCoding());
            } else if (extension.getValue() instanceof Coding coding) {
                codings.add(coding);
            }
        }
        return codings;
    }

    private static Set<String> codes(List<Extension> extensions, String url) {
        Set<String> codes = new LinkedHashSet<>();
        for (Coding coding : codings(extensions, url)) {
            if (!isBlank(coding.getCode())) {
                codes.add(coding.getCode().trim());
            }
        }
        return codes;
    }

    private static String firstCode(List<Extension> extensions, String url) {
        Set<String> codes = codes(extensions, url);
        return codes.isEmpty() ? null : codes.iterator().next();
    }

    private static String firstDisplay(List<Extension> extensions, String url) {
        for (Coding coding : codings(extensions, url)) {
            if (!isBlank(coding.getDisplay())) {
                return coding.getDisplay().trim();
            }
        }
        return null;
    }

    private static String productStatus(List<Extension> extensions) {
        Set<String> statuses = new LinkedHashSet<>();
        for (Coding coding : codings(extensions, EXT_PRODUCT_STATUS)) {
            String label = firstNonBlank(coding.getDisplay(), coding.getCode());
            if (label != null) {
                statuses.add(label);
            }
        }
        if (statuses.isEmpty()) {
            return null;
        }
        String joined = String.join(", ", statuses);
        return joined.length() <= MAX_STATUS_LENGTH ? joined : joined.substring(0, MAX_STATUS_LENGTH);
    }

    /** DINs are stored in an INT column; anything that is not a short digit string is dropped. */
    private static String din(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_DIN_DIGITS || !raw.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return raw;
    }

    /** Market authorization holders carry the SNOMED semantic tag "(supplier)"; drop it for display. */
    private static String supplierName(String display) {
        if (display == null) {
            return null;
        }
        String name = display.endsWith("(supplier)")
                ? display.substring(0, display.length() - "(supplier)".length()).trim()
                : display;
        return name.isEmpty() ? null : name;
    }

    private static String stringProperty(ConceptDefinitionComponent concept, String code) {
        for (ConceptPropertyComponent property : concept.getProperty()) {
            if (code.equals(property.getCode()) && property.hasValue() && property.getValue().isPrimitive()) {
                return property.getValue().primitiveValue();
            }
        }
        return null;
    }

    private static LocalDate dateProperty(ConceptDefinitionComponent concept, String code) {
        for (ConceptPropertyComponent property : concept.getProperty()) {
            if (code.equals(property.getCode()) && property.getValue() instanceof DateTimeType date
                    && date.getValue() != null) {
                // Expiry dates are published date-only. Read the calendar fields as written
                // rather than converting the java.util.Date, which would shift the day with the
                // server's default time zone.
                return LocalDate.of(date.getYear(), date.getMonth() + 1, date.getDay());
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Mutable accumulator; lots arrive after the tradename pass. */
    private static final class ProductBuilder {
        private final String snomedCode;
        private final String displayName;
        private String din;
        private String manufacturer;
        private String status;
        // Keyed by lot number: NVC republishes a handful of lots under two concept ids.
        private final Map<String, NvcCatalogue.Lot> lots = new LinkedHashMap<>();

        private ProductBuilder(String snomedCode, String displayName) {
            this.snomedCode = snomedCode;
            this.displayName = displayName;
        }

        private NvcCatalogue.Product build() {
            return new NvcCatalogue.Product(snomedCode, displayName, din, manufacturer, status,
                    new ArrayList<>(lots.values()));
        }
    }
}
