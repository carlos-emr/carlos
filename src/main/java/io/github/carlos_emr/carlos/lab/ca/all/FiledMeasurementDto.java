/**
 * Copyright (c) 2026. CARLOS EMR Project Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * CARLOS EMR Project: https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.lab.ca.all;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.Measurement;

/**
 * One result of a report as it will be filed: the measurement (not yet stored) and its extension
 * values (lab_no, abnormal, identifier, ... other_id) in the order they are written. Built from
 * the parsed report with no database access, so it can be built before the filing transaction and
 * filed again unchanged.
 *
 * @param measurement the measurement to store
 * @param extensions the extension key to value pairs, in filing order; a value may be null
 */
public record FiledMeasurementDto(Measurement measurement, Map<String, String> extensions) {

    public FiledMeasurementDto {
        extensions = Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
    }
}
