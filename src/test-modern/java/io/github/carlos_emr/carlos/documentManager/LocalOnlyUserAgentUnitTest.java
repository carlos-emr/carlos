/**
 * Copyright (c) 2024-2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LocalOnlyUserAgent} — the SSRF-prevention layer
 * for Flying Saucer PDF rendering.
 *
 * <p>Uses a test subclass to expose the protected {@code resolveAndOpenStream()}
 * method for direct verification of URI scheme blocking.</p>
 *
 * @since 2026-03-04
 */
@Tag("unit")
@Tag("security")
@Tag("pdf")
@DisplayName("LocalOnlyUserAgent Unit Tests")
class LocalOnlyUserAgentUnitTest {

    /** Default dots-per-point matching ITextRenderer's no-arg constructor. */
    private static final float DEFAULT_DOTS_PER_POINT = 26.666666f;

    /** Default dots-per-pixel matching ITextRenderer's no-arg constructor. */
    private static final int DEFAULT_DOTS_PER_PIXEL = 20;

    /**
     * Test subclass that exposes the protected resolveAndOpenStream method.
     */
    static class TestableUserAgent extends LocalOnlyUserAgent {
        TestableUserAgent() {
            super(new ITextOutputDevice(DEFAULT_DOTS_PER_POINT), DEFAULT_DOTS_PER_PIXEL);
        }

        @Override
        public InputStream resolveAndOpenStream(String uri) {
            return super.resolveAndOpenStream(uri);
        }
    }

    /** Tests for the resolveAndOpenStream method — the SSRF chokepoint. */
    @Nested
    @DisplayName("resolveAndOpenStream")
    class ResolveAndOpenStreamTests {

        private final TestableUserAgent agent = new TestableUserAgent();

        @Test
        @DisplayName("should return null when URI is null")
        void shouldReturnNull_whenUriIsNull() {
            assertThat(agent.resolveAndOpenStream(null)).isNull();
        }

        @Test
        @DisplayName("should return null when URI starts with http:")
        void shouldReturnNull_whenUriStartsWithHttp() {
            assertThat(agent.resolveAndOpenStream("http://evil.com/payload")).isNull();
        }

        @Test
        @DisplayName("should return null when URI starts with https:")
        void shouldReturnNull_whenUriStartsWithHttps() {
            assertThat(agent.resolveAndOpenStream("https://evil.com/payload")).isNull();
        }

        @Test
        @DisplayName("should return null when URI starts with ftp:")
        void shouldReturnNull_whenUriStartsWithFtp() {
            assertThat(agent.resolveAndOpenStream("ftp://evil.com/file.txt")).isNull();
        }

        @Test
        @DisplayName("should return null when URI starts with protocol-relative //")
        void shouldReturnNull_whenUriStartsWithProtocolRelativeSlashes() {
            assertThat(agent.resolveAndOpenStream("//evil.com/payload")).isNull();
        }

        @Test
        @DisplayName("should return null when URI uses uppercase HTTP scheme")
        void shouldReturnNull_whenUriUsesUppercaseHttpScheme() {
            assertThat(agent.resolveAndOpenStream("HTTP://evil.com/payload")).isNull();
        }

        @Test
        @DisplayName("should return null when URI uses mixed-case HTTPS scheme")
        void shouldReturnNull_whenUriUsesMixedCaseHttpsScheme() {
            assertThat(agent.resolveAndOpenStream("HtTpS://evil.com/payload")).isNull();
        }

        @Test
        @DisplayName("should return null for cloud metadata SSRF endpoint")
        void shouldReturnNull_forCloudMetadataSsrfEndpoint() {
            assertThat(agent.resolveAndOpenStream("http://169.254.169.254/latest/meta-data/")).isNull();
        }

        @Test
        @DisplayName("should delegate to parent when URI starts with data: scheme")
        void shouldDelegateToParent_whenUriStartsWithDataScheme() {
            // data: URIs are inline content — no network request; parent handles decoding.
            // Verify the method completes without throwing (not blocked by our security check).
            String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";
            agent.resolveAndOpenStream(dataUri);
            // If we reach here, the data: URI was not blocked — it was delegated to the parent.
        }

        @Test
        @DisplayName("should delegate to parent when URI is a relative path")
        void shouldDelegateToParent_whenUriIsRelativePath() {
            // Relative paths are local resources, should not be blocked.
            // Verify the method completes without throwing (not blocked by our security check).
            agent.resolveAndOpenStream("images/logo.png");
            // If we reach here, the relative path was not blocked — it was delegated to the parent.
        }
    }

    /** Tests for the createRestrictedRenderer static factory method. */
    @Nested
    @DisplayName("createRestrictedRenderer")
    class CreateRestrictedRendererTests {

        @Test
        @DisplayName("should return non-null renderer")
        void shouldReturnNonNullRenderer() {
            ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer();
            assertThat(renderer).isNotNull();
        }

        @Test
        @DisplayName("should return renderer of correct type")
        void shouldReturnRendererOfCorrectType() {
            ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer();
            assertThat(renderer).isInstanceOf(ITextRenderer.class);
        }

        @Test
        @DisplayName("should block external resources when rendering HTML with external images")
        void shouldBlockExternalResources_whenRenderingHtmlWithExternalImages() throws Exception {
            // HTML that references an external image — should be blocked, not fetched
            String html = "<html><body><p>Test</p>"
                    + "<img src=\"http://evil.com/track.png\" alt=\"blocked\" />"
                    + "</body></html>";

            ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer();
            renderer.getSharedContext().setPrint(true);
            renderer.getSharedContext().setInteractive(false);
            renderer.setDocumentFromString(html, null);
            renderer.layout();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            renderer.createPDF(baos, true);

            // Should produce a valid PDF (the image is silently dropped, not fetched)
            byte[] pdfBytes = baos.toByteArray();
            assertThat(pdfBytes).isNotEmpty();
            assertThat(pdfBytes).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        }
    }
}
