/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.dto;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;

/** The saved revision and the text synchronized to the legacy chart. */
public record CaseManagementNoteSaveResult(CaseManagementNote note, String legacyChartText) {
}
