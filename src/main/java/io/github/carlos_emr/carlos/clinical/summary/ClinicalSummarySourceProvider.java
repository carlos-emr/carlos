/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** IDs are artifact-local. Future implementations must enforce the same patient authorization as load. */
public interface ClinicalSummarySourceProvider {
    Optional<Map<String, Object>> resolve(LoggedInInfo user, ClinicalSummaryRequest request, String sourceId)
            throws IOException;
}
