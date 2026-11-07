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

import java.util.HashSet;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

@Tag("unit")
class CdsHealthCardAuthorityUnitTest {
    @Test void shouldPreserveEveryPinnedWireAuthority_andKeepUnknownSentinelsOutOfIdentityMatching() throws Exception {
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var actual = new HashSet<String>();
        try (var input = getClass().getResourceAsStream("/cds-import/schema/EMR_Data_Migration_Schema_DT.xsd")) {
            var document = factory.newDocumentBuilder().parse(input);
            var types = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
            for (int i = 0; i < types.getLength(); i++) {
                var type = (Element) types.item(i); if (!type.getAttribute("name").equals("healthCardProvinceCode")) continue;
                var values = type.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
                for (int j = 0; j < values.getLength(); j++) actual.add(((Element) values.item(j)).getAttribute("value"));
            }
        }
        assertThat(actual).hasSize(71);
        var expected = new HashSet<>(CdsHealthCardAuthority.PINNED_US);
        CdsHealthCardAuthority.CANADIAN.forEach(code -> expected.add("CA-" + code)); expected.addAll(Set.of("-50", "-70", "-90"));
        assertThat(actual).isEqualTo(expected);
        for (String code : actual) assertThat(CdsHealthCardAuthority.wire(code)).isEqualTo(code);
        for (String sentinel : Set.of("-50", "-70", "-90")) assertThat(CdsHealthCardAuthority.known(sentinel)).isNull();
    }
    @Test void shouldNormalizeOnlyExactCanadianAliases_withTheSameIdentityAndWireMeaning() {
        for (String code : CdsHealthCardAuthority.CANADIAN) {
            assertThat(CdsHealthCardAuthority.known(code)).isEqualTo(code);
            assertThat(CdsHealthCardAuthority.known("CA-" + code)).isEqualTo(code);
            assertThat(CdsHealthCardAuthority.wire(code)).isEqualTo("CA-" + code);
        }
    }
    @Test void shouldRefuseUnknownOrAmbiguousCodes_withoutTrimmingOrGuessingTheJurisdiction() {
        for (String code : new String[]{null, "", "NY", "CA-XX", "US-XX", "on", "ON ", " CA-ON"}) {
            assertThat(CdsHealthCardAuthority.wire(code)).isNull(); assertThat(CdsHealthCardAuthority.known(code)).isNull();
            assertThat(CdsHealthCardAuthority.normalize(code)).isEqualTo(code);
        }
    }
}
