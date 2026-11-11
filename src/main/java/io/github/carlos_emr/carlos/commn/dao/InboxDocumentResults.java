/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import java.util.ArrayList;
import java.util.List;

/** Request-local completeness evidence; callers must not infer completeness from page length. */
public record InboxDocumentResults(List<LabResultData> items, int omittedRows) {
    public InboxDocumentResults {
        items = List.copyOf(items);
        if (omittedRows < 0) throw new IllegalArgumentException("Invalid omission count");
    }

    /** Legacy/API callers cannot silently accept a partial page. */
    public ArrayList<LabResultData> requireComplete() {
        if (omittedRows != 0) throw new IllegalStateException("Some inbox items could not be listed");
        return new ArrayList<>(items);
    }
}
