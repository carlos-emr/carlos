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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.CarlosProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Reliable 5xx probes for issue #5575. Test-only servlets exercise the real filter
 * over HTTP on Tomcat 11 without relying on unfixed application defects or adding
 * fault-injection routes to the deployed application. Only startup configuration
 * is mocked; requests pass through the production filter implementation.
 */
@Tag("integration")
@Tag("filter")
@Tag("security")
@DisplayName("Response sanitization on real Tomcat")
public class ResponseSanitizationFilterTomcatIntegrationTest {
    private static final String PRIVATE_MARKER = "FAKE-5575-private-diagnostic";
    private static final String ENTITY = "{\"patientName\":\"FAKE-5575-patient\",\"note\":\"synthetic-prefix\"}";
    private static final String LATE_ENTITY = "{\"patients\":[" + (ENTITY + ",").repeat(300) + ENTITY + "]}";
    private static final String XML = "<patient><name>FAKE-5575-patient</name><note>synthetic-prefix</note></patient>";
    private static final String SAFE_VALIDATION = "{\"error\":\"Invalid synthetic date\"}";
    private static final String STACK = "java.lang.IllegalStateException: " + PRIVATE_MARKER
            + "\n\tat io.github.carlos_emr.synthetic.Fault.run(Fault.java:12)";

    @TempDir
    static Path baseDir;
    private static Tomcat tomcat;
    private static int port;

    @BeforeAll
    static void startTomcat() throws Exception {
        assertThat(LATE_ENTITY.getBytes(StandardCharsets.UTF_8).length)
                .isGreaterThan(8192)
                .isLessThan(ResponseSanitizationFilter.DEFAULT_WEB_SERVICE_RESPONSE_BUFFER_BYTES);
        Path docBase = Files.createDirectories(baseDir.resolve("webapp"));
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        tomcat.getHost().setAutoDeploy(false);
        addContext("/enabled", docBase, true);
        addContext("/disabled", docBase, false);
        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
    }

