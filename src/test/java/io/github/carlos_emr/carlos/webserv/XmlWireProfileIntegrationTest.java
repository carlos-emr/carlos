/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.utility.xml.XmlWireProfile;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AllowedAppointmentTypeTransfer;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SearchConfigTo1;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ScheduleTemplateCodeTransfer;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.annotation.*;
import java.util.List;
import java.util.Map;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Real CXF reader/writer dispatch, including no-header requests immediately after a profiled request. */
@Tag("integration")
@Tag("xml")
class XmlWireProfileIntegrationTest {
    private static final String ADDRESS = "local://xml-wire-profiles";
    private static Bus bus;
    private static Server server;

    @XmlRootElement(name = "value")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Payload {
        public Demographic patient;
        public Integer codePoint;
        @jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter(io.github.carlos_emr.carlos.utility.xml.DateXmlAdapter.class)
        @XmlSchemaType(name = "dateTime")
        public java.util.Date legacyDate;
        public ScheduleTemplateCodeTransfer scalar;
        public AllowedAppointmentTypeTransfer array;
        public SearchConfigTo1 map;
    }

    @Path("/xml")
    @Produces("application/xml")
    public static class Resource {
        @GET
        public Payload get() {
            Payload payload = new Payload();
            payload.patient = new Demographic();
            payload.patient.setYearOfBirth("1980");
            payload.patient.setMonthOfBirth("07");
            payload.patient.setDateOfBirth("15");
            payload.scalar = new ScheduleTemplateCodeTransfer();
            payload.scalar.setCode('A');
            payload.codePoint = (int) payload.scalar.getCode();
            payload.array = new AllowedAppointmentTypeTransfer();
            payload.array.setCodes(new Character[]{'A', '0', '7', '\0', null, 'é'});
            payload.map = new SearchConfigTo1();
            payload.map.setAppointmentCodeDurations(Map.of('A', 3));
            return payload;
        }

        @POST
        @Consumes("application/xml")
        public Payload echo(Payload payload) {
            payload.codePoint = payload.scalar == null || payload.scalar.getCode() == null
                    ? null : (int) payload.scalar.getCode();
            return payload;
        }
    }

    @BeforeAll
    static void start() {
        bus = BusFactory.newInstance().createBus();
        bus.getInInterceptors().add(new XmlProfileInInterceptor());
        bus.getOutInterceptors().add(new XmlProfileOutInterceptor());
        bus.getOutFaultInterceptors().add(new XmlProfileOutInterceptor());
        var provider = new io.github.carlos_emr.carlos.utility.xml.CompatibilityJaxbProvider();
        var factory = new JAXRSServerFactoryBean();
        factory.setBus(bus);
        factory.setAddress(ADDRESS);
        factory.setResourceClasses(Resource.class);
        factory.setProviders(List.of(provider));
        server = factory.create();
    }

    @AfterAll
    static void stop() {
        if (server != null) server.destroy();
        if (bus != null) bus.shutdown(true);
    }

    private record Result(int status, String body, String profile, String vary) {}

    private static Result call(String profile, String input) {
        var factory = new JAXRSClientFactoryBean();
        factory.setBus(bus);
        factory.setAddress(ADDRESS);
        var client = factory.createWebClient();
        try {
            client.path("/xml").accept("application/xml");
            if (profile != null) client.header(XmlWireProfile.HEADER, profile);
            try (Response response = input == null ? client.get() : client.type("application/xml").post(input)) {
                return new Result(response.getStatus(), response.readEntity(String.class),
                        response.getHeaderString(XmlWireProfile.HEADER), response.getHeaderString("Vary"));
            }
        } finally {
            client.close();
        }
    }

    @Test
    void shouldKeepPublishedDefault_afterExplicitMoxyBirthday() {
        Result legacy = call("moxy-date-only-birthday", null);
        assertThat(legacy.status).isEqualTo(200);
        assertThat(legacy.body).contains("<birthDay>1980-07-15</birthDay>", "<code>A</code>");
        assertThat(legacy.profile).isEqualTo("moxy-date-only-birthday");
        assertThat(legacy.vary).contains(XmlWireProfile.HEADER);
        Result normal = call(null, null);
        assertThat(normal.status).isEqualTo(200);
        assertThat(normal.body).contains("<birthDay>1980-07-15T00:00:00", "<code>A</code>");
        assertThat(normal.profile).isEqualTo("published");
    }

    @Test
    void shouldPreserveNumericCharacterIdentity_forScalarsArraysAndMaps() {
        Result legacy = call("glassfish-numeric-characters", null);
        assertThat(legacy.status).isEqualTo(200);
        assertThat(legacy.body).contains("<code>65</code>", "<codes>65</codes>", "<codes>48</codes>",
                "<codes>55</codes>", "<codes>0</codes>", "<codes>233</codes>", "<key>65</key>");
        Result echoed = call("glassfish-numeric-characters", legacy.body);
        assertThat(echoed.status).isEqualTo(200);
        assertThat(echoed.body).isEqualTo(legacy.body);
        Result normal = call(null, null);
        assertThat(normal.body).contains("<code>A</code>", "<codes>0</codes>", "<codes>7</codes>");
    }

