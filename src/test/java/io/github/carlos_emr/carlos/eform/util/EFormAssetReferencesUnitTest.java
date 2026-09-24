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
package io.github.carlos_emr.carlos.eform.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link EFormAssetReferences}.
 *
 * <p>Drives the package-private seam with an in-memory existence predicate, so the rule is tested
 * without an eForm asset directory on disk. The names used are the ones the published-eForm corpus
 * actually references.</p>
 */
@Tag("unit")
@Tag("eform")
class EFormAssetReferencesUnitTest {

    /** Assets the fake eForm asset directory can serve. */
    private static final Predicate<String> PRESENT =
            Set.of("jSignature.min.js", "onBodyLoad_Oct2018.js", "logo.png", "scan (1).png",
                    "instructions.html", "styles.css", "payload.bin", "logo+stamp.png",
                    "logo%20stamp.png")::contains;

    /** Nothing is servable — the shape every "left as authored" assertion needs. */
    private static final Predicate<String> ABSENT = name -> false;

    @Nested
    @DisplayName("references the asset route can serve")
    class Servable {

        @Test
        @DisplayName("should rewrite a bare script reference to the asset marker")
        void shouldRewriteBareScriptReference_toAssetMarker() {
            String html = "<script src=\"jSignature.min.js\"></script>";

            String result = EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT);

            assertThat(result).isEqualTo("<script src=\"${oscar_image_path}jSignature.min.js\"></script>");
        }

        @Test
        @DisplayName("should preserve the author's quote style when rewriting")
        void shouldPreserveQuoteStyle_whenRewriting() {
            String html = "<script src='onBodyLoad_Oct2018.js'></script>";

            String result = EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT);

