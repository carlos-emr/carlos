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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.context.FhirContext;

import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.CVCMedication;
import io.github.carlos_emr.carlos.commn.model.CVCMedicationLotNumber;

/**
 * Maps a trimmed copy of the real National Vaccine Catalogue V2 bundle
 * ({@code prevention/nvc-v2-bundle-sample.json}, cut from the October 2026 download). It holds
 * three generics, four brands (two NIMENRIX brands under one generic, one brand with two DINs,
 * one with none) and four lots, one linked to two brands and one to a brand not in the sample.
 * Public catalogue data only; no network.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("National Vaccine Catalogue V2 bundle mapping")
class NationalVaccineCatalogueMapperUnitTest {

    static final String SAMPLE = "prevention/nvc-v2-bundle-sample.json";

    private static NationalVaccineCatalogueMapper.Catalogue catalogue;

    static String sampleJson() throws IOException {
        try (InputStream in = NationalVaccineCatalogueMapperUnitTest.class.getClassLoader().getResourceAsStream(SAMPLE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeAll
    static void mapSample() throws IOException {
        Bundle bundle = FhirContext.forR4Cached().newJsonParser().parseResource(Bundle.class, sampleJson());
        catalogue = NationalVaccineCatalogueMapper.map(bundle);
    }

    @Test
    @DisplayName("should name each active generic by its SNOMED synonym, which is unique, not the shared public picklist term")
    void shouldMapGenerics_whenGenericValueSetPresent() {
        CVCImmunization menC = immunization("7121000087107");

        assertThat(catalogue.immunizations().stream().filter(CVCImmunization::isGeneric)).hasSize(3);
        assertThat(menC.isGeneric()).isTrue();
        assertThat(menC.getDisplayName()).isEqualTo("[Men-C-ACYW] Meningococcal conjugate A + C + Y + W vaccine");
        assertThat(menC.getPicklistName()).isEqualTo("[Men-C-ACYW] Meningococcal conjugate A + C + Y + W vaccine");
        assertThat(menC.getPrevalence()).isEqualTo(1);
    }

    @Test
    @DisplayName("should keep an inactive generic for lookups but leave it out of the prevention list")
    void shouldLeavePicklistEmpty_whenGenericIsInactive() {
        CVCImmunization varicella = immunization("108729007");

        assertThat(varicella.getDisplayName()).isEqualTo("Varicella virus vaccine");
        assertThat(varicella.getPicklistName()).isNull();
        assertThat(varicella.getPrevalence()).isZero();
    }

    @Test
    @DisplayName("should store a code listed as both a generic and a brand only as the brand")
    void shouldSkipGeneric_whenItsCodeIsAlsoABrand() {
        Bundle bundle = new Bundle();
        bundle.addEntry().setResource(valueSet("Generic", "111", "222"));
        bundle.addEntry().setResource(valueSet("Tradename", "222"));

        NationalVaccineCatalogueMapper.Catalogue mapped = NationalVaccineCatalogueMapper.map(bundle);

        assertThat(mapped.immunizations())
                .extracting(CVCImmunization::getSnomedConceptId, CVCImmunization::isGeneric)
                .containsExactlyInAnyOrder(tuple("111", true), tuple("222", false));
    }

    @Test
    @DisplayName("should link each brand to its generic through nvc-linked-generic-concept")
    void shouldLinkBrandToGeneric_whenLinkedGenericConceptPresent() {
        CVCImmunization nimenrixGsk = immunization("19351000087108");

        assertThat(nimenrixGsk.isGeneric()).isFalse();
        assertThat(nimenrixGsk.getParentConceptId()).isEqualTo("7121000087107");
        assertThat(nimenrixGsk.getDisplayName()).isEqualTo("[Men-C-ACYW] NIMENRIX (GSK)");
        assertThat(nimenrixGsk.getPicklistName()).isEqualTo("[Men-C-ACYW] NIMENRIX (GSK)");
        assertThat(catalogue.immunizations().stream()
                .filter(i -> "7121000087107".equals(i.getParentConceptId()))
                .map(CVCImmunization::getSnomedConceptId))
                .containsExactlyInAnyOrder("19351000087108", "21781000087106");
    }

    @Test
    @DisplayName("should keep one medication per brand with its first DIN, holder and status")
    void shouldCreateOneMedicationPerBrand_whenBrandHasTwoDins() {
        CVCMedication agriflu = medication("22971000087103");

        assertThat(catalogue.medications()).hasSize(4);
        assertThat(agriflu.isBrand()).isTrue();
        assertThat(agriflu.getDin()).isEqualTo("02346850");
        assertThat(agriflu.getManufacturerDisplay()).isEqualTo("SEQIRUS UK LIMITED (supplier)");
        assertThat(agriflu.getStatus()).isEqualTo("active");
        assertThat(medication("19291000087108").getDin()).isNull();
    }

    @Test
    @DisplayName("should list a lot under every brand it is linked to, with its expiry date")
    void shouldAttachLotToBothBrands_whenLotLinksTwoBrands() {
        CVCMedicationLotNumber gskLot = onlyLot(medication("19351000087108"));
        CVCMedicationLotNumber pfizerLot = onlyLot(medication("21781000087106"));

        assertThat(gskLot.getLotNumber()).isEqualTo("HA1461");
        assertThat(pfizerLot.getLotNumber()).isEqualTo("HA1461");
        assertThat(gskLot.getMedication().getSnomedCode()).isEqualTo("19351000087108");
        assertThat(gskLot.getExpiryDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(medication("22971000087103").getLotNumberList())
                .extracting(CVCMedicationLotNumber::getLotNumber)
                .containsExactlyInAnyOrder("151901", "150101");
    }

    @Test
    @DisplayName("should skip a lot whose brand is not in the Tradename value set")
    void shouldSkipLot_whenItsBrandIsNotInTheBundle() {
        assertThat(catalogue.lotNumberCount()).isEqualTo(4);
        assertThat(catalogue.medications().stream().flatMap(m -> m.getLotNumberList().stream()))
                .extracting(CVCMedicationLotNumber::getLotNumber)
                .doesNotContain("042D21A");
    }

    @Test
    @DisplayName("should refuse a bundle without the Generic and Tradename value sets")
    void shouldRefuseBundle_whenValueSetsMissing() {
        Bundle empty = new Bundle();
        empty.setType(Bundle.BundleType.COLLECTION);

        assertThatThrownBy(() -> NationalVaccineCatalogueMapper.map(empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Generic or Tradename");
    }

    private static ValueSet valueSet(String id, String... codes) {
        ValueSet valueSet = new ValueSet();
        valueSet.setId(id);
        ValueSet.ConceptSetComponent include = valueSet.getCompose().addInclude();
        for (String code : codes) {
            include.addConcept().setCode(code).setDisplay("Vaccine " + code);
        }
        return valueSet;
    }

    private static CVCImmunization immunization(String code) {
        return catalogue.immunizations().stream().filter(i -> code.equals(i.getSnomedConceptId()))
                .findFirst().orElseThrow();
    }

    private static CVCMedication medication(String code) {
        List<CVCMedication> matches = catalogue.medications().stream()
                .filter(m -> code.equals(m.getSnomedCode())).toList();
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }

    private static CVCMedicationLotNumber onlyLot(CVCMedication medication) {
        assertThat(medication.getLotNumberList()).hasSize(1);
        return medication.getLotNumberList().iterator().next();
    }
}
