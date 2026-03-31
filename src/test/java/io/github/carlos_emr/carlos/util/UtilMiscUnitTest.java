/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UtilMisc} HTML escape/unescape utilities.
 *
 * @since 2026-03-31
 */
@DisplayName("UtilMisc Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class UtilMiscUnitTest {

    @Nested
    @DisplayName("htmlEscape")
    class HtmlEscape {

        @Test
        @DisplayName("should escape ampersand")
        void shouldEscapeAmpersand() {
            assertThat(UtilMisc.htmlEscape("a&b")).isEqualTo("a&amp;b");
        }

        @Test
        @DisplayName("should escape less-than")
        void shouldEscapeLessThan() {
            assertThat(UtilMisc.htmlEscape("<tag>")).isEqualTo("&lt;tag&gt;");
        }

        @Test
        @DisplayName("should escape double quote")
        void shouldEscapeDoubleQuote() {
            assertThat(UtilMisc.htmlEscape("say \"hello\"")).isEqualTo("say &quot;hello&quot;");
        }

        @Test
        @DisplayName("should escape single quote")
        void shouldEscapeSingleQuote() {
            assertThat(UtilMisc.htmlEscape("it's")).isEqualTo("it&#39;s");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNull() {
            assertThat(UtilMisc.htmlEscape(null)).isNull();
        }

        @Test
        @DisplayName("should return unchanged string when no special chars")
        void shouldReturnUnchanged_whenNoSpecialChars() {
            assertThat(UtilMisc.htmlEscape("hello world")).isEqualTo("hello world");
        }

        @Test
        @DisplayName("should escape all special characters in one string")
        void shouldEscapeAll_inOneString() {
            String result = UtilMisc.htmlEscape("<a href=\"x\" title='y'>&z</a>");
            assertThat(result).contains("&lt;");
            assertThat(result).contains("&gt;");
            assertThat(result).contains("&quot;");
            assertThat(result).contains("&#39;");
            assertThat(result).contains("&amp;");
        }
    }

    @Nested
    @DisplayName("rhtmlEscape")
    class RhtmlEscape {

        @Test
        @DisplayName("should unescape &amp; to &")
        void shouldUnescapeAmp() {
            assertThat(UtilMisc.rhtmlEscape("a&amp;b")).isEqualTo("a&b");
        }

        @Test
        @DisplayName("should unescape &lt; to <")
        void shouldUnescapeLt() {
            assertThat(UtilMisc.rhtmlEscape("&lt;tag&gt;")).isEqualTo("<tag>");
        }

        @Test
        @DisplayName("should unescape &quot; to double quote")
        void shouldUnescapeQuot() {
            assertThat(UtilMisc.rhtmlEscape("say &quot;hello&quot;")).isEqualTo("say \"hello\"");
        }

        @Test
        @DisplayName("should unescape &#39; to single quote")
        void shouldUnescapeApos() {
            assertThat(UtilMisc.rhtmlEscape("it&#39;s")).isEqualTo("it's");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNull() {
            assertThat(UtilMisc.rhtmlEscape(null)).isNull();
        }

        @Test
        @DisplayName("should return unchanged when no entities")
        void shouldReturnUnchanged_whenNoEntities() {
            assertThat(UtilMisc.rhtmlEscape("hello world")).isEqualTo("hello world");
        }
    }

    @Nested
    @DisplayName("htmlEscape/rhtmlEscape round-trip")
    class RoundTrip {

        @Test
        @DisplayName("should survive escape then unescape for basic chars")
        void shouldSurviveRoundTrip() {
            String original = "<b>Hello & 'World'</b>";
            String escaped = UtilMisc.htmlEscape(original);
            String unescaped = UtilMisc.rhtmlEscape(escaped);
            assertThat(unescaped).isEqualTo(original);
        }
    }
}
