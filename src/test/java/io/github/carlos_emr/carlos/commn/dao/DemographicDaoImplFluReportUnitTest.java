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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Alias-mapping contract for {@code DemographicDaoImpl.toFluReportDemographicRow}.
 *
 * <p>The aliases are asserted as literals rather than through the
 * {@code FLU_*} constants on purpose: the constants also build the SQL, so
 * reading them here would make the test move with any rename and pass against
 * a consistently wrong alias.</p>
 *
 * <p>These stubs reproduce real {@code Tuple} semantics — a value is null only
 * when the column itself is SQL NULL, and an unknown alias raises
 * {@code IllegalArgumentException}. Whether MariaDB actually reports these
 * seven labels is a database contract that only
 * {@code scripts/flu-billing-report-playwright-checks.js} exercises.</p>
 */
@Tag("unit")
@Tag("fast")
@Tag("dao")
@DisplayName("Flu billing report DAO projection")
class DemographicDaoImplFluReportUnitTest {

    private static Tuple tupleReturning(Object demographicNo, Object patientName, Object phone,
                                        Object rosterStatus, Object patientStatus,
                                        Object dateOfBirth, Object age) {
        Tuple tuple = mock(Tuple.class);
        when(tuple.get("demographic_no")).thenReturn(demographicNo);
        when(tuple.get("patient_name")).thenReturn(patientName);
        when(tuple.get("phone")).thenReturn(phone);
        when(tuple.get("roster_status")).thenReturn(rosterStatus);
        when(tuple.get("patient_status")).thenReturn(patientStatus);
        when(tuple.get("dob_formatted")).thenReturn(dateOfBirth);
        when(tuple.get("age")).thenReturn(age);
        return tuple;
    }

    @Test
    @DisplayName("should map demographic values by query alias instead of column position")
    void shouldMapDemographicValues_byQueryAlias() {
        Tuple tuple = tupleReturning(714, "Patient,Flu", "416-555-0714", "RO", "AC", "1940-06-15", 85);

        FluReportDemographicRow patient = DemographicDaoImpl.toFluReportDemographicRow(tuple);

        assertThat(patient).isEqualTo(new FluReportDemographicRow(
            "714", "Patient,Flu", "416-555-0714", "RO", "AC", "1940-06-15", "85"
        ));
    }

    @Test
    @DisplayName("should normalize null column values to empty strings")
    void shouldNormalizeNullColumnValues_toEmptyStrings() {
        // phone is the only nullable column reachable in production; the query's
        // WHERE clause filters out rows with a null age, DOB, or status, and
        // CONCAT() yields null only when a name part is missing.
        Tuple tuple = tupleReturning(714, null, null, "RO", "AC", "1940-06-15", 85);

        FluReportDemographicRow patient = DemographicDaoImpl.toFluReportDemographicRow(tuple);

        assertThat(patient.patientName()).isEmpty();
        assertThat(patient.phone()).isEmpty();
        assertThat(patient.demographicNo()).isEqualTo("714");
    }

    @ParameterizedTest(name = "providerNo=\"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   ", "-1", " -1 "})
    @DisplayName("should select every provider when no specific provider is requested")
    void shouldSelectEveryProvider_whenNoSpecificProviderRequested(String providerNo) {
        // Guards the regression where a blank provider became a literal
        // provider_no = '' filter, matching nobody and emptying the report while
        // the UI still claimed to show All Providers.
        assertThat(DemographicDaoImpl.isAllProvidersSelection(providerNo)).isTrue();
    }

    @ParameterizedTest(name = "providerNo=\"{0}\"")
    @ValueSource(strings = {"999998", " 999998 ", "P123"})
    @DisplayName("should filter by provider when a specific provider is requested")
    void shouldFilterByProvider_whenSpecificProviderRequested(String providerNo) {
        assertThat(DemographicDaoImpl.isAllProvidersSelection(providerNo)).isFalse();
    }

    @Test
    @DisplayName("should propagate the lookup failure when a query alias is missing")
    void shouldPropagateLookupFailure_whenQueryAliasMissing() {
        // Real Tuple.get(String) throws for an unknown alias rather than returning
        // null, so alias drift must fail loudly instead of blanking a column.
        Tuple tuple = mock(Tuple.class);
        when(tuple.get("demographic_no")).thenThrow(new IllegalArgumentException("demographic_no"));

        assertThatThrownBy(() -> DemographicDaoImpl.toFluReportDemographicRow(tuple))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
