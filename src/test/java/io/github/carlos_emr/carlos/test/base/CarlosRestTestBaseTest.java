/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.test.base;

import java.sql.Date;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.AbstractServiceImpl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Exercises the harness itself, including failures that must not become false passes. */
class CarlosRestTestBaseTest extends CarlosRestTestBase {
    @Mock private ProbeDependency dependency;
    private final String identity = UUID.randomUUID().toString();

    @Override protected Object getServiceBean() {
        registerMock(ProbeDependency.class, dependency);
        return new ProbeService(identity);
    }

    @Test
    void shouldInvokeStaticMockOnCallerThread_throughCxf() {
        Thread caller = Thread.currentThread();
        when(dependency.value()).thenAnswer(invocation -> {
            assertThat(Thread.currentThread()).isSameAs(caller);
            return "wire-marker";
        });
        try (Response response = request().path("/probe/dependency").get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(String.class)).contains("wire-marker");
        }
    }

    @Test
    void shouldInjectAuthenticatedRequest_throughCxf() {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        try (Response response = request().path("/probe/auth").get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(String.class)).contains("999998");
        }
    }

    @Test
    void shouldRoundTripJson_withProductionDateAndAnnotationRules() throws Exception {
        Payload payload = new Payload();
        payload.label = "synthetic-label";
        payload.date = new java.util.Date(Date.valueOf("2000-01-02").getTime());
        try (Response response = request().path("/probe/echo").post(payload)) {
            assertThat(response.getStatus()).isEqualTo(200);
            JsonNode json = createTestObjectMapper().readTree(response.readEntity(String.class));
            assertThat(json.path("displayName").asText()).isEqualTo("synthetic-label");
            assertThat(json.path("date").asText()).isEqualTo("2000-01-02");
            assertThat(json.has("display_name")).isFalse();
        }
    }

    @Test
    void shouldRoundTripXml_withJaxbProvider() {
        Payload payload = new Payload();
        payload.label = "xml-marker";
        try (Response response = request().path("/probe/echo")
                .type(MediaType.APPLICATION_XML).replaceHeader("Accept", MediaType.APPLICATION_XML).post(payload)) {
            assertThat(response.getStatus()).isEqualTo(200);
            String xml = response.readEntity(String.class);
            assertThat(xml).contains("<display_name>xml-marker</display_name>");
        }
    }

    @Test
    void shouldPreserveFailures_forRoutingAndApplicationErrors() {
        try (Response missing = request().path("/absent").get();
             Response method = request().path("/probe/echo").get();
             Response rejected = request().path("/probe/rejected").get()) {
            assertThat(missing.getStatus()).isEqualTo(404);
            assertThat(method.getStatus()).isEqualTo(405);
            assertThat(rejected.getStatus()).isEqualTo(400);
        }
    }

    @Test
    void shouldKeepTwoInstancesIsolated_andReleaseOnlyTheirOwnResources() throws Exception {
        CarlosRestTestBaseTest other = new CarlosRestTestBaseTest();
        try {
            other.setUpRestEndpoint();
            assertThat(other.getServiceAddress()).isNotEqualTo(getServiceAddress());
            try (Response first = request().path("/probe/instance").get();
                 Response second = other.request().path("/probe/instance").get()) {
                assertThat(first.readEntity(String.class)).contains(identity);
                assertThat(second.readEntity(String.class)).contains(other.identity);
            }
        } finally {
            other.tearDownRestEndpoint();
        }
        // The other bus is gone; this bus and a fresh request still work.
        try (Response response = request().path("/probe/instance").get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(String.class)).contains(identity);
        }
    }

    public interface ProbeDependency { String value(); }

    @XmlRootElement(name = "payload")
    public static class Payload {
        @XmlElement(name = "display_name") @JsonProperty("displayName") public String label;
        public java.util.Date date;
    }

    @Path("/probe")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public static class ProbeService extends AbstractServiceImpl {
        private final String identity;
        public ProbeService(String identity) { this.identity = identity; }
        @GET @Path("/dependency") public String dependency() { return SpringUtils.getBean(ProbeDependency.class).value(); }
        @GET @Path("/auth") public String auth() { return getLoggedInInfo().getLoggedInProviderNo(); }
        @GET @Path("/instance") public String instance() { return identity; }
        @GET @Path("/rejected") public String rejected() { throw new BadRequestException("rejected fixture"); }
        @POST @Path("/echo") @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
        public Payload echo(Payload payload) { return payload; }
    }
}
