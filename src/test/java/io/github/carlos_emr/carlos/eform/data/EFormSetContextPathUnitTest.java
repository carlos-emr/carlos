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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EForm#setContextPath(String)}, which rewrites the {@code ${oscar_javascript_path}}
 * marker in the form HTML into a real, browser-facing {@code <context>/library/} URL prefix.
 *
 * <p>The marker is a servlet URL prefix, not a filesystem path, so the method must build the URL by
 * plain string substitution and must tolerate a trailing slash on the context path. Only a
 * {@code null} context path (no servlet environment) leaves the HTML untouched; an empty string
 * ({@code ""}) is a valid root-context (ROOT.war) deployment and must still be normalized.</p>
 *
 * @since 2026-06-01
 */
@Tag("unit")
@Tag("fast")
@DisplayName("EForm.setContextPath")
class EFormSetContextPathUnitTest {

    /** The literal marker EForm replaces (EFormBase.jsMarker). */
    private static final String JS_MARKER = "${oscar_javascript_path}";

    private static EForm formWithMarker() {
        EForm eform = new EForm();
        eform.setFormHtml("<script src=\"" + JS_MARKER + "eform.js\"></script>");
        return eform;
    }

    @Test
    @DisplayName("should rewrite the javascript marker to <context>/library/")
    void shouldRewriteJsMarker_withContextPath() {
        EForm eform = formWithMarker();

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("<script src=\"/carlos/library/eform.js\"></script>")
                .doesNotContain(JS_MARKER);
    }

    @Test
    @DisplayName("should strip a trailing slash on the context path before building the library URL")
    void shouldRewriteJsMarker_whenContextPathHasTrailingSlash() {
        EForm eform = formWithMarker();

        eform.setContextPath("/carlos/");

        assertThat(eform.getFormHtml())
                .contains("<script src=\"/carlos/library/eform.js\"></script>")
                .doesNotContain(JS_MARKER);
    }

    @Test
    @DisplayName("should rewrite legacy relative jquery references to the displayImage asset route")
    void shouldRewriteLegacyRelativeJqueryReference_whenContextPathSet() {
        EForm eform = new EForm();
        eform.setFormHtml("<script src=\"jquery-1.12.0.min.js\"></script><script src=\"/eform/jquery-1.12.0.min.js\"></script>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .doesNotContain("src=\"jquery-1.12.0.min.js\"")
                .doesNotContain("src=\"/eform/jquery-1.12.0.min.js\"")
                .contains("src=\"/carlos/eform/displayImage?imagefile=jquery-1.12.0.min.js\"");
    }

