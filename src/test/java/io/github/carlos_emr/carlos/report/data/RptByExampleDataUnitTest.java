/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("report")
@DisplayName("Query by example report data")
class RptByExampleDataUnitTest {

    @Test
    @DisplayName("should block direct SQL execution")
    void shouldBlockDirectSqlExecution() {
        RptByExampleData data = new RptByExampleData();

        String result = data.exampleReportGenerate("select * from demographic", new Properties());

        assertThat(result).isEqualTo(RptByExampleData.DIRECT_SQL_DISABLED_MESSAGE);
    }
}
