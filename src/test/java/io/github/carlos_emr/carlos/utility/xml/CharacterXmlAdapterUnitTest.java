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
package io.github.carlos_emr.carlos.utility.xml;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import io.github.carlos_emr.carlos.webserv.transfer_objects.CalendarScheduleCodePairTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ScheduleTemplateCodeTransfer;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.SchemaOutputResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@Tag("unit")
@Tag("xml")
class CharacterXmlAdapterUnitTest {
    @Test
    void shouldRetainSentinels_whenConvertingCharacters() {
        CharacterXmlAdapter adapter = new CharacterXmlAdapter();
        assertThat(adapter.marshal(null)).isNull();
        assertThat(adapter.unmarshal(null)).isNull();
        assertThat(adapter.marshal('\0')).isEmpty();
        assertThat(adapter.unmarshal("")).isEqualTo('\0');
        for (char value : new char[]{'A', 'é', ' ', '&', '<'}) {
            assertThat(adapter.unmarshal(adapter.marshal(value))).isEqualTo(value);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.unmarshal("AB"));
    }

    @Test
    void shouldPreserveArrayEntries_withNullAndSpecialCharacters() throws Exception {
        var type = io.github.carlos_emr.carlos.webserv.rest.to.model.AllowedAppointmentTypeTransfer.class;
        var bean = new io.github.carlos_emr.carlos.webserv.rest.to.model.AllowedAppointmentTypeTransfer();
        bean.setCodes(new Character[]{'A', null, '\0', 'é', '&'});
        JAXBContext context = JAXBContext.newInstance(type);
        StringWriter written = new StringWriter();
        context.createMarshaller().marshal(new jakarta.xml.bind.JAXBElement<>(new javax.xml.namespace.QName("value"), type, bean), written);
        var factory = io.github.carlos_emr.carlos.utility.XmlUtils.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        var actual = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(written.toString())));
        try (var input = getClass().getResourceAsStream("/xml/jaxb-character-array-baseline.xml")) {
            var expected = factory.newDocumentBuilder().parse(input);
            assertThat(actual.getDocumentElement().isEqualNode(expected.getDocumentElement())).isTrue();
        }
        var read = context.createUnmarshaller().unmarshal(new javax.xml.transform.stream.StreamSource(
                new java.io.StringReader(written.toString())), type).getValue();
        assertThat(read.getCodes()).containsExactly('A', null, '\0', 'é', '&');
    }

    @Test
    void shouldDeclareStringCodes_inSoapSchemas() throws Exception {
        JAXBContext context = JAXBContext.newInstance(ScheduleTemplateCodeTransfer.class, CalendarScheduleCodePairTransfer.class);
        assertThat(context.getClass().getName()).startsWith("org.glassfish.jaxb.");
        List<StringWriter> schemas = new ArrayList<>();
        context.generateSchema(new SchemaOutputResolver() {
            @Override
            public Result createOutput(String namespaceUri, String suggestedFileName) {
                StringWriter writer = new StringWriter();
                schemas.add(writer);
                StreamResult result = new StreamResult(writer);
                result.setSystemId("memory:/" + suggestedFileName);
                return result;
            }
        });
        String schema = schemas.toString();
        assertThat(schema).contains("name=\"code\" type=\"xs:string\"", "name=\"scheduleCode\" type=\"xs:string\"",
                "name=\"date\" type=\"xs:dateTime\"").doesNotContain("unsignedShort");
    }
}
