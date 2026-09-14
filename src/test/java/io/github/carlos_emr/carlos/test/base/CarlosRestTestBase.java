/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.test.base;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.util.SmartDateModule;

import jakarta.ws.rs.core.MediaType;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.transport.local.LocalConduit;
import java.util.ArrayList;
import java.util.UUID;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.transport.local.LocalTransportFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

/**
 * Base class for testing CXF JAX-RS REST endpoints at the HTTP level using
 * CXF's local transport (in-memory, no TCP sockets).
 *
 * <p>This base class spins up a lightweight CXF JAX-RS server with the same
 * Jackson JSON provider configuration used in production, and provides a
 * {@link WebClient} for making HTTP requests against the service under test.
 * Tests exercise the full CXF pipeline: path routing, content negotiation,
 * JSON/XML serialization, and interceptor chains.</p>
 *
 * <p><b>Usage Pattern:</b></p>
 * <pre>
 * class MyServiceEndpointTest extends CarlosRestTestBase {
 *     &#64;Mock private SomeManager mockManager;
 *
 *     &#64;Override
 *     protected Object getServiceBean() {
 *         MyService service = new MyService();
 *         injectDependency(service, "someManager", mockManager);
 *         return service;
 *     }
 *
 *     &#64;Test
 *     void shouldReturnData_whenGetEndpointCalled() {
 *         when(mockManager.getData(any(), eq(1))).thenReturn(testData);
 *
 *         Response response = client.path("/mypath").query("id", 1).get();
 *
 *         assertThat(response.getStatus()).isEqualTo(200);
 *     }
 * }
 * </pre>
 *
 * <p><b>Authentication:</b> A test interceptor automatically injects a mock
 * {@link LoggedInInfo} into the CXF message, satisfying
 * {@code AbstractServiceImpl.getLoggedInInfo()} without requiring real sessions
 * or OAuth. The mock is accessible via {@link #mockLoggedInInfo}.</p>
 *
 * <p><b>Mockito lifecycle:</b> This class calls
 * {@code MockitoAnnotations.openMocks(this)} in its {@code @BeforeEach},
 * which initializes {@code @Mock} fields in both this class and subclasses.
 * Subclasses should NOT call {@code openMocks} themselves — doing so would
 * open a second Mockito session for the same fields.</p>
 *
 * @since 2026-03-31
 * @see CarlosUnitTestBase
 * @see CarlosSoapTestBase
 */
@Tag("endpoint")
@Tag("rest")
public abstract class CarlosRestTestBase extends CarlosUnitTestBase {

    private Server server;
    private Bus bus;
    private final String serviceAddress = "local://rest-" + UUID.randomUUID();
    private final List<WebClient> clients = new ArrayList<>();
    private AutoCloseable mockitoCloseable;

    /**
     * Pre-configured CXF WebClient for making HTTP requests to the test server.
     * Defaults to JSON accept and content type.
     */
    protected WebClient client;

    /**
     * Mock LoggedInInfo that is automatically injected into the CXF message
     * by the test authentication interceptor.
     */
    @Mock
    protected LoggedInInfo mockLoggedInInfo;

    /**
     * The MockHttpServletRequest used by the test interceptor. Tests can
     * configure additional headers, parameters, or attributes on this
     * object before making requests.
     */
    protected MockHttpServletRequest mockServletRequest;

    /**
     * Returns the JAX-RS service bean to be published on the test server.
     * Subclasses must create the service instance, inject mock dependencies,
     * and return it.
     *
     * @return the JAX-RS annotated service instance
     */
    protected abstract Object getServiceBean();

    /**
     * Returns the local transport address for the test server. Override to
     * customize (e.g., to avoid address conflicts in parallel tests).
     *
     * @return a local transport address unique to this test instance
     */
    protected String getServiceAddress() {
        return serviceAddress;
    }

    /**
     * Sets up the CXF JAX-RS server with local transport and a pre-configured
     * WebClient before each test.
     */
    @BeforeEach
    void setUpRestEndpoint() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        bus = BusFactory.newInstance().createBus();

        mockServletRequest = new MockHttpServletRequest();
        MockHttpSession mockSession = new MockHttpSession();
        mockServletRequest.setSession(mockSession);

