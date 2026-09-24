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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins {@link NvcBundleParser} to the published NVC v2 bundle shape. The fixture is a slice of
 * the real PHAC {@code Bundle/NVC} (version 1.130): public reference data, no patient data.
 */
@Tag("unit")
@Tag("fast")
@Tag("prevention")
@DisplayName("NvcBundleParser")
class NvcBundleParserUnitTest {

    static final String FIXTURE = "/prevention/nvc/nvc-bundle-sample.json";

    private static final String PEDIATRIC_COVID_GENERIC = "33581000087104";
    private static final String INFLUENZA_TRIVALENT_GENERIC = "7691000087100";
    private static final String COMIRNATY_TRADENAME = "51511000087105";
    private static final String XANAFLU_TRADENAME = "19291000087108";

    private static String fixtureJson;
    private static NvcCatalogue catalogue;

    @BeforeAll
    static void parseFixture() throws Exception {
        fixtureJson = readFixture();
        catalogue = NvcBundleParser.parse(fixtureJson);
    }

    static String readFixture() throws IOException {
        try (InputStream in = NvcBundleParserUnitTest.class.getResourceAsStream(FIXTURE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void shouldUseSynonymAsPicklist_forActiveGenerics() {
        assertThat(catalogue.generics())
                .extracting(NvcCatalogue.Vaccine::snomedConceptId, NvcCatalogue.Vaccine::picklistName,
                        NvcCatalogue.Vaccine::displayName, NvcCatalogue.Vaccine::parentConceptId)
                .containsExactly(
                        tuple(PEDIATRIC_COVID_GENERIC,
                                "[COVID-19] Pediatric mRNA COVID-19 vaccine", "COVID-19 vaccine", null),
                        tuple(INFLUENZA_TRIVALENT_GENERIC,
                                "[Inf] Influenza trivalent vaccine", "Influenza (flu) vaccine", null));
    }

    @Test
    void shouldSkipInactiveConcepts_forGenericsAndTradenames() {
        assertThat(catalogue.generics()).extracting(NvcCatalogue.Vaccine::snomedConceptId)
                .doesNotContain("108729007");
        assertThat(catalogue.tradenames()).extracting(NvcCatalogue.Vaccine::snomedConceptId)
                .doesNotContain("37901000087108");
    }

    @Test
    void shouldLinkTradenameToGeneric_viaLinkedGenericConceptExtension() {
        assertThat(catalogue.tradenames())
                .extracting(NvcCatalogue.Vaccine::snomedConceptId, NvcCatalogue.Vaccine::parentConceptId,
                        NvcCatalogue.Vaccine::picklistName, NvcCatalogue.Vaccine::displayName)
                .containsExactly(
                        tuple(COMIRNATY_TRADENAME, PEDIATRIC_COVID_GENERIC,
                                "COMIRNATY Omicron XBB.1.5",
                                "[COVID-19] COMIRNATY Omicron XBB.1.5 pediatric 3 mcg/0.2 mL"),
                        tuple(XANAFLU_TRADENAME, INFLUENZA_TRIVALENT_GENERIC,
                                "Xanaflu", "[Inf] Xanaflu"));
    }

    @Test
    void shouldBuildProduct_withDinSupplierStatusAndLotsFromLotCodeSystem() {
        NvcCatalogue.Product comirnaty = product(COMIRNATY_TRADENAME);

        assertThat(comirnaty.din()).isEqualTo("02541866");
        assertThat(comirnaty.manufacturer()).isEqualTo("BIONTECH MANUFACTURING GMBH");
        assertThat(comirnaty.status()).isEqualTo("Cancelled Post Market");
        // The fixture republishes HH6775 under a second concept id; it must be stored once.
        assertThat(comirnaty.lots()).containsExactly(
                new NvcCatalogue.Lot("HH6775", LocalDate.of(2024, 10, 31)),
                new NvcCatalogue.Lot("HL2950", LocalDate.of(2025, 2, 28)));
    }

    @Test
    void shouldLeaveOptionalProductFields_whenTradenameHasNoDinOrStatus() {
        NvcCatalogue.Product xanaflu = product(XANAFLU_TRADENAME);

        assertThat(xanaflu.din()).isNull();
        assertThat(xanaflu.status()).isNull();
        assertThat(xanaflu.manufacturer()).isEqualTo("Drug Safety, Abbott Laboratories, Limited");
        assertThat(xanaflu.lots()).isEmpty();
    }

    @Test
    void shouldIgnoreLots_forTradenamesOutsideTheCatalogue() {
        assertThat(catalogue.products()).flatExtracting(NvcCatalogue.Product::lots)
                .extracting(NvcCatalogue.Lot::lotNumber)
                .doesNotContain("042D21A");
    }

    @Test
    void shouldMapEnglishDisplayTerms_forAnatomicalSitesAndRoutes() {
        assertThat(catalogue.anatomicalSites()).containsExactly(
                new NvcCatalogue.CodedValue("1217006009", "Right vastus lateralis muscle"),
                new NvcCatalogue.CodedValue("1217007000", "Left vastus lateralis muscle"));
        assertThat(catalogue.routes()).containsExactly(
                new NvcCatalogue.CodedValue("718329006", "Infiltrate: INFL"),
                new NvcCatalogue.CodedValue("372464004", "Intradermal: ID"));
    }

    @Test
    void shouldReportTradenameVersion_asCatalogueVersion() {
        assertThat(catalogue.version()).isEqualTo("1.130");
    }

    @Test
    void shouldDropDin_whenNotAShortDigitString() throws Exception {
        NvcCatalogue parsed = NvcBundleParser.parse(fixtureJson.replace("\"02541866\"", "\"DIN-UNKNOWN\""));

        assertThat(parsed.products()).filteredOn(p -> COMIRNATY_TRADENAME.equals(p.snomedCode()))
                .extracting(NvcCatalogue.Product::din).containsOnlyNulls();
    }

    @Test
    void shouldRejectBundle_whenGenericValueSetIsMissing() {
        String withoutGeneric = fixtureJson.replace("\"id\": \"Generic\"", "\"id\": \"GenericRenamed\"");

        assertThatThrownBy(() -> NvcBundleParser.parse(withoutGeneric))
                .isInstanceOf(NvcBundleException.class)
                .hasMessageContaining("Generic or Tradename");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not json", "{\"resourceType\":\"Patient\",\"id\":\"x\"}",
            "{\"resourceType\":\"Bundle\",\"type\":\"collection\"}"})
    void shouldRejectInput_whenNotAUsableNvcBundle(String body) {
        assertThatThrownBy(() -> NvcBundleParser.parse(body)).isInstanceOf(NvcBundleException.class);
    }

    private static NvcCatalogue.Product product(String snomedCode) {
        return catalogue.products().stream()
                .filter(p -> snomedCode.equals(p.snomedCode()))
                .findFirst()
                .orElseThrow();
    }
}
