/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

/** Timestamp-only callers must not be given a page that silently loses the rest of a shared second. */
public final class ScheduleSyncBoundaryException extends RuntimeException {
    public ScheduleSyncBoundaryException() {
        super("Timestamp-only schedule page splits a shared update timestamp; use the timestamp-id sync profile");
    }
}
