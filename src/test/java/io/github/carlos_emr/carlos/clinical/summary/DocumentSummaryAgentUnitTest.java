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
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class DocumentSummaryAgentUnitTest {
    private HttpServer server;
    private JsonNode request;
    private ObjectNode output;
    private boolean remoteModel;
    private int inferenceCalls;
    private boolean mismatch;

    @BeforeEach
    void setUp() throws Exception {
        output = JSON.createObjectNode().put("overview", "Repeat blood work planned.");
        output.putArray("points").addObject().put("text", "Repeat blood work planned.")
                .putArray("evidence").add("Repeat blood work planned.");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            JsonNode incoming = JSON.readTree(exchange.getRequestBody());
            ObjectNode reply = JSON.createObjectNode();
            String path = exchange.getRequestURI().getPath();
            if ("/api/show".equals(path)) {
                if (remoteModel) reply.put("remote_host", "https://example.invalid");
            } else if ("/api/generate".equals(path)) {
                inferenceCalls++;
                request = incoming;
                reply.put("model", "qwen3.5:2b").put("done", true).put("done_reason", "stop")
                        .put("response", output.toString());
            } else if ("/v1/document-summary".equals(path)) {
                inferenceCalls++;
                request = incoming;
                reply.put("contract_version", 1).put("status", "completed")
                        .put("request_id", mismatch ? "wrong-request" : incoming.path("request_id").asText());
                reply.set("output", output);
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = JSON.writeValueAsBytes(reply);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @Test
    void shouldUseDocumentSchema_withLocalOllama() throws Exception {
        var agent = new OllamaClinicalSummaryAgent(server.getAddress().getPort(), "qwen3.5:2b", 2000);
        var result = new DocumentSummaryService(agent).summarize("Repeat blood work planned.");
        assertThat(result.points()).hasSize(1);
        assertThat(request.path("format").path("properties").has("points")).isTrue();
        assertThat(request.path("format").path("properties").has("claims")).isFalse();
        assertThat(JSON.readTree(request.path("prompt").asText()).path("sources").get(0).path("title").asText())
                .isEqualTo("Document");
        assertThat(inferenceCalls).isEqualTo(1);
    }

    @Test
    void shouldRejectCloudOllama_beforeSendingDocument() {
        remoteModel = true;
        var agent = new OllamaClinicalSummaryAgent(server.getAddress().getPort(), "qwen3.5:2b", 2000);
        assertThatThrownBy(() -> new DocumentSummaryService(agent).summarize("Repeat blood work planned."))
                .isInstanceOf(ClinicalSummaryGenerationException.class);
        assertThat(inferenceCalls).isZero();
    }

    @Test
    void shouldUseDocumentOperation_withSharedHttpConfiguration() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("clinical.ai_summary_generation.agent", "http");
        properties.setProperty("clinical.ai_summary_generation.http.port", String.valueOf(server.getAddress().getPort()));
        var service = new DocumentSummaryService(ClinicalSummaryAgents.configuredDocument(properties));
        assertThat(service.summarize("Repeat blood work planned.").points()).hasSize(1);
        assertThat(request.path("workflow").asText()).isEqualTo("single-document-summary");
        assertThat(request.path("sources")).hasSize(1);
        mismatch = true;
        assertThatThrownBy(() -> service.summarize("Repeat blood work planned."))
                .isInstanceOf(ClinicalSummaryGenerationException.class);
    }
}
