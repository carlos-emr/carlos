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
 * plain string substitution and must tolerate a trailing slash on the context path. A blank/unset
 * context path must leave the HTML untouched.</p>
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
    @DisplayName("should leave the form HTML unchanged when the context path is blank")
    void shouldLeaveHtmlUnchanged_whenContextPathBlank() {
        EForm eform = formWithMarker();
        String original = eform.getFormHtml();

        eform.setContextPath("   ");

        assertThat(eform.getFormHtml()).isEqualTo(original);
        assertThat(eform.getFormHtml()).contains(JS_MARKER);
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

}