            assertThat(result).isEqualTo("<script src='${oscar_image_path}onBodyLoad_Oct2018.js'></script>");
        }

        @Test
        @DisplayName("should rewrite a JavaScript src assignment as well as an HTML attribute")
        void shouldRewriteScriptAssignment_asWellAsAttribute() {
            String html = "<script>document.getElementById('x').src = \"logo.png\";</script>";

            String result = EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT);

            assertThat(result).contains("src = \"${oscar_image_path}logo.png\"");
        }

        @Test
        @DisplayName("should rewrite a stylesheet link but leave navigation links alone")
        void shouldRewriteStylesheetLink_withoutChangingNavigation() {
            String html = "<link rel=\"stylesheet\" href=\"styles.css\">"
                    + "<a href=\"instructions.html\">Instructions</a>"
                    + "<form action=\"instructions.html\"></form>";

            String result = EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT);

            assertThat(result).isEqualTo("<link rel=\"stylesheet\" href=\"${oscar_image_path}styles.css\">"
                    + "<a href=\"instructions.html\">Instructions</a>"
                    + "<form action=\"instructions.html\"></form>");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "<LINK HREF='styles.css' REL='StyleSheet' media='print'>",
                "<link href=\"styles.css\" rel=stylesheet>",
                "<link rel='alternate \tStyleSheet' title='Print' href='styles.css' />"
        })
        @DisplayName("should recognize stylesheet relation tokens regardless of attribute order")
        void shouldRewriteStylesheetRelations_withAnyAttributeOrder(String html) {
            assertThat(EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT))
                    .isEqualTo(html.replace("styles.css", "${oscar_image_path}styles.css"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "<link rel='canonical' href='instructions.html'>",
                "<link href='instructions.html' rel='alternate'>",
                "<link rel='next' href='instructions.html'>",
                "<link href='styles.css'>",
                "<link rel='notstylesheet' href='styles.css'>",
                "<link rel='\u017Ftylesheet' href='styles.css'>",
                "<link data-rel='stylesheet' href='styles.css'>",
                "<link title=\" rel='stylesheet'\" href='styles.css' rel='canonical'>",
                "<link rel='canonical' rel='stylesheet' href='styles.css'>",
                "<link rel='preload' href='styles.css' as='style'>"
        })
        @DisplayName("should preserve non-stylesheet link relations even when the asset exists")
        void shouldPreserveLinks_withNonStylesheetRelations(String html) {
            assertThat(EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT)).isEqualTo(html);
        }

        @Test
        @DisplayName("should rewrite every reference when a form carries more than one")
        void shouldRewriteEveryReference_whenFormCarriesSeveral() {
            String html = "<script src=\"jSignature.min.js\"></script>"
                    + "<img src=\"logo.png\"><script src=\"onBodyLoad_Oct2018.js\"></script>";

            String result = EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT);

            assertThat(result).doesNotContain("src=\"jSignature.min.js\"")
                    .doesNotContain("src=\"logo.png\"")
                    .doesNotContain("src=\"onBodyLoad_Oct2018.js\"");
            assertThat(result.split("\\$\\{oscar_image_path}", -1)).hasSize(4);
        }

        @Test
        @DisplayName("should leave a name the marker substitution will encode intact for that step")
        void shouldLeaveHostileFilenameIntact_forEncodingStep() {
            // The space and parens are URL-hostile but legal in a filename; encoding them is
            // setImagePath's job, and doing it here would double-encode.
            String html = "<img src=\"scan (1).png\">";

            String result = EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT);

            assertThat(result).isEqualTo("<img src=\"${oscar_image_path}scan (1).png\">");
        }

        @Test
        @DisplayName("should be idempotent when run twice over the same HTML")
        void shouldBeIdempotent_whenRunTwice() {
            String html = "<script src=\"jSignature.min.js\"></script>";

            String once = EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT);
            String twice = EFormAssetReferences.normalizeBareAssetReferences(once, PRESENT);

            assertThat(twice).isEqualTo(once);
        }
    }

    @Nested
    @DisplayName("references left exactly as authored")
    class LeftAlone {

        @Test
        @DisplayName("should not rewrite a bare name the asset route cannot serve")
        void shouldNotRewriteBareName_whenAssetIsAbsent() {
            // 82 corpus references, shipped in no package: pointing it at a route that would also
            // 404 buys nothing and hides where the reference came from.
            String html = "<script src=\"jquery-1.7.1.min.js\"></script>";

            String result = EFormAssetReferences.normalizeBareAssetReferences(html, ABSENT);

            assertThat(result).isEqualTo(html);
        }

        @Test
        @DisplayName("should return the identical instance when nothing changed")
        void shouldReturnIdenticalInstance_whenNothingChanged() {
            String html = "<script src=\"jquery-1.7.1.min.js\"></script>";

            assertThat(EFormAssetReferences.normalizeBareAssetReferences(html, ABSENT)).isSameAs(html);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "../eform/displayImage.do?imagefile=consult_sig_",
            "../share/calendar/calendar.js",
            "/carlos/library/eforms/APCache.js",
            "https://code.jquery.com/jquery-2.2.4.min.js",
            "//cdn.example.org/thing.js",
            "data:image/png;base64,AAAA",
            "${oscar_image_path}jSignature.min.js",
            "${oscar_javascript_path}jquery/jSignature.min.js",
            "jSignature.min.js?v=2",
            "jSignature.min.js#frag",
            "..%2fjSignature.min.js",
            " jSignature.min.js",
        })
        @DisplayName("should not rewrite a value that is not a bare filename")
        void shouldNotRewrite_forNonBareFilenameValue(String value) {
            // PRESENT deliberately, so any rewrite here is the shape rule failing rather than the
            // existence rule saving it.
            String html = "<script src=\"" + value + "\"></script>";

            assertThat(EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT)).isEqualTo(html);
        }

        @Test
        @DisplayName("should not rewrite a traversal payload even when it names a present asset")
        void shouldNotRewriteTraversalPayload_whenItNamesPresentAsset() {
            String html = "<script src=\"../../../etc/passwd\"></script>"
                    + "<script src=\"subdir/jSignature.min.js\"></script>";

            assertThat(EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT)).isEqualTo(html);
        }

        @Test
        @DisplayName("should leave markup untouched when it carries no references")
        void shouldLeaveMarkupUntouched_whenNoReferencesPresent() {
            String html = "<p>Patient instructions.</p>";

            assertThat(EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT)).isEqualTo(html);
        }

        @Test
        @DisplayName("should leave data-src, x-src, and standalone src variables unchanged")
        void shouldPreserveSrcAssignments_whenNotResourceReferences() {
            String html = "<img data-src=\"logo.png\" x-src=\"logo.png\">"
                    + "<script>var src = 'logo.png'; source.src = 'logo.png';</script>";

            assertThat(EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT))
                    .isEqualTo("<img data-src=\"logo.png\" x-src=\"logo.png\">"
                            + "<script>var src = 'logo.png'; source.src = '${oscar_image_path}logo.png';</script>");
        }

        @Test
        @DisplayName("should leave unsupported extensions and reserved query characters unchanged")
        void shouldPreserveFilenames_whenUnservableOrAmbiguous() {
            String html = "<img src=\"payload.bin\"><img src=\"logo+stamp.png\">"
                    + "<img src=\"logo%20stamp.png\">";

            assertThat(EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT)).isEqualTo(html);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<div title=\"example src='logo.png'\"></div>",
            "<link rel='stylesheet' title=\"example href='styles.css'\">",
            "<!-- <img src='logo.png'> -->",
            "<script>var template = \"<img src='logo.png'>\";</script>",
            "<p>example.src = 'logo.png';</p>",
            "<div only=\"example.src = 'logo.png';\"></div>",
            "<script type='application/json'>{\"example\":\".src = 'logo.png';\"}</script>",
            "<script>var template = `.src = 'logo.png';`;</script>",
            "<script>object.SRC = 'logo.png';</script>",
            "<script>image.src = 'logo.png' + suffix;</script>",
            "<img onload=\"var note = &quot;.src = 'logo.png';&quot;;\">"
    })
    @DisplayName("should preserve source-like text outside actual resource references")
    void shouldPreserveAuthoredText_whenNotAResourceReference(String html) {
        assertThat(EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT)).isEqualTo(html);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "var template = \".src = 'logo.png';\";",
            "// .src = 'logo.png';\n",
            "/* .src = 'logo.png'; */",
            "var pattern = /\\.src = 'logo.png'/;",
            "if (ready) /\\.src = 'logo.png'/.test(note);",
            "var scale = width / 2;",
            "n++ / 2; var note = \"http://example .src = 'logo.png';\";",
            "var scale = object.return / 2;",
            "var note = \"escaped \\\" .src = 'logo.png';\";"
    })
    @DisplayName("should rewrite executable src assignments while preserving preceding strings and comments")
    void shouldRewriteActualAssignment_afterOtherJavaScriptTokens(String prefix) {
        String html = "<script>" + prefix + " image.src = 'logo.png';</script>";
        assertThat(EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT))
                .isEqualTo("<script>" + prefix + " image.src = '${oscar_image_path}logo.png';</script>");
    }

    @Test
    @DisplayName("should preserve quoted metadata while rewriting actual attributes and event handlers")
    void shouldRewriteReferences_withQuotedMetadataPresent() {
        String html = "<img title=\"example src='logo.png'\" src='logo.png' onerror=\"this.src='logo.png';\">"
                + "<link title=\"example href='styles.css'\" href='styles.css' rel='stylesheet'>";
        assertThat(EFormAssetReferences.normalizeBareAssetReferences(html, PRESENT))
                .isEqualTo("<img title=\"example src='logo.png'\" src='${oscar_image_path}logo.png' onerror=\"this.src='${oscar_image_path}logo.png';\">"
                        + "<link title=\"example href='styles.css'\" href='${oscar_image_path}styles.css' rel='stylesheet'>");
    }

    @Nested
    @DisplayName("input handling")
    class InputHandling {

        @Test
        @DisplayName("should return null when the form has no stored HTML")
        void shouldReturnNull_whenFormHtmlIsNull() {
            assertThat(EFormAssetReferences.normalizeBareAssetReferences(null, PRESENT)).isNull();
        }

        @Test
        @DisplayName("should return empty when the stored HTML is empty")
        void shouldReturnEmpty_whenFormHtmlIsEmpty() {
            assertThat(EFormAssetReferences.normalizeBareAssetReferences("", PRESENT)).isEmpty();
        }

        @Test
        @DisplayName("should resolve each distinct name once however often it is referenced")
        void shouldResolveEachName_onceAcrossRepeatedReferences() {
            java.util.concurrent.atomic.AtomicInteger lookups = new java.util.concurrent.atomic.AtomicInteger();
            Predicate<String> counting = name -> {
                lookups.incrementAndGet();
                return PRESENT.test(name);
            };
            String html = "<script src=\"jSignature.min.js\"></script>".repeat(25)
                    + "<img src=\"logo.png\">".repeat(25);

            EFormAssetReferences.normalizeBareAssetReferences(html, counting);

            assertThat(lookups).hasValue(2);
        }
    }
}
