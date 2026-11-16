/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.app;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.owasp.csrfguard.CsrfGuard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** #5938: Tomcat keeps the stream selected across an actual servlet forward. */
@Tag("integration")
class CsrfGuardForwardCharsetTomcatIntegrationTest {
    private static final String BODY = "<html><head><title>Québec</title></head><body>café</body></html>";
    @TempDir Path directory;
    private Tomcat tomcat;
    private URI base;

    @BeforeEach
    void startTomcat() throws Exception {
        tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        tomcat.getHost().setAutoDeploy(false);
        Context context = tomcat.addContext("/carlos", Files.createDirectory(directory.resolve("webapp")).toString());
        context.setParentClassLoader(getClass().getClassLoader());
        ((StandardJarScanner) context.getJarScanner()).setScanClassPath(false);
        Tomcat.addServlet(context, "start", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                response.setContentType("text/html");
                response.setCharacterEncoding(request.getParameter("charset"));
                response.getOutputStream();
                CsrfGuard guard = mock(CsrfGuard.class);
                when(guard.isEnabled()).thenReturn(true);
                try (MockedStatic<CsrfGuard> factory = mockStatic(CsrfGuard.class)) {
                    factory.when(CsrfGuard::getInstance).thenReturn(guard);
                    request.getRequestDispatcher("/view.jsp").forward(request, response);
                }
            }
        });
        context.addServletMappingDecoded("/start", "start");
        Tomcat.addServlet(context, "view", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
                response.getWriter().write(BODY);
            }
        });
        context.addServletMappingDecoded("/view.jsp", "view");
        FilterDef definition = new FilterDef();
        definition.setFilterName("csrf-script");
        definition.setFilter(new CsrfGuardScriptInjectionFilter());
        context.addFilterDef(definition);
        FilterMap mapping = new FilterMap();
        mapping.setFilterName("csrf-script");
        mapping.addURLPattern("/*");
        mapping.setDispatcher("FORWARD");
        context.addFilterMap(mapping);
        tomcat.start();
        base = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/carlos/start");
    }

    @AfterEach
    void stopTomcat() throws Exception {
        if (tomcat != null) {
            try { tomcat.stop(); } finally { tomcat.destroy(); }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-supported-charset", "bad-charset!", "UTF-8"})
    void shouldReplayExactUtf8Bytes_afterAStreamThenForward(String charset) throws Exception {
        URI uri = URI.create(base + "?charset=" + charset);
        byte[] expected = BODY.replace("</head>", "<script src=\"/carlos/csrfguard\"></script>\n</head>")
                .getBytes(StandardCharsets.UTF_8);
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(expected);
            assertThat(response.headers().firstValueAsLong("Content-Length")).hasValue(expected.length);
        }
    }
}
