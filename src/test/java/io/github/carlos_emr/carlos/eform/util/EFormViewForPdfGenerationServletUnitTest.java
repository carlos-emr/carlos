package io.github.carlos_emr.carlos.eform.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EFormViewForPdfGenerationServlet unit tests")
@Tag("unit")
@Tag("fast")
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

    @Test
    @DisplayName("should reject javascript signature URLs")
    void shouldRejectJavascriptUrl_whenNormalizingSignatureUrl() {
        String normalized = EFormViewForPdfGenerationServlet.normalizePdfSignatureUrl(
                "javascript:alert(1)",
                "/carlos");

        assertThat(normalized).isNull();
    }

    @Test
    @DisplayName("should reject external signature URLs")
    void shouldRejectExternalUrl_whenNormalizingSignatureUrl() {
        String normalized = EFormViewForPdfGenerationServlet.normalizePdfSignatureUrl(
                "https://evil.example/EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=5",
                "/carlos");

        assertThat(normalized).isNull();
    }

    @Test
    @DisplayName("should reject quote breaking signature URLs")
    void shouldRejectQuoteBreakingUrl_whenNormalizingSignatureUrl() {
        String normalized = EFormViewForPdfGenerationServlet.normalizePdfSignatureUrl(
                "/carlos/imageRenderingServlet?source=signature_stored&digitalSignatureId=12\" onerror=\"alert(1)",
                "/carlos");

        assertThat(normalized).isNull();
    }

    @Test
    @DisplayName("should reject rewritten URLs that do not carry a numeric digital signature id")
    void shouldRejectMissingDigitalSignatureId_whenNormalizingSignatureUrl() {
        String normalized = EFormViewForPdfGenerationServlet.normalizePdfSignatureUrl(
                "/carlos/imageRenderingServlet?source=signature_preview&signatureRequestId=temp123",
                "/carlos");

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
}
