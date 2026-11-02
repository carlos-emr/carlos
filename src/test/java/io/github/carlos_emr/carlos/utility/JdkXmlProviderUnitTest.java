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
package io.github.carlos_emr.carlos.utility;

import java.util.Collections;
import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/** Verify JDK XML discovery and absence of standalone Xerces on the application classpath. */
@Tag("unit")
class JdkXmlProviderUnitTest {
    @Test
    void shouldUseJdkFactories_whenOrdinaryXmlConsumersDiscoverProviders() throws Exception {
        assertAll(
                () -> assertThat(DocumentBuilderFactory.newInstance().getClass())
                        .isEqualTo(DocumentBuilderFactory.newDefaultInstance().getClass()),
                () -> assertThat(SAXParserFactory.newInstance().getClass())
                        .isEqualTo(SAXParserFactory.newDefaultInstance().getClass()),
                () -> assertThat(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).getClass())
                        .isEqualTo(SchemaFactory.newDefaultInstance().getClass()),
                () -> assertThat(DatatypeFactory.newInstance().getClass())
                        .isEqualTo(DatatypeFactory.newDefaultInstance().getClass()));
    }

    @Test
    void shouldRetainWoodstoxEvents_whenStandaloneXercesIsRemoved() {
        assertThat(XMLEventFactory.newFactory())
                .isInstanceOf(com.ctc.wstx.stax.WstxEventFactory.class);
    }

    @Test
    void shouldUseJdkDomImplementation_whenDiscoveringLoadSaveSupport() throws Exception {
        var implementation = DOMImplementationRegistry.newInstance().getDOMImplementation("LS 3.0");
        assertThat(implementation).isInstanceOf(DOMImplementationLS.class);
        assertThat(implementation.getClass().getModule().getName()).isEqualTo("java.xml");
    }

    @Test
    void shouldExcludeStandaloneXerces_fromTheApplicationClasspath() throws Exception {
        ClassLoader loader = JdkXmlProviderUnitTest.class.getClassLoader();
        for (String resource : new String[] {"org/apache/xerces/jaxp/SAXParserFactoryImpl.class",
                "org/apache/xerces/jaxp/validation/XMLSchemaFactory.class", "org/apache/xerces/xs/XSModel.class"}) {
            assertThat(Collections.list(loader.getResources(resource))).as(resource).isEmpty();
        }
        assertThat(io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSchemaValidator.newValidator()
                .getClass().getModule().getName()).isEqualTo("java.xml");
    }
}
