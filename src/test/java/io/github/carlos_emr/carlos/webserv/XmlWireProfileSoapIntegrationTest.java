/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.utility.xml.StrictXmlValidationHandler;
import io.github.carlos_emr.carlos.utility.xml.XmlWireProfile;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ScheduleTemplateCodeTransfer;
import jakarta.jws.WebMethod;
import jakarta.jws.WebService;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Unchanged numeric/literal client XML enters the actual SOAP JAXB binding, without a client-side adapter. */
@Tag("integration")
@Tag("xml")
class XmlWireProfileSoapIntegrationTest {
    private static final String ADDRESS = "local://xml-profile-soap";
    private static Bus bus;
    private static Server server;

    @WebService(targetNamespace = "urn:test:xmlprofiles")
    public static class EchoService {
        @WebMethod
        public io.github.carlos_emr.carlos.webserv.transfer_objects.DrugTransfer[] drugs() {
            var drug = new io.github.carlos_emr.carlos.webserv.transfer_objects.DrugTransfer();
            drug.seteTreatmentType("é<&"); drug.setUnitName("mg");
            return new io.github.carlos_emr.carlos.webserv.transfer_objects.DrugTransfer[]{drug, drug};
        }
        @WebMethod
        public io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentExtTo appointment() {
            var row = new io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentExtTo();
            row.setAppointmentNo(7); row.setDemographicNo(42); row.setLastName("Synthetic"); row.sethPhoneExt("é<&");
            return row;
        }
        @WebMethod
        public ScheduleTemplateCodeTransfer illegalCode() {
            var result = new ScheduleTemplateCodeTransfer(); result.setCode((char) 1); return result;
        }
        @WebMethod
        public ScheduleTemplateCodeTransfer echo(ScheduleTemplateCodeTransfer arg0) {
            return arg0;
        }
    }

    @BeforeAll
    static void start() {
        bus = BusFactory.newInstance().createBus();
        bus.setProperty("jaxb-reader-validation-event-handler", new StrictXmlValidationHandler());
        bus.getInInterceptors().add(new XmlProfileInInterceptor());
        bus.getOutInterceptors().add(new XmlProfileOutInterceptor());
        bus.getOutInterceptors().add(new LegacyDrugOrderOutInterceptor());
        bus.getOutFaultInterceptors().add(new XmlProfileOutInterceptor());
        var factory = new JaxWsServerFactoryBean();
        factory.setBus(bus);
        factory.setAddress(ADDRESS);
        factory.setServiceBean(new EchoService());
        server = factory.create();
    }

    @AfterAll
    static void stop() {
        if (server != null) server.destroy();
        if (bus != null) bus.shutdown(true);
    }

    private record Result(int status, String body, String profile) {}

    private static Result call(String profile, String code) throws Exception {
        return invoke(profile, "<p:echo xmlns:p=\"urn:test:xmlprofiles\"><arg0><code>" + code
                + "</code></arg0></p:echo>");
    }