    @AfterAll
    static void stopTomcat() throws Exception {
        if (tomcat != null) {
            try {
                tomcat.stop();
            } finally {
                tomcat.destroy();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"writer", "stream", "late-stream", "xml"})
    @DisplayName("should replace clean partial web-service entities after a deliberate server error")
    void shouldReplacePartialEntity_whenWebServiceReturnsServerError(String mode) throws Exception {
        assertSanitized(get("/enabled/ws/rs/" + mode));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stack", "throw"})
    @DisplayName("should replace HTML diagnostics and an exception escaping the servlet")
    void shouldReplaceHtmlError_whenServletWritesOrThrowsDiagnostics(String mode) throws Exception {
        assertSanitized(get("/enabled/html/" + mode));
    }

    @Test
    @DisplayName("should preserve successful web-service entities")
    void shouldPreserveEntity_whenWebServiceSucceeds() throws Exception {
        HttpResponse<String> response = get("/enabled/ws/rs/success");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json;charset=UTF-8");
        assertThat(response.body()).isEqualTo(ENTITY);
    }

    @Test
    @DisplayName("should preserve a deliberate validation refusal instead of requiring an obsolete 500")
    void shouldPreserveValidationError_whenWebServiceRejectsInput() throws Exception {
        HttpResponse<String> response = get("/enabled/ws/rs/validation");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json;charset=UTF-8");
        assertThat(response.body()).isEqualTo(SAFE_VALIDATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"writer", "stream", "late-stream", "xml"})
    @DisplayName("should expose the synthetic fixture only in the disabled negative control")
    void shouldExposeFixture_whenSanitizationIsDisabled(String mode) throws Exception {
        HttpResponse<String> response = get("/disabled/ws/rs/" + mode);
        // Without the filter's enlarged /ws buffer, the large prefix commits the
        // default 200 before the servlet can set 500. This is the leak being pinned.
        assertThat(response.statusCode()).isEqualTo("late-stream".equals(mode) ? 200 : 500);
        assertThat(response.body()).isEqualTo(switch (mode) {
            case "xml" -> XML;
            case "late-stream" -> LATE_ENTITY;
            default -> ENTITY;
        });
        assertThat(response.body()).doesNotContain("Reference ID:");
    }

    @Test
    @DisplayName("should expose the diagnostic fixture in the disabled HTML negative control")
    void shouldExposeDiagnostic_whenHtmlSanitizationIsDisabled() throws Exception {
        HttpResponse<String> response = get("/disabled/html/stack");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).isEqualTo(STACK);
    }

    private static void assertSanitized(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("text/html;charset=UTF-8");
        assertThat(response.body()).contains("HTTP Status: 500")
                .containsPattern("Reference ID: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                .doesNotContain(PRIVATE_MARKER, "FAKE-5575-patient", "synthetic-prefix",
                        "java.lang.", "Fault.java", "patientName");
        assertThat(response.headers().firstValue("Content-Length"))
                .hasValue(Integer.toString(response.body().getBytes(StandardCharsets.UTF_8).length));
    }

    private static HttpResponse<String> get(String path) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    private static void addContext(String path, Path docBase, boolean enabled) throws ServletException {
        Context context = tomcat.addContext(path, docBase.toString());
        context.setParentClassLoader(ResponseSanitizationFilterTomcatIntegrationTest.class.getClassLoader());
        StandardJarScanner scanner = new StandardJarScanner();
        scanner.setScanClassPath(false);
        context.setJarScanner(scanner);
        Tomcat.addServlet(context, "fault", new FaultServlet());
        context.addServletMappingDecoded("/ws/*", "fault");
        context.addServletMappingDecoded("/html/*", "fault");

        ResponseSanitizationFilter sanitizer = new ResponseSanitizationFilter();
        // Initializing before container startup scopes the configuration mock to
        // this thread. The global CarlosProperties singleton is never mutated.
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            CarlosProperties configuration = mock(CarlosProperties.class);
            properties.when(CarlosProperties::getInstance).thenReturn(configuration);
            when(configuration.getProperty(ResponseSanitizationFilter.ENABLED_PROPERTY, ""))
                    .thenReturn(Boolean.toString(enabled));
            when(configuration.getProperty(ResponseSanitizationFilter.WEB_SERVICE_BUFFER_PROPERTY, ""))
                    .thenReturn("");
            sanitizer.init(mock(FilterConfig.class));
        }
        FilterDef definition = new FilterDef();
        definition.setFilterName("sanitization");
        definition.setFilter(new InitializedFilter(sanitizer));
        context.addFilterDef(definition);
        FilterMap mapping = new FilterMap();
        mapping.setFilterName("sanitization");
        mapping.addURLPatternDecoded("/*");
        mapping.setDispatcher("REQUEST");
        context.addFilterMap(mapping);
    }

    /** Delegates requests to the production filter already initialized with isolated test configuration. */
    private record InitializedFilter(ResponseSanitizationFilter sanitizer) implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            sanitizer.doFilter(request, response, chain);
        }
    }

    private static class FaultServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String mode = request.getPathInfo();
            if ("/stack".equals(mode) || "/throw".equals(mode)) {
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write(STACK);
                if ("/throw".equals(mode)) {
                    throw new ServletException(PRIVATE_MARKER);
                }
                response.setStatus(500);
                return;
            }
            response.setContentType("application/json;charset=UTF-8");
            if ("/rs/success".equals(mode)) {
                response.getWriter().write(ENTITY);
                return;
            }
            if ("/rs/validation".equals(mode)) {
                response.setStatus(400);
                response.getWriter().write(SAFE_VALIDATION);
                return;
            }
            if ("/rs/late-stream".equals(mode)) {
                response.getOutputStream().write(LATE_ENTITY.getBytes(StandardCharsets.UTF_8));
                response.setStatus(500);
                return;
            }
            response.setStatus(500);
            if ("/rs/xml".equals(mode)) {
                response.setContentType("application/xml;charset=UTF-8");
                response.getOutputStream().write(XML.getBytes(StandardCharsets.UTF_8));
            } else if ("/rs/stream".equals(mode)) {
                response.getOutputStream().write(ENTITY.getBytes(StandardCharsets.UTF_8));
            } else {
                response.getWriter().write(ENTITY);
            }
        }
    }
}
