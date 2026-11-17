/*
 * Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.form;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the bytes emitted by a real servlet writer, which fixes its encoding on acquisition. */
@Tag("integration")
@Tag("form")
class JSONUtilTomcatIntegrationTest {
    private static final String VALUE = "Montréal — 漢字 🙂";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String JSON = MAPPER.createObjectNode().put("message", VALUE).toString();
    @TempDir
    static Path directory;
    private static Tomcat tomcat;
    private static HttpClient client;
    private static URI base;

    @BeforeAll
    static void startTomcat() throws Exception {
        tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        Context context = tomcat.addContext("", Files.createDirectory(directory.resolve("webapp")).toString());
        Tomcat.addServlet(context, "json", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) {
                // No application filter preselects UTF-8 in this fixture: JSONUtil owns its charset.
                response.setStatus(HttpServletResponse.SC_ACCEPTED);
                switch (request.getParameter("mode")) {
                    case "object" -> JSONUtil.jsonResponse(response, MAPPER.createObjectNode().put("message", VALUE));
                    case "string" -> JSONUtil.jsonResponse(response, JSON);
                    case "named" -> JSONUtil.jsonResponse(response, "message", VALUE);
                    case "error" -> JSONUtil.errorResponse(response, "message", VALUE);
                    default -> throw new IllegalArgumentException("Unknown fixture mode");
                }
            }
        });
        context.addServletMappingDecoded("/json", "json");
        tomcat.start();
        base = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterAll
    static void stopTomcat() throws Exception {
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
    }

    @ParameterizedTest
    @ValueSource(strings = {"object", "string", "named", "error"})
    void shouldPreserveUnicodeAndStatus_forEveryResponseOverload(String mode) throws Exception {
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(base.resolve("/json?mode=" + mode))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo("error".equals(mode) ? 500 : 202);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json;charset=UTF-8");
        assertThat(response.body()).isEqualTo(JSON.getBytes(StandardCharsets.UTF_8));
        assertThat(MAPPER.readTree(response.body()).get("message").asText()).isEqualTo(VALUE);
    }
}
