/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

/** Safe, fixed user-facing failure text. Never attach model responses or chart text. */
public final class ClinicalSummaryGenerationException extends Exception {
    public ClinicalSummaryGenerationException(String message) {
        super(message);
    }
}
