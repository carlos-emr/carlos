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
package io.github.carlos_emr.carlos.commn.model;

import io.github.carlos_emr.carlos.test.builders.DemographicTestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Demographic standard identification HTML")
@Tag("unit")
@Tag("demographic")
class DemographicStandardIdentificationHtmlUnitTest {

    @Test
    @DisplayName("should encode patient-controlled fields when rendering standard identification html")
    void shouldEncodePatientControlledFields_whenRenderingStandardIdentificationHtml() {
        Demographic demographic = DemographicTestBuilder.aDemographic()
                .withHin("123<456>")
                .withHcType("ON&<>")
                .withVer("<V>")
                .build();
        demographic.setDemographicNo(42);
        demographic.setTitle("<Dr&Co>");
        demographic.setPronoun("they & <them>");
        demographic.setSex("X<svg>");
        demographic.setGender("N<binary>");

        String html = demographic.getStandardIdentificationHTML("/carlos");

        assertThat(html)
                .contains("&lt;Dr&amp;Co&gt; ")
                .contains("they &amp; &lt;them&gt;")
                .contains("X&lt;svg&gt;")
                .contains("N&lt;binary&gt;")
                .contains("ON&amp;&lt;&gt;")
                .contains("123&lt;456&gt;")
                .contains("&lt;V&gt;")
                .doesNotContain("<Dr&Co>")
                .doesNotContain("they & <them>")
                .doesNotContain("X<svg>")
                .doesNotContain("N<binary>")
                .doesNotContain("ON&<>")
                .doesNotContain("123<456>")
                .doesNotContain("<V>");
    }

    @Test
    @DisplayName("should not render literal null when nullable identification fields are missing")
    void shouldNotRenderLiteralNull_whenNullableIdentificationFieldsAreMissing() {
        Demographic demographic = DemographicTestBuilder.aDemographic()
                .withHin("1234567890")
                .withHcType(null)
                .withVer(null)
                .build();
        demographic.setDemographicNo(42);
        demographic.setSex(null);
        demographic.setTitle(null);
        demographic.setPronoun(null);
        demographic.setGender(null);

        String html = demographic.getStandardIdentificationHTML("/carlos");

        assertThat(html)
                .contains("<div id='patient-sex'>")
                .doesNotContain("null");
    }
}
