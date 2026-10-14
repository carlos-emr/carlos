/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.service;

/** The submitted edit no longer describes the latest persisted note revision. */
public class StaleNoteRevisionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StaleNoteRevisionException() {
        super("The note has a newer revision. Reload it before saving another edit.");
    }
}
