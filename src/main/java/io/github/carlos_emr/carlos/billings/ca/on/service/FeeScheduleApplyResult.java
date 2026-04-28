/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.List;
import java.util.Map;

public record FeeScheduleApplyResult(
        List<FeeScheduleAppliedChange> changes,
        List<FeeScheduleValidationError> validationErrors) {

    public FeeScheduleApplyResult {
        changes = List.copyOf(changes);
        validationErrors = List.copyOf(validationErrors);
    }

    public List<Map<String, Object>> viewMaps() {
        return changes.stream().map(FeeScheduleAppliedChange::toViewMap).toList();
    }
}
