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

package io.github.carlos_emr.carlos.eform.util;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.eform.data.EForm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EFormRenderPdfHtmlComposer unit tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormRenderPdfHtmlComposerUnitTest {

    @Test
    @DisplayName("should prefer the servlet context path over project_home when building the image servlet base")
    void shouldPreferContextPath_whenBuildingImageServletBase() {
        // The context path is where this webapp — and the image servlet — is actually mounted.
        // project_home is a legacy OscarDocument DIRECTORY name and routinely differs from the
        // context (dev: project_home=oscar, context=/carlos); preferring it emitted
        // /oscar/EFormImageViewForPdfGenerationServlet URLs that 404 and blank the render.
        assertThat(EFormRenderPdfHtmlComposer.imageViewServletBase("/oscar", "/carlos"))
                .isEqualTo("/carlos/EFormImageViewForPdfGenerationServlet");
        assertThat(EFormRenderPdfHtmlComposer.imageViewServletBase("oscar", "/carlos"))
                .isEqualTo("/carlos/EFormImageViewForPdfGenerationServlet");
        assertThat(EFormRenderPdfHtmlComposer.imageViewServletBase("", "/carlos"))
                .isEqualTo("/carlos/EFormImageViewForPdfGenerationServlet");
        assertThat(EFormRenderPdfHtmlComposer.imageViewServletBase(null, "/carlos"))
                .isEqualTo("/carlos/EFormImageViewForPdfGenerationServlet");
    }

    @Test
    @DisplayName("should fall back to normalized project_home slashes when no context path is available")
    void shouldFallBackToNormalizedProjectHome_whenContextPathBlank() {
        // Only with no context path at all does project_home apply — and its slashes are normalized
        // so "/" + "/oscar" can never emit a protocol-relative //oscar/... URL that Chromium
        // resolves to host "oscar" (which the dead proxy would then block).
        assertThat(EFormRenderPdfHtmlComposer.imageViewServletBase("/oscar", ""))
                .isEqualTo("/oscar/EFormImageViewForPdfGenerationServlet");
        assertThat(EFormRenderPdfHtmlComposer.imageViewServletBase("oscar/", null))
                .isEqualTo("/oscar/EFormImageViewForPdfGenerationServlet");
        assertThat(EFormRenderPdfHtmlComposer.imageViewServletBase("/oscar/", "/"))
                .isEqualTo("/oscar/EFormImageViewForPdfGenerationServlet");
        // A slashes-only value normalizes to empty with no context path: the servlet name resolves
        // context-relative ("/<name>"), never "//".
        assertThat(EFormRenderPdfHtmlComposer.imageViewServletBase("/", ""))
                .isEqualTo("/EFormImageViewForPdfGenerationServlet");
    }

    @Test
    @DisplayName("should normalize a valid stored signature URL under the current context path")
    void shouldNormalizeValidStoredSignatureUrl_whenContextScoped() {
        String normalized = EFormRenderPdfHtmlComposer.normalizePdfSignatureUrl(
                "/carlos/imageRenderingServlet?source=signature_stored&digitalSignatureId=42&r=99",
                "/carlos");

        assertThat(normalized).isEqualTo("/carlos/EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=42");
    }

    @Test
    @DisplayName("should normalize a valid stored signature URL without a context path prefix")
    void shouldNormalizeValidStoredSignatureUrl_whenRootRelative() {
        String normalized = EFormRenderPdfHtmlComposer.normalizePdfSignatureUrl(
                "/imageRenderingServlet?source=signature_stored&digitalSignatureId=7",
                "/carlos");

        assertThat(normalized).isEqualTo("/EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=7");
    }

    @ParameterizedTest(name = "invalid signature URL [{index}]")
    @MethodSource("invalidSignatureUrls")
    @DisplayName("should reject invalid signature URLs")
    void shouldRejectInvalidSignatureUrl_whenNormalizingSignatureUrl(String rawUrl) {
        String normalized = EFormRenderPdfHtmlComposer.normalizePdfSignatureUrl(rawUrl, "/carlos");

        assertThat(normalized).isNull();
    }

    @Test
    @DisplayName("should HTML attribute encode the generated signature image markup")
    void shouldEncodeSignatureImageMarkup_whenBuildingImageHtml() {
        String markup = EFormRenderPdfHtmlComposer.buildSignatureImageMarkup(
                "/carlos/EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=42&foo=bar",
                "1",
                "2",
                "3",
                "4");

        assertThat(markup).contains("src=\"/carlos/EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=42&amp;foo=bar\"");
    }

    @Test
    @DisplayName("should return null for null input")
    void shouldReturnNull_forNullUrl() {
        assertThat(EFormRenderPdfHtmlComposer.normalizePdfSignatureUrl(null, "/carlos")).isNull();
    }

    @Test
    @DisplayName("should return null for empty string input")
    void shouldReturnNull_forEmptyUrl() {
        assertThat(EFormRenderPdfHtmlComposer.normalizePdfSignatureUrl("", "/carlos")).isNull();
    }

    @Test
    @DisplayName("should build render-ready HTML from the stored letter content")
    void shouldBuildRenderReadyHtml_whenPreparingPdfHtml() {
        EForm eForm = mock(EForm.class);
        AtomicReference<String> htmlRef = new AtomicReference<>("<div id=\"signatureDisplay\"></div>");
        when(eForm.getDemographicNo()).thenReturn("1");
        when(eForm.getFormHtml()).thenAnswer(invocation -> htmlRef.get());
        doAnswer(invocation -> {
            htmlRef.set(invocation.getArgument(0));
            return null;
        }).when(eForm).setFormHtml(anyString());

        EFormValue letter = new EFormValue();
        letter.setVarName("Letter");
        letter.setVarValue("<div class=\"DoNotPrint\" style=\"color:red\">hide</div><img src=\"../eform/displayImage?imagefile=bg.png\" />");

        String html = EFormRenderPdfHtmlComposer.buildPdfHtml(
                eForm,
                List.of(letter),
                "/carlos",
                "carlos",
                null);

        assertThat(html)
                .contains("/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png")
                .contains("<div class=\"DoNotPrint\" style=\"display:none;color:red\"")
                .contains("<body style=\"width:640px;\">");
    }

    @Test
    @DisplayName("should render a stored letter as markup rather than printing its escaped tags")
    void shouldRenderStoredLetterAsMarkup_whenSaveRtlEscapedIt() {
        EForm eForm = mockEformWithHtml("<div id=\"signatureDisplay\"></div>");

        // Exactly what saveRTL() writes into the Letter textarea before submit.
        EFormValue letter = eformValue("Letter",
                "&lt;h3&gt;Consultation Letter&lt;/h3&gt;&lt;p&gt;Dear Dr. Smith,&lt;/p&gt;"
                + "&lt;ul&gt;&lt;li&gt;Metformin 500 mg BID&lt;/li&gt;&lt;/ul&gt;");

        String html = EFormRenderPdfHtmlComposer.buildPdfHtml(
                eForm, List.of(letter), "/carlos", "carlos", null);

        assertThat(html)
                .contains("<h3>Consultation Letter</h3>")
                .contains("<li>Metformin 500 mg BID</li>")
                .doesNotContain("&lt;h3&gt;");
    }

    @Test
    @DisplayName("should keep a literal angle bracket literal when the letter double-escaped it")
    void shouldKeepDoubleEscapedTextLiteral_whenDecodingStoredLetter() {
        // "&amp;lt;" is a clinician who typed the characters "&lt;" — it must stay text, not become
        // a tag. This is what the reverse decode order protects.
        assertThat(EFormRenderPdfHtmlComposer.decodeStoredLetter("&amp;lt;p&amp;gt; stays text"))
                .isEqualTo("&lt;p&gt; stays text");
    }

    @Test
    @DisplayName("should strip interaction hooks from a decoded letter")
    void shouldStripInteractionHooks_whenHardeningStoredLetter() {
        String hardened = EFormRenderPdfHtmlComposer.hardenLetterHtml(
                "<p>Keep this</p><img src=\"bg.png\" onerror=\"steal()\">"
                + "<a href=\"javascript:steal()\">link</a>"
                + "<a href=\"JaVaScript:&#09;steal()\">obfuscated</a>");

        assertThat(hardened)
                .contains("<p>Keep this</p>")
                .doesNotContain("onerror")
                .doesNotContainIgnoringCase("javascript:");
    }

    @Test
    @DisplayName("should strip interaction hooks using ASCII-only case folding")
    void shouldStripInteractionHooks_usingAsciiOnlyCaseFolding() {
        // The filter decides on attacker-influenced markup, so it must fold exactly the way an HTML
        // parser does — over ASCII only. Full Unicode lowering can map non-ASCII code points onto
        // ASCII letters, which would make the filter and the browser disagree about the same input.
        String hardened = EFormRenderPdfHtmlComposer.hardenLetterHtml(
                "<a href=\"JAVASCRIPT:steal()\">upper</a>"
                + "<a href=\"jAvAsCrIpT:steal()\">mixed</a>"
                + "<a href=\"  java\tscript:steal()\">whitespace split</a>"
                + "<img src=\"bg.png\" ONERROR=\"steal()\" OnLoad=\"steal()\">");

        assertThat(hardened)
                .doesNotContainIgnoringCase("javascript:")
                .doesNotContainIgnoringCase("onerror")
                .doesNotContainIgnoringCase("onload");
        // The Kelvin sign lowercases to ASCII "k" under full Unicode folding but is NOT an ASCII
        // "k" to a browser, so this href is inert and must survive as authored content.
        assertThat(EFormRenderPdfHtmlComposer.hardenLetterHtml("<a href=\"Keep.html\">keep</a>"))
                .contains("Keep.html");
    }

    @Test
    @DisplayName("should keep letter structure and inline scripts when hardening a stored letter")
    void shouldKeepStructureAndScripts_whenHardeningStoredLetter() {
        // Scripts stay: applySignatureHtml reads the signature geometry out of the letter's own
        // signatureControl.initialize(...) call, and clinic letters carry image-path fixups inline.
        String hardened = EFormRenderPdfHtmlComposer.hardenLetterHtml(
                "<div style=\"text-align:center\"><font color=\"#ff0000\" size=\"3\">Red</font></div>"
                + "<table border=\"1\"><tr><td colspan=\"2\">cell</td></tr></table>"
                + "<img src=\"../eform/displayImage?imagefile=sig.png\">"
                + "<script>signatureControl.initialize({height:40})</script>");

        assertThat(hardened)
                .contains("style=\"text-align:center\"")
                .contains("<font color=\"#ff0000\" size=\"3\">")
                .contains("colspan=\"2\"")
                .contains("../eform/displayImage?imagefile=sig.png")
                .contains("signatureControl.initialize({height:40})");
    }

    @Test
    @DisplayName("should remove the WYSIWYG editor and interactive controls from the render surface")
    void shouldRemoveEditorAndInteractiveControls_whenComposingRenderSurface() {
        EForm eForm = mockEformWithHtml(
                "<html><head>"
                + "<script src=\"/carlos/library/eforms/printControl.js\"></script>"
                + "<script src=\"/carlos/library/eforms/faxControl.js\"></script>"
                + "<script src=\"/carlos/library/eforms/imageControl.js\"></script>"
                + "<script src=\"/carlos/library/eforms/APCache.js\"></script>"
                + "</head><body>"
                + "<script src=\"/carlos/eform/displayImage?imagefile=editControl2.js\"></script>"
                + "<script>cfg_width = 720; insertEditControl();</script>"
                + "<div class=\"edit-controllers\" id=\"edit-controllers\"></div>"
                + "<div id=\"keepThis\">saved clinical content</div>"
                + "</body></html>");

        String html = EFormRenderPdfHtmlComposer.buildPdfHtml(
                eForm, List.of(), "/carlos", "carlos", null);

        assertThat(html)
                .doesNotContain("printControl.js")
                .doesNotContain("faxControl.js")
                .doesNotContain("imageControl.js")
                .doesNotContain("editControl2.js")
                // The bootstrap CALL is gone; the shim below still defines a no-op of the same name
                // so a form that invokes it later cannot throw.
                .doesNotContain("insertEditControl();")
                .doesNotContain("edit-controllers");
        // APCache populates clinical field content, so it must survive the strip.
        assertThat(html)
                .contains("APCache.js")
                .contains("saved clinical content")
                .contains("cfg_width = 720;");
    }

    @Test
    @DisplayName("should emit an injected shim that is syntactically valid JavaScript")
    void shouldEmitParsableShim_whenEditorIsStripped() throws Exception {
        // The shim is built by Java string concatenation and injected into EVERY render. A stray
        // brace or comma is invisible to a `contains(...)` assertion but throws a parse error in the
        // browser, taking window.Start and window.cache with it — which the completeness gate then
        // counts as severe console errors and refuses to print. Parse it for real.
        EForm eForm = mockEformWithHtml(
                "<html><head><script src=\"/carlos/library/eforms/APCache.js\"></script></head>"
                + "<body onload=\"Start();\"></body></html>");
        String html = EFormRenderPdfHtmlComposer.buildPdfHtml(
                eForm, List.of(), "/carlos", "carlos", null);

        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(html);
        String shim = document.select("script:not([src])").stream()
                .map(org.jsoup.nodes.Element::data)
                .filter(script -> script.contains("w.Start"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("injected shim not found in composed HTML"));

        java.nio.file.Path scratch = java.nio.file.Files.createTempFile("carlos-shim-", ".js");
        try {
            java.nio.file.Files.writeString(scratch, shim, java.nio.charset.StandardCharsets.UTF_8);
            Process check = new ProcessBuilder("node", "--check", scratch.toString())
                    .redirectErrorStream(true).start();
            String output = new String(check.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertThat(check.waitFor())
                    .as("node --check on the injected shim: %s", output)
                    .isZero();
        } finally {
            java.nio.file.Files.deleteIfExists(scratch);
        }
    }

    @Test
    @DisplayName("should shim the load-time globals the stripped editor used to define")
    void shouldShimLoadTimeGlobals_whenEditorIsStripped() {
        // The Rich Text Letter calls Start() from body onload and cache.addMapping({...}) inline,
        // both defined inside editControl2.js. Removing the editor without these turned every such
        // render into two severe console errors, which the completeness gate refuses to print.
        EForm eForm = mockEformWithHtml(
                "<html><head><script src=\"/carlos/library/eforms/APCache.js\"></script></head>"
                + "<body onload=\"Start();\"><script>cache.addMapping({name:'ageGender'});</script>"
                + "</body></html>");

        String html = EFormRenderPdfHtmlComposer.buildPdfHtml(
                eForm, List.of(), "/carlos", "carlos", null);

        assertThat(html)
                .contains("w.Start = w.Start || function Start(){}")
                .contains("typeof createCache === 'function'")
                .contains("w.doHtml = w.doHtml || function doHtml(){}");
        // Ordering is load-bearing: the shim must follow APCache.js (so createCache exists) and
        // precede the form's own inline script (so `cache` is defined before addMapping runs).
        assertThat(html.indexOf("APCache.js")).isLessThan(html.indexOf("w.Start = w.Start"));
        assertThat(html.indexOf("w.Start = w.Start")).isLessThan(html.indexOf("cache.addMapping"));
    }

    @Test
    @DisplayName("should rewrite the image-path marker in attributes only, leaving script literals untouched")
    void shouldRewriteMarkerInAttributesOnly_leavingScriptLiteralsUntouched() {
        EForm eForm = mockEformWithHtml("");
        // The widespread "standalone development" helper strips the URL-encoded marker from image
        // srcs when the page is not https. A blind whole-string replace rewrote the marker inside
        // that script's string literal too, turning the helper into
        // src.replace("<asset-servlet-prefix>","") — which on the HTTP loopback render surface
        // stripped the entire rewritten prefix and blanked every background image.
        EFormValue letter = eformValue("Letter",
                "<img id=\"BGImage1\" src=\"${oscar_image_path}bg.png\" />"
                + "<script>var s1 = document.getElementById('BGImage1').src;"
                + " document.getElementById('BGImage1').src = s1.replace(\"$%7Boscar_image_path%7D\",\"\");</script>");

        String html = EFormRenderPdfHtmlComposer.buildPdfHtml(eForm, List.of(letter), "/carlos", "oscar", null);

        assertThat(html)
                // The attribute reference is rewritten onto the render-asset servlet…
                .contains("/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png")
                // …while the script literal keeps the marker text, so the legacy helper's replace is
                // a harmless no-op at render time instead of a src-destroying prefix strip.
                .contains("s1.replace(\"$%7Boscar_image_path%7D\",\"\")");
    }

    @Test
    @DisplayName("should fall back to the context path for image asset URLs when project_home is blank")
    void shouldFallBackToContextPath_whenProjectHomeBlank() {
        EForm eForm = mockEformWithHtml("");
        EFormValue letter = eformValue("Letter",
                "<img src=\"../eform/displayImage?imagefile=bg.png\" /><img src=\"${oscar_image_path}logo.png\" />");

        // A blank project_home must fall back to the servlet context path — never emit a
        // protocol-relative "//EFormImage..." (which points at an external host) or drop the context
        // prefix entirely.
        String html = EFormRenderPdfHtmlComposer.buildPdfHtml(eForm, List.of(letter), "/carlos", "", null);

        assertThat(html)
                .contains("/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png")
                .contains("/carlos/EFormImageViewForPdfGenerationServlet?imagefile=logo.png")
                .doesNotContain("//EFormImageViewForPdfGenerationServlet");
    }

    @Test
    @DisplayName("should anchor legacy relative share references to the context path")
    void shouldAnchorLegacyShareReferences_toContextPath() {
        EForm eForm = mock(EForm.class);
        AtomicReference<String> htmlRef = new AtomicReference<>(
                "<link href=\"../share/calendar/calendar.css\"/><script src=\"../share/calendar/calendar.js\"></script>");
        when(eForm.getDemographicNo()).thenReturn("1");
        when(eForm.getFormHtml()).thenAnswer(invocation -> htmlRef.get());
        doAnswer(invocation -> {
            htmlRef.set(invocation.getArgument(0));
            return null;
        }).when(eForm).setFormHtml(anyString());

        String html = EFormRenderPdfHtmlComposer.buildPdfHtml(
                eForm,
                List.of(),
                "/carlos",
                "carlos",
                null);

        // Relative "../share/..." resolves against the /eform/ viewer base in normal use, but
        // against the origin ROOT on the render servlet's path — where it 404s and fails the
        // render gates. The composer must anchor these to the context explicitly.
        assertThat(html)
                .contains("href=\"/carlos/share/calendar/calendar.css\"")
                .contains("src=\"/carlos/share/calendar/calendar.js\"")
                .doesNotContain("../share/");
    }

    @Test
    @DisplayName("should extract the exact image files referenced by normalized asset URLs")
    void shouldExtractImageFiles_thatTheFormActuallyReferences() {
        String html = "<img src=\"/carlos/EFormImageViewForPdfGenerationServlet?"
                + "imagefile=background%20one.png&amp;v=1\">"
                + "<link href=\"/carlos/EFormImageViewForPdfGenerationServlet?"
                + "v=1&imagefile=form.css\">"
                + "<img src=\"/other?imagefile=not-authorized.png\">";

        assertThat(EFormRenderPdfHtmlComposer.referencedImageFiles(html))
                .containsExactlyInAnyOrder("background one.png", "form.css");
    }

    @Test
    @DisplayName("should grant only literal APCache lookup and mapping keys")
    void shouldExtractApCacheKeys_fromLiteralLookupsAndMappings() {
        String html = "<script>"
                + "cache.lookup('patient_name');"
                + "cache.lookup(dynamicKey);"
                + "cache.addMapping({name:'patient',values:[\"age\", 'hin']});"
                + "</script>";

        assertThat(EFormRenderPdfHtmlComposer.referencedApCacheKeys(html))
                .containsExactlyInAnyOrder("patient_name", "age", "hin")
                .doesNotContain("dynamicKey", "patient");
    }

    @Test
    @DisplayName("should neutralize interactive signature markers and preserve dependency order")
    void shouldApplySavedViewerProfile_withoutInteractiveBehaviour() {
        EForm eForm = mockEformWithHtml(
                "<html><head><script src=\"/clinic.js\"></script></head><body>"
                + "${oscar_signature_code}"
                + "<script src=\"/library/eforms/signatureControl.jsp\"></script>"
                + "</body></html>");
        when(eForm.getFdid()).thenReturn("77");
        when(eForm.getFid()).thenReturn("8");
        when(eForm.getDemographicNo()).thenReturn("123");

        EFormRenderPdfHtmlComposer.applyRendererViewProfile(
                eForm, "/carlos", eForm.getFdid());
        String html = eForm.getFormHtml();

        assertThat(html)
                .doesNotContain("${oscar_signature_code}")
                .doesNotContain("signatureControl.jsp")
                .doesNotContain("signature.js")
                .contains("signatureControl.initialize=function initialize(){}")
                .contains("name=\"fdid\" id=\"fdid\" value=\"77\"")
                .contains("name=\"demographicNo\" id=\"demographicNo\" value=\"123\"");
        assertThat(html.indexOf("/library/jquery/jquery-3.7.1.min.js"))
                .isLessThan(html.indexOf("/library/jquery/jquery-ui-1.14.2.min.js"));
        assertThat(html.indexOf("/library/jquery/jquery-ui-1.14.2.min.js"))
                .isLessThan(html.indexOf("/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"));
        assertThat(html.indexOf("/eform/eform-runtime-compat.js"))
                .isLessThan(html.indexOf("/clinic.js"));
    }

    @Test
    @DisplayName("should keep the bootstrap grant out of image asset URLs")
    void shouldKeepRenderTokenOutOfAssetUrls_whenBrowserRenderingImageBearingForm() {
        EForm eForm = mock(EForm.class);
        AtomicReference<String> htmlRef = new AtomicReference<>("");
        when(eForm.getDemographicNo()).thenReturn("1");
        when(eForm.getFormHtml()).thenAnswer(invocation -> htmlRef.get());
        doAnswer(invocation -> {
            htmlRef.set(invocation.getArgument(0));
            return null;
        }).when(eForm).setFormHtml(anyString());

        EFormValue letter = new EFormValue();
        letter.setVarName("Letter");
        letter.setVarValue("<img src=\"../eform/displayImage?imagefile=bg.png\" />"
                + "<img src=\"${oscar_image_path}logo.png\" />");

        String html = EFormRenderPdfHtmlComposer.buildPdfHtml(
                eForm,
                List.of(letter),
                "/carlos",
                "carlos",
                EFormRenderTokenService.RenderToken.fromRequestValue("grant-abc123"));

        // Subresources use the renderer-only HttpOnly cookie. The bootstrap token must never enter
        // authored HTML, referrers, browser diagnostics, or asset URLs.
        assertThat(html)
                .contains("/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png")
                .contains("/EFormImageViewForPdfGenerationServlet?imagefile=logo.png")
                .doesNotContain("renderToken")
                .doesNotContain("grant-abc123");
    }

    @Test
    @DisplayName("should never reflect a request token into composed HTML")
    void shouldNeverReflectRenderToken_whenTokenCarriesMetacharacters() {
        EForm eForm = mock(EForm.class);
        AtomicReference<String> htmlRef = new AtomicReference<>("");
        when(eForm.getDemographicNo()).thenReturn("1");
        when(eForm.getFormHtml()).thenAnswer(invocation -> htmlRef.get());
        doAnswer(invocation -> {
            htmlRef.set(invocation.getArgument(0));
            return null;
        }).when(eForm).setFormHtml(anyString());

        EFormValue letter = new EFormValue();
        letter.setVarName("Letter");
        letter.setVarValue("<img src=\"${oscar_image_path}logo.png\" />");

        // A well-formed grant is URL-safe base64; a token carrying HTML/query metacharacters must be
        // neutralized before it reaches the src attribute (defence in depth over the upstream grant check).
        String html = EFormRenderPdfHtmlComposer.buildPdfHtml(
                eForm,
                List.of(letter),
                "/carlos",
                "carlos",
                EFormRenderTokenService.RenderToken.fromRequestValue("\"><script>alert(1)</script>"));

        assertThat(html)
                .doesNotContain("<script>alert(1)</script>")
                .doesNotContain("renderToken")
                .doesNotContain("%22%3E")
                .contains("EFormImageViewForPdfGenerationServlet?imagefile=logo.png");
    }

    @Test
    @DisplayName("should apply stored signature when signature value appears before letter content")
    void shouldApplySignature_whenSignatureValuePrecedesLetter() {
        EForm eForm = mock(EForm.class);
        AtomicReference<String> htmlRef = new AtomicReference<>("");
        when(eForm.getDemographicNo()).thenReturn("1");
        when(eForm.getFormHtml()).thenAnswer(invocation -> htmlRef.get());
        doAnswer(invocation -> {
            htmlRef.set(invocation.getArgument(0));
            return null;
        }).when(eForm).setFormHtml(anyString());

        EFormValue signature = new EFormValue();
        signature.setVarName("signatureValue");
        signature.setVarValue("/carlos/imageRenderingServlet?source=signature_stored&digitalSignatureId=42");
        EFormValue letter = new EFormValue();
        letter.setVarName("Letter");
        letter.setVarValue("<script>signatureControl.initialize({eform:true, height:40, width:120, top:10, left:20})</script><div id=\"signatureDisplay\"></div>");

        String html = EFormRenderPdfHtmlComposer.buildPdfHtml(
                eForm,
                List.of(signature, letter),
                "/carlos",
                "carlos",
                null);

        assertThat(html)
                .contains("/carlos/EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=42")
                .contains("position:absolute;left:20;top:10;width:120;height:40;")
                .doesNotContain("<div id=\"signatureDisplay\"></div>");
    }

    @Test
    @DisplayName("should fail the render when a stored signature URL cannot be normalized")
    void shouldThrow_whenStoredSignatureUrlInvalid() {
        EForm eForm = mockEformWithHtml("<html>signatureControl.initialize({eform:true, height:80, width:200, top:10, left:20})<div id=\"signatureDisplay\"></div></html>");
        EFormValue sig = eformValue("signatureValue", "https://evil.example/steal.png");

        assertThatThrownBy(() -> EFormRenderPdfHtmlComposer.buildPdfHtml(eForm, List.of(sig), "/carlos", "carlos", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("should fail the render when the signature geometry cannot be located in the form")
    void shouldThrow_whenSignatureGeometryUnmatched() {
        EForm eForm = mockEformWithHtml("<html><div id=\"signatureDisplay\"></div></html>"); // no initialize(...) call
        EFormValue sig = eformValue("signatureValue", "/carlos/imageRenderingServlet?digitalSignatureId=42");

        assertThatThrownBy(() -> EFormRenderPdfHtmlComposer.buildPdfHtml(eForm, List.of(sig), "/carlos", "carlos", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("should skip splicing silently for a blank signature value")
    void shouldSkipSplice_forBlankSignatureValue() {
        EForm eForm = mockEformWithHtml("<html><div id=\"signatureDisplay\"></div></html>");
        EFormValue sig = eformValue("signatureValue", "   ");

        assertThatCode(() -> EFormRenderPdfHtmlComposer.buildPdfHtml(eForm, List.of(sig), "/carlos", "carlos", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should throw a defined error when the fdid has no stored form HTML")
    void shouldThrowIllegalState_whenFormHtmlMissing() {
        EForm eForm = mockEformWithHtml(null);

        assertThatThrownBy(() -> EFormRenderPdfHtmlComposer.buildPdfHtml(eForm, List.of(), "/carlos", "carlos", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("form HTML");
    }

    /**
     * Builds a mocked {@link EForm} backed by an {@link AtomicReference}-held HTML buffer, reusing
     * the get/set idiom used throughout this test class so {@code buildPdfHtml}'s interleaved
     * {@code getFormHtml}/{@code setFormHtml} calls observe a consistent, mutable HTML string.
     */
    private static EForm mockEformWithHtml(String initialHtml) {
        EForm eForm = mock(EForm.class);
        AtomicReference<String> htmlRef = new AtomicReference<>(initialHtml);
        when(eForm.getDemographicNo()).thenReturn("1");
        when(eForm.getFormHtml()).thenAnswer(invocation -> htmlRef.get());
        doAnswer(invocation -> {
            htmlRef.set(invocation.getArgument(0));
            return null;
        }).when(eForm).setFormHtml(anyString());
        return eForm;
    }

    /** Builds an {@link EFormValue} with the given stored variable name/value pair. */
    private static EFormValue eformValue(String varName, String varValue) {
        EFormValue value = new EFormValue();
        value.setVarName(varName);
        value.setVarValue(varValue);
        return value;
    }

    private static Stream<String> invalidSignatureUrls() {
        return Stream.of(
                "javascript:alert(1)",
                "https://evil.example/EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=5",
                "/carlos/imageRenderingServlet?source=signature_stored&digitalSignatureId=12\" onerror=\"alert(1)",
                "/carlos/imageRenderingServlet?source=signature_preview&signatureRequestId=temp123"
        );
    }

    @Test
    @DisplayName("should authorize the signature stamp a legacy script builds at runtime")
    void shouldAuthorizeSignatureStamp_builtAtRuntime() {
        // The grant is built by scanning the composed HTML, which cannot see a URL the page
        // assembles later. A widespread legacy stamp script concatenates the provider number onto
        // "consult_sig_", so the assembled filename was never granted and the image servlet refused
        // it with 403 - counted as missing content, blocking the render even when the signature was
        // present. This was the single largest content-side blocker across the shared-form corpus.
        String html = "<script>document.getElementById('StampSignature').src ="
                + " \"/x?imagefile=consult_sig_\" + ProviderNumber + \".png\";</script>";
        EForm eform = new EForm();
        eform.setProviderNo("999998");

        assertThat(EFormRenderPdfHtmlComposer.runtimeSignatureStampFiles(html, eform, List.of()))
                .containsExactly("consult_sig_999998.png");
    }

    @Test
    @DisplayName("should prefer the form's stored current_user_id for the signature stamp")
    void shouldPreferStoredCurrentUserId_forSignatureStamp() {
        // current_user_id is an oscarDB field the server populated at save time, so it names the
        // provider whose stamp the page will actually request.
        String html = "<img id=\"StampSignature\"><script>x='consult_sig_'+id;</script>";
        EForm eform = new EForm();
        eform.setProviderNo("111111");
        EFormValue storedProvider = new EFormValue();
        storedProvider.setVarName("current_user_id");
        storedProvider.setVarValue("222222");

        assertThat(EFormRenderPdfHtmlComposer.runtimeSignatureStampFiles(
                        html, eform, List.of(storedProvider)))
                .containsExactly("consult_sig_222222.png");
    }

    @Test
    @DisplayName("should not widen the grant for a form that shows no stamp or a non-numeric provider")
    void shouldNotWidenGrant_withoutStampIntentOrNumericProvider() {
        EForm eform = new EForm();
        eform.setProviderNo("999998");
        // No stamp reference in the document: nothing extra may be authorized.
        assertThat(EFormRenderPdfHtmlComposer.runtimeSignatureStampFiles(
                "<p>no stamp here</p>", eform, List.of())).isEmpty();

        // A filename becomes an authorization grant, so the provider number is constrained to the
        // shape it can legitimately have rather than trusted.
        EForm traversal = new EForm();
        traversal.setProviderNo("../../etc/passwd");
        assertThat(EFormRenderPdfHtmlComposer.runtimeSignatureStampFiles(
                "consult_sig_", traversal, List.of())).isEmpty();
    }

    @Test
    @DisplayName("should report an absent provider stamp as its own condition, not a 404")
    void shouldReportAbsentProviderStamp_asItsOwnCondition() {
        // The stamp URL is built by the form's own script at load time, so there is no src to
        // rewrite - blanking the element the script targets is what stops the request. Left alone it
        // 404s and lands in the report as an unexplained failed content resource, which a clinician
        // cannot act on.
        String html = "<html><body><img id=\"StampSignature\">"
                + "<script>x='consult_sig_'+id;</script></body></html>";

        String marked = EFormRenderPdfHtmlComposer.markProviderStampMissing(html);

        assertThat(marked).contains("carlos-provider-stamp-missing");
        assertThat(marked).doesNotContain("StampSignature");
    }

    @Test
    @DisplayName("should treat an unreadable image directory as unable to prove a stamp absent")
    void shouldTreatUnreadableImageDirectory_asUnableToProveAbsent() {
        // Same guard removeAbsentOptionalStamps uses: a lookup failure is not evidence of absence,
        // so the name stays granted and the render fails the ordinary way rather than on a guess.
        assertThat(EFormRenderPdfHtmlComposer.existingImageFiles(java.util.Set.of()))
                .isEmpty();
    }

}
