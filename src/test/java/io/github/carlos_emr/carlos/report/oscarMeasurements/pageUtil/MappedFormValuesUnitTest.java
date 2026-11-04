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

package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MappedFormValues}, the reader for {@code value(key)} fields that Struts 7
 * does not bind.
 */
@Tag("unit")
@Tag("report")
@DisplayName("MappedFormValues")
class MappedFormValuesUnitTest {

    /**
     * Struts 7.1.1 {@code DefaultAcceptedPatternsChecker.ACCEPTED_PATTERNS}: the {@code params}
     * interceptor accepts only a numeric or quoted key inside parentheses.
     */
    private static final Pattern STRUTS_ACCEPTED = Pattern.compile(
            "\\w+((\\.\\w+)|(\\[\\d+])|(\\(\\d+\\))|(\\['(\\w-?|[\\u4e00-\\u9fa5]-?)+'])|(\\('(\\w-?|[\\u4e00-\\u9fa5]-?)+'\\)))*");

    @Test
    @DisplayName("should confirm Struts rejects the value(key) names the CDM JSPs post")
    void shouldBeRejectedByStruts_forCdmFieldNames() {
        assertThat(STRUTS_ACCEPTED.matcher("value(CDMgroup)").matches()).isFalse();
        assertThat(STRUTS_ACCEPTED.matcher("value(measurementType0)").matches()).isFalse();
        assertThat(STRUTS_ACCEPTED.matcher("value(mInstrcsCheckbox01)").matches()).isFalse();
    }

    @Test
    @DisplayName("should read the posted value(key) parameter")
    void shouldReturnPostedValue_forMappedKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("value(CDMgroup)", "FAKE-OMD-CDM");
        request.addParameter("value(mInstrcsCheckbox01)", "Yes/No");

        assertThat(MappedFormValues.get(request, "CDMgroup")).isEqualTo("FAKE-OMD-CDM");
        assertThat(MappedFormValues.get(request, "mInstrcsCheckbox01")).isEqualTo("Yes/No");
    }

    @Test
    @DisplayName("should return null when the field, key or request is missing")
    void shouldReturnNull_whenAnythingIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("CDMgroup", "not-mapped");

        assertThat(MappedFormValues.get(request, "CDMgroup")).isNull();
        assertThat(MappedFormValues.get(request, null)).isNull();
        assertThat(MappedFormValues.get(null, "CDMgroup")).isNull();
    }

    @Test
    @DisplayName("should route getValue through MappedFormValues in every CDM report action")
    void shouldReadRequestFallback_inEveryCdmAction() throws Exception {
        Path dir = Path.of("src", "main", "java", "io", "github", "carlos_emr", "carlos", "report",
                "oscarMeasurements", "pageUtil");
        for (String action : new String[] {
                "RptSelectCDMReport2Action.java",
                "RptInitializePatientsMetGuidelineCDMReport2Action.java",
                "RptInitializePatientsInAbnormalRangeCDMReport2Action.java",
                "RptInitializeFrequencyOfRelevantTestsCDMReport2Action.java"}) {
            assertThat(Files.readString(dir.resolve(action), StandardCharsets.UTF_8))
                    .as(action)
                    .contains("MappedFormValues.get(request, key)");
        }
    }
}
