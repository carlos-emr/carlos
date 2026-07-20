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

package io.github.carlos_emr.carlos.webserv.rest.exceptionMapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ConversionException;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration tests for the REST exception mappers.
 *
 * <p>Unlike the per-mapper unit tests, this suite exercises the mappers through a real CXF
 * JAX-RS server with a stub resource that throws each mapped exception type. It verifies the
 * full HTTP contract clients see — status code, JSON media type, and the absence of stack
 * traces in the body — including the CXF-level dispatch that the unit tests cannot cover.
 *
 * <p>The server is bound to CXF's in-process {@code local://} transport (no network socket or
 * embedded servlet container needed) and driven with a CXF {@link WebClient}. It deliberately
 * does not extend {@code CarlosTestBase}: no Spring context or database is required, only the
 * mapper providers and a local server.
 *
 * @since 2026-06-21
 */
@Tag("integration")
@DisplayName("Exception mappers (end-to-end)")
class ExceptionMapperIntegrationTest {

    private static final String BASE_ADDRESS = "local://exception-mapper-it";

    private static Server server;

    /**
     * JAX-RS stub resource: each endpoint throws one mapped exception type so the registered
     * {@code ExceptionMapper} providers convert it into the client-facing HTTP response.
     */
    @Path("/boom")
    public static class ThrowingResource {

        @GET
        @Path("/access-denied")
        @Produces(MediaType.APPLICATION_JSON)
        public String accessDenied() {
            throw new AccessDeniedException("_rx", "r", 1);
        }

        @GET
        @Path("/security")
        @Produces(MediaType.APPLICATION_JSON)
        public String security() {
            throw new SecurityException("missing required sec object (_rx)");
        }

        @GET
        @Path("/patient-directive")
        @Produces(MediaType.APPLICATION_JSON)
        public String patientDirective() {
            throw new PatientDirectiveException("blocked by directive");
        }

        @GET
        @Path("/illegal-argument")
        @Produces(MediaType.APPLICATION_JSON)
        public String illegalArgument() {
            throw new IllegalArgumentException("Unknown drug status: active");
        }

        @GET
        @Path("/conversion")
        @Produces(MediaType.APPLICATION_JSON)
        public String conversion() {
            throw new ConversionException("cannot convert drug");
        }

        @GET
        @Path("/unhandled")
        @Produces(MediaType.APPLICATION_JSON)
        public String unhandled() {
            // Not specifically mapped -> falls through to GeneralExceptionMapper (500).
            throw new IllegalStateException("kaboom with secret internals");
        }
    }

    /** Immutable holder for the parts of an HTTP response the assertions care about. */
    private record Result(int status, String contentType, String body) {
    }

    @BeforeAll
    static void startServer() {
        JAXRSServerFactoryBean factory = new JAXRSServerFactoryBean();
        factory.setResourceClasses(ThrowingResource.class);
        factory.setProviders(List.of(
                new JacksonJsonProvider(),
                new GeneralExceptionMapper(),
                new AccessDeniedExceptionMapper(),
                new SecurityExceptionMapper(),
                new PatientDirectiveExceptionMapper(),
                new IllegalArgumentExceptionMapper(),
                new ConversionExceptionMapper()));
        factory.setAddress(BASE_ADDRESS);
        server = factory.create();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.destroy();
        }
    }

    private static Result get(String path) {
        WebClient client = WebClient.create(BASE_ADDRESS);
        try {
            Response response = client.path(path).accept(MediaType.APPLICATION_JSON).get();
            String contentType = response.getMediaType() == null
                    ? "" : response.getMediaType().toString();
            return new Result(response.getStatus(), contentType, response.readEntity(String.class));
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("should return HTTP 403 for AccessDeniedException")
    void shouldReturn403ForAccessDeniedException() {
        Result response = get("/boom/access-denied");

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"ACCESS_DENIED\"");
    }

    @Test
    @DisplayName("should return HTTP 403 for SecurityException")
    void shouldReturn403ForSecurityException() {
        Result response = get("/boom/security");

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"SECURITY_ERROR\"");
    }

    @Test
    @DisplayName("should return HTTP 403 for PatientDirectiveException")
    void shouldReturn403ForPatientDirectiveException() {
        Result response = get("/boom/patient-directive");

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"PATIENT_DIRECTIVE\"");
    }

    @Test
    @DisplayName("should return HTTP 400 for IllegalArgumentException")
    void shouldReturn400ForIllegalArgumentException() {
        Result response = get("/boom/illegal-argument");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"VALIDATION_ERROR\"");
    }

    @Test
    @DisplayName("should return HTTP 400 for ConversionException")
    void shouldReturn400ForConversionException() {
        Result response = get("/boom/conversion");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"CONVERSION_ERROR\"");
    }

    @Test
    @DisplayName("should return HTTP 500 for an unhandled exception")
    void shouldReturn500ForUnhandledException() {
        Result response = get("/boom/unhandled");

        assertThat(response.status()).isEqualTo(500);
        assertThat(response.body()).contains("\"code\":\"INTERNAL_ERROR\"");
    }

    @Test
    @DisplayName("should return an application/json content type")
    void shouldReturnJsonContentType() {
        Result response = get("/boom/illegal-argument");

        assertThat(response.contentType()).contains(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("should not expose a stack trace in the response body")
    void shouldNotExposeStackTraceInResponse() {
        Result response = get("/boom/unhandled");

        assertThat(response.body())
                .doesNotContain("kaboom with secret internals")
                .doesNotContain("IllegalStateException")
                .doesNotContain(".java:")
                .doesNotContain("\tat ");
    }
}
