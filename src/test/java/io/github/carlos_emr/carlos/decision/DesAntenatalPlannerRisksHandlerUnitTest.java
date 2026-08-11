/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under GPL version 2 or later.
 */
package io.github.carlos_emr.carlos.decision;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

@DisplayName("Antenatal risk renderer")
@Tag("unit")
@Tag("decision")
class DesAntenatalPlannerRisksHandlerUnitTest {

    @Test
    @DisplayName("should contextually encode configuration values in generated HTML")
    void shouldEncode_configurationValues() throws Exception {
        DesAntenatalPlannerRisksHandler_99_12 handler = new DesAntenatalPlannerRisksHandler_99_12();
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "", "href", "CDATA", "https://example.test/');alert(1);//");
        attributes.addAttribute("", "", "name", "CDATA", "risk1\" autofocus=\"true");
        char[] label = "<script>alert(2)</script>".toCharArray();

        handler.startDocument();
        handler.startElement("", "", "risk", attributes);
        handler.characters(label, 0, label.length);
        handler.endElement("", "", "risk");
        handler.endDocument();

        String html = handler.getResults();
        assertThat(html)
                .contains("&lt;script&gt;alert(2)&lt;/script&gt;")
                .doesNotContain("<script>")
                .doesNotContain("name=\"risk_risk1\" autofocus")
                .doesNotContain("');alert(1);//");
        assertThat(html.indexOf("<input")).isLessThan(html.indexOf("<a href"));
    }
}
