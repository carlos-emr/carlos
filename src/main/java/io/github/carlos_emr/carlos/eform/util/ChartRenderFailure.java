/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;

/** Fixed diagnostics: never expose browser replies, request URLs or clinical values.
 * @since 2026-11-09
 */
public final class ChartRenderFailure extends IOException {
    private static final long serialVersionUID = 1L;
    public enum Reason { BUSY, UNAVAILABLE, DRAW_FAILED, INTERRUPTED }
    private final Reason reason;
    public ChartRenderFailure(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason reason() { return reason; }
    public static String code(Throwable failure) {
        if (failure instanceof ChartRenderFailure chart) return chart.reason().name();
        if (failure instanceof java.text.ParseException || failure instanceof IllegalArgumentException) return "INVALID_DATA";
        return "INTERNAL_ERROR";
    }
    public static int status(Throwable failure) {
        return switch (code(failure)) {
            case "BUSY", "UNAVAILABLE", "INTERRUPTED" -> 503;
            case "INVALID_DATA" -> 422;
            default -> 500;
        };
    }
}
