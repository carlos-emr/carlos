/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.printing;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.ResponseDefaultsFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/** Real DefaultServlet conditional requests through the production response-default and privacy filters (#5936). */
@Tag("integration")
@Isolated("Changes the process-wide print statement while serving real HTTP requests")
class PrivacyStatementStaticMetadataTomcatIntegrationTest {
    private static final String STATEMENT = "FAKE-print-statement-5936";
    private static final byte[] HTML = "<html><body>FAKE-original-page-5936</body></html>".getBytes(StandardCharsets.UTF_8);
    @TempDir Path directory;
    private Tomcat tomcat;
    private URI base;
    private Object originalStatement;
    private final AtomicReference<HeaderSnapshot> observedHeaders = new AtomicReference<>();

    private record HeaderSnapshot(String tag, String modified, long modifiedDate,
                                  List<String> tags, List<String> dates, List<String> names,
                                  String other, List<String> otherValues, long otherDate) {}

    @BeforeEach
    void startTomcat() throws Exception {
        originalStatement = CarlosProperties.getInstance().setProperty("confidentiality_statement.v1", STATEMENT);
        Path webapp = Files.createDirectories(directory.resolve("webapp"));
        Files.write(webapp.resolve("fixture.html"), HTML);
        Files.write(webapp.resolve("excluded.html"), HTML);
        Files.write(webapp.resolve("fixture.txt"), HTML);
        tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        tomcat.getHost().setAutoDeploy(false);
        Context context = tomcat.addContext("/carlos", webapp.toString());
        context.setParentClassLoader(getClass().getClassLoader());
        ((StandardJarScanner) context.getJarScanner()).setScanClassPath(false);
        context.addMimeMapping("html", "text/html");
        context.addMimeMapping("txt", "text/plain");
        Tomcat.addServlet(context, "default", new DefaultServlet()).setLoadOnStartup(1);
        context.addServletMappingDecoded("/", "default");
        addFilter(context, "defaults", new ResponseDefaultsFilter());
        FilterDef privacy = addFilter(context, "privacy", new PrivacyStatementAppendingFilter());
        privacy.addInitParameter("exclusions", "/excluded.html");
        addFilter(context, "header-observer", (request, response, chain) -> {
            HttpServletRequest http = (HttpServletRequest) request;
            observedHeaders.set(new HeaderSnapshot(http.getHeader("If-None-Match"),
                    http.getHeader("If-Modified-Since"), http.getDateHeader("If-Modified-Since"),
                    Collections.list(http.getHeaders("If-None-Match")), Collections.list(http.getHeaders("If-Modified-Since")),
                    Collections.list(http.getHeaderNames()), http.getHeader("X-FAKE-5936"),
                    Collections.list(http.getHeaders("X-FAKE-5936")), http.getDateHeader("Date")));
            chain.doFilter(request, response);
        });
        tomcat.start();
        base = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/carlos/");
    }

    private static FilterDef addFilter(Context context, String name, Filter filter) {
        FilterDef definition = new FilterDef();
        definition.setFilterName(name);
        definition.setFilter(filter);
        context.addFilterDef(definition);
        FilterMap mapping = new FilterMap();
        mapping.setFilterName(name);
        mapping.addURLPattern("/*");
        context.addFilterMap(mapping);
        return definition;
    }

    @AfterEach
    void stopTomcatAndRestoreStatement() throws Exception {
        try {
            if (tomcat != null) {
                try { tomcat.stop(); } finally { tomcat.destroy(); }
            }
        } finally {
            if (originalStatement == null) CarlosProperties.getInstance().remove("confidentiality_statement.v1");
            else CarlosProperties.getInstance().put("confidentiality_statement.v1", originalStatement);
        }
    }

