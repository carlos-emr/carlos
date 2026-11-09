/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

/** Compatibility entry point for the shared Teleplan amount decoder. */
public final class TeleplanAmount {
    private TeleplanAmount() { }
    public static String decode(String value) {
        return io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanAmount.decode(value);
    }
}
