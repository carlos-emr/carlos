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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.HttpClientHTTPConduit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises real HTTP exchanges after removing CXF's optional Apache HTTP 4 adapter.
 * The loopback server is independent of CXF so no local/in-process transport can hide a
 * missing HTTP implementation. No Spring context or database is needed.
 */
@Tag("unit")
@Tag("webservice")
@Timeout(30)
class CxfHttpTransportUnitTest {

    private static final String SOAP_RESPONSE = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <ns:echoResponse xmlns:ns="urn:carlos:transport-test">
                  <return>soap-ok</return>
                </ns:echoResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    private final ConcurrentLinkedQueue<ReceivedRequest> requests = new ConcurrentLinkedQueue<>();
    private HttpServer server;
    private Bus bus;
    private String address;

    /** Minimal SOAP contract; payloads are synthetic transport fixtures. */
    @WebService(targetNamespace = "urn:carlos:transport-test", name = "Echo")
    public interface EchoService {
        /** Returns the transport fixture's response. */
        @WebMethod
        String echo(@WebParam(name = "text") String text);
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        address = "http://127.0.0.1:" + server.getAddress().getPort();
        bus = BusFactory.newInstance().createBus();
    }

    @AfterEach
    void tearDown() {
        if (bus != null) {
            bus.shutdown(true);
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldPostRestBody_withStandardHttpTransport() {
        WebClient client = restClient();
        try (Response response = client.type(MediaType.TEXT_PLAIN).post("transport-écho")) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(String.class)).isEqualTo("rest-ok");
            assertThat(requests).containsExactly(new ReceivedRequest("POST", "/rest", "transport-écho"));
        } finally {
            client.close();
        }
    }

    @Test
    void shouldCompleteAsyncRestCall_withoutApacheHttp4() throws Exception {
        WebClient client = restClient();
        try (Response response = client.async().get().get(10, TimeUnit.SECONDS)) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(String.class)).isEqualTo("rest-ok");
            assertThat(requests).containsExactly(new ReceivedRequest("GET", "/rest", ""));
        } finally {
            client.close();
        }
    }

    @Test
    void shouldInvokeSoapProxy_withStandardHttpTransport() {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setBus(bus);
        factory.setServiceClass(EchoService.class);
        factory.setAddress(address + "/soap");
        EchoService proxy = (EchoService) factory.create();
        org.apache.cxf.endpoint.Client client = ClientProxy.getClient(proxy);
        try {
            configure((HTTPConduit) client.getConduit());
            assertThat(proxy.echo("soap-fixture")).isEqualTo("soap-ok");
            assertThat(requests).hasSize(1);
            ReceivedRequest request = requests.element();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/soap");
            assertThat(request.body()).contains("soap-fixture", "urn:carlos:transport-test");
        } finally {
            client.destroy();
        }
    }

    private WebClient restClient() {
        JAXRSClientFactoryBean factory = new JAXRSClientFactoryBean();
        factory.setBus(bus);
        factory.setAddress(address + "/rest");
        WebClient client = factory.createWebClient();
        configure(WebClient.getConfig(client).getHttpConduit());
        return client;
    }

    private void configure(HTTPConduit conduit) {
        // Keep factory discovery unchanged: this must be the default transport.
        assertThat(conduit).isInstanceOf(HttpClientHTTPConduit.class);
        conduit.getClient().setConnectionTimeout(5000);
        conduit.getClient().setReceiveTimeout(5000);
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            requests.add(new ReceivedRequest(exchange.getRequestMethod(), path,
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            boolean soap = "/soap".equals(path);
            byte[] body = (soap ? SOAP_RESPONSE : "rest-ok").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",
                    soap ? "text/xml; charset=UTF-8" : "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private record ReceivedRequest(String method, String path, String body) { }
}