    private static Result invoke(String profile, String body) throws Exception {
        Bus previous = BusFactory.getThreadDefaultBus(false);
        jakarta.xml.ws.Dispatch<javax.xml.transform.Source> dispatch;
        try {
            BusFactory.setThreadDefaultBus(bus);
            var service = jakarta.xml.ws.Service.create(new javax.xml.namespace.QName("urn:test:xmlprofiles", "Echo"));
            var port = new javax.xml.namespace.QName("urn:test:xmlprofiles", "Port");
            service.addPort(port, jakarta.xml.ws.soap.SOAPBinding.SOAP11HTTP_BINDING, ADDRESS);
            dispatch = service.createDispatch(port, javax.xml.transform.Source.class, jakarta.xml.ws.Service.Mode.MESSAGE);
        } finally {
            BusFactory.setThreadDefaultBus(previous);
        }
        try {
            if (profile != null) dispatch.getRequestContext().put(org.apache.cxf.message.Message.PROTOCOL_HEADERS,
                    java.util.Map.of(XmlWireProfile.HEADER, java.util.List.of(profile)));
            String input = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                    + body + "</soap:Body></soap:Envelope>";
            javax.xml.transform.Source output;
            int status;
            try {
                output = dispatch.invoke(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(input)));
                status = 200; // The SOAP local transport has no HTTP status on a successful dispatch.
            } catch (jakarta.xml.ws.soap.SOAPFaultException fault) {
                output = new javax.xml.transform.dom.DOMSource(fault.getFault());
                Object responseCode = dispatch.getResponseContext().get(jakarta.xml.ws.handler.MessageContext.HTTP_RESPONSE_CODE);
                status = responseCode instanceof Number number ? number.intValue() : 500;
            }
            var writer = new java.io.StringWriter();
            var transformer = javax.xml.transform.TransformerFactory.newDefaultInstance().newTransformer();
            transformer.transform(output, new javax.xml.transform.stream.StreamResult(writer));
            @SuppressWarnings("unchecked")
            var headers = (java.util.Map<String, java.util.List<String>>) dispatch.getResponseContext()
                    .get(jakarta.xml.ws.handler.MessageContext.HTTP_RESPONSE_HEADERS);
            String selected = headers == null ? null : headers.entrySet().stream()
                    .filter(entry -> XmlWireProfile.HEADER.equalsIgnoreCase(entry.getKey()))
                    .map(entry -> entry.getValue().getFirst()).findFirst().orElse(null);
            return new Result(status, writer.toString(), selected);
        } finally {
            ((AutoCloseable) dispatch).close();
        }
    }

    @Test
    void shouldApplyMoxySequence_onlyToTheExplicitSoapProfile() throws Exception {
        String operation = "<p:appointment xmlns:p=\"urn:test:xmlprofiles\"/>";
        Result legacy = invoke("moxy-legacy", operation);
        assertThat(legacy.status).isEqualTo(200);
        assertThat(legacy.body.indexOf("<hPhoneExt>")).isLessThan(legacy.body.indexOf("<lastName>"));
        Result normal = invoke(null, operation);
        assertThat(normal.status).isEqualTo(200);
        assertThat(normal.body.indexOf("<hPhoneExt>")).isGreaterThan(normal.body.indexOf("<lastName>"));
    }

    @Test
    void shouldPreserveNumericSoapCodes_withAnExplicitProfile() throws Exception {
        Result response = call("glassfish-numeric-characters", "65");
        assertThat(response.status).isEqualTo(200);
        assertThat(response.body).contains("<code>65</code>");
        assertThat(response.profile).isEqualTo("glassfish-numeric-characters");
        Result published = call(null, "A");
        assertThat(published.status).isEqualTo(200);
        assertThat(published.body).contains("<code>A</code>");
    }

    @Test
    void shouldPreserveBulkDrugSequence_onTheActualSoapWriter() throws Exception {
        String request = "<p:drugs xmlns:p=\"urn:test:xmlprofiles\"/>";
        Result legacy = invoke("glassfish-legacy", request);
        assertThat(legacy.status).isEqualTo(200);
        assertThat(legacy.profile).isEqualTo("glassfish-legacy");
        for (String drug : legacy.body.split("<return>")) {
            if (!drug.contains("<eTreatmentType>")) continue;
            assertThat(drug.indexOf("<eTreatmentType>")).isGreaterThan(drug.indexOf("<unitName>"));
        }
        assertThat(legacy.body).contains("é&lt;&amp;");
        Result published = invoke(null, request);
        assertThat(published.status).isEqualTo(200);
        assertThat(published.body.indexOf("<eTreatmentType>")).isLessThan(published.body.indexOf("<unitName>"));
    }

    @Test
    void shouldRejectNumericTextUnderTheLiteralContract_withoutSilentlyReturningNull() throws Exception {
        Result response = call(null, "65");
        assertThat(response.status).isGreaterThanOrEqualTo(400);
        assertThat(response.body).contains("Fault");
    }

    @Test
    void shouldRejectUnknownSoapProfiles_beforeBindingTheBody() throws Exception {
        Result response = call("unknown-client-name", "65");
        // SOAP client faults may use HTTP 400 or the SOAP 1.1 default 500. The fault is the contract.
        assertThat(response.status).isBetween(400, 599);
        assertThat(response.body).contains("Fault", "Client").doesNotContain("unknown-client-name");
    }
    @Test
    void shouldFaultUnrepresentablePublishedOutput_andRetainExplicitNumericCodeUnits() throws Exception {
        String operation = "<p:illegalCode xmlns:p=\"urn:test:xmlprofiles\"/>";
        Result published = invoke(null, operation);
        assertThat(published.status).isGreaterThanOrEqualTo(400);
        assertThat(published.body).contains("Fault");
        Result numeric = invoke("glassfish-numeric-characters", operation);
        assertThat(numeric.status).isEqualTo(200);
        assertThat(numeric.body).contains("<code>1</code>");
        Result surrogate = call("glassfish-numeric-characters", "55296");
        assertThat(surrogate.status).isEqualTo(200);
        assertThat(surrogate.body).contains("<code>55296</code>");
    }
}
