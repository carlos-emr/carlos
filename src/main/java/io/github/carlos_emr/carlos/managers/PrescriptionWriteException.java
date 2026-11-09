/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

/** Signals a write failure across the transaction proxy before REST translates it to a failure response. */
public final class PrescriptionWriteException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PrescriptionWriteException() {
        super("Failed to prescribe drugs");
    }
}
