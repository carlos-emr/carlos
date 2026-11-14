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
package io.github.carlos_emr.carlos.prevention.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The contract callers rely on to tell "the preventions could not be read" from every other
 * failure by type, and to show it without leaking anything from the failure itself.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("PreventionSourceUnavailableException contract")
class PreventionSourceUnavailableExceptionUnitTest {

    private static final String SQL_AND_PHI = "select * from preventions where demographic_no = 7 -- FAKE Patient";

    @Test
    @DisplayName("should be an unchecked exception of its own final type")
    void shouldBeItsOwnUncheckedType_forTypeBasedHandlers() {
        PreventionSourceUnavailableException unavailable =
                new PreventionSourceUnavailableException(new SQLException(SQL_AND_PHI));

        assertThat(unavailable).isInstanceOf(RuntimeException.class);
        assertThat(Modifier.isFinal(PreventionSourceUnavailableException.class.getModifiers())).isTrue();
        // Existing catch blocks for the readers' former IllegalStateException keep working.
        assertThat(unavailable).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should not be mistaken for the date-review refusal, in either direction")
    void shouldStayDistinctFromTheDateReviewRefusal_forHandlersOfEither() {
        Object unavailable = new PreventionSourceUnavailableException(new SQLException(SQL_AND_PHI));
        Object dateReview = new PreventionDatePrecisionException();

        assertThat(unavailable).isNotInstanceOf(PreventionDatePrecisionException.class);
        assertThat(dateReview).isNotInstanceOf(PreventionSourceUnavailableException.class);
    }

    @Test
    @DisplayName("should keep the failure as its cause")
    void shouldKeepTheCause_forDiagnosis() {
        SQLException failure = new SQLException(SQL_AND_PHI);

        assertThat(new PreventionSourceUnavailableException(failure)).hasCause(failure);
    }

    @Test
    @DisplayName("should carry a fixed message with nothing from the failure in it")
    void shouldCarryAFixedMessage_withoutSqlOrPatientText() {
        PreventionSourceUnavailableException unavailable =
                new PreventionSourceUnavailableException(new SQLException(SQL_AND_PHI));

        assertThat(unavailable).hasMessage(PreventionSourceUnavailableException.MESSAGE);
        assertThat(unavailable.getMessage()).doesNotContain("select", "preventions", "FAKE", "7");
        assertThat(unavailable.toString()).doesNotContain("select", "FAKE");
        assertThat(new PreventionSourceUnavailableException(null))
                .hasMessage(PreventionSourceUnavailableException.MESSAGE)
                .hasNoCause();
    }
}