    @Test
    void shouldDisambiguateSingleDigits_usingOnlyTheRequestedProfile() {
        for (String code : List.of("0", "7")) {
            String xml = "<value><scalar><code>" + code + "</code></scalar></value>";
            Result numeric = call("glassfish-numeric-characters", xml);
            assertThat(numeric.status).isEqualTo(200);
            assertThat(numeric.body).contains("<code>" + code + "</code>", "<codePoint>" + Integer.parseInt(code) + "</codePoint>");
            Result literal = call("published", xml);
            assertThat(literal.status).isEqualTo(200);
            assertThat(literal.body).contains("<code>" + code + "</code>", "<codePoint>" + (int) code.charAt(0) + "</codePoint>");
        }
    }

    @Test
    void shouldRejectMalformedCharacters_withoutSilentlyNullingTheirValues() {
        for (String code : List.of("65536", "-1", "A", "", " 65 ")) {
            Result invalid = call("glassfish-numeric-characters", "<value><scalar><code>" + code + "</code></scalar></value>");
            assertThat(invalid.status).as("numeric code %s", code).isEqualTo(400);
        }
        Result literal = call(null, "<value><map><appointmentCodeDurations><entry><key>65</key><value>1</value>"
                + "</entry></appointmentCodeDurations></map></value>");
        assertThat(literal.status).isEqualTo(400);
    }

    /** Represents the unchanged Glassfish Character reader, without CARLOS package adapters. */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OldScalar {
        public int bookinglimit;
        public Character code;
        public String color;
        public String confirm;
        public String description;
        public String duration;
        public Integer id;
    }

    @Test
    void shouldRemainReadableByTheOldNumericClient_withoutItsOwnCompatibilityAdapter() throws Exception {
        var context = org.glassfish.jaxb.runtime.v2.ContextFactory.createContext(new Class<?>[]{OldScalar.class}, Map.of());
        Result numeric = call("glassfish-numeric-characters", null);
        var document = io.github.carlos_emr.carlos.utility.XmlUtils.createSecureDocumentBuilderFactory()
                .newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(numeric.body)));
        var scalar = document.getElementsByTagName("scalar").item(0);
        document.renameNode(scalar, null, "value");
        var reader = context.createUnmarshaller();
        reader.setEventHandler(event -> false);
        assertThat(reader.unmarshal(new javax.xml.transform.dom.DOMSource(scalar), OldScalar.class).getValue().code)
                .isEqualTo('A');
        for (String text : List.of("0", "7", "48", "55", "233", "65535")) {
            String xml = "<value><scalar><code>" + text + "</code></scalar></value>";
            Result echoed = call("glassfish-numeric-characters", xml);
            assertThat(echoed.status).isEqualTo(200);
            assertThat(echoed.body).contains("<codePoint>" + Integer.parseInt(text) + "</codePoint>");
        }
    }

    @Test
    void shouldKeepProfilesIsolated_acrossConcurrentAndSubsequentRequests() throws Exception {
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(3)) {
            var results = pool.invokeAll(List.<java.util.concurrent.Callable<Result>>of(
                    () -> call("glassfish-numeric-characters", null),
                    () -> call("moxy-date-only-birthday", null),
                    () -> call(null, null)));
            assertThat(results.get(0).get().body).contains("<code>65</code>");
            assertThat(results.get(1).get().body).contains("<birthDay>1980-07-15</birthDay>", "<code>A</code>");
            assertThat(results.get(2).get().body).contains("<birthDay>1980-07-15T", "<code>A</code>");
        }
        assertThat(call(null, null).profile).isEqualTo("published");
    }

    @Test
    void shouldRetainBlankLegacyRestDates_withoutRelaxingCharacterValidation() {
        Result response = call(null, "<value><legacyDate>   </legacyDate></value>");
        assertThat(response.status).isEqualTo(200);
        assertThat(response.body).doesNotContain("<legacyDate>");
        Result invalid = call(null, "<value><legacyDate/><scalar><code>65</code></scalar></value>");
        assertThat(invalid.status).isEqualTo(400);
    }

    @Test
    void shouldRejectUnknownOrCombinedProfiles_withoutEchoingRawInput() {
        for (String name : List.of("unknown-patient-name", "", "published,moxy-date-only-birthday")) {
            Result invalid = call(name, null);
            assertThat(invalid.status).isEqualTo(400);
            assertThat(invalid.body).doesNotContain("unknown-patient-name");
        }
    }
}
