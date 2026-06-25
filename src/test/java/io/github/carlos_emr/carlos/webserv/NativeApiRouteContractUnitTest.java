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
package io.github.carlos_emr.carlos.webserv;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import jakarta.jws.WebService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.github.carlos_emr.carlos.webserv.rest.DemographicService;
import io.github.carlos_emr.carlos.webserv.rest.DocumentService;
import io.github.carlos_emr.carlos.webserv.rest.ProviderService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests that lock down the <em>native</em> CARLOS API route shape used by the
 * Cortico/Juno compatibility matrix in {@code docs/api/cortico-carlos-compatibility.md}.
 *
 * <p>These assertions prove native CARLOS behavior only. They deliberately verify that the
 * native surface is reached through service-level routes — SOAP under {@code /<context>/ws/<Service>}
 * and OAuth REST under {@code /<context>/ws/services/...} (the CXF servlet is mounted at
 * {@code /ws/*} in {@code web.xml}) — and that CARLOS exposes <strong>no</strong> literal
 * Cortico/Juno operation-alias routes such as {@code addAppointment.ws} or
 * {@code updateAppointment.ws}. Any such literal-path compatibility is an adapter/proxy
 * responsibility, never a native CARLOS route.</p>
 *
 * <p>Fixtures are synthetic: the test reads the shipped Spring/CXF configuration and the JAX-WS /
 * JAX-RS annotations on the service classes. No PHI, credentials, tokens, or live calls are used.</p>
 *
 * @since 2026-06-25
 */
@DisplayName("Native CARLOS API route-shape contract")
@Tag("unit")
@Tag("webservice")
class NativeApiRouteContractUnitTest {

    /** SOAP operations the matrix maps to native {@code ScheduleService}. */
    private static final String[] SCHEDULE_OPERATIONS = {
            "addAppointment", "updateAppointment", "getDayWorkSchedule",
            "getAppointmentsForProvider", "getAppointmentsForPatient",
            "getAppointment", "getAppointmentTypes"
    };

    /** SOAP operations the matrix maps to native {@code DemographicService}. */
    private static final String[] DEMOGRAPHIC_OPERATIONS = {
            "getDemographic", "getDemographic2",
            "searchDemographicByName", "searchDemographicsByAttributes"
    };

    @Test
    @DisplayName("should publish ScheduleService at the native /ScheduleService SOAP route")
    void shouldPublishScheduleService_atNativeSoapRoute() throws Exception {
        List<String> addresses = soapEndpointAddresses();

        assertThat(addresses)
                .as("native CARLOS SOAP services are published under /ws/<ServiceName>")
                .contains("/ScheduleService", "/DemographicService", "/ProviderService");
    }

    @Test
    @DisplayName("should expose no literal .ws operation-alias SOAP routes (adapter/proxy responsibility)")
    void shouldExposeNoOperationAliasRoutes_whenInspectingSoapConfig() throws Exception {
        List<String> addresses = soapEndpointAddresses();

        // Literal Cortico/Juno operation paths such as addAppointment.ws / updateAppointment.ws are
        // NOT native CARLOS routes; if a client needs them that is a shim/proxy decision.
        assertThat(addresses)
                .as("no SOAP endpoint is a literal operation alias")
                .noneMatch(address -> address.endsWith(".ws"))
                .noneMatch(address -> address.toLowerCase().contains("addappointment"))
                .noneMatch(address -> address.toLowerCase().contains("updateappointment"));
    }

    @Test
    @DisplayName("should declare native Schedule SOAP operations on the @WebService implementor")
    void shouldDeclareScheduleOperations_onWebServiceImplementor() {
        assertWebServiceDeclares(ScheduleWs.class, SCHEDULE_OPERATIONS);
    }

    @Test
    @DisplayName("should declare native Demographic SOAP operations on the @WebService implementor")
    void shouldDeclareDemographicOperations_onWebServiceImplementor() {
        assertWebServiceDeclares(DemographicWs.class, DEMOGRAPHIC_OPERATIONS);
    }

    @Test
    @DisplayName("should declare native provider lookup operations used by get_providers")
    void shouldDeclareProviderLookupOperations_onWebServiceImplementor() {
        // get_providers is "Partial" in the matrix: native CARLOS uses ProviderService.getProviders /
        // getProviders2 (SOAP) or the REST provider routes, never a ScheduleService operation.
        assertWebServiceDeclares(ProviderWs.class, new String[]{"getProviders", "getProviders2"});
    }

    @Test
    @DisplayName("should mount OAuth REST data services at /ws/services")
    void shouldMountOAuthRestServices_atServicesAddress() throws Exception {
        Element restServer = restServicesServer();

        assertThat(restServer.getAttribute("address"))
                .as("OAuth-protected REST data APIs are published under /ws/services")
                .isEqualTo("/services");
        assertThat(serviceBeanClasses(restServer))
                .as("the OAuth REST server hosts the demographics, document and provider data services")
                .contains(DemographicService.class.getName(),
                        DocumentService.class.getName(),
                        ProviderService.class.getName());
    }

    @Test
    @DisplayName("should expose OAuth REST demographics create/update under /demographics")
    void shouldExposeDemographicsCreateAndUpdate_underDemographicsPath() {
        assertThat(DemographicService.class.getAnnotation(Path.class).value())
                .as("native demographics REST contract lives at /ws/services/demographics")
                .isEqualTo("/demographics");

        // submit_patient_data -> POST /ws/services/demographics ; update_patient_data -> PUT same path.
        assertThat(restMethodHasAnnotation(DemographicService.class, "createDemographicData", POST.class)).isTrue();
        assertThat(restMethodHasAnnotation(DemographicService.class, "updateDemographicData", PUT.class)).isTrue();
    }

    @Test
    @DisplayName("should expose OAuth REST document upload under /document/saveDocumentToDemographic")
    void shouldExposeDocumentUpload_underSaveDocumentToDemographicPath() throws Exception {
        assertThat(DocumentService.class.getAnnotation(Path.class).value())
                .as("native document REST contract lives at /ws/services/document")
                .isEqualTo("/document");

        Method save = DocumentService.class.getMethod("saveDocumentToDemographic",
                io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentTo1.class);
        assertThat(save.isAnnotationPresent(POST.class))
                .as("upload_document is a POST")
                .isTrue();
        assertThat(save.getAnnotation(Path.class).value())
                .as("native document upload path is /saveDocumentToDemographic")
                .isEqualTo("/saveDocumentToDemographic");
    }

    @Test
    @DisplayName("should expose native provider lookup REST route for get_providers")
    void shouldExposeProviderLookupRest_forGetProviders() {
        assertThat(ProviderService.class.getAnnotation(Path.class).value())
                .as("native provider REST lookup lives under /ws/services/providerService")
                .isEqualTo("/providerService/");
        assertThat(restMethodHasAnnotation(ProviderService.class, "getProviders", GET.class)).isTrue();
    }

    // --- helpers -----------------------------------------------------------------------------

    private static void assertWebServiceDeclares(Class<?> implementor, String[] operations) {
        assertThat(implementor.isAnnotationPresent(WebService.class))
                .as("%s is a JAX-WS @WebService implementor", implementor.getSimpleName())
                .isTrue();

        List<String> methodNames = new ArrayList<>();
        for (Method method : implementor.getMethods()) {
            methodNames.add(method.getName());
        }
        assertThat(methodNames)
                .as("%s declares the native SOAP operations from the compatibility matrix",
                        implementor.getSimpleName())
                .contains(operations);
    }

    private static boolean restMethodHasAnnotation(Class<?> service, String methodName,
            Class<? extends java.lang.annotation.Annotation> annotation) {
        for (Method method : service.getMethods()) {
            if (method.getName().equals(methodName) && method.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> soapEndpointAddresses() throws Exception {
        Document doc = parseConfig("spring_ws.xml");
        NodeList endpoints = doc.getElementsByTagName("jaxws:endpoint");
        List<String> addresses = new ArrayList<>();
        for (int i = 0; i < endpoints.getLength(); i++) {
            Element endpoint = (Element) endpoints.item(i);
            if (endpoint.hasAttribute("address")) {
                addresses.add(endpoint.getAttribute("address"));
            }
        }
        assertThat(addresses).as("spring_ws.xml declares SOAP endpoints").isNotEmpty();
        return addresses;
    }

    private static Element restServicesServer() throws Exception {
        Document doc = parseConfig("applicationContextREST.xml");
        NodeList servers = doc.getElementsByTagName("jaxrs:server");
        for (int i = 0; i < servers.getLength(); i++) {
            Element server = (Element) servers.item(i);
            if ("restServices".equals(server.getAttribute("id"))) {
                return server;
            }
        }
        throw new AssertionError("restServices jaxrs:server not found in applicationContextREST.xml");
    }

    private static List<String> serviceBeanClasses(Element restServer) {
        List<String> classes = new ArrayList<>();
        NodeList beans = restServer.getElementsByTagName("bean");
        for (int i = 0; i < beans.getLength(); i++) {
            Element bean = (Element) beans.item(i);
            if (bean.hasAttribute("class")) {
                classes.add(bean.getAttribute("class"));
            }
        }
        return classes;
    }

    private static Document parseConfig(String resource) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream in = NativeApiRouteContractUnitTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(in).as("%s is on the classpath", resource).isNotNull();
            return builder.parse(in);
        }
    }
}
