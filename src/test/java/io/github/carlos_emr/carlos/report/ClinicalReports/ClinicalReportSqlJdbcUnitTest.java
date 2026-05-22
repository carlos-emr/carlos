/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.ClinicalReports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Hashtable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("database")
@DisplayName("Clinical report SQL JDBC boundary")
class ClinicalReportSqlJdbcUnitTest {

    @Test
    @DisplayName("should reject unsafe numerator SQL before JDBC")
    void shouldRejectUnsafeNumeratorSql_beforeJdbc() {
        SQLNumerator numerator = new SQLNumerator();
        numerator.setSQL("select count(*) as count from demographic where demographic_no=${demographic_no}; drop table provider");

        boolean result = numerator.evaluate(null, "100");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should reject unsafe denominator SQL before JDBC")
    void shouldRejectUnsafeDenominatorSql_beforeJdbc() {
        SQLDenominator denominator = new SQLDenominator();
        denominator.setSQL("select demographic_no from demographic where provider_no=${provider_no}; drop table provider");
        Hashtable<String, String> replaceableValues = new Hashtable<>();
        replaceableValues.put("provider_no", "100");
        denominator.setReplaceableValues(replaceableValues);

        assertThat(denominator.getDenominatorList()).isEmpty();
    }
}
