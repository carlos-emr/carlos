/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under GPL version 2 or later.
 */
package io.github.carlos_emr.carlos.decision;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test
    @DisplayName("should preserve ordinary clinical label text and its spacing")
    void shouldPreserveLabelText_forBenignContent() throws Exception {
        DesAntenatalPlannerRisksHandler_99_12 handler = new DesAntenatalPlannerRisksHandler_99_12();
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "", "name", "CDATA", "risk103");
        // Leading/trailing spacing and an ampersand, as the shipped configuration uses.
        char[] label = "  Preterm labour & PROM  ".toCharArray();

        handler.startDocument();
        handler.startElement("", "", "risk", attributes);
        handler.characters(label, 0, label.length);
        handler.endElement("", "", "risk");
        handler.endDocument();

        String html = handler.getResults();
        assertThat(html)
                .contains("  Preterm labour &amp; PROM  ")
                // Encoded exactly once: a double pass would render "&amp;" to the clinician.
                .doesNotContain("&amp;amp;");
    }

    /**
     * A configuration file written before save-side validation existed -- or
     * planted directly on disk -- can still carry a scripting URL. popupPage()
     * passes it to window.open(), so escaping the JavaScript string literal is
     * not enough; the anchor must not be emitted at all.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JaVaScRiPt:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "//evil.test/phish",
            "\\\\evil.test\\phish"
    })
    @DisplayName("should drop the popup link for an unsafe configured URL")
    void shouldDropPopupLink_forUnsafeScheme(String unsafeHref) throws Exception {
        DesAntenatalPlannerRisksHandler_99_12 handler = new DesAntenatalPlannerRisksHandler_99_12();
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "", "href", "CDATA", unsafeHref);
        attributes.addAttribute("", "", "name", "CDATA", "risk1");
        char[] label = "Stillbirth".toCharArray();

        handler.startDocument();
        handler.startElement("", "", "risk", attributes);
        handler.characters(label, 0, label.length);
        handler.endElement("", "", "risk");
        handler.endDocument();

        String html = handler.getResults();
        assertThat(html)
                .doesNotContain("popupPage")
                .doesNotContain("<a href")
                .doesNotContain("</a>")
                .contains("name=\"risk_risk1\"")
                .contains("Stillbirth");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.test/risk",
            "http://example.test/risk",
            "/guidance/nutrition",
            "ar1risk_99_12.htm"
    })
    @DisplayName("should keep the popup link for an HTTP or relative URL")
    void shouldKeepPopupLink_forAllowedUrl(String safeHref) throws Exception {
        DesAntenatalPlannerRisksHandler_99_12 handler = new DesAntenatalPlannerRisksHandler_99_12();
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "", "href", "CDATA", safeHref);
        attributes.addAttribute("", "", "name", "CDATA", "risk1");

        handler.startDocument();
        handler.startElement("", "", "risk", attributes);
        handler.endElement("", "", "risk");
        handler.endDocument();

        assertThat(handler.getResults()).contains("popupPage").contains("</a>");
    }
}
