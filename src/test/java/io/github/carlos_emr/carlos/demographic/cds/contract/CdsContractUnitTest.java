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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalSourceService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSchemaValidator;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.NodeList;

@Tag("unit")
@Tag("cds-export")
class CdsContractUnitTest {
    @Test void shouldMatchPinnedWireSections_inTheirActualSchemaOrder() throws Exception {
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (var input = getClass().getResourceAsStream("/cds-import/schema/EMR_Data_Migration_Schema.xsd")) {
            assertThat(input).isNotNull();
            byte[] bytes = input.readAllBytes();
            assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))
                    .isEqualTo(CdsSchemaValidator.MAIN_SHA256);
            var document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
            var path = XPathFactory.newDefaultInstance().newXPath();
            var nodes = (NodeList) path.evaluate(
                    "/*[local-name()='schema']/*[local-name()='element' and @name='PatientRecord']"
                    + "/*[local-name()='complexType']/*[local-name()='sequence']/*[local-name()='element']/@ref",
                    document, XPathConstants.NODESET);
            List<String> actual = new ArrayList<>();
            for (int index = 0; index < nodes.getLength(); index++) actual.add(nodes.item(index).getNodeValue());
            assertThat(CdsCategory.values()).extracting(CdsCategory::wireName).containsExactlyElementsOf(actual);
            assertThat(actual).hasSize(16);
        }
    }

    @Test void shouldMatchImporterClinicalCategories_withoutIncludingHelperUnitsOrReportSubtypes() {
        assertThat(CdsCategory.wireNames()).containsExactlyInAnyOrderElementsOf(CdsClinicalSourceService.CATEGORIES);
        for (String invalid : List.of("ReportsText", "ReportsBinary", "ClinicalNotes_FIELDS", "unknown", "reports")) {
            assertThatThrownBy(() -> CdsCategory.fromWireName(invalid)).hasMessage("CDS_CATEGORY_INVALID");
        }
    }

    @Test void shouldRequireAnExplicitLogicalProfile_withoutTreatingSchemaEqualityAsProfileSelection() {
        assertThat(CdsLogicalProfile.fromIdentifier("omd-5.2")).isEqualTo(CdsLogicalProfile.ONTARIO_MD_5_2);
        assertThat(CdsLogicalProfile.fromIdentifier("pcon-2024-02")).isEqualTo(CdsLogicalProfile.PCON_2024_02);
        for (String invalid : new String[]{null, "", "5.2", "OMD-5.2", "omd-5.2 "}) {
            assertThatThrownBy(() -> CdsLogicalProfile.fromIdentifier(invalid))
                    .hasMessage("CDS_LOGICAL_PROFILE_INVALID");
        }
    }
}
