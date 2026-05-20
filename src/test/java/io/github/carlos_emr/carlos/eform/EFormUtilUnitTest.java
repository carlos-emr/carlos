/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@DisplayName("eForm legacy SQL safety")
class EFormUtilUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should allow closing braces outside unresolved template markers")
    void shouldAllow_closingBracesOutsideUnresolvedTemplateMarkers() {
        assertThatCode(() -> EFormSqlSafety.validateLegacySqlSafety(
                "select JSON_OBJECT('status', '}') as payload from demographic"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject unresolved template markers")
    void shouldReject_unresolvedTemplateMarkers() {
        assertThatThrownBy(() -> EFormSqlSafety.validateLegacySqlSafety(
                "select * from demographic where provider_no = ${providerNo}"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsafe dynamic SQL template");
    }
}
