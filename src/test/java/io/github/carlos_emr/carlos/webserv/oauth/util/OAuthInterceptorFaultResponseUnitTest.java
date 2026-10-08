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
package io.github.carlos_emr.carlos.webserv.oauth.util;

import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.transport.local.LocalConduit;
import org.apache.cxf.transport.local.LocalTransportFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.webserv.oauth.OAuth1ExceptionMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What an anonymous {@code /ws/services} caller receives (#4438).
 *
 * <p>Runs the real {@link OAuthInterceptor} in a CXF JAX-RS server on the local transport with
 * {@link OAuth1ExceptionMapper}, the provider applicationContextREST.xml registers on that server. Without
 * it, CXF's {@code JAXRSDefaultFaultOutInterceptor} writes the rejection as an XMLFault whose faultstring is
 * {@code Throwable.toString()}, naming {@code ...OAuth1Exception}: a Java class name that
 * ResponseSanitizationFilter treats as a leaked stack trace, replaced and logged at ERROR for every anonymous
 * call. (The local transport cannot render that XMLFault path, so only the mapped shape is asserted here;
 * the deployed oauth-rest-surfaces check asserts the body over HTTP.)
 */
@DisplayName("OAuthInterceptor rejection body on /ws/services")
@Tag("unit")
@Tag("security")
class OAuthInterceptorFaultResponseUnitTest {

    /** A data resource the interceptor must never let an anonymous call reach. */
    @Path("/probe")
    public static class ProbeService {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String probe() {
            return "reached";
        }
    }

    private final List<Bus> buses = new ArrayList<>();
    private final List<Server> servers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        servers.forEach(Server::destroy);
        buses.forEach(bus -> bus.shutdown(true));
    }

    @Test
    @DisplayName("should answer an anonymous call with a plain 401 reason when the mapper is registered")
    void shouldAnswerPlainReason_whenMapperRegistered() {
        WebClient client = clientFor("local://oauth-fault-mapped", List.of(new OAuth1ExceptionMapper()));

        try (Response response = client.path("/probe").get()) {
            String body = response.readEntity(String.class);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(body).isEqualTo("authentication_required");
            assertThat(body).doesNotContain("Exception").doesNotContain("reached");
        }
    }

    private WebClient clientFor(String address, List<Object> providers) {
        Bus bus = BusFactory.newInstance().createBus();
        buses.add(bus);
        // An HTTP request with no OAuth parameters: the interceptor's anonymous-call branch.
        MockHttpServletRequest anonymous = new MockHttpServletRequest("GET", "/carlos/ws/services/probe");
        anonymous.setPathInfo("/services/probe");

        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setBus(bus);
        sf.setAddress(address);
        sf.setServiceBean(new ProbeService());
        sf.setProviders(providers);
        sf.getInInterceptors().add(new AbstractPhaseInterceptor<Message>(Phase.RECEIVE) {
            @Override
            public void handleMessage(Message message) {
                message.put(AbstractHTTPDestination.HTTP_REQUEST, anonymous);
            }
        });
        sf.getInInterceptors().add(new OAuthInterceptor());
        sf.setTransportId(LocalTransportFactory.TRANSPORT_ID);
        servers.add(sf.create());

        WebClient client = WebClient.create(address);
        WebClient.getConfig(client).getRequestContext().put(LocalConduit.DIRECT_DISPATCH, true);
        return client;
    }
}
