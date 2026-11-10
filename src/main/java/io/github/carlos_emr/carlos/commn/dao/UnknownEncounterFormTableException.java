/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import java.io.Serial;

/**
 * Rejects a form table identifier that is invalid or absent from the approved registry.
 * No patient history SQL has executed when this exception is thrown.
 *
 * @since 2026.08
 */
public final class UnknownEncounterFormTableException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Creates a rejection without exposing the submitted table identifier. */
    public UnknownEncounterFormTableException() {
        super("Unknown encounter form table");
    }
}
