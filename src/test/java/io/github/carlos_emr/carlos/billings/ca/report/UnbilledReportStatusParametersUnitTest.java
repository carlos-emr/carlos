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
package io.github.carlos_emr.carlos.billings.ca.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link UnbilledReportStatusParameters}: only the exact
 * checkbox value {@code true} opts a status into the unbilled report.
 *
 * @since 2026-09-26
 */
@DisplayName("UnbilledReportStatusParameters")
@Tag("unit")
@Tag("billing")
class UnbilledReportStatusParametersUnitTest {

    @Test
    void shouldExcludeBoth_whenRequestIsNull() {
        assertThat(UnbilledReportStatusParameters.includeNoShow(null)).isFalse();
        assertThat(UnbilledReportStatusParameters.includeCancelled(null)).isFalse();
    }

    @Test
    void shouldExcludeBoth_whenParametersAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(UnbilledReportStatusParameters.includeNoShow(request)).isFalse();
        assertThat(UnbilledReportStatusParameters.includeCancelled(request)).isFalse();
    }

    @Test
    void shouldIncludeEachIndependently_whenCheckboxValueIsTrue() {
        MockHttpServletRequest noShowOnly = new MockHttpServletRequest();
        noShowOnly.setParameter(UnbilledReportStatusParameters.INCLUDE_NO_SHOW, "true");
        MockHttpServletRequest cancelledOnly = new MockHttpServletRequest();
        cancelledOnly.setParameter(UnbilledReportStatusParameters.INCLUDE_CANCELLED, "true");

        assertThat(UnbilledReportStatusParameters.includeNoShow(noShowOnly)).isTrue();
        assertThat(UnbilledReportStatusParameters.includeCancelled(noShowOnly)).isFalse();
        assertThat(UnbilledReportStatusParameters.includeNoShow(cancelledOnly)).isFalse();
        assertThat(UnbilledReportStatusParameters.includeCancelled(cancelledOnly)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"on", "TRUE", "1", "yes", " true", "false"})
    void shouldKeepExclusionDefault_forAnyOtherValue(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (value != null) {
            request.setParameter(UnbilledReportStatusParameters.INCLUDE_NO_SHOW, value);
            request.setParameter(UnbilledReportStatusParameters.INCLUDE_CANCELLED, value);
        }

        assertThat(UnbilledReportStatusParameters.includeNoShow(request)).isFalse();
        assertThat(UnbilledReportStatusParameters.includeCancelled(request)).isFalse();
    }

    @Test
    void shouldExposeStableParameterNames_forJspCheckboxes() {
        assertThat(UnbilledReportStatusParameters.INCLUDE_NO_SHOW).isEqualTo("includeNoShow");
        assertThat(UnbilledReportStatusParameters.INCLUDE_CANCELLED).isEqualTo("includeCancelled");
    }
}
