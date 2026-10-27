/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prevention.dto;

/** Explicit refusal of a Date-only representation; the affected record is not omitted. */
public final class PreventionDatePrecisionException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public PreventionDatePrecisionException() {
        super("Prevention history contains a partial or unknown date; use a precision-aware representation");
    }

    public PreventionDatePrecisionException(String message) {
        super(message);
    }
}
