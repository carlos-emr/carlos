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
    @DisplayName("should inject the idempotent loadSig fallback even when a definition is present")
    void shouldInjectIdempotentFallback_whenDefinitionPresent() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body onload=\"loadSig();\"><script>window.loadSig = function(){};</script></body></html>");

        eform.setContextPath("/carlos");

        // The fallback is idempotent (window.loadSig || function loadSig(){}), so it is injected
        // unconditionally whenever the page calls loadSig(); a real definition earlier in the body is
        // preserved via the ||. This replaced a definition-detection heuristic that false-matched
        // loadSig text inside comments/strings and could suppress the fallback, breaking form onload.
        assertThat(eform.getFormHtml())
                .contains("window.loadSig = window.loadSig || function loadSig() {};");
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
    @DisplayName("should preserve timer-like source in HTML attributes and inline scripts")
    void shouldPreserveTimerSource_inAttributesAndInlineScripts() {
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
                .contains("<script>setTimeout('loadSig()', 300); setInterval('tick()', 400);</script>");
    }

    @Test
    @DisplayName("should preserve double-quoted string timer bodies")
    void shouldPreserveLegacyStringTimers_withDoubleQuotedCodeBodies() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body>"
                + "<script>setTimeout(\"loadSig()\", 300); setInterval(\"say('hi')\", 400);</script>"
                + "</body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("<script>setTimeout(\"loadSig()\", 300); setInterval(\"say('hi')\", 400);</script>");
    }

    @Test
    @DisplayName("should preserve escaped script-close text in timer strings")
    void shouldPreserveEscapedScriptCloseText_inTimerStrings() {
        EForm eform = new EForm();
        String original = "<html><body>"
                + "<script>setTimeout(\"var a='<\\/script>'; var b='<\\/script\\vx'\", 100);</script>"
                + "</body></html>";
        eform.setFormHtml(original);

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml()).isEqualTo(original);
    }

    @Test
    @DisplayName("should preserve matching quote escapes in string timer bodies")
    void shouldPreserveStringTimers_withEscapedMatchingQuotesInCodeBody() {
        EForm eform = new EForm();
        String original = "<html><body>"
                + "<script>setTimeout(\"$('#field').val(\\\"done\\\")\", 100); setInterval('say(\\'hi\\')', 200);</script>"
                + "</body></html>";
        eform.setFormHtml(original);

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml()).isEqualTo(original);
    }

    @Test
    @DisplayName("should preserve script operators during context-path normalization")
    void shouldPreserveScriptOperators_duringContextPathNormalization() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body>"
                + "<script>setTimeout('poll()', 100); for (var i=0; i<n; i++) { total += i; }</script>"
                + "</body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("i<n")
                .doesNotContain("i&lt;n");
    }

    @Test
    @DisplayName("should preserve string-literal escapes in legacy timers")
    void shouldPreserveStringLiteralEscapes_inLegacyStringTimers() {
        EForm eform = new EForm();
        String original = "<html><body>"
                + "<script>setTimeout('a\\nb; c=\\x41', 100)</script>"
                + "</body></html>";
        eform.setFormHtml(original);

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml()).isEqualTo(original);
    }

    @Test
    @DisplayName("should preserve an escaped script-closing tag in a legacy string timer")
    void shouldPreserveScriptClose_whenTimerBodyContainsOne() {
        EForm eform = new EForm();
        String original = "<html><body>"
                + "<script>setTimeout('document.write(\"a<\\/script>b\")', 100)</script>"
                + "</body></html>";
        eform.setFormHtml(original);

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml()).isEqualTo(original);
    }

    @Test
    @DisplayName("should preserve a non-closing script identifier in timer source")
    void shouldPreserveNonClosingScriptToken_inTimerSource() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body>"
                + "<script>setTimeout('doc.querySelector(\"a[href=</scripting]\")', 100)</script>"
                + "</body></html>");

        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml())
                .contains("a[href=</scripting]")
                .doesNotContain("<\\/scripting");
    }

}
