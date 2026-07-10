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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EFormViewForPdfGenerationServlet unit tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormViewForPdfGenerationServletUnitTest {

    @Test
    @DisplayName("should normalize a valid stored signature URL under the current context path")
    void shouldNormalizeValidStoredSignatureUrl_whenContextScoped() {
        String normalized = EFormViewForPdfGenerationServlet.normalizePdfSignatureUrl(
                "/carlos/imageRenderingServlet?source=signature_stored&digitalSignatureId=42&r=99",
                "/carlos");

        assertThat(normalized).isEqualTo("/carlos/EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=42");
    }

    @Test
    @DisplayName("should normalize a valid stored signature URL without a context path prefix")
    void shouldNormalizeValidStoredSignatureUrl_whenRootRelative() {
        String normalized = EFormViewForPdfGenerationServlet.normalizePdfSignatureUrl(
                "/imageRenderingServlet?source=signature_stored&digitalSignatureId=7",
                "/carlos");

        assertThat(normalized).isEqualTo("/EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=7");
    }

    @ParameterizedTest(name = "invalid signature URL [{index}]")
    @MethodSource("invalidSignatureUrls")
    @DisplayName("should reject invalid signature URLs")
    void shouldRejectInvalidSignatureUrl_whenNormalizingSignatureUrl(String rawUrl) {
        String normalized = EFormViewForPdfGenerationServlet.normalizePdfSignatureUrl(rawUrl, "/carlos");

        assertThat(normalized).isNull();
    }

    @Test
    @DisplayName("should HTML attribute encode the generated signature image markup")
    void shouldEncodeSignatureImageMarkup_whenBuildingImageHtml() {
        String markup = EFormViewForPdfGenerationServlet.buildSignatureImageMarkup(
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
        assertThat(EFormViewForPdfGenerationServlet.normalizePdfSignatureUrl(null, "/carlos")).isNull();
    }

    @Test
    @DisplayName("should return null for empty string input")
    void shouldReturnNull_forEmptyUrl() {
        assertThat(EFormViewForPdfGenerationServlet.normalizePdfSignatureUrl("", "/carlos")).isNull();
    }

    @Test
    @DisplayName("should build fax-ready HTML from the stored letter content")
    void shouldBuildFaxReadyHtml_whenPreparingPdfHtml() {
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

        String html = EFormViewForPdfGenerationServlet.buildPdfHtml(
                eForm,
                List.of(letter),
                "/carlos",
                "carlos",
                true);

        assertThat(html)
                .contains("position:absolute; margin-top:35px;")
                .contains("/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png")
                .contains("<div class=\"DoNotPrint\" style=\"display:none;color:red\"")
                .contains("<body style='width:640px;'>");
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

        String html = EFormViewForPdfGenerationServlet.buildPdfHtml(
                eForm,
                List.of(signature, letter),
                "/carlos",
                "carlos",
                false);

        assertThat(html)
                .contains("/carlos/EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=42")
                .contains("position:absolute;left:20;top:10;width:120;height:40;")
                .doesNotContain("<div id=\"signatureDisplay\"></div>");
    }

    @Test
    @DisplayName("should keep scripts blocked for legacy server-side PDF rendering")
    void shouldBuildStrictCsp_whenNotBrowserRendering() {
        assertThat(EFormViewForPdfGenerationServlet.buildContentSecurityPolicy(false))
                .contains("script-src 'none'")
                .contains("object-src 'none'");
    }

    @Test
    @DisplayName("should allow same-origin scripts for browser PDF rendering")
    void shouldBuildBrowserRenderCsp_whenBrowserRendering() {
        assertThat(EFormViewForPdfGenerationServlet.buildContentSecurityPolicy(true))
                .contains("default-src 'self' data:")
                .contains("script-src 'self' 'unsafe-inline' 'unsafe-eval'")
                .contains("object-src 'none'")
                .contains("img-src 'self' data: blob:");
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
