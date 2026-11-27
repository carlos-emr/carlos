/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

/** Explicit patient scope; encounter and artifact selection are not query-bindable. */
public record ClinicalSummaryRequest(Integer demographicNo, Integer encounterNo, String workflow, String artifactId) {
    public static ClinicalSummaryRequest chart(int demographicNo) {
        return new ClinicalSummaryRequest(demographicNo, null, "patient-overview", null);
    }
    public static ClinicalSummaryRequest synthetic() {
        return new ClinicalSummaryRequest(null, null, "patient-overview", "synthetic-overview-001");
    }
}
