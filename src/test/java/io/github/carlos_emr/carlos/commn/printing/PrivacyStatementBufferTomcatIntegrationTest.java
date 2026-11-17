/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.printing;

import io.github.carlos_emr.CarlosProperties;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Real container buffer and response-byte contracts for the privacy appender (#5940). */
class PrivacyStatementBufferTomcatIntegrationTest {
    private static final String NOTICE = "Synthetic buffer privacy notice";
    private static final String HTML = "<html><body>" + "x".repeat(20 * 1024) + "</body></html>";
    private static final String SMALL_HTML = "<html><body>FAKE late HTML</body></html>";
    private static final Map<String, byte[]> ASSETS = Map.of(
            "/style.css", "body { color: black; }".getBytes(StandardCharsets.UTF_8),
            "/script.js", "window.syntheticStaticFixture = true;".getBytes(StandardCharsets.UTF_8),
            "/binary.bin", new byte[]{0, 1, 2, 3, (byte) 255},
            "/excluded.css", "body { color: blue; }".getBytes(StandardCharsets.UTF_8));
    private static final Map<String, CompletableFuture<int[]>> BUFFERS = new ConcurrentHashMap<>();
    @TempDir static Path directory;
    private static Tomcat tomcat;
    private static HttpClient client;
    private static URI base;
    private static Object originalNotice;

    @BeforeAll
    static void startTomcat() throws Exception {
        originalNotice = CarlosProperties.getInstance().setProperty("confidentiality_statement.v1", NOTICE);
        Path web = Files.createDirectory(directory.resolve("web"));
        for (var asset : ASSETS.entrySet()) Files.write(web.resolve(asset.getKey().substring(1)), asset.getValue());
        tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        Context context = tomcat.addContext("", web.toString());
        context.addMimeMapping("css", "text/css");
        context.addMimeMapping("js", "text/javascript");
        context.addMimeMapping("bin", "application/octet-stream");
        Tomcat.addServlet(context, "default", new DefaultServlet()).setLoadOnStartup(1);
        context.addServletMappingDecoded("/", "default");
        Tomcat.addServlet(context, "html", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                boolean typeFirst = Boolean.parseBoolean(request.getParameter("typeFirst"));
                String body = "plain".equals(request.getParameter("initial")) ? SMALL_HTML : HTML;
                if ("plain".equals(request.getParameter("initial"))) {
                    response.setContentType("text/plain;charset=UTF-8");
                }
                if (typeFirst) response.setContentType("text/html;charset=UTF-8");
                if ("writer".equals(request.getParameter("channel"))) response.getWriter().write(body);
                else response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                if (!typeFirst) response.setContentType("text/html;charset=UTF-8");
            }

            @Override
            protected void doHead(HttpServletRequest request, HttpServletResponse response) {
                response.setContentType("text/html;charset=UTF-8");
                response.setContentLength(12345);
            }
        });
        context.addServletMappingDecoded("/html", "html");
        FilterDef observer = new FilterDef();
        observer.setFilterName("buffer-observer");
        observer.setFilter((request, response, chain) -> {
            var http = (HttpServletRequest) request;
            String key = http.getRequestURI() + (http.getQueryString() == null ? "" : "?" + http.getQueryString());
            CompletableFuture<int[]> result = new CompletableFuture<>();
            BUFFERS.put(key, result);
            int initial = response.getBufferSize();
            chain.doFilter(request, response);
            result.complete(new int[]{initial, response.getBufferSize()});
        });
        context.addFilterDef(observer);
        FilterDef privacy = new FilterDef();
        privacy.setFilterName("privacy");
        privacy.setFilter(new PrivacyStatementAppendingFilter());
        privacy.addInitParameter("exclusions", "/excluded.css");
        context.addFilterDef(privacy);
        for (String name : List.of("buffer-observer", "privacy")) {
            FilterMap map = new FilterMap();
            map.setFilterName(name);
            map.addURLPattern("/*");
            map.setDispatcher("REQUEST");
            context.addFilterMap(map);
        }
        tomcat.start();
        base = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterAll
    static void stopTomcat() throws Exception {
        try {
            if (client != null) client.close();
            if (tomcat != null) {
                try { tomcat.stop(); } finally { tomcat.destroy(); }
            }
        } finally {
            if (originalNotice == null) CarlosProperties.getInstance().remove("confidentiality_statement.v1");
            else CarlosProperties.getInstance().put("confidentiality_statement.v1", originalNotice);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/style.css", "/script.js", "/binary.bin", "/excluded.css"})
    void shouldKeepContainerBuffer_forKnownNonHtmlStaticAssets(String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        int[] buffer = BUFFERS.get(path).get(10, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(ASSETS.get(path));
        assertThat(buffer[1]).isEqualTo(buffer[0]);
    }

    @Test
    void shouldAppendNotice_whenAnUncommittedPlainResponseBecomesHtml() throws Exception {
        String path = "/html?channel=stream&typeFirst=false&initial=plain";
        var response = client.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith(SMALL_HTML).containsOnlyOnce(NOTICE);
        response.headers().firstValue("Content-Length").ifPresent(length ->
                assertThat(Long.parseLong(length)).isEqualTo(response.body().getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void shouldKeepHeadMetadata_withoutAllocatingABodyBuffer() throws Exception {
        var response = client.send(HttpRequest.newBuilder(base.resolve("/html")).timeout(Duration.ofSeconds(15))
                .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
        int[] buffer = BUFFERS.get("/html").get(10, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().firstValue("Content-Length")).hasValue("12345");
        assertThat(buffer[1]).isEqualTo(buffer[0]);
    }

    @ParameterizedTest
    @CsvSource({"writer,true", "writer,false", "stream,true", "stream,false"})
    void shouldKeepLargeHtmlAndItsNotice_forBothChannelsAndTypeDeclarationOrders(String channel, boolean typeFirst) throws Exception {
        String path = "/html?channel=" + channel + "&typeFirst=" + typeFirst;
        var response = client.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        int[] buffer = BUFFERS.get(path).get(10, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith(HTML).containsOnlyOnce(NOTICE);
        assertThat(buffer[1]).isGreaterThanOrEqualTo(1024 * 1024);
    }
}
