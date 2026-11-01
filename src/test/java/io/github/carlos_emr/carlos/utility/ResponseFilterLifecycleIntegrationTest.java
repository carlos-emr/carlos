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
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import org.apache.catalina.connector.ResponseFacade;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.app.CarlosCsrfGuardFilter;
import io.github.carlos_emr.carlos.app.CsrfGuardScriptInjectionFilter;
import io.github.carlos_emr.carlos.app.LogoutBroadcastFilter;
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
import org.apache.tomcat.util.descriptor.web.ErrorPage;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.owasp.csrfguard.CsrfGuard;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Tomcat requests pin the capture/replay compatibility contract for #5487.
 * Runs every CARLOS response-transforming filter in the shipped mapping order, with
 * real CSRFGuard and JavaMelody. Authentication is a synthetic session, not a login;
 * full application/database and front-door checks belong to the deployed browser suite.
 */
@Tag("integration")
@Tag("filter")
@DisplayName("CARLOS response-filter dispatch lifecycle on Tomcat")
class ResponseFilterLifecycleIntegrationTest {
    private static final String HEAD = "<!doctype html><html><head><title>Lifecycle</title></head><body>";
    private static final String TAIL = "<p id=\"tail\">COMPLETE é 漢 🙂</p></body></html>";
    private static final String NOTICE = "SYNTHETIC PRINT NOTICE é 漢 🙂";
    private static final String PRIVATE_DIAGNOSTIC = "java.lang.IllegalStateException: synthetic-private-diagnostic";
    private static final int[] SIZES = {8191, 8192, 8193, 55_324,
            ResponseSanitizationFilter.MAX_CAPTURE_CHARS - 1,
            ResponseSanitizationFilter.MAX_CAPTURE_CHARS,
            ResponseSanitizationFilter.MAX_CAPTURE_CHARS + 1};
    private static final Set<String> RESPONSE_FILTERS = Set.of("ResponseSanitizationFilter",
            "CrossOriginOpenerPolicyFilter", "monitoring", "ResponseDefaultsFilter", "CSRFGuard",
            "CsrfGuardScriptInjectionFilter", "LogoutBroadcastFilter", "PrivacyStatementAppendingFilter");
    private static final byte[] BINARY = new byte[100_123];
    private static final ConcurrentHashMap<String, Boolean> FINISHED_AFTER_FORWARD = new ConcurrentHashMap<>();
    private static final Properties ORIGINAL_PROPERTIES = new Properties();
    @TempDir
    static Path baseDir;
    private static Tomcat tomcat;
    private static HttpClient client;
    private static int port;