        // Set LoggedInInfo in both session and request attributes.
        // Both locations are needed: AbstractServiceImpl.getLoggedInInfo() reads
        // from session first, but falls back to request attributes when the session
        // value has a null loggedInProvider (which mocks do by default).
        LoggedInInfo.setLoggedInInfoIntoRequest(mockServletRequest, mockLoggedInInfo);
        LoggedInInfo.setLoggedInInfoIntoSession(mockSession, mockLoggedInInfo);

        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setBus(bus);
        sf.setAddress(getServiceAddress());
        sf.setServiceBean(getServiceBean());
        sf.setProviders(createProviders());
        sf.getInInterceptors().add(new TestAuthenticationInterceptor(mockServletRequest));
        sf.setTransportId(LocalTransportFactory.TRANSPORT_ID);
        server = sf.create();

        JAXRSClientFactoryBean factory = new JAXRSClientFactoryBean();
        factory.setBus(bus);
        factory.setAddress(getServiceAddress());
        factory.setProviders(createProviders());
        client = factory.createWebClient().accept(MediaType.APPLICATION_JSON).type(MediaType.APPLICATION_JSON);
        clients.add(client);
        // Mockito static mocks belong to the caller thread. Preserve that thread
        // while still exercising CXF marshalling, routing and interceptors.
        WebClient.getConfig(client).getRequestContext().put(LocalConduit.DIRECT_DISPATCH, true);
    }

    /**
     * Tears down the CXF server after each test.
     */
    @AfterEach
    void tearDownRestEndpoint() throws Exception {
        try {
            for (var client : clients) {
                client.close();
            }
        } finally {
            try {
                if (server != null) {
                    server.destroy();
                }
            } finally {
                try {
                    if (bus != null) {
                        bus.shutdown(true);
                    }
                } finally {
                    if (mockitoCloseable != null) {
                        mockitoCloseable.close();
                    }
                }
            }
        }
    }

    /**
     * Returns a fresh {@link WebClient} copy that is reset to the base address.
     * Use this instead of accessing {@link #client} directly to avoid path
     * accumulation — CXF's {@code WebClient.path()} appends to the current
     * URI rather than replacing it.
     *
     * @return a fresh WebClient copy with JSON accept/content-type
     */
    protected WebClient request() {
        WebClient request = WebClient.fromClient(client, true);
        WebClient.getConfig(request).getRequestContext().put(LocalConduit.DIRECT_DISPATCH, true);
        clients.add(request);
        return request;
    }

    /** Reads the wire JSON, failing on malformed output instead of accepting a status alone. */
    protected com.fasterxml.jackson.databind.JsonNode responseJson(jakarta.ws.rs.core.Response response) {
        try (response) {
            return createTestObjectMapper().readTree(response.readEntity(String.class));
        } catch (java.io.IOException e) {
            throw new AssertionError("Endpoint returned invalid JSON", e);
        }
    }

    /**
     * Creates a Jackson ObjectMapper matching the production configuration
     * in applicationContextREST.xml: combined Jackson + JAXB annotation
     * introspector for proper transfer object serialization.
     *
     * @return ObjectMapper configured for CARLOS REST API
     */
    protected ObjectMapper createTestObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setAnnotationIntrospector(
            new AnnotationIntrospectorPair(
                new JacksonAnnotationIntrospector(),
                new JakartaXmlBindAnnotationIntrospector(TypeFactory.defaultInstance())
            )
        );
        mapper.registerModule(new SmartDateModule());
        return mapper;
    }

    private List<Object> createProviders() {
        JAXBElementProvider<Object> jaxbProvider = new JAXBElementProvider<>();
        jaxbProvider.setProduceMediaTypes(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));
        jaxbProvider.setConsumeMediaTypes(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));
        return List.of(new JacksonJsonProvider(createTestObjectMapper()), jaxbProvider);
    }

    /**
     * CXF Phase.PRE_INVOKE interceptor that injects a MockHttpServletRequest
     * (with LoggedInInfo) into the CXF message, satisfying
     * {@code AbstractServiceImpl.getLoggedInInfo()}.
     */
    static class TestAuthenticationInterceptor extends AbstractPhaseInterceptor<Message> {

        private final MockHttpServletRequest mockRequest;

        TestAuthenticationInterceptor(MockHttpServletRequest mockRequest) {
            super(Phase.PRE_INVOKE);
            this.mockRequest = mockRequest;
        }

        @Override
        public void handleMessage(Message message) {
            message.put(AbstractHTTPDestination.HTTP_REQUEST, mockRequest);
        }
    }
}
