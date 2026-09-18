/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/** An agent produces claims, not a trusted chart artifact. No CARLOS session or managers are supplied. */
public interface ClinicalSummaryAgent {
    String displayName();

    /**
     * Revalidate the backend and return an immutable model/configuration revision for caching.
     * Null opts out: an agent display name alone cannot identify changing downstream models.
     * Implementations must not include source text or credentials in this identity.
     */
    default String cacheIdentity() throws IOException { return null; }

    /** Serialized request budget, including instructions/schema; never a summary-length limit. */
    default int requestBytes() { return 10000; }

    JsonNode generate(JsonNode request) throws IOException;
}
