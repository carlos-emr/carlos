package io.github.carlos_emr.carlos.eform.util;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSignatureUrls")
    @DisplayName("should reject invalid signature URLs")
    void shouldRejectInvalidSignatureUrl_whenNormalizingSignatureUrl(String scenario, String rawUrl) {
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

    private static Stream<Arguments> invalidSignatureUrls() {
        return Stream.of(
                Arguments.of("javascript scheme", "javascript:alert(1)"),
                Arguments.of("external url", "https://evil.example/EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=5"),
                Arguments.of("quote breaking payload", "/carlos/imageRenderingServlet?source=signature_stored&digitalSignatureId=12\" onerror=\"alert(1)"),
                Arguments.of("missing numeric digital signature id", "/carlos/imageRenderingServlet?source=signature_preview&signatureRequestId=temp123")
        );
    }
}
