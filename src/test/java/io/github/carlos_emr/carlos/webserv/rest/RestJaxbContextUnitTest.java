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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalNativeDateDto;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.AllergyResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.PreventionResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AllergyTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PreventionTo1;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer;
import jakarta.xml.bind.JAXBContext;
import jakarta.ws.rs.core.MediaType;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.jaxrs.JAXRSServiceFactoryBean;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Exercises the deployed shared JAXB context and clinical-date JSON contracts (issue #5624). */
@Tag("unit")
@Tag("rest")
class RestJaxbContextUnitTest extends CarlosUnitTestBase {
    private static final String[] JSON_DATES = {
            "{\"precision\":\"UNKNOWN\",\"value\":null,\"intervalStart\":null,\"intervalEnd\":null}",
            "{\"precision\":\"YEAR\",\"value\":\"2020\",\"intervalStart\":\"2020-01-01\",\"intervalEnd\":\"2020-12-31\"}",
            "{\"precision\":\"MONTH\",\"value\":\"2020-02\",\"intervalStart\":\"2020-02-01\",\"intervalEnd\":\"2020-02-29\"}",
            "{\"precision\":\"FULL_DATE\",\"value\":\"2020-02-29\",\"intervalStart\":\"2020-02-29\",\"intervalEnd\":\"2020-02-29\"}"
    };

    /** A per-type fallback must not conceal failure of the deployed provider's shared context. */
    @ParameterizedTest
    @ValueSource(strings = {"org.eclipse.persistence.jaxb.JAXBContextFactory",
            "org.glassfish.jaxb.runtime.v2.JAXBContextFactory"})
    void shouldBuildOneContext_forEveryRegisteredSessionService(String contextFactory) throws Exception {
        Document config = configuration("spring_ws.xml");
        Element server = null;
        var servers = config.getElementsByTagNameNS("http://cxf.apache.org/jaxrs", "server");
        for (int i = 0; i < servers.getLength(); i++) {
            Element candidate = (Element) servers.item(i);
            if ("jaxrServer".equals(candidate.getAttribute("id"))) server = candidate;
        }
        assertThat(server).isNotNull();
        var serviceBeans = server.getElementsByTagNameNS("http://cxf.apache.org/jaxrs", "serviceBeans");
        assertThat(serviceBeans.getLength()).isEqualTo(1);
        var beans = ((Element) serviceBeans.item(0)).getElementsByTagNameNS(
                "http://www.springframework.org/schema/beans", "bean");
        List<Class<?>> services = new ArrayList<>();
        for (int i = 0; i < beans.getLength(); i++) {
            services.add(Class.forName(((Element) beans.item(i)).getAttribute("class"), false,
                    getClass().getClassLoader()));
        }
        assertThat(services).contains(AllergyService.class, DemographicService.class, ProviderService.class);
        var bus = new ExtensionManagerBus();
        var factory = selectedBeans("spring_ws.xml", "jaxb", "strictXmlValidationHandler");
        try {
            var model = new JAXRSServiceFactoryBean();
            model.setBus(bus);
            model.setResourceClasses(services);
            var provider = factory.getBean("jaxb", JAXBElementProvider.class);
            provider.setContextProperties(Map.of(JAXBContext.JAXB_CONTEXT_FACTORY, contextFactory));
            provider.init(model.getClassResourceInfo());
            JAXBContext context = provider.getJAXBContext(AllergyResponse.class, AllergyResponse.class);
            assertThat(context).isNotNull();
            assertThat(context.getClass().getPackageName()).startsWith(
                    contextFactory.substring(0, contextFactory.lastIndexOf('.')));
            // A failed init can silently fall back to per-type contexts. That must fail this guard.
            assertThat(provider.getJAXBContext(DemographicTo1.class, DemographicTo1.class)).isSameAs(context);
            assertThat(provider.getJAXBContext(ProviderTransfer.class, ProviderTransfer.class)).isSameAs(context);
            assertThat(provider.isWriteable(ProviderTransfer.class, ProviderTransfer.class,
                    new java.lang.annotation.Annotation[0], MediaType.APPLICATION_XML_TYPE)).isTrue();
            var providerDto = new ProviderTransfer();
            providerDto.setProviderNo("999998");
            var writer = new StringWriter();
            context.createMarshaller().marshal(providerDto, writer);
            Document providerXml = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().parse(
                    new ByteArrayInputStream(writer.toString().getBytes(StandardCharsets.UTF_8)));
            assertThat(providerXml.getDocumentElement().getNodeName()).isEqualTo("providerTransfer");
            assertThat(providerXml.getElementsByTagName("providerNo").item(0).getTextContent()).isEqualTo("999998");
        } finally {
            factory.destroySingletons();
            bus.shutdown(true);
        }
    }

    /** Both installed JAXB runtimes must round-trip every native precision without inventing dates. */
    @ParameterizedTest
    @ValueSource(strings = {"org.eclipse.persistence.jaxb.JAXBContextFactory",
            "org.glassfish.jaxb.runtime.v2.JAXBContextFactory"})
    void shouldPreservePrecisionInXml_forEveryNativeDateShape(String contextFactory) throws Exception {
        JAXBContext context = JAXBContext.newInstance(new Class<?>[] {AllergyResponse.class, PreventionResponse.class},
                Map.of(JAXBContext.JAXB_CONTEXT_FACTORY, contextFactory));
        ObjectMapper json = new ObjectMapper();
        for (String expected : JSON_DATES) {
            var allergy = new AllergyTo1();
            allergy.setStartDateInfo(json.readValue(expected, CdsTemporalNativeDateDto.class));
            allergy.setEntryDateInfo(CdsTemporalNativeDateDto.from(Date.valueOf("2020-02-29"), null));
            var response = new AllergyResponse();
            response.setAllergies(List.of(allergy));
            var writer = new StringWriter();
            context.createMarshaller().marshal(response, writer);
            byte[] xml = writer.toString().getBytes(StandardCharsets.UTF_8);
            Document document = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml));
            assertThat(document.getElementsByTagName("precision").getLength()).isEqualTo(2);
            assertThat(document.getElementsByTagName("exact").getLength()).isZero();
            var restored = (AllergyResponse) context.createUnmarshaller().unmarshal(
                    XmlUtils.createSecureJaxbSource(new ByteArrayInputStream(xml)));
            assertThat(json.writeValueAsString(restored.getAllergies().getFirst().getStartDateInfo()))
                    .isEqualTo(expected);
            assertThat(json.writeValueAsString(restored.getAllergies().getFirst().getEntryDateInfo()))
                    .isEqualTo(JSON_DATES[3]);

            String expectedPrevention = preventionJson(expected);
            var prevention = new PreventionTo1();
            prevention.setPreventionDateInfo(json.readValue(expectedPrevention, PreventionDateDTO.class));
            var preventions = new PreventionResponse();
            preventions.setPreventions(List.of(prevention));
            writer = new StringWriter();
            context.createMarshaller().marshal(preventions, writer);
            var restoredPreventions = (PreventionResponse) context.createUnmarshaller().unmarshal(
                    XmlUtils.createSecureJaxbSource(new ByteArrayInputStream(
                            writer.toString().getBytes(StandardCharsets.UTF_8))));
            assertThat(json.writeValueAsString(restoredPreventions.getPreventions().getFirst().getPreventionDateInfo()))
                    .isEqualTo(expectedPrevention);
        }
    }

    /** Load the actual mapper beans so JAXB annotation handling cannot silently change JSON. */
    @ParameterizedTest
    @ValueSource(strings = {"spring_ws.xml", "applicationContextREST.xml"})
    void shouldKeepClinicalDateJsonUnchanged_withProductionMapper(String resource) throws Exception {
        String id = "spring_ws.xml".equals(resource) ? "jacksonObjectMapper" : "oauthJacksonObjectMapper";
        var factory = selectedBeans(resource, id);
        try {
            ObjectMapper mapper = factory.getBean(id, ObjectMapper.class);
            if ("applicationContextREST.xml".equals(resource)) {
                assertThat(mapper.getRegisteredModuleIds()).contains("SmartDateModule");
            }
            for (String expected : JSON_DATES) {
                var date = mapper.readValue(expected, CdsTemporalNativeDateDto.class);
                assertThat(mapper.writeValueAsString(date)).isEqualTo(expected);
                var allergy = new AllergyTo1();
                allergy.setStartDateInfo(date);
                allergy.setEntryDateInfo(date);
                assertThat(mapper.writeValueAsString(allergy))
                        .isEqualTo(new ObjectMapper().writeValueAsString(allergy));
                var tree = mapper.readTree(mapper.writeValueAsString(allergy));
                assertThat(tree.get("startDateInfo")).isEqualTo(mapper.readTree(expected));
                assertThat(tree.get("entryDateInfo")).isEqualTo(mapper.readTree(expected));

                String expectedPrevention = preventionJson(expected);
                var preventionDate = mapper.readValue(expectedPrevention, PreventionDateDTO.class);
                assertThat(mapper.writeValueAsString(preventionDate)).isEqualTo(expectedPrevention);
                var prevention = new PreventionTo1();
                prevention.setPreventionDateInfo(preventionDate);
                assertThat(mapper.writeValueAsString(prevention))
                        .isEqualTo(new ObjectMapper().writeValueAsString(prevention));
            }
            assertThatThrownBy(() -> mapper.readValue(JSON_DATES[2].replace("2020-02-29", "2020-02-28"),
                    CdsTemporalNativeDateDto.class)).isInstanceOf(
                            io.github.carlos_emr.carlos.commn.model.CdsTemporalPrecisionException.class);
            var provider = new ProviderTransfer();
            provider.setProviderNo("999998");
            assertThat(mapper.writeValueAsString(provider)).isEqualTo(new ObjectMapper().writeValueAsString(provider));
        } finally {
            factory.destroySingletons();
        }
    }

    private static String preventionJson(String allergyJson) {
        return allergyJson.replace("\"value\":", "\"originalValue\":").replace("\"MONTH\"", "\"YEAR_MONTH\"");
    }

    /** Read service and mapper definitions without initializing the application or its database. */
    private static Document configuration(String resource) throws Exception {
        var parser = XmlUtils.createSecureDocumentBuilderFactory();
        parser.setNamespaceAware(true);
        try (InputStream stream = new ClassPathResource(resource).getInputStream()) {
            return parser.newDocumentBuilder().parse(stream);
        }
    }

    /** Keep the deployed bean definitions while excluding unrelated services and component scans. */
    private static DefaultListableBeanFactory selectedBeans(String resource, String... ids) throws Exception {
        Document document = configuration(resource);
        Element root = document.getDocumentElement();
        Set<String> selected = Set.of(ids);
        for (Node child = root.getFirstChild(); child != null; ) {
            Node next = child.getNextSibling();
            boolean keep = false;
            if (child instanceof Element element && "bean".equals(element.getLocalName())) {
                keep = selected.contains(element.getAttribute("id"))
                        || (selected.contains("oauthJacksonObjectMapper")
                            && "smartDateModule".equals(element.getAttribute("id")));
                if ("org.springframework.beans.factory.config.MethodInvokingFactoryBean"
                        .equals(element.getAttribute("class"))) {
                    var properties = element.getElementsByTagNameNS(
                            "http://www.springframework.org/schema/beans", "property");
                    for (int i = 0; i < properties.getLength(); i++) {
                        var property = (Element) properties.item(i);
                        keep |= "targetObject".equals(property.getAttribute("name"))
                                && selected.contains(property.getAttribute("ref"));
                    }
                }
            }
            if (!keep) root.removeChild(child);
            child = next;
        }
        var factory = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(factory).registerBeanDefinitions(document, new ClassPathResource(resource));
        factory.preInstantiateSingletons();
        return factory;
    }
}
