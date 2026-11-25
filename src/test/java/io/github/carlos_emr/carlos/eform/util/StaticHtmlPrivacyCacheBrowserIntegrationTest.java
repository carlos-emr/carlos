/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.printing.PrivacyStatementAppendingFilter;
import io.github.carlos_emr.carlos.test.browser.SmokeDriverService;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
@Tag("browser")
@Isolated("Changes the process-wide confidentiality statement")
class StaticHtmlPrivacyCacheBrowserIntegrationTest {
    private static final String PROPERTY = "confidentiality_statement.v1";
    private static final String STATEMENT = "FAKE-browser-cache-statement";
    private static final String ORIGINAL = "FAKE-cacheable-page";
    @TempDir Path directory;

    @Test
    void shouldFetchTheCurrentPage_whenThePrintStatementIsEnabledAndDisabled() throws Exception {
        String chromium = System.getenv("CARLOS_SMOKE_CHROMIUM");
        assumeTrue(chromium != null, "Configure Chromium for static HTML cache qualification");
        try (var drivers = new SmokeDriverService()) {
            String endpoint = drivers.endpointOrNull();
            assumeTrue(endpoint != null, "Configure a browser driver for static HTML cache qualification");
            var properties = CarlosProperties.getInstance();
            Object previous = properties.setProperty(PROPERTY, "");
            var tomcat = new Tomcat();
            try {
                Path webapp = Files.createDirectories(directory.resolve("webapp"));
                Path fixture = Files.writeString(webapp.resolve("fixture.html"), "<html><body>" + ORIGINAL + "</body></html>");
                // With no explicit freshness lifetime, Chromium can cache based on Last-Modified.
                Files.setLastModifiedTime(fixture, FileTime.from(Instant.now().minus(Duration.ofDays(7))));
                Files.writeString(webapp.resolve("origin.txt"), "FAKE-browser-test-origin");

                tomcat.setBaseDir(directory.resolve("tomcat").toString());
                tomcat.setPort(0);
                tomcat.getConnector().setProperty("address", "127.0.0.1");
                tomcat.getHost().setAutoDeploy(false);
                var context = tomcat.addContext("/carlos", webapp.toString());
                context.setParentClassLoader(getClass().getClassLoader());
                ((StandardJarScanner) context.getJarScanner()).setScanClassPath(false);
                context.addMimeMapping("html", "text/html");
                Tomcat.addServlet(context, "default", new DefaultServlet()).setLoadOnStartup(1);
                context.addServletMappingDecoded("/", "default");
                var filter = new FilterDef();
                filter.setFilterName("privacy");
                filter.setFilter(new PrivacyStatementAppendingFilter());
                context.addFilterDef(filter);
                var mapping = new FilterMap();
                mapping.setFilterName("privacy");
                mapping.addURLPattern("/*");
                context.addFilterMap(mapping);
                tomcat.start();

                String origin = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort();
                var options = EFormBrowserPdfService.buildBrowserOptions(chromium, true, origin);
                try (var browser = new RenderBrowser(URI.create(endpoint), options.asMap())) {
                    browser.setTimeouts(Duration.ofSeconds(30), Duration.ofSeconds(30));
                    browser.get(origin + "/carlos/origin.txt");
                    assertThat(fetchFixture(browser)).contains(ORIGINAL).doesNotContain(STATEMENT);
                    properties.setProperty(PROPERTY, STATEMENT);
                    assertThat(fetchFixture(browser)).contains(ORIGINAL, STATEMENT);
                    properties.setProperty(PROPERTY, "");
                    assertThat(fetchFixture(browser)).contains(ORIGINAL).doesNotContain(STATEMENT);
                }
            } finally {
                try {
                    try { tomcat.stop(); } finally { tomcat.destroy(); }
                } finally {
                    if (previous == null) properties.remove(PROPERTY);
                    else properties.put(PROPERTY, previous);
                }
            }
        }
    }

    private static String fetchFixture(RenderBrowser browser) {
        return (String) browser.executeAsyncScript("""
                const done = arguments[0];
                fetch('/carlos/fixture.html')
                  .then(response => response.ok ? response.text() : Promise.reject(response.status))
                  .then(done, error => done(String(error)));
                """);
    }
}
