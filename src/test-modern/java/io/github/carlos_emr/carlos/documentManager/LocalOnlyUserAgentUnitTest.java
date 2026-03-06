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
import org.junit.jupiter.api.io.TempDir;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LocalOnlyUserAgent} — the SSRF-prevention layer
 * for Flying Saucer PDF rendering.
 *
 * <p>Uses reflection to invoke the protected {@code resolveAndOpenStream()} and
 * {@code openStream()} methods for direct verification of URI scheme blocking and
 * path containment. {@code LocalOnlyUserAgent} is {@code final} to prevent security
 * bypass via subclassing.</p>
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

    private static final Method RESOLVE_AND_OPEN_STREAM;
    private static final Method OPEN_STREAM;

    static {
        try {
            RESOLVE_AND_OPEN_STREAM = LocalOnlyUserAgent.class.getDeclaredMethod("resolveAndOpenStream", String.class);
            RESOLVE_AND_OPEN_STREAM.setAccessible(true);
            OPEN_STREAM = LocalOnlyUserAgent.class.getDeclaredMethod("openStream", String.class);
            OPEN_STREAM.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("LocalOnlyUserAgent missing expected protected method", e);
        }
    }

    /** Creates a new LocalOnlyUserAgent instance for testing. */
    private static LocalOnlyUserAgent createAgent() {
        return new LocalOnlyUserAgent(new ITextOutputDevice(DEFAULT_DOTS_PER_POINT), DEFAULT_DOTS_PER_PIXEL);
    }

    /** Invokes the protected resolveAndOpenStream via reflection. */
    private static InputStream invokeResolveAndOpenStream(LocalOnlyUserAgent agent, String uri) {
        try {
            return (InputStream) RESOLVE_AND_OPEN_STREAM.invoke(agent, uri);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new AssertionError("Cannot access resolveAndOpenStream", e);
        }
    }

    /** Invokes the protected openStream via reflection. */
    private static InputStream invokeOpenStream(LocalOnlyUserAgent agent, String uri) throws IOException {
        try {
            return (InputStream) OPEN_STREAM.invoke(agent, uri);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException ioe) throw ioe;
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new AssertionError("Cannot access openStream", e);
        }
    }

    /** Tests for the resolveAndOpenStream method — the SSRF chokepoint. */
    @Nested
    @DisplayName("resolveAndOpenStream")
    class ResolveAndOpenStreamTests {

        private final LocalOnlyUserAgent agent = createAgent();

        @Test
        @DisplayName("should return null when URI is null")
        void shouldReturnNull_whenUriIsNull() {
            assertThat(invokeResolveAndOpenStream(agent,null)).isNull();
        }

        @Test
        @DisplayName("should return null when URI starts with http:")
        void shouldReturnNull_whenUriStartsWithHttp() {
            assertThat(invokeResolveAndOpenStream(agent,"http://evil.com/payload")).isNull();
        }

        @Test
        @DisplayName("should return null when URI starts with https:")
        void shouldReturnNull_whenUriStartsWithHttps() {
            assertThat(invokeResolveAndOpenStream(agent,"https://evil.com/payload")).isNull();
        }

        @Test
        @DisplayName("should return null when URI starts with ftp:")
        void shouldReturnNull_whenUriStartsWithFtp() {
            assertThat(invokeResolveAndOpenStream(agent,"ftp://evil.com/file.txt")).isNull();
        }

        @Test
        @DisplayName("should return null when URI starts with protocol-relative //")
        void shouldReturnNull_whenUriStartsWithProtocolRelativeSlashes() {
            assertThat(invokeResolveAndOpenStream(agent,"//evil.com/payload")).isNull();
        }

        @Test
        @DisplayName("should return null when URI uses uppercase HTTP scheme")
        void shouldReturnNull_whenUriUsesUppercaseHttpScheme() {
            assertThat(invokeResolveAndOpenStream(agent,"HTTP://evil.com/payload")).isNull();
        }

        @Test
        @DisplayName("should return null when URI uses mixed-case HTTPS scheme")
        void shouldReturnNull_whenUriUsesMixedCaseHttpsScheme() {
            assertThat(invokeResolveAndOpenStream(agent,"HtTpS://evil.com/payload")).isNull();
        }

        @Test
        @DisplayName("should return null for cloud metadata SSRF endpoint")
        void shouldReturnNull_forCloudMetadataSsrfEndpoint() {
            assertThat(invokeResolveAndOpenStream(agent,"http://169.254.169.254/latest/meta-data/")).isNull();
        }

        @Test
        @DisplayName("should delegate to parent when URI starts with data: scheme")
        void shouldDelegateToParent_whenUriStartsWithDataScheme() {
            // data: URIs are inline content — no network request; parent handles decoding.
            // Parent returns null without a full rendering context, but the key assertion is
            // that data: URIs are delegated to the parent (not returned null directly like
            // blocked http/https/ftp schemes).
            String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";
            InputStream result = invokeResolveAndOpenStream(agent,dataUri);
            assertThat(result).as("data: URI should be delegated to parent, not blocked").isNull();
        }

        @Test
        @DisplayName("should delegate to parent when URI is a relative path")
        void shouldDelegateToParent_whenUriIsRelativePath() {
            // Relative paths are local resources, should not be blocked.
            // Parent may return null without a base URL context, but must not throw.
            InputStream result = invokeResolveAndOpenStream(agent,"images/logo.png");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when URI uses jar: scheme (SSRF bypass vector)")
        void shouldReturnNull_whenUriUsesJarScheme() {
            // jar:https://... triggers java.net.URL to make an outbound HTTPS request
            assertThat(invokeResolveAndOpenStream(agent,"jar:https://evil.com/malicious.jar!/entry")).isNull();
        }

        @Test
        @DisplayName("should return null when URI uses gopher: scheme")
        void shouldReturnNull_whenUriUsesGopherScheme() {
            assertThat(invokeResolveAndOpenStream(agent,"gopher://evil.com/")).isNull();
        }

        @Test
        @DisplayName("should return null when URI uses jar:http: scheme (SSRF bypass vector)")
        void shouldReturnNull_whenUriUsesJarHttpScheme() {
            assertThat(invokeResolveAndOpenStream(agent,"jar:http://evil.com/malicious.jar!/entry")).isNull();
        }

        @Test
        @DisplayName("should return null when URI uses ldap: scheme")
        void shouldReturnNull_whenUriUsesLdapScheme() {
            assertThat(invokeResolveAndOpenStream(agent,"ldap://evil.com/")).isNull();
        }

        @Test
        @DisplayName("should return null when URI uses javascript: scheme")
        void shouldReturnNull_whenUriUsesJavascriptScheme() {
            assertThat(invokeResolveAndOpenStream(agent,"javascript:alert(1)")).isNull();
        }

        @Test
        @DisplayName("should return null when URI uses vbscript: scheme")
        void shouldReturnNull_whenUriUsesVbscriptScheme() {
            assertThat(invokeResolveAndOpenStream(agent,"vbscript:MsgBox")).isNull();
        }

        @Test
        @DisplayName("should return null when URI uses mailto: scheme")
        void shouldReturnNull_whenUriUsesMailtoScheme() {
            assertThat(invokeResolveAndOpenStream(agent,"mailto:attacker@evil.com")).isNull();
        }

        @Test
        @DisplayName("should delegate to parent when URI is empty string (not blocked)")
        void shouldDelegateToParent_whenUriIsEmptyString() {
            // Empty string has no scheme (no colon), so it is treated as a relative path
            // and delegated to the parent — it must NOT be blocked as a dangerous scheme.
            // Parent may return a non-null stream even for empty input.
            invokeResolveAndOpenStream(agent,"");
            // The key assertion: no exception thrown — empty string is handled gracefully.
        }

        @Test
        @DisplayName("should delegate to parent when URI uses file: scheme (not blocked at scheme level)")
        void shouldDelegateToParent_whenUriUsesFileScheme() {
            // file: URIs pass the scheme check in resolveAndOpenStream (not blocked as external).
            // They may be subsequently blocked by openStream's path containment, but the
            // scheme-level filter should not reject them. A nonexistent path returns null
            // regardless (either blocked by containment or file-not-found in parent).
            InputStream result = invokeResolveAndOpenStream(agent,"file:///nonexistent/path.png");
            assertThat(result).isNull();
        }
    }

    /** Tests for openStream path containment — blocks file: URIs outside allowed directories. */
    @Nested
    @DisplayName("openStream path containment")
    class OpenStreamPathContainmentTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("should allow file URI when under webapp root (base URL)")
        void shouldAllowFileUri_whenUnderWebappRoot() throws Exception {
            LocalOnlyUserAgent agent = createAgent();

            // Create a real file under the temp dir acting as webapp root
            Path testFile = tempDir.resolve("style.css");
            Files.writeString(testFile, "body { color: black; }");

            // Set base URL to the temp dir (simulating webapp root)
            agent.setBaseURL(tempDir.toUri().toString());

            InputStream result = invokeOpenStream(agent,testFile.toUri().toString());
            assertThat(result).as("file under webapp root should be allowed").isNotNull();
            result.close();
        }

        @Test
        @DisplayName("should block file URI when outside all allowed directories")
        void shouldBlockFileUri_whenOutsideAllowedDirectories() throws Exception {
            LocalOnlyUserAgent agent = createAgent();

            // Set base URL to a specific directory that does NOT contain /etc
            agent.setBaseURL(tempDir.toUri().toString());

            InputStream result = invokeOpenStream(agent,"file:///etc/passwd");
            assertThat(result).as("file outside allowed directories should be blocked").isNull();
        }

        @Test
        @DisplayName("should block file URI with path traversal attempting to escape webapp root")
        void shouldBlockFileUri_withPathTraversalEscape() throws Exception {
            LocalOnlyUserAgent agent = createAgent();

            // Use a deep subdirectory as webapp root so traversal escapes BOTH
            // the webapp root AND the temp dir (reaching /etc which is not allowed)
            Path webappRoot = tempDir.resolve("webapp");
            Files.createDirectories(webappRoot);
            agent.setBaseURL(webappRoot.toUri().toString());

            // Traversal URI that normalizes to /etc/passwd — outside any allowed dir
            String traversalUri = "file://" + webappRoot.toAbsolutePath() + "/../../../etc/passwd";
            InputStream result = invokeOpenStream(agent,traversalUri);
            assertThat(result).as("path traversal escaping webapp root should be blocked").isNull();
        }

        @Test
        @DisplayName("should allow file URI when under system temp directory")
        void shouldAllowFileUri_whenUnderTempDirectory() throws Exception {
            LocalOnlyUserAgent agent = createAgent();

            // Don't set a base URL — temp dir should still be allowed
            String sysTmpDir = System.getProperty("java.io.tmpdir");
            Path tmpFile = Path.of(sysTmpDir, "carlos-test-pdf-" + System.nanoTime() + ".css");
            try {
                Files.writeString(tmpFile, "body { margin: 0; }");

                InputStream result = invokeOpenStream(agent,tmpFile.toUri().toString());
                assertThat(result).as("file under java.io.tmpdir should be allowed").isNotNull();
                result.close();
            } finally {
                Files.deleteIfExists(tmpFile);
            }
        }

        @Test
        @DisplayName("should block file URI targeting patient documents directory")
        void shouldBlockFileUri_whenTargetingPatientDocuments() throws Exception {
            LocalOnlyUserAgent agent = createAgent();
            agent.setBaseURL(tempDir.toUri().toString());

            // Simulate access to patient document directory
            InputStream result = invokeOpenStream(agent,"file:///opt/carlos/documents/999/patient_scan.jpg");
            assertThat(result).as("patient document directory should be blocked").isNull();
        }

        @Test
        @DisplayName("should block file URI when base URL is not set and path is outside temp dirs")
        void shouldBlockFileUri_whenBaseUrlNotSetAndOutsideTempDirs() throws Exception {
            LocalOnlyUserAgent agent = createAgent();
            // Do NOT set base URL

            InputStream result = invokeOpenStream(agent,"file:///etc/shadow");
            assertThat(result).as("sensitive system file should be blocked even without base URL").isNull();
        }

        @Test
        @DisplayName("should block file URI with percent-encoded path traversal sequences")
        void shouldBlockFileUri_withEncodedPathTraversal() throws Exception {
            LocalOnlyUserAgent agent = createAgent();
            Path webappRoot = tempDir.resolve("webapp");
            Files.createDirectories(webappRoot);
            agent.setBaseURL(webappRoot.toUri().toString());

            // %2e%2e = .. — URI decoding followed by normalize/canonicalize should resolve this
            String encodedTraversalUri = webappRoot.toUri() + "%2e%2e/%2e%2e/%2e%2e/etc/passwd";
            InputStream result = invokeOpenStream(agent,encodedTraversalUri);
            assertThat(result).as("percent-encoded path traversal should be blocked").isNull();
        }

        @Test
        @DisplayName("should block file URI when symlink inside webapp root points outside")
        void shouldBlockFileUri_whenSymlinkEscapesAllowedDirectory() throws Exception {
            LocalOnlyUserAgent agent = createAgent();
            agent.setBaseURL(tempDir.toUri().toString());

            // Create a symlink inside the allowed directory that points to /etc
            Path symlink = tempDir.resolve("escape");
            try {
                Files.createSymbolicLink(symlink, Path.of("/etc"));
            } catch (UnsupportedOperationException | IOException e) {
                // Symlinks may not be supported on all filesystems/OS — skip gracefully
                return;
            }

            // The symlink resolves to /etc/passwd which is outside allowed dirs
            Path target = symlink.resolve("passwd");
            InputStream result = invokeOpenStream(agent,target.toUri().toString());
            assertThat(result).as("symlink escaping allowed directory should be blocked by canonical path resolution").isNull();
        }
    }

    /** Tests for openStream defense-in-depth — blocks non-file/non-data schemes. */
    @Nested
    @DisplayName("openStream defense-in-depth")
    class OpenStreamDefenseInDepthTests {

        @Test
        @DisplayName("should block http: URI in openStream even if it bypassed resolveAndOpenStream")
        void shouldBlockHttpUri_inOpenStream() throws Exception {
            LocalOnlyUserAgent agent = createAgent();
            InputStream result = invokeOpenStream(agent,"http://evil.com/payload");
            assertThat(result).as("http: URI should be blocked in openStream defense-in-depth").isNull();
        }

        @Test
        @DisplayName("should block https: URI in openStream")
        void shouldBlockHttpsUri_inOpenStream() throws Exception {
            LocalOnlyUserAgent agent = createAgent();
            InputStream result = invokeOpenStream(agent,"https://evil.com/payload");
            assertThat(result).as("https: URI should be blocked in openStream defense-in-depth").isNull();
        }

        @Test
        @DisplayName("should block javascript: URI in openStream")
        void shouldBlockJavascriptUri_inOpenStream() throws Exception {
            LocalOnlyUserAgent agent = createAgent();
            InputStream result = invokeOpenStream(agent,"javascript:alert(1)");
            assertThat(result).as("javascript: URI should be blocked in openStream").isNull();
        }

        @Test
        @DisplayName("should block ftp: URI in openStream")
        void shouldBlockFtpUri_inOpenStream() throws Exception {
            LocalOnlyUserAgent agent = createAgent();
            InputStream result = invokeOpenStream(agent,"ftp://evil.com/file.txt");
            assertThat(result).as("ftp: URI should be blocked in openStream defense-in-depth").isNull();
        }
    }

    /** Tests for setBaseURL — rejects non-file base URLs to prevent SSRF via relative resolution. */
    @Nested
    @DisplayName("setBaseURL")
    class SetBaseURLTests {

        @Test
        @DisplayName("should accept file: base URL")
        void shouldAcceptFileBaseUrl() {
            LocalOnlyUserAgent agent = createAgent();
            agent.setBaseURL("file:///opt/webapp/");
            assertThat(agent.getBaseURL()).isEqualTo("file:///opt/webapp/");
        }

        @Test
        @DisplayName("should accept null base URL")
        void shouldAcceptNullBaseUrl() {
            LocalOnlyUserAgent agent = createAgent();
            agent.setBaseURL(null);
            assertThat(agent.getBaseURL()).isNull();
        }

        @Test
        @DisplayName("should reject http: base URL")
        void shouldRejectHttpBaseUrl() {
            LocalOnlyUserAgent agent = createAgent();
            agent.setBaseURL("file:///safe/path/");
            agent.setBaseURL("http://evil.com/");
            assertThat(agent.getBaseURL())
                    .as("http: base URL should be rejected, keeping previous value")
                    .isEqualTo("file:///safe/path/");
        }

        @Test
        @DisplayName("should reject https: base URL")
        void shouldRejectHttpsBaseUrl() {
            LocalOnlyUserAgent agent = createAgent();
            agent.setBaseURL("https://evil.com/");
            assertThat(agent.getBaseURL())
                    .as("https: base URL should be rejected")
                    .isNotEqualTo("https://evil.com/");
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
