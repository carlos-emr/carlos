/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.demographic.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Demographic XML storage values")
@Tag("unit")
@Tag("demographic")
class DemographicXmlTest {

    @Test
    @DisplayName("should escape family doctor text when inputs contain XML markup")
    void shouldEscapeFamilyDoctorText_whenInputsContainXmlMarkup() {
        String familyDoctor = DemographicXml.familyDoctor(
                "</rdohip><injected>EVIL</injected><rdohip>",
                "O'Brien \"Smith\" & Sons <Clinic>",
                "A ]]> B");

        assertThat(familyDoctor).isEqualTo(
                "<rdohip>&lt;/rdohip&gt;&lt;injected&gt;EVIL&lt;/injected&gt;&lt;rdohip&gt;</rdohip>" +
                        "<rd>O&apos;Brien &quot;Smith&quot; &amp; Sons &lt;Clinic&gt;</rd>" +
                        "<family_doc>A ]]&gt; B</family_doc>");
    }

    @Test
    @DisplayName("should escape user notes text when content contains XML markup")
    void shouldEscapeUserNotesText_whenContentContainsXmlMarkup() {
        String notes = DemographicXml.userNotes("</unotes><injected>EVIL</injected><unotes>");

        assertThat(notes).isEqualTo(
                "<unotes>&lt;/unotes&gt;&lt;injected&gt;EVIL&lt;/injected&gt;&lt;unotes&gt;</unotes>");
    }

    @Test
    @DisplayName("should use empty XML text when inputs are null")
    void shouldUseEmptyXmlText_whenInputsAreNull() {
        assertThat(DemographicXml.familyDoctor(null, null, "Dr. Smith"))
                .isEqualTo("<rdohip></rdohip><rd></rd><family_doc>Dr. Smith</family_doc>");
        assertThat(DemographicXml.userNotes(null)).isEqualTo("<unotes></unotes>");
    }

    @Test
    @DisplayName("should omit optional family doctor element when value is null")
    void shouldOmitOptionalFamilyDoctorElement_whenValueIsNull() {
        String familyDoctor = DemographicXml.familyDoctor("1234", "Dr. O'Brien & Co.", null);

        assertThat(familyDoctor).isEqualTo("<rdohip>1234</rdohip><rd>Dr. O&apos;Brien &amp; Co.</rd>");
    }
}