    @Test
    @DisplayName("should inject a loadSig fallback when the form calls it but does not define it")
    void shouldInjectLoadSigFallback_whenBodyOnloadCallsLoadSigWithoutDefinition() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body onload=\"startUp(); loadSig();\"><script>function startUp(){}</script></body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("window.loadSig = window.loadSig || function loadSig() {};")
                .contains("body onload=\"startUp(); loadSig();\"");
    }

    @Test
    @DisplayName("should still inject a loadSig fallback when only an inline call is present")
    void shouldInjectLoadSigFallback_whenInlineCallIsNotADefinition() {
        EForm eform = new EForm();
        // An inline window.loadSig() CALL must not be mistaken for a definition; the fallback is
        // still required so onload's loadSig() has something to invoke.
        eform.setFormHtml("<html><body onload=\"loadSig();\"><script>window.loadSig();</script></body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("window.loadSig = window.loadSig || function loadSig() {};");
    }

    @Test
    @DisplayName("should not inject a loadSig fallback when a real definition is present")
    void shouldNotInjectLoadSigFallback_whenDefinitionPresent() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body onload=\"loadSig();\"><script>window.loadSig = function(){};</script></body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml()).doesNotContain("|| function loadSig() {};");
    }

    @Test
    @DisplayName("should leave the form HTML unchanged when the context path is null")
    void shouldLeaveHtmlUnchanged_whenContextPathNull() {
        EForm eform = formWithMarker();
        String original = eform.getFormHtml();

        eform.setContextPath(null);

        assertThat(eform.getFormHtml()).isEqualTo(original);
        assertThat(eform.getFormHtml()).contains(JS_MARKER);
    }

    @Test
    @DisplayName("should apply legacy normalization for a root-context deployment")
    void shouldNormalizeLegacyAssets_forRootContextPath() {
        EForm eform = new EForm();
        eform.setFormHtml("<script src=\"jquery-1.12.0.min.js\"></script>");

        eform.setContextPath("");

        assertThat(eform.getFormHtml())
                .contains("src=\"/eform/displayImage?imagefile=jquery-1.12.0.min.js\"")
                .doesNotContain("src=\"jquery-1.12.0.min.js\"");
    }

    @Test
    @DisplayName("should treat a whitespace-only context path as root context")
    void shouldNormalizeLegacyAssets_forWhitespaceContextPath() {
        EForm eform = new EForm();
        eform.setFormHtml("<script src=\"jquery-1.12.0.min.js\"></script>");

        eform.setContextPath("   ");

        assertThat(eform.getFormHtml())
                .contains("src=\"/eform/displayImage?imagefile=jquery-1.12.0.min.js\"")
                .doesNotContain("   /eform/displayImage");
    }

    @Test
    @DisplayName("should rewrite legacy string timers only inside inline script content")
    void shouldRewriteLegacyStringTimers_onlyInsideInlineScripts() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body>"
                + "<textarea>setTimeout('literal textarea', 100)</textarea>"
                + "<div data-timer=\"setInterval('literal attribute', 200)\"></div>"
                + "<script>setTimeout('loadSig()', 300); setInterval('tick()', 400);</script>"
                + "</body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("<textarea>setTimeout('literal textarea', 100)</textarea>")
                .contains("data-timer=\"setInterval('literal attribute', 200)\"")
                .contains("setTimeout(function(){ loadSig() }, 300)")
                .contains("setInterval(function(){ tick() }, 400)");
    }

    @Test
    @DisplayName("should rewrite legacy string timers with double-quoted code bodies")
    void shouldRewriteLegacyStringTimers_withDoubleQuotedCodeBodies() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body>"
                + "<script>setTimeout(\"loadSig()\", 300); setInterval(\"say('hi')\", 400);</script>"
                + "</body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("setTimeout(function(){ loadSig() }, 300)")
                .contains("setInterval(function(){ say('hi') }, 400)");
    }

    @Test
    @DisplayName("should neutralize a real </script> but leave </script followed by a vertical tab intact")
    void shouldNeutralizeScriptClose_onlyForHtmlDelimiters() {
        EForm eform = new EForm();
        // Decoded timer body: var a='</script>'  (real close — must be neutralized to <\/script>)
        //                     var b='</scriptx'  (vertical tab after 'script' — NOT an HTML tag
        //                     delimiter, so it must be left intact; Java \s wrongly matched it, HOdZ).
        eform.setFormHtml("<html><body>"
                + "<script>setTimeout(\"var a='<\\/script>'; var b='<\\/script\\vx'\", 100);</script>"
                + "</body></html>");

        eform.setContextPath("/carlos");

        String html = eform.getFormHtml();
        assertThat(html).contains("<\\/script>");            // real close-tag neutralized
        assertThat(html).contains("</scriptx");        // vertical-tab sequence preserved
        assertThat(html).doesNotContain("<\\/scriptx"); // and NOT neutralized
    }

    @Test
    @DisplayName("should rewrite legacy string timers when the code body contains escaped matching quotes")
    void shouldRewriteLegacyStringTimers_withEscapedMatchingQuotesInCodeBody() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body>"
                + "<script>setTimeout(\"$('#field').val(\\\"done\\\")\", 100); setInterval('say(\\'hi\\')', 200);</script>"
                + "</body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("setTimeout(function(){ $('#field').val(\"done\") }, 100)")
                .contains("setInterval(function(){ say('hi') }, 200)");
    }

    @Test
    @DisplayName("should not entity-escape script operators when normalizing a rewritten inline script")
    void shouldNotEntityEscapeScriptOperators_whenNormalizingRewrittenScript() {
        EForm eform = new EForm();
        // A single inline script that both triggers the legacy-timer rewrite AND uses a '<' operator.
        // The DOM normalization must emit the body verbatim; escaping '<' to '&lt;' would break the JS.
        eform.setFormHtml("<html><body>"
                + "<script>setTimeout('poll()', 100); for (var i=0; i<n; i++) { total += i; }</script>"
                + "</body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("i<n")
                .doesNotContain("i&lt;n");
    }

    @Test
    @DisplayName("should decode string-literal escapes when rewriting legacy string timers")
    void shouldDecodeStringLiteralEscapes_whenRewritingLegacyStringTimers() {
        EForm eform = new EForm();
        // The body is a JS string literal; hoisting it into a function body must decode its escapes
        // once (\n -> newline, \x41 -> 'A') the way the engine would when evaluating the string —
        // NOT leave a literal backslash-n, which is invalid function source.
        eform.setFormHtml("<html><body>"
                + "<script>setTimeout('a\\nb; c=\\x41', 100)</script>"
                + "</body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("setTimeout(function(){ a\nb; c=A }, 100)")
                .doesNotContain("a\\nb")
                .doesNotContain("\\x41");
    }

    @Test
    @DisplayName("should not expose a raw script-closing tag when decoding a legacy string timer")
    void shouldNeutralizeScriptClose_whenDecodedTimerBodyClosesTheScript() {
        EForm eform = new EForm();
        // The legacy string body writes a </script> escaped as <\/script> — decoding resolves the
        // \/ to /, so without re-escaping the hoisted body would contain a raw </script> that
        // truncates the inline script mid-parse. The rewrite must keep it as <\/script>.
        eform.setFormHtml("<html><body>"
                + "<script>setTimeout('document.write(\"a<\\/script>b\")', 100)</script>"
                + "</body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("setTimeout(function(){ document.write(\"a<\\/script>b\") }, 100)")
                .doesNotContain("a</script>b");
    }

    @Test
    @DisplayName("should not escape a non-closing script identifier when rewriting legacy string timers")
    void shouldNotEscapeNonClosingScriptToken_whenRewritingLegacyStringTimers() {
        EForm eform = new EForm();
        // "</scripting" is not a script end tag (no whitespace / "/" / ">" after "script"), so the
        // script-close neutralization must leave it verbatim rather than emitting an invalid "<\/".
        eform.setFormHtml("<html><body>"
                + "<script>setTimeout('doc.querySelector(\"a[href=</scripting]\")', 100)</script>"
                + "</body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("a[href=</scripting]")
                .doesNotContain("<\\/scripting");
    }

}
