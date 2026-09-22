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
    /**
     * Serialized bytes an adapter accepts per model request, shared by the prompt and the source text.
     *
     * <p>The prompt is a fixed cost paid on every pass, so a small budget starves each request of
     * clinical text. At 10,000 the current generation prompt left too little room for the committed
     * synthetic charts, and a note too large to fit but below the pipeline's 1024-character split
     * floor could not be planned at all. 16,000 is about 4,000 tokens, well inside the adapter's
     * 16,384-token context less its 4,096-token output budget.
     *
     * @return the per-request budget, between the pipeline floor and {@code MAX_REQUEST_BYTES}
     */
    default int requestBytes() { return ClinicalSummaryAgentProtocol.MIN_REQUEST_BYTES; }

    JsonNode generate(JsonNode request) throws IOException;

    /**
     * Rewrite only the statements the host faulted, or return null when this agent cannot. The host
     * decides what is faulted and whether a rewrite is accepted; see {@link ClinicalSummaryRepair}.
     *
     * @param request contract version 2: statements with their problems, and the notes they cite
     * @return an object with {@code statements}, each with {@code id} and {@code text}, or null
     */
    default JsonNode repair(JsonNode request) throws IOException { return null; }
}
