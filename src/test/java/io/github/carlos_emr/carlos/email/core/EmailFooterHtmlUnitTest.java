/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * The email footer's formatting (issue #3981): the server-side allow-list, the plain-text version
 * and the formatted email document.
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
class EmailFooterHtmlUnitTest {

    @Nested
    @DisplayName("clean")
    class Clean {

        @Test
        @DisplayName("should keep bold, italic, line breaks, paragraphs and https or mailto links")
        void shouldKeepAllowedFormatting_forEditorOutput() {
            String footer = "<p><b>Riverside</b> <strong>Clinic</strong></p><div><i>Dr.</i> <em>Test</em><br>"
                    + "<a href=\"https://clinic.example/hours\">Hours</a> <a href=\"mailto:desk@clinic.example\">Desk</a></div>";

            assertThat(EmailFooterHtml.clean(footer)).isEqualTo(footer);
        }

        @Test
        @DisplayName("should drop scripts, event handlers, styles, images and other tags, keeping their text")
        void shouldDropEverythingElse_whenFooterCarriesUnsafeMarkup() {
            String cleaned = EmailFooterHtml.clean("<span style=\"color:red\" onmouseover=\"x()\">Clinic</span>"
                    + "<script>alert(1)</script><style>p{}</style><img src=\"https://t.example/p.gif\">"
                    + "<iframe src=\"https://e.example\"></iframe><b onclick=\"y()\">Open</b>");

            assertThat(cleaned).isEqualTo("Clinic<b>Open</b>");
        }

        @Test
        @DisplayName("should drop link addresses that are not https or mailto")
        void shouldDropHref_whenProtocolNotAllowed() {
            assertThat(EmailFooterHtml.clean("<a href=\"javascript:alert(1)\">A</a>"
                    + "<a href=\"http://clinic.example\">B</a><a href=\"data:text/html,x\">C</a>"
                    + "<a href=\"/relative\">D</a>"))
                    .isEqualTo("<a>A</a><a>B</a><a>C</a><a>D</a>");
        }

        @Test
        @DisplayName("should drop disguised script links and protocol-relative links")
        void shouldDropHref_whenProtocolDisguisedOrMissing() {
            assertThat(EmailFooterHtml.clean("<a href=\"&#106;avascript:alert(1)\">A</a>"
                    + "<a href=\"java\tscript:alert(1)\">B</a><a href=\"//elsewhere.example/x\">C</a>"))
                    .isEqualTo("<a>A</a><a>B</a><a>C</a>");
        }

        @Test
        @DisplayName("should return empty for null, blank or a footer with no visible text")
        void shouldReturnEmpty_whenNoVisibleText() {
            assertThat(EmailFooterHtml.clean(null)).isEmpty();
            assertThat(EmailFooterHtml.clean(" \n ")).isEmpty();
            assertThat(EmailFooterHtml.clean("<br><p>&nbsp;</p><b> </b><img src=\"https://t.example/p.gif\">")).isEmpty();
        }

        @Test
        @DisplayName("should drop the line breaks an editor leaves at the end")
        void shouldStripTrailingBreaks_whenEditorLeavesThem() {
            assertThat(EmailFooterHtml.clean("Clinic<br> <br>\n<br>  ")).isEqualTo("Clinic");
            assertThat(EmailFooterHtml.clean("Clinic<br>Desk")).isEqualTo("Clinic<br>Desk");
            // Chrome writes an empty last line as <div><br></div>; other editors use <p>.
            assertThat(EmailFooterHtml.clean("Clinic<div>Desk</div><div><br></div><p></p><div></div><br>"))
                    .isEqualTo("Clinic<div>Desk</div>");
        }

        @Test
        @DisplayName("should clean a long run of spaces and breaks quickly")
        void shouldFinishQuickly_whenInputHasLongWhitespaceRuns() {
            String input = "A" + " ".repeat(30_000) + "B" + "<br> ".repeat(2_000);

            String cleaned = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> EmailFooterHtml.clean(input));

