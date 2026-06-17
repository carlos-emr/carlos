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

import io.github.carlos_emr.carlos.utility.SafeEncode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Demographic standard identification HTML")
@Tag("unit")
@Tag("demographic")
class DemographicStandardIdentificationHtmlUnitTest {

    @Test
    void shouldEscapePatientLabelFields_whenRenderingStandardIdentificationHtml() {
        Demographic demographic = demographicWithHostileDisplayFields();

        String html = demographic.getStandardIdentificationHtml();

        assertThat(html)
                .contains("&lt;LAST&gt;")
                .contains("&lt;First&gt;")
                .contains("&lt;Dr&gt;")
                .contains("123&lt;456")
                .contains("A&amp;B")
                .contains("O&lt;N")
                .contains("&lt;chart&gt;")
                .doesNotContain("<Last>")
                .doesNotContain("<First>")
                .doesNotContain("<Dr>")
                .doesNotContain("<chart>");
    }

    @Test
    void shouldRenderEmptyOptionalFields_whenValuesAreNull() {
        Demographic demographic = new Demographic();
        demographic.setLastName("Last");
        demographic.setFirstName("First");
        demographic.setHin("1234567890");
        demographic.setVer(null);
        demographic.setHcType(null);

        String html = demographic.getStandardIdentificationHtml();

        assertThat(html).doesNotContain("null");
    }

    @Test
    void shouldEscapeChartHeaderFields_whenRenderingStandardIdentificationHTML() {
        Demographic demographic = demographicWithHostileDisplayFields();
        demographic.setDemographicNo(42);
        demographic.setPronoun("<they>");
        demographic.setSex("<S>");
        demographic.setGender("<G>");
        demographic.setPhone("555<1");
        demographic.setPhoneComment("\"copy\"");
        demographic.setCellPhone("555<2");
        demographic.setEmail("a<b@example.com");

        String html = demographic.getStandardIdentificationHTML("/ctx");

        assertThat(html)
                .contains("&lt;Dr&gt;")
                .contains("&lt;they&gt;")
                .contains("&lt;S&gt;")
                .contains("&lt;G&gt;")
                .contains("123&lt;456")
                .contains("O&lt;N")
                .contains("A&amp;B")
                .contains("555&lt;1")
                .contains("a&lt;b@example.com")
                .doesNotContain("<Dr>")
                .doesNotContain("<they>")
                .doesNotContain("<S>")
                .doesNotContain("<G>")
                .doesNotContain("123<456")
                .doesNotContain("555<1")
                .doesNotContain("a<b@example.com");
    }

    @Test
    void shouldNeutralizeQuotes_whenCopyToClipValuesContainAttributeBreakers() {
        // The copyToClip onclick handlers embed HIN/phone/email inside a
        // double-quoted HTML attribute wrapping a single-quoted JS string. A
        // raw double quote in the value must never survive into the markup,
        // or it terminates the onclick attribute.
        Demographic demographic = demographicWithHostileDisplayFields();
        demographic.setDemographicNo(42);
        demographic.setHin("12\"34'56");
        demographic.setPhone("555\"000");
        demographic.setEmail("a\"b@example.com");

        String html = demographic.getStandardIdentificationHTML("/ctx");

        // Body text renders via forHtmlContent, where a bare quote is harmless,
        // so only the copyToClip attribute context must be quote-free.
        assertThat(html)
                .contains(SafeEncode.forHtmlAttribute(SafeEncode.forJavaScript("12\"34'56")))
                .contains(SafeEncode.forHtmlAttribute(SafeEncode.forJavaScript("555\"000")))
                .contains(SafeEncode.forHtmlAttribute(SafeEncode.forJavaScript("a\"b@example.com")))
                .doesNotContain("copyToClip('12\"")
                .doesNotContain("copyToClip('555\"")
                .doesNotContain("copyToClip('a\"");
    }

    private static Demographic demographicWithHostileDisplayFields() {
        Demographic demographic = new Demographic();
        demographic.setLastName("<Last>");
        demographic.setFirstName("<First>");
        demographic.setTitle("<Dr>");
        demographic.setHin("123<456");
        demographic.setVer("A&B");
        demographic.setHcType("O<N");
        demographic.setChartNo("<chart>");
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("02");
        return demographic;
    }
}
