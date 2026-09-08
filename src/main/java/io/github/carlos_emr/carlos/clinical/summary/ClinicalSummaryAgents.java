/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import java.util.Properties;
import io.github.carlos_emr.CarlosProperties;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.timeoutMillis;

/** Server configuration, never browser parameters, selects the adapter. Unknown adapters fail closed. */
public final class ClinicalSummaryAgents {
    private static final String PREFIX = "clinical.ai_summary_generation.";

    private ClinicalSummaryAgents() { }

    public static ClinicalSummaryAgent configured() { return configured(CarlosProperties.getInstance()); }

    static ClinicalSummaryAgent configured(Properties properties) {
        return switch (properties.getProperty(PREFIX + "agent", "ollama")) {
            case "ollama" -> new OllamaClinicalSummaryAgent(
                    Integer.parseInt(properties.getProperty(PREFIX + "ollama.port", "11434")),
                    properties.getProperty(PREFIX + "ollama.model", "qwen3.5:2b"),
                    timeoutMillis(properties.getProperty(PREFIX + "ollama.timeoutSeconds", "600")));
            case "http" -> new HttpClinicalSummaryAgent(
                    Integer.parseInt(properties.getProperty(PREFIX + "http.port", "11435")),
                    properties.getProperty(PREFIX + "http.path", "/v1/clinical-summary"),
                    properties.getProperty(PREFIX + "http.name", "Configured agent"),
                    timeoutMillis(properties.getProperty(PREFIX + "http.timeoutSeconds", "600")));
            default -> throw new IllegalArgumentException("Unknown clinical summary agent adapter");
        };
    }
}
