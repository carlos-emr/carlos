/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

/** A prescription batch could not be stored; its transaction must roll back. */
public final class RxPrescriptionWriteException extends IllegalStateException {
    public RxPrescriptionWriteException() { super("Prescription medication write failed"); }
}
