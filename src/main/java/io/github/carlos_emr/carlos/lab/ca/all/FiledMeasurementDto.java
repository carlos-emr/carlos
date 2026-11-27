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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.carlos_emr.carlos.commn.model.Measurement;

/**
 * One result of a report as it will be filed: the measurement (not yet stored) and its extension
 * values (lab_no, abnormal, identifier, ... other_id) in the order they are written. Built from
 * the parsed report with no database access, so it can be built before the filing transaction and
 * filed again unchanged.
 *
 * @param measurement the measurement template to store
 * @param extensions the extension key to value pairs, in filing order; a value may be null
 */
public record FiledMeasurementDto(Measurement measurement, Map<String, String> extensions) {

    public FiledMeasurementDto {
        measurement = copyTransient(Objects.requireNonNull(measurement, "measurement must not be null"));
        extensions = Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
    }

    /**
     * Returns a fresh transient {@link Measurement} (with {@code id == null}) copied from this DTO,
     * so retrying a rolled-back filing attempt never passes a previously-persisted detached entity
     * back to {@code EntityManager.persist}.
     *
     * @return Measurement a new transient measurement copy ready to persist
     */
    public Measurement newTransientMeasurement() {
        return copyTransient(measurement);
    }

    private static Measurement copyTransient(Measurement source) {
        Measurement copy = new Measurement();
        copy.setType(source.getType());
        copy.setDemographicId(source.getDemographicId());
        copy.setProviderNo(source.getProviderNo());
        copy.setDataField(source.getDataField());
        copy.setMeasuringInstruction(source.getMeasuringInstruction());
        copy.setComments(source.getComments());
        Date observed = source.getDateObserved();
        copy.setDateObserved(observed == null ? null : new Date(observed.getTime()));
        copy.setAppointmentNo(source.getAppointmentNo());
        Date created = source.getCreateDate();
        copy.setCreateDate(created == null ? null : new Date(created.getTime()));
        return copy;
    }
}
