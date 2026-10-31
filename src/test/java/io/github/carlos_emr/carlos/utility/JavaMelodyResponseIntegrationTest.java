/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import io.github.carlos_emr.carlos.commn.printing.PrivacyStatementAppendingFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.bull.javamelody.MonitoringFilter;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises javamelody/javamelody#1306 on real Tomcat 11. The /monitored context uses
 * Tomcat's default forward handling, which lost the final writer buffer with 2.6.0.
 * The /carlos context retains CARLOS's forward mitigation and runs its sanitizer,
 * response defaults and privacy wrapper around the real MonitoringFilter in web.xml order.
 * No Spring context, database, or production application startup is needed.
 */
@Tag("integration")
@Tag("filter")
@DisplayName("JavaMelody response integrity on Tomcat 11")
public class JavaMelodyResponseIntegrationTest {
    /** Shared with the compiled test JSP; includes multibyte text and a detectable tail. */
    public static final String HTML = "<!doctype html><html><head><title>Response integrity</title></head><body>"
            + "<p>synthetic content</p>".repeat(2300)
            + "<p id=\"tail\">COMPLETE é 漢 🙂</p></body></html>";
    private static final byte[] HTML_BYTES = HTML.getBytes(StandardCharsets.UTF_8);
    private static final byte[] BINARY = new byte[100_123];
    private static final Set<String> CARLOS_FILTERS = Set.of("ResponseSanitizationFilter",
            "CrossOriginOpenerPolicyFilter", "monitoring", "ResponseDefaultsFilter",
            "PrivacyStatementAppendingFilter");

    @TempDir
    static Path baseDir;
    private static Tomcat tomcat;
    private static int port;

    @BeforeAll
    static void startTomcat() throws Exception {
        for (int i = 0; i < BINARY.length; i++) {
            BINARY[i] = (byte) i;
        }
        assertThat(HTML_BYTES.length).isGreaterThan(8192);
        assertThat(HTML_BYTES.length % 8192).isNotZero();
        Path docBase = Files.createDirectories(baseDir.resolve("webapp"));
        try (var resource = JavaMelodyResponseIntegrationTest.class
                .getResourceAsStream("/javamelody-webapp/large.jsp")) {
            if (resource == null) {
                throw new IOException("Missing JavaMelody test JSP");
            }
            Files.copy(resource, docBase.resolve("large.jsp"));
        }
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        tomcat.getHost().setAutoDeploy(false);
        addContext("/monitored", docBase, false);
        addContext("/carlos", docBase, true);
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
    @ValueSource(strings = {"/monitored/writer", "/monitored/forward-writer",
            "/monitored/large.jsp", "/monitored/forward-jsp", "/carlos/writer",
            "/carlos/forward-writer", "/carlos/large.jsp", "/carlos/forward-jsp"})
    @DisplayName("should preserve every UTF-8 byte for direct and forwarded writer/JSP responses")
    void shouldPreserveHtml_whenRenderedDirectlyOrThroughForward(String path) throws Exception {
        HttpResponse<byte[]> response = get(path);
        assertThat(response.statusCode()).isEqualTo(200);
        if (path.startsWith("/carlos/")) {
            // The privacy filter may append configured print boilerplate after the intact page.
            assertThat(response.body()).as("intact page before CARLOS print decoration at %s", path)
                    .startsWith(HTML_BYTES);
        } else {
            assertThat(response.body()).as("complete response at %s: expected %d bytes, received %d",
                    path, HTML_BYTES.length, response.body().length).isEqualTo(HTML_BYTES);
        }
        assertThat(response.headers().firstValue("Content-Type")).hasValue("text/html;charset=UTF-8");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/monitored/binary", "/monitored/forward-binary",
            "/carlos/binary", "/carlos/forward-binary"})
    @DisplayName("should preserve binary output-stream responses")
    void shouldPreserveBinary_whenWrittenThroughOutputStream(String path) throws Exception {
        HttpResponse<byte[]> response = get(path);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(BINARY);
    }