    private HttpResponse<byte[]> request(String file, String method, String... headers) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var request = HttpRequest.newBuilder(base.resolve(file)).timeout(Duration.ofSeconds(10))
                    .method(method, HttpRequest.BodyPublishers.noBody());
            if (headers.length > 0) request.headers(headers);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    @Test
    void shouldOmitOrMatchDecoratedLength_whenDefaultServletAnswersHead() throws Exception {
        var get = request("fixture.html", "GET");
        assertThat(new String(get.body(), StandardCharsets.UTF_8)).contains("FAKE-original-page-5936", STATEMENT);
        var head = request("fixture.html", "HEAD");
        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.body()).isEmpty();
        var length = head.headers().firstValueAsLong("Content-Length");
        assertThat(length.isEmpty() || length.getAsLong() == get.body().length)
                .as("HEAD must not advertise the undecorated file length").isTrue();
    }

    @Test
    void shouldRemoveBareFileValidators_whenTheHtmlRepresentationIsDecorated() throws Exception {
        for (String method : new String[]{"GET", "HEAD"}) {
            var response = request("fixture.html", method);
            assertThat(response.statusCode()).isEqualTo(200);
            for (String header : new String[]{"ETag", "Last-Modified", "Accept-Ranges"}) {
                assertThat(response.headers().firstValue(header)).as(method + " " + header).isEmpty();
            }
        }
    }

    @Test
    void shouldServeTheCurrentStatement_whenConditionalRequestsCarryBareFileValidators() throws Exception {
        CarlosProperties.getInstance().setProperty("confidentiality_statement.v1", "");
        var bare = request("fixture.html", "GET");
        String etag = bare.headers().firstValue("ETag").orElseThrow();
        String modified = bare.headers().firstValue("Last-Modified").orElseThrow();
        CarlosProperties.getInstance().setProperty("confidentiality_statement.v1", STATEMENT);
        var byTag = request("fixture.html", "GET", "If-None-Match", etag);
        assertThat(byTag.statusCode()).isEqualTo(200);
        assertThat(new String(byTag.body(), StandardCharsets.UTF_8)).contains("FAKE-original-page-5936", STATEMENT);
        CarlosProperties.getInstance().setProperty("confidentiality_statement.v1", STATEMENT + "-updated");
        var byDate = request("fixture.html", "GET", "If-Modified-Since", modified);
        assertThat(byDate.statusCode()).isEqualTo(200);
        assertThat(new String(byDate.body(), StandardCharsets.UTF_8)).contains(STATEMENT + "-updated");
        assertThat(request("fixture.html", "HEAD", "If-None-Match", etag).statusCode()).isEqualTo(200);
    }

    @Test
    void shouldRetainFileBytesAndConditionalCaching_whenTheStatementIsDisabled() throws Exception {
        CarlosProperties.getInstance().setProperty("confidentiality_statement.v1", "");
        assertUndecoratedFile("fixture.html");
    }

    @Test
    void shouldRetainFileBytesAndConditionalCaching_whenTheContentIsNotHtml() throws Exception {
        assertUndecoratedFile("fixture.txt");
    }

    @Test
    void shouldRetainFileBytesAndConditionalCaching_whenTheRouteIsExcluded() throws Exception {
        assertUndecoratedFile("excluded.html");
    }

    @Test
    void shouldRetainFileBytesAndConditionalCaching_whenTheRequestIsAjax() throws Exception {
        assertUndecoratedFile("fixture.html", "X-Requested-With", "XMLHttpRequest");
    }

    @Test
    void shouldHideOnlyObsoleteCacheValidators_throughEveryServletHeaderAccessor() throws Exception {
        CarlosProperties.getInstance().setProperty("confidentiality_statement.v1", "");
        var bare = request("fixture.html", "GET");
        CarlosProperties.getInstance().setProperty("confidentiality_statement.v1", STATEMENT);
        var response = request("fixture.html", "GET",
                "If-None-Match", bare.headers().firstValue("ETag").orElseThrow(),
                "If-Modified-Since", bare.headers().firstValue("Last-Modified").orElseThrow(), "X-FAKE-5936", "keep");
        assertThat(response.statusCode()).isEqualTo(200);
        HeaderSnapshot seen = observedHeaders.get();
        assertThat(seen.tag()).isNull();
        assertThat(seen.modified()).isNull();
        assertThat(seen.modifiedDate()).isEqualTo(-1);
        assertThat(seen.tags()).isEmpty();
        assertThat(seen.dates()).isEmpty();
        assertThat(seen.names()).noneMatch(name -> "If-None-Match".equalsIgnoreCase(name)
                || "If-Modified-Since".equalsIgnoreCase(name));
        assertThat(seen.other()).isEqualTo("keep");
        assertThat(seen.otherValues()).containsExactly("keep");
        assertThat(seen.otherDate()).isEqualTo(-1);
    }

    @Test
    void shouldPreserveWildcardExistenceConditions_withoutEmittingBareFileValidators() throws Exception {
        for (String method : new String[]{"GET", "HEAD"}) {
            var response = request("fixture.html", method, "If-None-Match", "*");
            assertThat(response.statusCode()).isEqualTo(304);
            assertThat(response.body()).isEmpty();
            assertThat(response.headers().firstValue("ETag")).isEmpty();
            assertThat(response.headers().firstValue("Last-Modified")).isEmpty();
            assertThat(observedHeaders.get().tag()).isEqualTo("*");
            assertThat(observedHeaders.get().tags()).containsExactly("*");
            assertThat(observedHeaders.get().names()).anyMatch("If-None-Match"::equalsIgnoreCase);
        }
    }

    private void assertUndecoratedFile(String file, String... headers) throws Exception {
        var get = request(file, "GET", headers);
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(get.body()).isEqualTo(HTML);
        assertThat(request(file, "HEAD", headers).headers().firstValueAsLong("Content-Length")).hasValue(HTML.length);
        String[] conditional = java.util.Arrays.copyOf(headers, headers.length + 2);
        conditional[headers.length] = "If-None-Match";
        conditional[headers.length + 1] = get.headers().firstValue("ETag").orElseThrow();
        assertThat(request(file, "GET", conditional).statusCode()).isEqualTo(304);
    }
}
