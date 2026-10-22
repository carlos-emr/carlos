/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PDF preview capabilities")
@Tag("unit")
@Tag("fast")
class PdfPreviewCapabilityServiceUnitTest {

    @Test
    @DisplayName("should bind an opaque token to the exact file, provider, and session")
    void shouldBindCapability_toFileProviderAndSession() throws Exception {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "carlos-eform-browser-pdf-temp");
        Files.createDirectories(root);
        Path pdf = Files.createTempFile(root, "preview-capability-", ".pdf");
        try {
            PdfPreviewCapabilityService service = new PdfPreviewCapabilityService();
            LoggedInInfo provider = provider("p1");
            MockHttpServletRequest issuer = new MockHttpServletRequest();
            issuer.getSession();

            String token = service.issue(issuer, provider, pdf);

            assertThat(token).doesNotContain(pdf.toString());
            assertThat(service.resolve(issuer, provider, token)).isEqualTo(pdf.toRealPath());

            MockHttpServletRequest otherSession = new MockHttpServletRequest();
            otherSession.getSession();
            assertThat(service.resolve(otherSession, provider, token)).isNull();
            assertThat(service.resolve(issuer, provider("p2"), token)).isNull();
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    @Test
    @DisplayName("should refuse to issue a capability for an arbitrary readable file")
    void shouldRefuseCapability_outsideCarlosTempRoot() throws Exception {
        Path pdf = Files.createTempFile("unowned-preview-", ".pdf");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession();
            assertThatThrownBy(() -> new PdfPreviewCapabilityService()
                    .issue(request, provider("p1"), pdf))
                    .isInstanceOf(PDFGenerationException.class);
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    private static LoggedInInfo provider(String providerNo) {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(providerNo);
        return loggedInInfo;
    }
}
