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

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
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
 * Base class for testing CXF JAX-WS SOAP endpoints at the SOAP message level
 * using CXF's local transport (in-memory, no TCP sockets).
 *
 * <p>This base class spins up a lightweight CXF JAX-WS server and provides
 * typed client proxies via {@link JaxWsProxyFactoryBean}. Tests exercise the
 * full JAX-WS pipeline: SOAP envelope marshalling/unmarshalling, WSDL
 * processing, and interceptor chains — without network overhead.</p>
 *
 * <p><b>Usage Pattern:</b></p>
 * <pre>
 * class MyWsEndpointTest extends CarlosSoapTestBase {
 *
 *     &#64;Override
 *     protected Object getServiceBean() {
 *         return new MyWs();
 *     }
 *
 *     &#64;Override
 *     protected Class&lt;?&gt; getServiceInterface() {
 *         return MyWs.class;
 *     }
 *
 *     &#64;Test
 *     void shouldReturnResult_viaSoap() {
 *         MyWs proxy = createClient(MyWs.class);
 *         String result = proxy.someMethod();
 *         assertThat(result).isEqualTo("expected");
 *     }
 * }
 * </pre>
 *
 * <p><b>Authentication:</b> WS-Security is bypassed by default. A test
 * interceptor injects a mock {@link LoggedInInfo} into the CXF message
 * context, satisfying {@code AbstractWs.getLoggedInInfo()}. The mock is
 * accessible via {@link #mockLoggedInInfo}.</p>
 *
 * @since 2026-03-31
 * @see CarlosUnitTestBase
 * @see CarlosRestTestBase
 */
@Tag("endpoint")
@Tag("soap")
public abstract class CarlosSoapTestBase extends CarlosUnitTestBase {

    private Server server;
    private AutoCloseable mockitoCloseable;

    /**
     * Mock LoggedInInfo that is automatically injected into the CXF message
     * by the test authentication interceptor.
     */
    @Mock
    protected LoggedInInfo mockLoggedInInfo;

    /**
     * The MockHttpServletRequest used by the test interceptor. Tests can
     * configure additional attributes on this object before making requests.
     */
    protected MockHttpServletRequest mockServletRequest;

    /**
     * Returns the SOAP service implementation bean to publish.
     *
     * @return the {@code @WebService} annotated service instance
     */
    protected abstract Object getServiceBean();

    /**
     * Returns the service interface or implementation class for the JAX-WS
     * endpoint. This is used by both the server factory and client proxy factory.
     *
     * @return the {@code @WebService} annotated class
     */
    protected abstract Class<?> getServiceInterface();

    /**
     * Returns the local transport address for the test server. Override to
     * customize (e.g., to avoid address conflicts in parallel tests).
     *
     * @return the local transport address (default: {@code "local://soap-test"})
     */
    protected String getServiceAddress() {
        return "local://soap-test";
    }

    /**
     * Sets up the CXF JAX-WS server with local transport before each test.
     */
    @BeforeEach
    void setUpSoapEndpoint() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        mockServletRequest = new MockHttpServletRequest();
        MockHttpSession mockSession = new MockHttpSession();
        mockServletRequest.setSession(mockSession);

        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        mockServletRequest.setAttribute(key, mockLoggedInInfo);
        mockSession.setAttribute(key, mockLoggedInInfo);

        JaxWsServerFactoryBean sf = new JaxWsServerFactoryBean();
        sf.setAddress(getServiceAddress());
        sf.setServiceBean(getServiceBean());
        sf.setServiceClass(getServiceInterface());
        sf.getInInterceptors().add(new TestSoapAuthenticationInterceptor(mockServletRequest));
        sf.setTransportId(LocalTransportFactory.TRANSPORT_ID);
        server = sf.create();
    }

    /**
     * Tears down the CXF server after each test.
     */
    @AfterEach
    void tearDownSoapEndpoint() throws Exception {
        if (server != null) {
            server.destroy();
        }
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    /**
     * Creates a typed JAX-WS client proxy for the SOAP service.
     * The proxy generates real SOAP XML, sends it through the local
     * transport, and the server processes it through the full JAX-WS pipeline.
     *
     * @param <T> the service type
     * @param serviceClass the {@code @WebService} annotated class
     * @return a typed client proxy
     */
    @SuppressWarnings("unchecked")
    protected <T> T createClient(Class<T> serviceClass) {
        JaxWsProxyFactoryBean pf = new JaxWsProxyFactoryBean();
        pf.setAddress(getServiceAddress());
        pf.setServiceClass(serviceClass);
        pf.setTransportId(LocalTransportFactory.TRANSPORT_ID);
        return (T) pf.create();
    }

    /**
     * CXF Phase.PRE_INVOKE interceptor that injects a MockHttpServletRequest
     * (with LoggedInInfo) into the CXF message, satisfying
     * {@code AbstractWs.getLoggedInInfo()}.
     *
     * <p>This replaces the production {@code AuthenticationInWSS4JInterceptor},
     * allowing tests to bypass WS-Security entirely.</p>
     */
    static class TestSoapAuthenticationInterceptor extends AbstractPhaseInterceptor<Message> {

        private final MockHttpServletRequest mockRequest;

        TestSoapAuthenticationInterceptor(MockHttpServletRequest mockRequest) {
            super(Phase.PRE_INVOKE);
            this.mockRequest = mockRequest;
        }

        @Override
        public void handleMessage(Message message) {
            message.put(AbstractHTTPDestination.HTTP_REQUEST, mockRequest);
        }
    }
}
