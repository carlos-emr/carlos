/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dao.projection.FluReportDemographicRow;

import jakarta.persistence.Tuple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("fast")
@DisplayName("Flu billing report DAO projection")
class DemographicDaoImplFluReportUnitTest {

    @Test
    @DisplayName("should map demographic values by query alias instead of column position")
    void shouldMapDemographicValuesByQueryAlias() {
        Tuple tuple = mock(Tuple.class);
        when(tuple.get("demographic_no")).thenReturn(714);
        when(tuple.get("patient_name")).thenReturn("Patient,Flu");
        when(tuple.get("phone")).thenReturn("416-555-0714");
        when(tuple.get("roster_status")).thenReturn("RO");
        when(tuple.get("patient_status")).thenReturn("AC");
        when(tuple.get("date_of_birth")).thenReturn("1940-06-15");
        when(tuple.get("age")).thenReturn(85);

        FluReportDemographicRow patient = DemographicDaoImpl.toFluReportDemographicRow(tuple);

        assertThat(patient).isEqualTo(new FluReportDemographicRow(
            "714", "Patient,Flu", "416-555-0714", "RO", "AC", "1940-06-15", "85"
        ));
    }

    @Test
    @DisplayName("should normalize null demographic query values to empty strings")
    void shouldNormalizeNullDemographicQueryValues() {
        Tuple tuple = mock(Tuple.class);

        FluReportDemographicRow patient = DemographicDaoImpl.toFluReportDemographicRow(tuple);

        assertThat(patient).isEqualTo(new FluReportDemographicRow("", "", "", "", "", "", ""));
    }
}
