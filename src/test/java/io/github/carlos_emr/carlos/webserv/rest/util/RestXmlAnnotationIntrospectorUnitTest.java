/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.xml.CharacterXmlAdapter;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("rest")
class RestXmlAnnotationIntrospectorUnitTest extends CarlosUnitTestBase {
    public static class OrdinaryAdapter extends XmlAdapter<String, String> {
        @Override public String marshal(String value) { return "wire:" + value; }
        @Override public String unmarshal(String value) { return value.substring("wire:".length()); }
    }

    public static class Payload {
        @XmlJavaTypeAdapter(CharacterXmlAdapter.class)
        public Character character = '\0';
        @XmlJavaTypeAdapter(OrdinaryAdapter.class)
        public String ordinary = "FAKE";
        @XmlElement(name = "renamed")
        public String originalName = "visible";
        @XmlTransient
        public String hidden = "FAKE-private";
    }

    @Test
    void shouldSkipXmlOnlyConversions_withoutDiscardingJsonOrJaxbContracts() throws Exception {
        var mapper = new ObjectMapper();
        mapper.setAnnotationIntrospector(new RestXmlAnnotationIntrospector(mapper.getTypeFactory()));
        var written = mapper.readTree(mapper.writeValueAsString(new Payload()));
        assertThat(written.get("character").asText()).isEqualTo("\0");
        assertThat(written.get("ordinary").asText()).isEqualTo("wire:FAKE");
        assertThat(written.get("renamed").asText()).isEqualTo("visible");
        assertThat(written.has("originalName")).isFalse();
        assertThat(written.has("hidden")).isFalse();
        Payload read = mapper.readValue(written.toString(), Payload.class);
        assertThat(read.character).isEqualTo('\0');
        assertThat(read.ordinary).isEqualTo("FAKE");
    }
}