            assertThat(cleaned).startsWith("A").endsWith("B");
        }
    }

    @Nested
    @DisplayName("toPlainText")
    class ToPlainText {

        @Test
        @DisplayName("should keep line breaks and turn paragraphs into lines")
        void shouldKeepLines_forBreaksAndParagraphs() {
            assertThat(EmailFooterHtml.toPlainText("<b>Riverside Clinic</b><br>123 Main St<p>Not urgent</p><div>Thanks</div>"))
                    .isEqualTo("Riverside Clinic\n123 Main St\nNot urgent\nThanks");
        }

        @Test
        @DisplayName("should write a link's address after its text when the two differ")
        void shouldAppendAddress_whenLinkTextDiffers() {
            assertThat(EmailFooterHtml.toPlainText("<a href=\"https://clinic.example\">Website</a> "
                    + "<a href=\"mailto:desk@clinic.example\">desk@clinic.example</a> "
                    + "<a href=\"https://clinic.example\">https://clinic.example</a>"))
                    .isEqualTo("Website <https://clinic.example> desk@clinic.example https://clinic.example");
        }

        @Test
        @DisplayName("should decode entities, turn non-breaking spaces into spaces and collapse source whitespace")
        void shouldDecodeEntities_forTextNodes() {
            assertThat(EmailFooterHtml.toPlainText("Smith &amp; Jones&nbsp;Clinic\n   &lt;Family&gt;"))
                    .isEqualTo("Smith & Jones Clinic <Family>");
        }

        @Test
        @DisplayName("should keep at most one blank line and no trailing spaces")
        void shouldTidyLines_whenManyBreaks() {
            assertThat(EmailFooterHtml.toPlainText("A &nbsp;<br><br><br><br>B")).isEqualTo("A\n\nB");
            assertThat(EmailFooterHtml.toPlainText(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("fromPlainText and visibleLength")
    class FromPlainText {

        @Test
        @DisplayName("should escape plain text and keep its line breaks")
        void shouldEscapeAndKeepLines_forPlainText() {
            String html = EmailFooterHtml.fromPlainText("  Smith & Jones <Clinic>\r\nCall 555-0100\r");

            assertThat(html).isEqualTo("Smith &amp; Jones &lt;Clinic&gt;<br>Call 555-0100");
            assertThat(EmailFooterHtml.toPlainText(EmailFooterHtml.clean(html)))
                    .isEqualTo("Smith & Jones <Clinic>\nCall 555-0100");
            assertThat(EmailFooterHtml.fromPlainText(" ")).isEmpty();
        }

        @Test
        @DisplayName("should count the plain-text characters, not the formatting")
        void shouldCountVisibleCharacters_forFormattedFooter() {
            assertThat(EmailFooterHtml.visibleLength("<b>Clinic</b><br><i>Desk</i>")).isEqualTo("Clinic\nDesk".length());
        }
    }

    @Nested
    @DisplayName("toHtmlDocument")
    class ToHtmlDocument {

        @Test
        @DisplayName("should escape the message, add the logo by Content-ID and clean the footer again")
        void shouldBuildDocument_withEscapedBodyLogoAndCleanFooter() {
            String html = EmailFooterHtml.toHtmlDocument("<b>not bold</b>\nline two",
                    "<b>Clinic</b><script>alert(1)</script>", "clinic-logo-\"x@carlos-emr");

            assertThat(html).startsWith("<!DOCTYPE html>")
                    .contains("&lt;b&gt;not bold&lt;/b&gt;<br>line two")
                    .contains("src=\"cid:clinic-logo-&#34;x@carlos-emr\"")
                    .contains("<b>Clinic</b>")
                    .doesNotContain("<script");
        }

        @Test
        @DisplayName("should keep the message's runs of spaces, indents and tabs in every mail app")
        void shouldKeepSpacing_forMessageText() {
            String html = EmailFooterHtml.toHtmlDocument("  Dose:    2 tabs\n  indented\tx\n\n", "Clinic", null);

            assertThat(html)
                    .contains("<div style=\"white-space: pre-wrap;\">")
                    // The first line keeps its indent too, as the plain-text version does.
                    .contains(">&nbsp;&nbsp;Dose: &nbsp;&nbsp;&nbsp;2 tabs<br>&nbsp;&nbsp;indented &nbsp;&nbsp;&nbsp;x</div>");
        }

        @Test
        @DisplayName("should add no picture when there is no logo")
        void shouldOmitImage_whenNoLogo() {
            assertThat(EmailFooterHtml.toHtmlDocument("Hi", "Clinic", null)).doesNotContain("<img");
        }
    }
}
