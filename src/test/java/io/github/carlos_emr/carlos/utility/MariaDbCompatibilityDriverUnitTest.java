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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Verifies driver discovery before a database URL has been selected. */
@Tag("unit")
class MariaDbCompatibilityDriverUnitTest {
    @Test
    void shouldReturnNoPropertyDescriptors_withoutAConfiguredUrl() throws Exception {
        var properties = new Properties();
        properties.setProperty("connectTimeout", "1234");
        var expected = (Properties) properties.clone();
        assertThat(new MariaDbCompatibilityDriver().getPropertyInfo(null, properties)).isEmpty();
        assertThat(properties).isEqualTo(expected);
        assertThat(new MariaDbCompatibilityDriver().getPropertyInfo(null, null)).isEmpty();
    }

    @Test
    void shouldDescribeConfiguredTimeout_withALegacyUrl() throws Exception {
        var properties = new Properties();
        properties.setProperty("connectTimeout", "1234");
        var descriptors = new MariaDbCompatibilityDriver().getPropertyInfo("jdbc:mysql://localhost/example", properties);
        assertThat(descriptors).filteredOn(property -> "connectTimeout".equals(property.name))
                .singleElement().extracting(property -> property.value).isEqualTo("1234");
    }

    /** Connector/J's catch-all type for unknown SQLSTATE classes is relabelled; real connection failures are not (#6333). */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"45000", "07004", "HZ000", "3D000", "44000"})
    void shouldReportPlainSqlFailure_forNonConnectionStateMislabelledAsTransientConnection(String state) {
        var cause = new java.io.IOException("socket detail");
        var next = new java.sql.SQLException("next");
        var suppressed = new IllegalStateException("suppressed");
        var original = new java.sql.SQLTransientConnectionException("(conn=7) probe", state, 1644, cause);
        original.setNextException(next);
        original.addSuppressed(suppressed);

        java.sql.SQLException corrected = MariaDbCompatibilityDriver.correctedLabel(original);

        assertThat(corrected.getClass()).isEqualTo(java.sql.SQLException.class);
        assertThat(corrected.getMessage()).isEqualTo("(conn=7) probe");
        assertThat(corrected.getSQLState()).isEqualTo(state);
        assertThat(corrected.getErrorCode()).isEqualTo(1644);
        assertThat(corrected.getCause()).isSameAs(cause);
        assertThat((Object) corrected.getNextException()).isSameAs(next);
        assertThat(corrected.getSuppressed()).containsExactly(suppressed);
        assertThat(corrected.getStackTrace()).isEqualTo(original.getStackTrace());
        for (Throwable link = corrected; link != null; link = link.getCause()) {
            assertThat((Object) link).isNotInstanceOf(java.sql.SQLTransientConnectionException.class);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"08000", "08S01", "25S03"})
    void shouldKeepTransientConnectionType_forConnectionAndFailoverStates(String state) {
        var original = new java.sql.SQLTransientConnectionException("lost", state);
        assertThat((Object) MariaDbCompatibilityDriver.correctedLabel(original)).isSameAs(original);
    }

    @Test
    void shouldLeaveFailureUnchanged_forOtherTypesOrMissingState() {
        var timeout = new java.sql.SQLTimeoutException("timeout", "70100");
        var integrity = new java.sql.SQLIntegrityConstraintViolationException("dup", "23000");
        var unknownState = new java.sql.SQLTransientConnectionException("no state");
        assertThat((Object) MariaDbCompatibilityDriver.correctedLabel(timeout)).isSameAs(timeout);
        assertThat((Object) MariaDbCompatibilityDriver.correctedLabel(integrity)).isSameAs(integrity);
        assertThat((Object) MariaDbCompatibilityDriver.correctedLabel(unknownState)).isSameAs(unknownState);
    }
}
