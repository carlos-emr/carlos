/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/** An agent produces claims, not a trusted chart artifact. No CARLOS session or managers are supplied. */
public interface ClinicalSummaryAgent {
    String displayName();

    JsonNode generate(JsonNode request) throws IOException;
}
