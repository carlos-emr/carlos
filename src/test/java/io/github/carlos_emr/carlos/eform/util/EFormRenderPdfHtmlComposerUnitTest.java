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
                .contains("<body style='width:640px;'>");
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
    @DisplayName("should append the render grant to image asset URLs when rendering")
    void shouldAppendRenderToken_whenBrowserRenderingImageBearingForm() {
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

        // Both the /eform/displayImage form and the ${oscar_image_path} form carry the grant so the
        // sessionless render browser can fetch each asset image.
        assertThat(html)
                .contains("/carlos/EFormImageViewForPdfGenerationServlet?renderToken=grant-abc123&imagefile=bg.png")
                .contains("/EFormImageViewForPdfGenerationServlet?renderToken=grant-abc123&imagefile=logo.png");
    }

    @Test
    @DisplayName("should URI-encode the render grant before splicing it into asset URLs")
    void shouldEncodeRenderToken_whenTokenCarriesMetacharacters() {
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
                .contains("renderToken=%22%3E%3Cscript%3Ealert%281%29%3C%2Fscript%3E&imagefile=logo.png");
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
}
