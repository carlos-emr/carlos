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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceDesignationComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;

import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.CVCMedication;
import io.github.carlos_emr.carlos.commn.model.CVCMedicationLotNumber;

/**
 * Reads a National Vaccine Catalogue (NVC) V2 bundle into the catalogue entities CARLOS stores.
 *
 * <p>The NVC V2 bundle ({@code https://nvc-cnv.canada.ca/fhir/v2/Bundle/NVC}) is a FHIR R4
 * collection. CARLOS uses three of its resources:</p>
 * <ul>
 *   <li>the {@code Generic} value set: one generic {@link CVCImmunization} per concept;</li>
 *   <li>the {@code Tradename} value set: one brand {@link CVCImmunization} per concept, linked to
 *       its generic through {@code nvc-linked-generic-concept}, and one {@link CVCMedication}
 *       carrying the brand's DIN, market authorization holder and status;</li>
 *   <li>the {@code nvc-vaccine-lot-id} code system: lot numbers and expiry dates, each linked to
 *       its brands through {@code nvc-linked-tradename-concept}. A brand also lists its lots in
 *       {@code nvc-lot}; a few links appear only there, so both are read.</li>
 * </ul>
 *
 * <p>V2 replaced the CVC V1 wrapper extensions ({@code nvc-dins}, {@code nvc-lots},
 * {@code nvc-parent-concept} and so on) with single-valued ones on each concept, and gave lots
 * their own code system. Nothing here reads the V1 names.</p>
 *
 * <p>Names come from an external feed and reach pages and stored settings, so markup characters are
 * removed from them, and a prevention type name also loses the brackets and equals sign that the
 * prevention settings use as separators.</p>
 *
 * <p>Mapping is pure: it reads the parsed bundle and builds unsaved entities, so it can be tested
 * without a database or network.</p>
 *
 * @since 2026-10-05
 */
final class NationalVaccineCatalogueMapper {

    static final String NVC_BASE = "https://nvc-cnv.canada.ca/fhir/v2";
    private static final String EXTENSION_BASE = NVC_BASE + "/StructureDefinition/";
    static final String LINKED_GENERIC_CONCEPT = EXTENSION_BASE + "nvc-linked-generic-concept";
    static final String LINKED_TRADENAME_CONCEPT = EXTENSION_BASE + "nvc-linked-tradename-concept";
    static final String MARKET_AUTHORIZATION_HOLDER = EXTENSION_BASE + "nvc-linked-to-market-authorization-holder";
    static final String DIN = EXTENSION_BASE + "nvc-din";
    static final String CONCEPT_STATUS = EXTENSION_BASE + "nvc-concept-status";
    static final String LOT_CODE_SYSTEM = NVC_BASE + "/CodeSystem/nvc-vaccine-lot-id";
    static final String LOT = EXTENSION_BASE + "nvc-lot";

    private static final String SNOMED_FULLY_SPECIFIED_NAME = "900000000000003001";
    private static final String SNOMED_SYNONYM = "900000000000013009";
    private static final String ACTIVE = "active";
    private static final String LOT_NUMBER_PROPERTY = "lotNumber";
    private static final String EXPIRY_DATE_PROPERTY = "expiryDate";

    /** The catalogue read from one bundle; medications carry their lot numbers. */
    record Catalogue(List<CVCImmunization> immunizations, List<CVCMedication> medications, int lotNumberCount) {
    }

    private NationalVaccineCatalogueMapper() {
    }

