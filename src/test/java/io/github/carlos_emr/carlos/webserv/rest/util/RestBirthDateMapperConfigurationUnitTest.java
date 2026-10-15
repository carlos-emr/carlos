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
package io.github.carlos_emr.carlos.webserv.rest.util;

import java.io.InputStream;
import java.util.GregorianCalendar;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the actual mapper definitions on both deployed REST surfaces. */
@Tag("unit")
@Tag("rest")
class RestBirthDateMapperConfigurationUnitTest extends CarlosUnitTestBase {

    // Each surface has its own mapper/provider ids: applicationContextREST.xml is loaded into the
    // same root context as spring_ws.xml, so it cannot reuse spring_ws.xml's ids (issue #3446).
    @ParameterizedTest
    @CsvSource({
            "spring_ws.xml,jacksonObjectMapper,jsonProvider,1980-06-15",
            "applicationContextREST.xml,oauthJacksonObjectMapper,oauthJsonProvider,1980-06-15",
            "spring_ws.xml,jacksonObjectMapper,jsonProvider,1970-01-01",
            "applicationContextREST.xml,oauthJacksonObjectMapper,oauthJsonProvider,1970-01-01",
            "spring_ws.xml,jacksonObjectMapper,jsonProvider,NULL",
            "applicationContextREST.xml,oauthJacksonObjectMapper,oauthJsonProvider,NULL"
    })
    void shouldHonorDateOnlyBirthDate_whenUsingProductionMapper(String resourceName, String mapperId,
            String providerId, String date) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourceName);
        var parser = XmlUtils.createSecureDocumentBuilderFactory();
        parser.setNamespaceAware(true);
        Document document;
        try (InputStream stream = resource.getInputStream()) {
            document = parser.newDocumentBuilder().parse(stream);
        }
        // Load the real mapper/provider graph, without starting unrelated services.
        // Copying the test-base mapper here would hide drift in spring_ws.xml.
        Element root = document.getDocumentElement();
        Set<String> selectedBeans = Set.of(mapperId, providerId);
        for (Node child = root.getFirstChild(); child != null; ) {
            Node next = child.getNextSibling();
            if (!(child instanceof Element element && "bean".equals(element.getLocalName())
                    && selectedBeans.contains(element.getAttribute("id")))) {
                root.removeChild(child);
            }
            child = next;
        }
        var factory = new DefaultListableBeanFactory();
        try {
            new XmlBeanDefinitionReader(factory).registerBeanDefinitions(document, resource);
            JacksonJsonProvider provider = factory.getBean(providerId, JacksonJsonProvider.class);
            ObjectMapper mapper = provider.locateMapper(DemographicTo1.class, MediaType.APPLICATION_JSON_TYPE);
            DemographicTo1 dto = new DemographicTo1();
            if (!"NULL".equals(date)) {
                String[] parts = date.split("-");
                var calendar = new GregorianCalendar();
                calendar.clear();
                calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                        Integer.parseInt(parts[2]));
                dto.setDateOfBirth(calendar.getTime());
            }
            JsonNode actual = mapper.readTree(mapper.writeValueAsString(dto)).get("dateOfBirth");
            if ("NULL".equals(date)) {
                assertThat(actual.isNull()).isTrue();
            } else {
                assertThat(actual.isTextual()).as(resourceName + " DOB must be a JSON string").isTrue();
                assertThat(actual.textValue()).isEqualTo(date);
            }
        } finally {
            factory.destroySingletons();
        }
    }
}
