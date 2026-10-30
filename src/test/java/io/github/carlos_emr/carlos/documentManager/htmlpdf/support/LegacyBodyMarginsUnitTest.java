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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.support;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Unit tests for {@link LegacyBodyMargins}: the {@code <body leftmargin="0" topmargin="0">} attributes of the
 * legacy forms, read the way WebKit (and so wkhtmltopdf) read them.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("LegacyBodyMargins Unit Tests")
class LegacyBodyMarginsUnitTest {

    private static Document page(String bodyAttributes) {
        return Jsoup.parse("<html><head><title>T</title></head><body " + bodyAttributes + "><p>FAKE</p></body></html>");
    }

    private static String rule(String bodyAttributes) {
        return LegacyBodyMargins.rule(page(bodyAttributes)).orElse(null);
    }

    @Test
    @DisplayName("should give both sides the margin of leftmargin, and both ends the margin of topmargin")
    void shouldSetBothSides_forLeftmarginAndTopmargin() {
        assertThat(rule("leftmargin=\"3\"")).isEqualTo("body { margin-left: 3px; margin-right: 3px; }");
        assertThat(rule("topmargin=\"5\"")).isEqualTo("body { margin-top: 5px; margin-bottom: 5px; }");
        assertThat(rule("topmargin=\"0\" leftmargin=\"0\" rightmargin=\"0\""))
                .isEqualTo("body { margin-left: 0px; margin-right: 0px; margin-top: 0px; margin-bottom: 0px; }");
    }

    @Test
    @DisplayName("should read marginwidth like leftmargin and marginheight like topmargin")
    void shouldSetBothSides_forMarginwidthAndMarginheight() {
        assertThat(rule("marginwidth=\"7\"")).isEqualTo("body { margin-left: 7px; margin-right: 7px; }");
        assertThat(rule("marginheight=\"9\"")).isEqualTo("body { margin-top: 9px; margin-bottom: 9px; }");
    }

    @Test
    @DisplayName("should not read rightmargin and bottommargin, which WebKit ignored")
    void shouldIgnoreRightmarginAndBottommargin_asWebKitDid() {
        assertThat(LegacyBodyMargins.rule(page("rightmargin=\"0\" bottommargin=\"0\""))).isEmpty();
        assertThat(rule("rightmargin=\"4\" leftmargin=\"1\"")).isEqualTo("body { margin-left: 1px; margin-right: 1px; }");
    }

    @Test
    @DisplayName("should take the last of the attributes that name the same sides, in document order")
    void shouldTakeLastAttribute_inDocumentOrder() {
        assertThat(rule("marginwidth=\"5\" leftmargin=\"2\"")).isEqualTo("body { margin-left: 2px; margin-right: 2px; }");
        assertThat(rule("leftmargin=\"2\" marginwidth=\"5\"")).isEqualTo("body { margin-left: 5px; margin-right: 5px; }");
        assertThat(rule("topmargin=\"1\" marginheight=\"6\"")).isEqualTo("body { margin-top: 6px; margin-bottom: 6px; }");
    }

    @Test
    @DisplayName("should keep the earlier value when the last attribute is not a length")
    void shouldKeepEarlierValue_whenLaterOneIsNotALength() {
        assertThat(rule("marginwidth=\"5\" leftmargin=\"wide\"")).isEqualTo("body { margin-left: 5px; margin-right: 5px; }");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" is {1} px")
    @CsvSource({"0,0", "12,12", "' 7 ',7", "+4,4", "10px,10", "10PX,10", "10 px,10", "10.5,10", "10.,10", "007,7", "1000,1000",
            "1001,1000", "99999,1000", "123456789012345678901234567890,1000"})
    @DisplayName("should read pixels, with or without px and a fraction, and clamp what is absurd")
    void shouldReadPixels(String value, long pixels) {
        assertThat(rule("leftmargin=\"" + value + "\"")).isEqualTo("body { margin-left: " + pixels + "px; margin-right: " + pixels + "px; }");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "abc", "-5", "5%", "1e3", "5em", "5 5", "0x10", ".5", "px"})
    @DisplayName("should ignore a value that is not a length in pixels")
    void shouldIgnoreValue_thatIsNotALength(String value) {
        assertThat(LegacyBodyMargins.rule(page("leftmargin=\"" + value + "\" topmargin=\"" + value + "\""))).isEmpty();
    }

    @Test
    @DisplayName("should return nothing when the body has none of the attributes")
    void shouldReturnEmpty_whenNoMarginAttributes() {
        assertThat(LegacyBodyMargins.rule(page(""))).isEmpty();
        assertThat(LegacyBodyMargins.rule(page("class=\"x\" bgcolor=\"#eeeeee\" onload=\"f()\""))).isEmpty();
    }

    @Test
    @DisplayName("should read only the body's attributes")
    void shouldReadOnly_theBody() {
        Document document = Jsoup.parse("<html leftmargin=\"4\"><head></head><body><div leftmargin=\"6\" topmargin=\"6\">FAKE</div></body></html>");

        assertThat(LegacyBodyMargins.rule(document)).isEmpty();
    }

    @Test
    @DisplayName("should read the attribute names however they were written")
    void shouldReadAttributeNames_whateverTheCase() {
        assertThat(rule("LeftMargin=\"0\" TOPMARGIN=\"0\""))
                .isEqualTo("body { margin-left: 0px; margin-right: 0px; margin-top: 0px; margin-bottom: 0px; }");
    }

    @Test
    @DisplayName("should not change the document")
    void shouldNotChange_theDocument() {
        Document document = page("leftmargin=\"0\" topmargin=\"0\"");
        String before = document.outerHtml();

        LegacyBodyMargins.rule(document);

        assertThat(document.outerHtml()).isEqualTo(before);
    }

    @Test
    @DisplayName("should reject a number followed by a very long run of white space and a letter in linear time")
    void shouldRejectLongWhitespaceRun_inLinearTime() {
        // With the quantifiers that gave back what they took, 60,000 spaces cost seconds (the square of their number).
        String hostile = "1" + " ".repeat(60_000) + "x";

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThat(LegacyBodyMargins.rule(page("leftmargin=\"" + hostile + "\""))).isEmpty());
    }
}