    @Test
    @DisplayName("should keep sanitizing internal diagnostics through the upgraded monitoring writer")
    void shouldSanitizeError_whenMonitoringWrapsCarlosResponse() throws Exception {
        HttpResponse<byte[]> response = get("/carlos/error");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .contains("500").doesNotContain("java.lang.IllegalStateException", "synthetic-private-diagnostic");
        assertThat(response.headers().firstValue("Cross-Origin-Opener-Policy")).hasValue("same-origin");
    }

    private static HttpResponse<byte[]> get(String path) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private static void addContext(String path, Path docBase, boolean carlos) {
        Context context = tomcat.addContext(path, docBase.toString());
        context.setParentClassLoader(JavaMelodyResponseIntegrationTest.class.getClassLoader());
        if (carlos) {
            // Both CARLOS deployment descriptors require this for its own buffered filters too.
            context.setSuspendWrappedResponseAfterForward(false);
        }
        Tomcat.initWebappDefaults(context);
        StandardJarScanner scanner = new StandardJarScanner();
        scanner.setScanClassPath(false);
        context.setJarScanner(scanner);
        context.addServletContainerInitializer(new JasperInitializer(), null);

        Tomcat.addServlet(context, "content", new ContentServlet());
        context.addServletMappingDecoded("/writer", "content");
        context.addServletMappingDecoded("/binary", "content");
        context.addServletMappingDecoded("/error", "content");
        Tomcat.addServlet(context, "forward", new ForwardServlet());
        context.addServletMappingDecoded("/forward-writer", "forward");
        context.addServletMappingDecoded("/forward-jsp", "forward");
        context.addServletMappingDecoded("/forward-binary", "forward");

        Set<String> defined = new java.util.HashSet<>();
        for (var mapping : WebXmlFilterMappings.production()) {
            String name = mapping.filterName();
            if (!(carlos ? CARLOS_FILTERS.contains(name) : "monitoring".equals(name))) {
                continue;
            }
            if (defined.add(name)) {
                FilterDef def = new FilterDef();
                def.setFilterName(name);
                def.setFilter(filterFor(name));
                if ("monitoring".equals(name)) {
                    def.addInitParameter("disabled", "false");
                    def.addInitParameter("storage-directory", baseDir.resolve("metrics" + path).toString());
                }
                context.addFilterDef(def);
            }
            FilterMap map = new FilterMap();
            map.setFilterName(name);
            mapping.urlPatterns().forEach(map::addURLPatternDecoded);
            mapping.effectiveDispatchers().forEach(map::setDispatcher);
            context.addFilterMap(map);
        }
    }

    private static Filter filterFor(String name) {
        return switch (name) {
            case "ResponseSanitizationFilter" -> new ResponseSanitizationFilter();
            case "CrossOriginOpenerPolicyFilter" -> new CrossOriginOpenerPolicyFilter();
            case "monitoring" -> new MonitoringFilter();
            case "ResponseDefaultsFilter" -> new ResponseDefaultsFilter();
            case "PrivacyStatementAppendingFilter" -> new PrivacyStatementAppendingFilter();
            default -> throw new IllegalArgumentException("Unexpected test filter " + name);
        };
    }

    private static class ContentServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if ("/binary".equals(request.getServletPath())) {
                response.setContentType("application/octet-stream");
                response.getOutputStream().write(BINARY);
            } else {
                response.setContentType("text/html;charset=UTF-8");
                if ("/error".equals(request.getServletPath())) {
                    response.setStatus(500);
                    response.getWriter().write("java.lang.IllegalStateException: synthetic-private-diagnostic");
                } else {
                    // Deliberately do not flush: the forward must finish the container writer itself.
                    response.getWriter().write(HTML);
                }
            }
        }
    }

    private static class ForwardServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String target = switch (request.getServletPath()) {
                case "/forward-jsp" -> "/large.jsp";
                case "/forward-binary" -> "/binary";
                default -> "/writer";
            };
            request.getRequestDispatcher(target).forward(request, response);
        }
    }
}
