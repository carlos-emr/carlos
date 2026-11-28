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

import java.util.List;

/**
 * Everything {@link LabMeasurementFilingService} needs to file one report's measurements, read
 * from the parsed report and the report's own row before the filing transaction begins.
 *
 * @param labNo the report to file
 * @param patient the matched patient, or {@code null} when none that can be compared was supplied
 * @param revisionOrdered true when the report's chain is ordered by the lab's own revision evidence
 *                        (a CML report), so its last version is current
 * @param accession the report's accession number, which locks the rows of its chain; may be blank
 * @param fillerOrderNumber the report's filler order number, which locks a glucose tolerance
 *                          series filed under several accession numbers; may be blank
 * @param measurements the viewable results, as they will be filed
 */
public record LabMeasurementFilingDto(
        String labNo,
        Integer patient,
        boolean revisionOrdered,
        String accession,
        String fillerOrderNumber,
        List<FiledMeasurementDto> measurements) {

    public LabMeasurementFilingDto {
        measurements = List.copyOf(measurements);
    }
}
