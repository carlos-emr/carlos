/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

/** No current link can be changed; callers must refresh merge state before retrying. */
public final class DemographicMergeConflictException extends IllegalStateException {
    public DemographicMergeConflictException() {
        super("No active demographic merge link exists");
    }
}
