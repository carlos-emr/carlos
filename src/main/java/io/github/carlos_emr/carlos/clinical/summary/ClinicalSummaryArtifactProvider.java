/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import java.io.IOException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Future chart implementations must authorize the user for the requested patient before retrieval. */
public interface ClinicalSummaryArtifactProvider {
    ClinicalSummaryArtifact load(LoggedInInfo user, ClinicalSummaryRequest request) throws IOException;
}
