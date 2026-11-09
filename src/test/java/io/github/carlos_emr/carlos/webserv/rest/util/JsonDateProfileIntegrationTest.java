/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Tag("rest")
@ResourceLock("java.util.TimeZone")
class JsonDateProfileIntegrationTest {
    private static final String ADDRESS = "local://json-date-profiles";
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static Bus bus;
    private static Server server;
    private static TimeZone previousZone;

    @Path("/dates")
    @Produces("application/json")
    public static class Resource {
        @GET public DemographicTo1 get() {
            CALLS.incrementAndGet();
            DemographicTo1 patient = new DemographicTo1();
            patient.setEffDate(new Date(0));
            patient.setRosterDate(Date.from(Instant.parse("2026-11-09T12:34:56Z")));
            patient.setLastUpdateDate(new Date(0));
            return patient;
        }
        @POST @Consumes("application/json")
        public DemographicTo1 echo(DemographicTo1 patient) { CALLS.incrementAndGet(); return patient; }
    }

    @BeforeAll static void start() {
        previousZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        bus = BusFactory.newInstance().createBus();
        var factory = new JAXRSServerFactoryBean(); factory.setBus(bus); factory.setAddress(ADDRESS);
        factory.setResourceClasses(Resource.class);
        factory.setProviders(List.of(new JacksonJsonProvider(ProductionRestObjectMappers.oauthSurface())));
        factory.getInInterceptors().add(new JsonDateProfileInInterceptor());
        factory.getOutInterceptors().add(new JsonDateProfileOutInterceptor());
        factory.getOutFaultInterceptors().add(new JsonDateProfileOutInterceptor());
        server = factory.create();
    }
    @AfterAll static void stop() {
        if (server != null) server.destroy(); if (bus != null) bus.shutdown(true); TimeZone.setDefault(previousZone);
    }
    private record Result(int status, JsonNode body, String profile, String vary) {}
    private static Result call(String profile, String input) throws Exception {
        var factory = new JAXRSClientFactoryBean(); factory.setBus(bus); factory.setAddress(ADDRESS);
        var client = factory.createWebClient();
        try {
            client.path("/dates").accept("application/json");
            if (profile != null) client.header(JsonDateProfile.HEADER, profile);
            try (Response response = input == null ? client.get() : client.type("application/json").post(input)) {
                String body = response.hasEntity() ? response.readEntity(String.class) : "null";
                return new Result(response.getStatus(), response.getStatus() == 200
                        ? ProductionRestObjectMappers.oauthSurface().readTree(body) : null,
                        response.getHeaderString(JsonDateProfile.HEADER), response.getHeaderString("Vary"));
            }
        } finally { client.close(); }
    }
    private static void assertDeclared(Result result) {
        assertThat(result.status).isEqualTo(200);
        assertThat(result.body.get("effDate").asText()).isEqualTo("1970-01-01");
        assertThat(result.body.get("rosterDate").asText()).isEqualTo("2026-11-09");
        assertThat(result.body.get("lastUpdateDate").isIntegralNumber()).isTrue();
        assertThat(result.body.get("lastUpdateDate").longValue()).isZero();
        assertThat(result.profile).isEqualTo("declared"); assertThat(result.vary).contains(JsonDateProfile.HEADER);
    }
    @Test void shouldUseDeclaredCalendarSemantics_byDefaultWithoutChangingTimestamps() throws Exception {
        assertDeclared(call(null, null));
    }
    @Test void shouldPreserveExplicitLegacyInference_withoutAffectingTheNextRequest() throws Exception {
        Result legacy = call("legacy-value-inference", null);
        assertThat(legacy.status).isEqualTo(200);
        assertThat(legacy.body.get("effDate").asText()).isEqualTo("00:00:00");
        assertThat(legacy.body.get("rosterDate").isIntegralNumber()).isTrue();
        assertThat(legacy.profile).isEqualTo("legacy-value-inference");
        assertDeclared(call(null, null));
    }
    @Test void shouldRefuseInvalidProfiles_beforeCallingTheResource() throws Exception {
        int before = CALLS.get();
        for (String invalid : List.of("", "declared,legacy-value-inference", "DECLARED", "unknown")) {
            assertThat(call(invalid, null).status).isEqualTo(400);
        }
        assertThat(CALLS.get()).isEqualTo(before);
    }
    @Test void shouldRetainAcceptedJsonInputs_forAnEpochDayCalendarDate() throws Exception {
        Result result = call(null, "{\"effDate\":\"1970-01-01\"}");
        assertThat(result.status).isEqualTo(200);
        assertThat(result.body.get("effDate").asText()).isEqualTo("1970-01-01");
    }
    @Test void shouldIsolateProfiles_acrossConcurrentRequests() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = java.util.stream.IntStream.range(0, 16).<java.util.concurrent.Callable<Result>>mapToObj(index ->
                    () -> call(index % 2 == 0 ? "declared" : "legacy-value-inference", null)).toList();
            var results = executor.invokeAll(tasks);
            for (int index = 0; index < results.size(); index++) {
                Result result = results.get(index).get();
                if (index % 2 == 0) assertDeclared(result);
                else assertThat(result.body.get("effDate").asText()).isEqualTo("00:00:00");
            }
        }
    }
}
