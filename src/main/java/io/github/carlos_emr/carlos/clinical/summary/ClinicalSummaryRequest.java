/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

/** Future chart scope; never populated from Struts parameters in this prototype. */
public record ClinicalSummaryRequest(Integer demographicNo, Integer encounterNo, String workflow, String artifactId) {
    public static ClinicalSummaryRequest synthetic() {
        return new ClinicalSummaryRequest(null, null, "patient-overview", "synthetic-overview-001");
    }
}
