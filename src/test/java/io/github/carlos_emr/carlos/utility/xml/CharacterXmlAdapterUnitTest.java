/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
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
        assertThat(schema).contains("name=\"code\" type=\"xs:string\"", "name=\"scheduleCode\" type=\"xs:string\"");
        assertThat(schema).contains("name=\"date\" type=\"xs:dateTime\"");
        assertThat(schema).doesNotContain("unsignedShort");
    }
}
