/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.managers;

/** A rejected fax input before any file preparation or queue persistence begins. */
public final class FaxPreparationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public FaxPreparationException(String message) {
        super(message);
    }
}
