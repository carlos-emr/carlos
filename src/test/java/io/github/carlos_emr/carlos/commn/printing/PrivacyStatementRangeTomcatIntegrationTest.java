/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.printing;

import io.github.carlos_emr.CarlosProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
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

import static org.assertj.core.api.Assertions.assertThat;

/** #5782: real DefaultServlet range framing must survive the production HTML decorator. */
@Tag("integration")
class PrivacyStatementRangeTomcatIntegrationTest {
    private static final byte[] HTML = ("<html><body>" + "FAKE-5782-content-".repeat(32)
            + "</body></html>").getBytes(StandardCharsets.UTF_8);
    @TempDir Path directory;
    private Tomcat tomcat;
    private URI uri;

    @BeforeEach
    void startTomcat() throws Exception {
        Path webapp = Files.createDirectories(directory.resolve("webapp"));
        Files.write(webapp.resolve("fixture.html"), HTML);
        tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        tomcat.getHost().setAutoDeploy(false);
        Context context = tomcat.addContext("/carlos", webapp.toString());
        context.setParentClassLoader(getClass().getClassLoader());
        ((StandardJarScanner) context.getJarScanner()).setScanClassPath(false);
        context.addMimeMapping("html", "text/html");
        Tomcat.addServlet(context, "default", new DefaultServlet()).setLoadOnStartup(1);
        context.addServletMappingDecoded("/", "default");
        FilterDef definition = new FilterDef();
        definition.setFilterName("privacy");
        definition.setFilter(new PrivacyStatementAppendingFilter());
        context.addFilterDef(definition);
        FilterMap mapping = new FilterMap();
        mapping.setFilterName("privacy");
        mapping.addURLPattern("/*");
        context.addFilterMap(mapping);
        tomcat.start();
        uri = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/carlos/fixture.html");
    }

    @AfterEach
    void stopTomcat() throws Exception {
        if (tomcat != null) {
            try { tomcat.stop(); } finally { tomcat.destroy(); }
        }
    }

    @Test
    void shouldPreserveSingleRangeFraming_withDefaultServletHtml() throws Exception {
        var properties = CarlosProperties.getInstance();
        Object original = properties.setProperty("confidentiality_statement.v1", "FAKE-5782-NOTICE");
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var full = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(full.statusCode()).isEqualTo(200);
            assertThat(new String(full.body(), StandardCharsets.UTF_8))
                    .contains("FAKE-5782-NOTICE", "FAKE-5782-content-".repeat(32), "<html><body>", "</body></html>");

            // AJAX selects the raw file representation. A decorated static representation
            // may ignore Range and return a complete 200 under the metadata policy.
            var partial = client.send(HttpRequest.newBuilder(uri).header("X-Requested-With", "XMLHttpRequest")
                    .header("Range", "bytes=0-99").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(partial.statusCode()).isEqualTo(206);
            assertThat(partial.body()).isEqualTo(Arrays.copyOfRange(HTML, 0, 100));
            assertThat(partial.headers().firstValue("Content-Range")).contains("bytes 0-99/" + HTML.length);
            assertThat(partial.headers().firstValueAsLong("Content-Length")).hasValue(100);

            var ignored = client.send(HttpRequest.newBuilder(uri).header("Range", "bytes=0-99")
                    .header("If-Range", "Thu, 01 Jan 1970 00:00:00 GMT").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(ignored.statusCode()).isEqualTo(200);
            assertThat(new String(ignored.body(), StandardCharsets.UTF_8))
                    .contains("FAKE-5782-NOTICE", "FAKE-5782-content-".repeat(32), "<html><body>", "</body></html>");
        } finally {
            if (original == null) properties.remove("confidentiality_statement.v1");
            else properties.put("confidentiality_statement.v1", original);
        }
    }
}