    /**
     * Maps an NVC V2 bundle.
     *
     * @throws IllegalArgumentException if the bundle lacks concepts in the {@code Generic} or
     *     {@code Tradename} value set, the lot code system, or any brand linked to a generic, so a
     *     malformed or partial download never replaces the stored catalogue
     */
    static Catalogue map(Bundle bundle) {
        ValueSet generic = null;
        ValueSet tradename = null;
        CodeSystem lots = null;
        for (BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof ValueSet valueSet) {
                String id = valueSet.getIdElement().getIdPart();
                if ("Generic".equals(id)) {
                    generic = valueSet;
                } else if ("Tradename".equals(id)) {
                    tradename = valueSet;
                }
            } else if (resource instanceof CodeSystem codeSystem && LOT_CODE_SYSTEM.equals(codeSystem.getUrl())) {
                lots = codeSystem;
            }
        }
        if (generic == null || tradename == null) {
            throw new IllegalArgumentException("NVC bundle has no Generic or Tradename value set");
        }
        List<ConceptReferenceComponent> brands = concepts(tradename);
        List<ConceptReferenceComponent> generics = concepts(generic);
        if (brands.isEmpty() || generics.isEmpty() || lots == null || lots.getConcept().isEmpty()) {
            throw new IllegalArgumentException("NVC bundle has no generics, brands or lots");
        }
        Set<String> brandCodes = new HashSet<>();
        for (ConceptReferenceComponent concept : brands) {
            brandCodes.add(concept.getCode());
        }
        List<CVCImmunization> immunizations = new ArrayList<>();
        for (ConceptReferenceComponent concept : generics) {
            // The Generic value set also lists some discontinued brands. Storing them twice would
            // make a lookup by SNOMED code return a generic row with no parent for a brand.
            if (!brandCodes.contains(concept.getCode())) {
                immunizations.add(immunization(concept, true));
            }
        }
        // One medication per brand: the prevention screen lists a brand's lots through the single
        // medication found by its SNOMED code, so splitting a brand by DIN would hide lots.
        Map<String, CVCMedication> medicationsByBrand = new LinkedHashMap<>();
        boolean anyBrandLinked = false;
        for (ConceptReferenceComponent concept : brands) {
            CVCImmunization brand = immunization(concept, false);
            brand.setParentConceptId(firstCode(extensionConcept(concept.getExtension(), LINKED_GENERIC_CONCEPT)));
            anyBrandLinked |= brand.getParentConceptId() != null;
            immunizations.add(brand);
            medicationsByBrand.putIfAbsent(concept.getCode(), medication(concept, brand.getDisplayName()));
        }
        if (!anyBrandLinked) {
            throw new IllegalArgumentException("NVC bundle links no brand to a generic");
        }
        int lotNumberCount = addLotNumbers(lots, brands, medicationsByBrand);
        return new Catalogue(immunizations, new ArrayList<>(medicationsByBrand.values()), lotNumberCount);
    }

    private static List<ConceptReferenceComponent> concepts(ValueSet valueSet) {
        List<ConceptReferenceComponent> concepts = new ArrayList<>();
        if (valueSet.hasCompose()) {
            for (ConceptSetComponent include : valueSet.getCompose().getInclude()) {
                for (ConceptReferenceComponent concept : include.getConcept()) {
                    if (concept.hasCode()) {
                        concepts.add(concept);
                    }
                }
            }
        }
        return concepts;
    }

    private static CVCImmunization immunization(ConceptReferenceComponent concept, boolean generic) {
        CVCImmunization immunization = new CVCImmunization();
        immunization.setSnomedConceptId(concept.getCode());
        immunization.setVersionId(0);
        String displayName = withoutMarkup(firstNonBlank(designation(concept, SNOMED_SYNONYM), concept.getDisplay(),
                designation(concept, SNOMED_FULLY_SPECIFIED_NAME)));
        boolean active = ACTIVE.equals(stringValue(concept.getExtension(), CONCEPT_STATUS));
        immunization.setDisplayName(displayName);
        // The picklist name becomes the prevention type a generic is offered and recorded as, so it
        // must be unique. The SNOMED synonym ("[Inf] Influenza quadrivalent vaccine") is, for every
        // active generic and every brand; NVC's public picklist term is not ("Influenza (flu)
        // vaccine" covers eleven generics). A generic's type drops the bracketed abbreviation,
        // because prevention settings store type names between brackets. An inactive generic is
        // kept for lookups by code but is not offered as a prevention type.
        if (generic) {
            immunization.setPicklistName(active ? typeName(displayName) : null);
        } else {
            immunization.setPicklistName(displayName);
        }
        // V2 has no prevalence; active concepts sort above inactive ones in catalogue search.
        immunization.setPrevalence(active ? 1 : 0);
        immunization.setGeneric(generic);
        return immunization;
    }

    private static CVCMedication medication(ConceptReferenceComponent concept, String displayName) {
        CVCMedication medication = new CVCMedication();
        medication.setSnomedCode(concept.getCode());
        medication.setSnomedDisplay(displayName);
        medication.setBrand(true);
        medication.setStatus(stringValue(concept.getExtension(), CONCEPT_STATUS));
        Coding din = firstCoding(extensionConcept(concept.getExtension(), DIN));
        if (din != null) {
            medication.setDin(din.getCode());
            medication.setDinDisplayName(din.getDisplay());
        }
        Coding holder = firstCoding(extensionConcept(concept.getExtension(), MARKET_AUTHORIZATION_HOLDER));
        if (holder != null) {
            // manufacturerId is an int column and the holder's SNOMED code does not fit; nothing
            // reads the id, so only the name is kept.
            medication.setManufacturerDisplay(withoutMarkup(holder.getDisplay()));
        }
        return medication;
    }

    /**
     * Adds each lot to the brands it is linked to, from either side: the lot code system's
     * {@code nvc-linked-tradename-concept} and the brand's own {@code nvc-lot} list. A lot linked to
     * two brands is listed under both; a lot whose brands are not in the Tradename value set is
     * skipped. A lot that only the brand names takes its expiry date from the code system when that
     * lot number is a single concept there, and none when it is several, so it never borrows another
     * product's date; and a brand that already has the lot number from the code system gets no
     * second row.
     */
    private static int addLotNumbers(CodeSystem lots, List<ConceptReferenceComponent> brands,
            Map<String, CVCMedication> medicationsByBrand) {
        Set<String> brandLots = new HashSet<>();
        Map<String, Date> expiryByLotNumber = new HashMap<>();
        Set<String> sharedLotNumbers = new HashSet<>();
        int count = 0;
        for (ConceptDefinitionComponent lot : lots.getConcept()) {
            String lotNumber = firstNonBlank(stringProperty(lot, LOT_NUMBER_PROPERTY), lotNumberFromCode(lot.getCode()));
            if (lotNumber == null) {
                continue;
            }
            Date expiryDate = dateProperty(lot, EXPIRY_DATE_PROPERTY);
            if (expiryByLotNumber.containsKey(lotNumber)) {
                sharedLotNumbers.add(lotNumber);
            }
            expiryByLotNumber.put(lotNumber, expiryDate);
            for (Extension link : lot.getExtension()) {
                if (LINKED_TRADENAME_CONCEPT.equals(link.getUrl())) {
                    count += addLot(medicationsByBrand, brandLots, firstCode(asCodeableConcept(link)), lotNumber, expiryDate);
                }
            }
        }
        for (ConceptReferenceComponent brand : brands) {
            for (Extension lotLink : brand.getExtension()) {
                if (LOT.equals(lotLink.getUrl()) && lotLink.getValue() instanceof Coding coding && coding.hasCode()) {
                    String lotNumber = lotNumberFromCode(coding.getCode());
                    Date expiryDate = sharedLotNumbers.contains(lotNumber) ? null : expiryByLotNumber.get(lotNumber);
                    count += addLot(medicationsByBrand, brandLots, brand.getCode(), lotNumber, expiryDate);
                }
            }
        }
        return count;
    }

    /**
     * Adds a lot to a brand's medication, once per brand and lot number: a lot number repeated for the
     * same brand (with the same or another expiry) is one row.
     */
    private static int addLot(Map<String, CVCMedication> medicationsByBrand, Set<String> brandLots, String brand,
            String lotNumber, Date expiryDate) {
        CVCMedication medication = medicationsByBrand.get(brand);
        if (medication == null || lotNumber == null || !brandLots.add(brand + '\n' + lotNumber)) {
            return 0;
        }
        medication.getLotNumberList().add(new CVCMedicationLotNumber(medication, lotNumber, expiryDate));
        return 1;
    }

    /** "[Inf] Influenza quadrivalent vaccine" becomes "Influenza quadrivalent vaccine". */
    static String withoutAbbreviationTag(String name) {
        if (name != null && name.startsWith("[")) {
            int end = name.indexOf("] ");
            if (end > 0 && end + 2 < name.length()) {
                return name.substring(end + 2);
            }
        }
        return name;
    }

    /**
     * A prevention type name: the name without its bracketed abbreviation and without the brackets
     * and equals sign that prevention settings use to separate names.
     */
    static String typeName(String name) {
        String type = withoutAbbreviationTag(name);
        return type == null ? null : firstNonBlank(type.replaceAll("[\\[\\]=]", " ").replaceAll("\\s+", " ").strip());
    }

    /** Removes the characters that would be markup in a page or an attribute. */
    static String withoutMarkup(String text) {
        return text == null ? null : firstNonBlank(text.replaceAll("[<>\"'&]", " ").replaceAll("\\s+", " ").strip());
    }

    /** Lot codes are the lot number plus an NVC suffix, for example {@code 042D21A_[1]}. */
    private static String lotNumberFromCode(String code) {
        if (code == null) {
            return null;
        }
        int suffix = code.indexOf("_[");
        return suffix > 0 ? code.substring(0, suffix) : code;
    }

    private static String designation(ConceptReferenceComponent concept, String use) {
        for (ConceptReferenceDesignationComponent designation : concept.getDesignation()) {
            Coding designationUse = designation.getUse();
            boolean english = !designation.hasLanguage() || "en".equals(designation.getLanguage());
            if (english && designationUse != null && use.equals(designationUse.getCode())
                    && designation.hasValue() && !designation.getValue().isBlank()) {
                return designation.getValue();
            }
        }
        return null;
    }

    private static CodeableConcept extensionConcept(List<Extension> extensions, String url) {
        for (Extension extension : extensions) {
            if (url.equals(extension.getUrl())) {
                CodeableConcept concept = asCodeableConcept(extension);
                if (concept != null) {
                    return concept;
                }
            }
        }
        return null;
    }

    private static CodeableConcept asCodeableConcept(Extension extension) {
        return extension.getValue() instanceof CodeableConcept concept ? concept : null;
    }

    private static String stringValue(List<Extension> extensions, String url) {
        for (Extension extension : extensions) {
            if (url.equals(extension.getUrl()) && extension.getValue() instanceof PrimitiveType<?> value) {
                return value.getValueAsString();
            }
        }
        return null;
    }

    private static Coding firstCoding(CodeableConcept concept) {
        if (concept == null) {
            return null;
        }
        for (Coding coding : concept.getCoding()) {
            if (coding.hasCode()) {
                return coding;
            }
        }
        return null;
    }

    private static String firstCode(CodeableConcept concept) {
        Coding coding = firstCoding(concept);
        return coding == null ? null : coding.getCode();
    }

    private static String stringProperty(ConceptDefinitionComponent concept, String code) {
        for (ConceptPropertyComponent property : concept.getProperty()) {
            if (code.equals(property.getCode()) && property.getValue() instanceof PrimitiveType<?> value) {
                return value.getValueAsString();
            }
        }
        return null;
    }

    private static Date dateProperty(ConceptDefinitionComponent concept, String code) {
        for (ConceptPropertyComponent property : concept.getProperty()) {
            if (code.equals(property.getCode()) && property.getValue() instanceof DateTimeType value) {
                return value.getValue();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
