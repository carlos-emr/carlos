/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Set;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.*;

/** Framework-neutral bridge to an operator-configured agent service on numeric loopback. */
public final class HttpClinicalSummaryAgent implements ClinicalSummaryAgent {
    private final int port;
    private final String path;
    private final String name;
    private final int timeoutMs;

    public HttpClinicalSummaryAgent(int port, String path, String name, int timeoutMs) {
        validateConnection(port, timeoutMs);
        if (path == null || !path.matches("/[A-Za-z0-9/_-]+") || name == null || name.isBlank()
                || name.length() > 160 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid HTTP agent configuration");
        }
        this.port = port;
        this.path = path;
        this.name = name;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String displayName() { return name + " via agent API v1"; }

    @Override
    public JsonNode generate(JsonNode request) throws IOException {
        JsonNode response = post(port, path, JSON.writeValueAsBytes(request), timeoutMs);
        exactFields(response, Set.of("contract_version", "request_id", "status", "output"));
        if (!response.path("contract_version").isIntegralNumber()
                || !response.get("contract_version").equals(request.get("contract_version"))
                || !request.get("request_id").equals(response.path("request_id"))
                || !"completed".equals(response.path("status").asText())) {
            throw new IOException("Mismatched or incomplete agent response");
        }
        return response.get("output");
    }
}