    @BeforeAll
    static void startTomcat() throws Exception {
        ORIGINAL_PROPERTIES.putAll(CarlosProperties.getInstance());
        CarlosProperties.getInstance().keySet().removeIf(key -> key.toString().startsWith("confidentiality_statement.v"));
        CarlosProperties.getInstance().setProperty("confidentiality_statement.v1", NOTICE);
        CarlosProperties.getInstance().setProperty("response.sanitization.enabled", "true");
        Properties csrf = new Properties();
        try (var in = Files.newInputStream(Path.of("src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties"))) {
            csrf.load(in);
        }
        csrf.setProperty("org.owasp.csrfguard.configuration.provider.factory",
                "org.owasp.csrfguard.config.PropertiesConfigurationProviderFactory");
        CsrfGuard.load(csrf);
        assertThat(CsrfGuard.getInstance().isEnabled()).isTrue();
        for (int i = 0; i < BINARY.length; i++) {
            BINARY[i] = (byte) i;
        }
        Path docBase = Files.createDirectories(baseDir.resolve("webapp"));
        Files.write(docBase.resolve("fixture.bin"), BINARY);
        Files.writeString(docBase.resolve("page.jsp"),
                "<%@ page contentType=\"text/html;charset=UTF-8\" pageEncoding=\"UTF-8\" trimDirectiveWhitespaces=\"true\" %>"
                        + "<% out.write((String) request.getAttribute(\"fixture.html\")); %>");
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        tomcat.getHost().setAutoDeploy(false);
        addContext("/development", docBase, descriptorSetting("src/main/webapp/META-INF/context.xml"), RESPONSE_FILTERS);
        addContext("/packaged", docBase, descriptorSetting("debian/assets/tomcat/Catalina/localhost/carlos.xml"), RESPONSE_FILTERS);
        addContext("/default", docBase, true, RESPONSE_FILTERS);
        addContext("/capture", docBase, false, Set.of("ResponseSanitizationFilter"));
        addContext("/sanitizer", docBase, true, Set.of("ResponseSanitizationFilter"));
        addContext("/monitoring", docBase, true, Set.of("monitoring"));
        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterAll
    static void stopTomcat() throws Exception {
        try {
            if (client != null) {
                client.close();
            }
            if (tomcat != null) {
                try {
                    tomcat.stop();
                } finally {
                    tomcat.destroy();
                }
            }
        } finally {
            CarlosProperties.getInstance().clear();
            CarlosProperties.getInstance().putAll(ORIGINAL_PROPERTIES);
        }
    }

    static Stream<Arguments> htmlCases() {
        return Stream.of("development", "packaged").flatMap(context ->
                Stream.of("direct", "forward", "nested").flatMap(dispatch ->
                        Stream.of("writer", "stream", "jsp").flatMap(output ->
                                java.util.Arrays.stream(SIZES).boxed().flatMap(size ->
                                        Stream.of(200, 404, 500).map(status ->
                                                Arguments.of(context, dispatch, output, size, status))))));
    }

    @ParameterizedTest(name = "{0} {1} {2} {3} bytes status={4}")
    @MethodSource("htmlCases")
    void shouldPreservePayloadAndDecorateOnce_acrossDispatchAndCaptureBoundaries(
            String context, String dispatch, String output, int size, int status) throws Exception {
        HttpResponse<byte[]> response = get("/" + context + "/" + dispatch
                + "?output=" + output + "&size=" + size + "&status=" + status);
        assertThat(response.statusCode()).isEqualTo(status);
        String body = new String(response.body(), StandardCharsets.UTF_8);
        if (status >= 400 && size >= ResponseSanitizationFilter.MAX_CAPTURE_CHARS - 1) {
            // Existing policy replaces any oversized captured error, including safe prose.
            assertThat(body).contains("Reference ID:").doesNotContain(NOTICE, "<p id=\"tail\">");
            assertLength(response);
            return;
        }
        String csrfScript = "<script src=\"/" + context + "/csrfguard\"></script>\n";
        String expected = html(size);
        boolean injectionExpected = (!"direct".equals(dispatch) || "jsp".equals(output))
                && !"stream".equals(output);
        if (injectionExpected) {
            expected = expected.replace("</head>", csrfScript + "</head>");
        }
        assertThat(response.body()).as("exact UTF-8 payload including multibyte tail")
                .startsWith(expected.getBytes(StandardCharsets.UTF_8));
        assertThat(body).doesNotContain("DISCARDED PRE-FORWARD PREFIX", "synthetic-private-diagnostic");
        assertThat(occurrences(body, csrfScript)).isEqualTo(injectionExpected ? 1 : 0);
        assertThat(occurrences(body, "window.__carlosLogoutActive=true")).isEqualTo("stream".equals(output) ? 0 : 1);
        assertThat(occurrences(body, NOTICE)).isEqualTo(1);
        assertThat(body).contains("<b>END OF PRINTED DOCUMENT</b></p>");
        assertLength(response);
    }

    static Stream<Arguments> errorCases() {
        return Stream.of("development", "packaged").flatMap(context ->
                Stream.of("direct", "forward", "nested").flatMap(dispatch ->
                        Stream.of("writer", "stream").flatMap(output ->
                                Stream.of(403, 500).flatMap(status -> Stream.of(8193, 524_289).map(size ->
                                        Arguments.of(context, dispatch, output, status, size))))));
    }

    @ParameterizedTest(name = "{0} {1} {2} error={3} bytes={4}")
    @MethodSource("errorCases")
    void shouldSanitizeDiagnosticsAndRetainStatus_onWriterAndStreamErrors(
            String context, String dispatch, String output, int status, int size) throws Exception {
        HttpResponse<byte[]> response = get("/" + context + "/" + dispatch
                + "?output=" + output + "&status=" + status + "&size=" + size + "&tainted=true");
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .contains("" + status, "Reference ID:")
                .doesNotContain(PRIVATE_DIAGNOSTIC, "synthetic-private-diagnostic", NOTICE,
                        "window.__carlosLogoutActive=true", "DISCARDED PRE-FORWARD PREFIX");
        assertLength(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/development", "/packaged"})
    void shouldSanitizeContainerErrorDispatch_andKeepItsStatus(String context) throws Exception {
        HttpResponse<byte[]> response = get(context + "/send-error");
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .contains("503").doesNotContain(PRIVATE_DIAGNOSTIC, "synthetic-private-diagnostic");
        assertLength(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/development", "/packaged"})
    void shouldPreserveEveryBinaryAndStaticByte_acrossDirectAndNestedForwards(String context) throws Exception {
        for (String dispatch : new String[] {"direct", "forward", "nested"}) {
            HttpResponse<byte[]> response = get(context + "/" + dispatch + "?output=binary");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(BINARY);
            assertLength(response);
        }
        HttpResponse<byte[]> resource = get(context + "/fixture.bin");
        assertThat(resource.statusCode()).isEqualTo(200);
        assertThat(resource.body()).isEqualTo(BINARY);
        assertLength(resource);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/development", "/packaged"})
    void shouldPreserveSuppliedLength_whenNonHtmlResponsesFlushThroughAllWrappers(String context) throws Exception {
        for (String output : new String[] {"binary", "plain"}) {
            for (String dispatch : new String[] {"direct", "forward", "nested"}) {
                HttpResponse<byte[]> response = get(context + "/" + dispatch
                        + "?output=" + output + "&size=1100000&flush=true");
                byte[] expected = "binary".equals(output) ? BINARY : html(1_100_000).getBytes(StandardCharsets.UTF_8);
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEqualTo(expected);
                if ("plain".equals(output) && "nested".equals(dispatch)) {
                    // A writer acquired before Content-Type stays in the outer CSRF capture.
                    // Its replay deliberately leaves byte framing to the container; chunked is valid.
                    assertLength(response);
                } else {
                    assertThat(response.headers().firstValueAsLong("Content-Length"))
                            .as("explicit length for %s %s at %s", dispatch, output, context).hasValue(expected.length);
                }
            }
        }
    }

    static Stream<Arguments> fullResetCases() {
        return Stream.of("/development", "/packaged").flatMap(context ->
                Stream.of("direct", "forward", "nested").flatMap(dispatch ->
                        Stream.of("writer", "stream").flatMap(initial ->
                                Stream.of("writer", "stream").map(output ->
                                        Arguments.of(context, dispatch, initial, output)))));
    }

    static Stream<Arguments> lateTypeHeaderCases() {
        return Stream.of("/development", "/packaged").flatMap(context ->
                Stream.of("set", "add").map(api -> Arguments.of(context, api)));
    }

    @ParameterizedTest
    @MethodSource("lateTypeHeaderCases")
    void shouldPublishStreamLength_whenTypeHeaderFollowsChannelSelection(String context, String api)
            throws Exception {
        HttpResponse<byte[]> response = get(context + "/direct?output=binary&lateType=" + api + "&flush=true");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(BINARY);
        assertThat(response.headers().firstValueAsLong("Content-Length")).hasValue(BINARY.length);
    }

    @ParameterizedTest(name = "reset {0} {1} {2} -> {3}")
    @MethodSource("fullResetCases")
    void shouldCoordinateFullResetAcrossEveryWrapper_andAllowChannelReselection(
            String context, String dispatch, String initial, String output) throws Exception {
        HttpResponse<byte[]> response = get(context + "/" + dispatch
                + "?reset=" + initial + "&output=" + output + "&size=55324");
        assertThat(response.statusCode()).isEqualTo(200);
        String body = new String(response.body(), StandardCharsets.UTF_8);
        String csrf = "<script src=\"" + context + "/csrfguard\"></script>\n";
        String expected = html(55_324);
        if (!"direct".equals(dispatch) && "writer".equals(output)) {
            expected = expected.replace("</head>", csrf + "</head>");
        }
        assertThat(response.body()).startsWith(expected.getBytes(StandardCharsets.UTF_8));
        assertThat(body).doesNotContain("DISCARDED FULL RESET PREFIX", "DISCARDED PRE-FORWARD PREFIX");
        assertThat(occurrences(body, NOTICE)).isEqualTo(1);
        assertThat(occurrences(body, "window.__carlosLogoutActive=true"))
                .isEqualTo("writer".equals(output) ? 1 : 0);
        assertLength(response);
    }

    static Stream<Arguments> captureCases() {
        return Stream.of("direct", "forward", "nested").flatMap(dispatch ->
                Stream.of("writer", "stream").flatMap(output ->
                        Stream.of(-1, 0, 1).flatMap(delta -> Stream.of(200, 500).map(status ->
                                Arguments.of(dispatch, output, delta, status)))));
    }

    @ParameterizedTest(name = "capture {0} {1} limit{2} status={3}")
    @MethodSource("captureCases")
    void shouldPreserveExactBody_atTheActualWriterOrStreamCaptureLimit(
            String dispatch, String output, int delta, int status) throws Exception {
        // Writer capture counts UTF-16 characters; stream capture counts UTF-8 bytes.
        int multibyteExtra = TAIL.getBytes(StandardCharsets.UTF_8).length - TAIL.length();
        int size = ResponseSanitizationFilter.MAX_CAPTURE_CHARS + delta
                + ("writer".equals(output) ? multibyteExtra : 0);
        HttpResponse<byte[]> response = get("/capture/" + dispatch + "?output=" + output
                + "&size=" + size + "&status=" + status);
        assertThat(response.statusCode()).isEqualTo(status);
        if (status >= 400 && delta > 0) {
            assertThat(new String(response.body(), StandardCharsets.UTF_8))
                    .contains("Reference ID:").doesNotContain("<p id=\"tail\">");
        } else {
            assertThat(response.body()).isEqualTo(html(size).getBytes(StandardCharsets.UTF_8));
        }
        assertLength(response);
    }

    @Test
    void shouldExposeReplayAfterSuspension_withoutBlamingMonitoring() throws Exception {
        // Minimal isolation: the outer REQUEST-only sanitizer alone loses its captured writer
        // after forward; JavaMelody 2.9.0 alone drains the writer during forward and is complete.
        HttpResponse<byte[]> isolated = get("/sanitizer/forward?size=55324");
        assertThat(isolated.statusCode()).isEqualTo(200);
        assertThat(isolated.body()).isEmpty();
        assertThat(FINISHED_AFTER_FORWARD.get("/sanitizer")).isTrue();
        HttpResponse<byte[]> captured = get("/capture/forward?size=55324");
        assertThat(captured.statusCode()).isEqualTo(200);
        assertThat(captured.body()).isEqualTo(html(55_324).getBytes(StandardCharsets.UTF_8));
        assertThat(FINISHED_AFTER_FORWARD.get("/capture")).isFalse();
        HttpResponse<byte[]> monitored = get("/monitoring/forward?size=55324");
        assertThat(monitored.statusCode()).isEqualTo(200);
        assertThat(monitored.body()).isEqualTo(html(55_324).getBytes(StandardCharsets.UTF_8));
        HttpResponse<byte[]> composed = get("/default/forward?size=55324");
        assertThat(composed.statusCode()).isEqualTo(200);
        assertThat(composed.body()).isEmpty();
        assertThat(FINISHED_AFTER_FORWARD.get("/default")).isTrue();
    }

    private static void assertLength(HttpResponse<byte[]> response) {
        response.headers().firstValueAsLong("Content-Length").ifPresent(length ->
                assertThat(length).as("Content-Length describes final UTF-8 bytes").isEqualTo(response.body().length));
    }

    private static long occurrences(String body, String needle) {
        return (body.length() - body.replace(needle, "").length()) / needle.length();
    }

    private static String html(int bytes) {
        int fixedBytes = (HEAD + TAIL).getBytes(StandardCharsets.UTF_8).length;
        return HEAD + "x".repeat(bytes - fixedBytes) + TAIL;
    }

    private static HttpResponse<byte[]> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(new URI("http", null, "127.0.0.1", port,
                        path.split("\\?", 2)[0], path.contains("?") ? path.split("\\?", 2)[1] : null, null))
                .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static boolean descriptorSetting(String file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var in = Files.newInputStream(Path.of(file))) {
            String value = factory.newDocumentBuilder().parse(in).getDocumentElement()
                    .getAttribute("suspendWrappedResponseAfterForward");
            assertThat(value).as("compatibility contract in %s", file).isEqualTo("false");
            return Boolean.parseBoolean(value);
        }
    }

    private static void addContext(String path, Path docBase, boolean suspend, Set<String> filters) {
        Context context = tomcat.addContext(path, docBase.toString());
        context.setParentClassLoader(ResponseFilterLifecycleIntegrationTest.class.getClassLoader());
        context.setSuspendWrappedResponseAfterForward(suspend);
        Tomcat.initWebappDefaults(context);
        StandardJarScanner scanner = new StandardJarScanner();
        scanner.setScanClassPath(false);
        context.setJarScanner(scanner);
        context.addServletContainerInitializer(new JasperInitializer(), null);
        // Fixed synthetic identity before any response wrapping; no database or real account.
        FilterDef identity = new FilterDef();
        identity.setFilterName("fixture-identity");
        identity.setFilter((request, response, chain) -> {
            ((HttpServletRequest) request).getSession().setAttribute("user", "synthetic-lifecycle-user");
            chain.doFilter(request, response);
        });
        context.addFilterDef(identity);
        FilterMap identityMap = new FilterMap();
        identityMap.setFilterName("fixture-identity");
        identityMap.addURLPatternDecoded("/*");
        context.addFilterMap(identityMap);
        Tomcat.addServlet(context, "content", new ContentServlet());
        context.addServletMappingDecoded("/direct", "content");
        context.addServletMappingDecoded("/forward", "content");
        context.addServletMappingDecoded("/nested", "content");
        context.addServletMappingDecoded("/send-error", "content");
        context.addServletMappingDecoded("/error-page", "content");
        ErrorPage error = new ErrorPage();
        error.setErrorCode(503);
        error.setLocation("/error-page");
        context.addErrorPage(error);
        Set<String> defined = new HashSet<>();
        for (var mapping : WebXmlFilterMappings.production()) {
            String name = mapping.filterName();
            if (!filters.contains(name)) {
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
            case "CSRFGuard" -> new CarlosCsrfGuardFilter();
            case "CsrfGuardScriptInjectionFilter" -> new CsrfGuardScriptInjectionFilter();
            case "LogoutBroadcastFilter" -> new LogoutBroadcastFilter();
            case "PrivacyStatementAppendingFilter" -> new PrivacyStatementAppendingFilter();
            default -> throw new IllegalArgumentException("Unexpected filter " + name);
        };
    }

    private static class ContentServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            response.setCharacterEncoding("UTF-8");
            String path = request.getServletPath();
            if ("/send-error".equals(path)) {
                response.sendError(503);
                return;
            }
            if ("/error-page".equals(path)) {
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write(PRIVATE_DIAGNOSTIC);
                return;
            }
            String output = request.getParameter("output");
            if ("/nested".equals(path) || "/forward".equals(path)) {
                // resetBuffer at each forward must discard earlier captured output.
                String initial = request.getParameter("reset");
                if ("stream".equals(initial)) {
                    response.getOutputStream().write("DISCARDED PRE-FORWARD PREFIX".getBytes(StandardCharsets.UTF_8));
                } else if ("writer".equals(initial) || (!"stream".equals(output) && !"binary".equals(output))) {
                    response.getWriter().write("DISCARDED PRE-FORWARD PREFIX");
                }
                request.getRequestDispatcher("/nested".equals(path) ? "/forward" : "/direct")
                        .forward(request, response);
                ServletResponse raw = response;
                while (raw instanceof ServletResponseWrapper wrapper) {
                    raw = wrapper.getResponse();
                }
                FINISHED_AFTER_FORWARD.put(request.getContextPath(), ((ResponseFacade) raw).isFinished());
                return;
            }
            if ("binary".equals(output)) {
                String lateType = request.getParameter("lateType");
                jakarta.servlet.ServletOutputStream stream;
                if (lateType == null) {
                    response.setContentType("application/octet-stream");
                    response.setContentLength(BINARY.length);
                    stream = response.getOutputStream();
                } else {
                    response.setContentLength(BINARY.length);
                    stream = response.getOutputStream();
                    if ("add".equals(lateType)) {
                        response.addHeader("cOnTeNt-TyPe", "application/octet-stream");
                    } else {
                        response.setHeader("cOnTeNt-TyPe", "application/octet-stream");
                    }
                }
                stream.write(BINARY);
                if ("true".equals(request.getParameter("flush"))) {
                    stream.flush();
                }
                return;
            }
            String reset = request.getParameter("reset");
            if (reset != null) {
                response.setContentType("text/html;charset=UTF-8");
                if ("stream".equals(reset)) {
                    response.getOutputStream().write("DISCARDED FULL RESET PREFIX".getBytes(StandardCharsets.UTF_8));
                } else {
                    response.getWriter().write("DISCARDED FULL RESET PREFIX");
                }
                response.reset();
            }
            int size = request.getParameter("size") == null ? 8193 : Integer.parseInt(request.getParameter("size"));
            int status = request.getParameter("status") == null ? 200 : Integer.parseInt(request.getParameter("status"));
            response.setStatus(status);
            response.setContentType("plain".equals(output) ? "text/plain;charset=UTF-8" : "text/html;charset=UTF-8");
            String body = "true".equals(request.getParameter("tainted"))
                    ? PRIVATE_DIAGNOSTIC + "x".repeat(size) : html(size);
            if (!Set.of("/sanitizer", "/capture").contains(request.getContextPath())) {
                response.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
            }
            if ("jsp".equals(output)) {
                request.setAttribute("fixture.html", body);
                request.getRequestDispatcher("/page.jsp").forward(request, response);
            } else if ("stream".equals(output)) {
                response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            } else {
                response.getWriter().write(body);
                if ("true".equals(request.getParameter("flush"))) {
                    response.getWriter().flush();
                }
            }
        }
    }
}
