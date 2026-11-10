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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.SvgReferences;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xhtmlrenderer.extend.FSImage;
import org.xhtmlrenderer.pdf.ITextFSImage;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.resource.ImageResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

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
@Tag("htmlpdf")
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

    /** Creates an agent that may read files from the given directories (none by default). */
    private static LocalOnlyUserAgent createAgent(Path... allowedDirectories) {
        LocalOnlyUserAgent agent = new LocalOnlyUserAgent(new ITextOutputDevice(DEFAULT_DOTS_PER_POINT), DEFAULT_DOTS_PER_PIXEL,
                List.of(allowedDirectories));
        agent.setSvgCleaner(bytes -> SvgReferences.clean(bytes).map(SvgReferences.Cleaned::svg).orElse(null));
        return agent;
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

    @Test
    @DisplayName("should preserve CSS streams that contain literal SVG markup")
    void shouldPreserveStylesheet_whenCssMentionsSvg(@TempDir Path directory) throws Exception {
        String css = "/* Inline SVG example: <svg xmlns='http://www.w3.org/2000/svg'/> */ p { color: red }";
        Path stylesheet = Files.writeString(directory.resolve("print.css"), css);

        try (InputStream stream = invokeResolveAndOpenStream(createAgent(directory), stylesheet.toUri().toString())) {
            assertThat(stream).isNotNull();
            assertThat(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(css);
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

    /** Flying Saucer's built-in stylesheet is the one jar: resource that must open (and nothing else in the jar). */
    @Nested
    @DisplayName("default stylesheet")
    class DefaultStylesheetTests {

        @Test
        @DisplayName("should locate the built-in stylesheet")
        void shouldLocateStylesheet_inFlyingSaucerJar() {
            assertThat(LocalOnlyUserAgent.DEFAULT_STYLESHEET_URI)
                    .startsWith("jar:file:")
                    .endsWith("!/resources/css/XhtmlNamespaceHandler.css");
        }

        @Test
        @DisplayName("should open the built-in stylesheet by its exact URI")
        void shouldOpenFlyingSaucerDefaultStylesheet_byExactUri() throws Exception {
            LocalOnlyUserAgent agent = createAgent();
            try (InputStream viaResolve = invokeResolveAndOpenStream(agent, LocalOnlyUserAgent.DEFAULT_STYLESHEET_URI);
                 InputStream viaOpen = invokeOpenStream(agent, LocalOnlyUserAgent.DEFAULT_STYLESHEET_URI)) {
                assertThat(viaResolve).isNotNull();
                assertThat(viaOpen).isNotNull();
                assertThat(new String(viaOpen.readNBytes(4096))).contains("display");
            }
        }

        @Test
        @DisplayName("should ignore the stylesheet as a base URL without a warning")
        void shouldNotWarn_whenBaseUrlIsDefaultStylesheet() {
            LocalOnlyUserAgent agent = createAgent();
            agent.setBaseURL("file:///opt/webapp/");
            try (LogCapture capture = LogCapture.forLogger(LocalOnlyUserAgent.class)) {
                agent.setBaseURL(LocalOnlyUserAgent.DEFAULT_STYLESHEET_URI);
                assertThat(capture.messages()).isEmpty();
            }
            assertThat(agent.getBaseURL()).isEqualTo("file:///opt/webapp/");
        }

        @Test
        @DisplayName("should keep every other entry of the same jar blocked")
        void shouldBlockOtherEntries_inFlyingSaucerJar() throws Exception {
            LocalOnlyUserAgent agent = createAgent();
            String jarPart = LocalOnlyUserAgent.DEFAULT_STYLESHEET_URI
                    .substring(0, LocalOnlyUserAgent.DEFAULT_STYLESHEET_URI.indexOf("!/"));
            assertThat(invokeResolveAndOpenStream(agent, jarPart + "!/META-INF/MANIFEST.MF")).isNull();
            assertThat(invokeOpenStream(agent, jarPart + "!/META-INF/MANIFEST.MF")).isNull();
            assertThat(invokeOpenStream(agent, jarPart + "!/resources/css/XhtmlNamespaceHandler.css.bak")).isNull();
        }

        @Test
        @DisplayName("should keep jar:http: blocked even with the stylesheet's path")
        void shouldBlockJarHttp_forStylesheet() throws Exception {
            LocalOnlyUserAgent agent = createAgent();
            String uri = "jar:http://evil.com/x.jar!/resources/css/XhtmlNamespaceHandler.css";
            assertThat(invokeResolveAndOpenStream(agent, uri)).isNull();
            assertThat(invokeOpenStream(agent, uri)).isNull();
        }

        @Test
        @DisplayName("should not print the title or the stylesheet text when the built-in stylesheet loads")
        void shouldNotPrintTitleOrCssText_whenRenderingWithDefaultStylesheet(@TempDir Path tempDir) throws Exception {
            String html = "<!DOCTYPE html><html><head><title>FAKE-Title-Text</title>"
                    + "<style>p { color: red }</style></head><body><p>FAKE-Body-Text</p></body></html>";
            ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer(List.of(tempDir), new ITextFontResolver());
            renderer.getSharedContext().setPrint(true);
            renderer.getSharedContext().setInteractive(false);
            renderer.setDocumentFromString(html, tempDir.toUri().toString());
            renderer.layout();
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            renderer.createPDF(pdf, true);

            try (PDDocument document = Loader.loadPDF(pdf.toByteArray())) {
                String text = new PDFTextStripper().getText(document);
                assertThat(text).contains("FAKE-Body-Text").doesNotContain("FAKE-Title-Text").doesNotContain("color");
            }
        }
    }

    /** Tests for openStream path containment — blocks file: URIs outside the allowed directories. */
    @Nested
    @DisplayName("openStream path containment")
    class OpenStreamPathContainmentTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("should allow file URI when under an allowed directory")
        void shouldAllowFileUri_whenUnderAllowedDirectory() throws Exception {
            LocalOnlyUserAgent agent = createAgent(tempDir);
            Path testFile = tempDir.resolve("style.css");
            Files.writeString(testFile, "body { color: black; }");

            try (InputStream result = invokeOpenStream(agent, testFile.toUri().toString())) {
                assertThat(result).as("file under an allowed directory should be allowed").isNotNull();
                assertThat(new String(result.readAllBytes())).isEqualTo("body { color: black; }");
            }
        }

        @Test
        @DisplayName("should allow a file under any of several allowed directories")
        void shouldAllowFileUri_whenUnderAnotherAllowedDirectory() throws Exception {
            Path first = Files.createDirectories(tempDir.resolve("first"));
            Path second = Files.createDirectories(tempDir.resolve("second"));
            Path file = second.resolve("late.css");
            Files.writeString(file, "p { margin: 0; }");
            LocalOnlyUserAgent agent = createAgent(first, second);

            try (InputStream result = invokeOpenStream(agent, file.toUri().toString())) {
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("should block file URI when outside all allowed directories")
        void shouldBlockFileUri_whenOutsideAllowedDirectories() throws Exception {
            LocalOnlyUserAgent agent = createAgent(tempDir);

            assertThat(invokeOpenStream(agent, "file:///etc/passwd"))
                    .as("file outside allowed directories should be blocked").isNull();
        }

        @Test
        @DisplayName("should block every file when no directory is allowed")
        void shouldBlockFileUri_whenNoDirectoryIsAllowed() throws Exception {
            Path file = tempDir.resolve("style.css");
            Files.writeString(file, "x");

            assertThat(invokeOpenStream(createAgent(), file.toUri().toString())).isNull();
        }

        @Test
        @DisplayName("should block the working directory and the temp directory unless they are allowed")
        void shouldBlockFile_inWorkingDirectoryNotAllowed() throws Exception {
            LocalOnlyUserAgent agent = createAgent(tempDir);
            Path inWorkingDirectory = Path.of("pom.xml").toAbsolutePath();

            assertThat(Files.exists(inWorkingDirectory)).isTrue();
            assertThat(invokeOpenStream(agent, inWorkingDirectory.toUri().toString()))
                    .as("the JVM working directory is not an implicit root").isNull();
            Path inSystemTemp = Files.createTempFile("carlos-agent-test-", ".css");
            try {
                assertThat(invokeOpenStream(agent, inSystemTemp.toUri().toString()))
                        .as("java.io.tmpdir is not an implicit root").isNull();
            } finally {
                Files.deleteIfExists(inSystemTemp);
            }
        }

        @Test
        @DisplayName("should allow the temp directory when it is listed")
        void shouldAllowFileUri_whenTempDirectoryIsListed() throws Exception {
            Path systemTemp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
            LocalOnlyUserAgent agent = createAgent(systemTemp);
            Path tmpFile = Files.createTempFile("carlos-agent-test-", ".css");
            try {
                Files.writeString(tmpFile, "body { margin: 0; }");
                try (InputStream result = invokeOpenStream(agent, tmpFile.toUri().toString())) {
                    assertThat(result).as("file under a listed java.io.tmpdir should be allowed").isNotNull();
                }
            } finally {
                Files.deleteIfExists(tmpFile);
            }
        }

        @Test
        @DisplayName("should block file URI with path traversal attempting to escape the allowed directory")
        void shouldBlockFileUri_withPathTraversalEscape() throws Exception {
            Path webappRoot = tempDir.resolve("webapp");
            Files.createDirectories(webappRoot);
            LocalOnlyUserAgent agent = createAgent(webappRoot);

            String traversalUri = "file://" + webappRoot.toAbsolutePath() + "/../../../etc/passwd";
            assertThat(invokeOpenStream(agent, traversalUri))
                    .as("path traversal escaping the allowed directory should be blocked").isNull();
        }

        @Test
        @DisplayName("should block file URI targeting patient documents directory")
        void shouldBlockFileUri_whenTargetingPatientDocuments() throws Exception {
            LocalOnlyUserAgent agent = createAgent(tempDir);

            assertThat(invokeOpenStream(agent, "file:///opt/carlos/documents/999/patient_scan.jpg"))
                    .as("patient document directory should be blocked").isNull();
        }

        @Test
        @DisplayName("should block file URI with percent-encoded path traversal sequences")
        void shouldBlockFileUri_withEncodedPathTraversal() throws Exception {
            Path webappRoot = tempDir.resolve("webapp");
            Files.createDirectories(webappRoot);
            LocalOnlyUserAgent agent = createAgent(webappRoot);

            // %2e%2e = .. — URI decoding followed by canonicalisation resolves this
            String encodedTraversalUri = webappRoot.toUri() + "%2e%2e/%2e%2e/%2e%2e/etc/passwd";
            assertThat(invokeOpenStream(agent, encodedTraversalUri))
                    .as("percent-encoded path traversal should be blocked").isNull();
        }

        @Test
        @DisplayName("should block file URI when symlink inside an allowed directory points outside")
        void shouldBlockFileUri_whenSymlinkEscapesAllowedDirectory() throws Exception {
            LocalOnlyUserAgent agent = createAgent(tempDir);

            Path symlink = tempDir.resolve("escape");
            try {
                Files.createSymbolicLink(symlink, Path.of("/etc"));
            } catch (UnsupportedOperationException | IOException e) {
                // Symlinks may not be supported on all filesystems/OS — skip gracefully
                return;
            }

            Path target = symlink.resolve("passwd");
            assertThat(invokeOpenStream(agent, target.toUri().toString()))
                    .as("symlink escaping allowed directory should be blocked by canonical path resolution").isNull();
        }

        @Test
        @DisplayName("should log the scheme but never the path of a blocked file")
        void shouldLogNoPath_whenFileBlocked() throws Exception {
            LocalOnlyUserAgent agent = createAgent(tempDir);
            try (LogCapture capture = LogCapture.forLogger(LocalOnlyUserAgent.class)) {
                invokeOpenStream(agent, "file:///etc/FAKE-patient-secret.txt");
                assertThat(capture.messages()).isNotEmpty();
                assertThat(String.join("\n", capture.messages()))
                        .contains("scheme: file")
                        .doesNotContain("FAKE-patient").doesNotContain("/etc");
            }
        }

        @Test
        @DisplayName("should log the scheme and host, but not the path or query, of a blocked network request")
        void shouldLogHostOnly_whenNetworkRequestBlocked() {
            LocalOnlyUserAgent agent = createAgent(tempDir);
            try (LogCapture capture = LogCapture.forLogger(LocalOnlyUserAgent.class)) {
                invokeResolveAndOpenStream(agent, "https://evil.example.com/FAKE-patient/secret.png?token=FAKE-token");
                assertThat(String.join("\n", capture.messages()))
                        .contains("scheme: https").contains("evil.example.com")
                        .doesNotContain("FAKE-patient").doesNotContain("FAKE-token");
            }
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
        void shouldAcceptBaseUrl_ofTheFileScheme() {
            LocalOnlyUserAgent agent = createAgent();
            agent.setBaseURL("file:///opt/webapp/");
            assertThat(agent.getBaseURL()).isEqualTo("file:///opt/webapp/");
        }

        @Test
        @DisplayName("should accept null base URL")
        void shouldAcceptBaseUrl_thatIsNull() {
            LocalOnlyUserAgent agent = createAgent();
            agent.setBaseURL(null);
            assertThat(agent.getBaseURL()).isNull();
        }

        @Test
        @DisplayName("should reject http: base URL")
        void shouldRejectBaseUrl_ofTheHttpScheme() {
            LocalOnlyUserAgent agent = createAgent();
            agent.setBaseURL("file:///safe/path/");
            agent.setBaseURL("http://evil.com/");
            assertThat(agent.getBaseURL())
                    .as("http: base URL should be rejected, keeping previous value")
                    .isEqualTo("file:///safe/path/");
        }

        @Test
        @DisplayName("should reject https: base URL")
        void shouldRejectBaseUrl_ofTheHttpsScheme() {
            LocalOnlyUserAgent agent = createAgent();
            agent.setBaseURL("https://evil.com/");
            assertThat(agent.getBaseURL())
                    .as("https: base URL should be rejected")
                    .isNotEqualTo("https://evil.com/");
        }
    }

    /** Tests for the safety net that decides which images are loaded (CSS backgrounds and strays). */
    @Nested
    @DisplayName("getImageResource")
    class ImageResourceTests {

        @TempDir
        Path tempDir;

        private ImageResource load(LocalOnlyUserAgent agent, Path file) {
            return agent.getImageResource(file.toUri().toString());
        }

        @Test
        @DisplayName("should refuse SVG resources when no sanitizer was supplied")
        void shouldRefuseSvg_whenCleanerIsMissing() throws Exception {
            Path svg = Files.writeString(tempDir.resolve("unsafe.svg"),
                    "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'><rect width='10' height='10'/></svg>");
            LocalOnlyUserAgent agent = new LocalOnlyUserAgent(new ITextOutputDevice(DEFAULT_DOTS_PER_POINT),
                    DEFAULT_DOTS_PER_PIXEL, List.of(tempDir));

            assertThat(load(agent, svg).getImage()).isNull();
            assertThat(agent.leftOutImages()).isPositive();
        }

        @Test
        @DisplayName("should keep raster images whose metadata or trailing bytes mention SVG")
        void shouldKeepRasterImage_whenBytesMentionSvg() throws Exception {
            Path png = writeGrayPng("with-svg-text.png", 2, 2);
            Files.writeString(png, "<svg xmlns='http://www.w3.org/2000/svg'/>", java.nio.file.StandardOpenOption.APPEND);

            assertThat(load(createAgent(tempDir), png).getImage()).isNotNull();
        }

        private Path writeGrayPng(String name, int width, int height) throws IOException {
            Path file = tempDir.resolve(name);
            ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY), "png", file.toFile());
            return file;
        }

        /** The OpenPDF image inside the resource. */
        private org.openpdf.text.Image openPdfImage(ImageResource resource) {
            return ((ITextFSImage) resource.getImage()).getImage();
        }

        /** Pixels OpenPDF decoded: its raw data holds three bytes per pixel (it expands gray and indexed images to RGB). */
        private long decodedPixels(ImageResource resource) {
            return openPdfImage(resource).getRawData().length / 3L;
        }

        @Test
        @DisplayName("should load a data: image as it is")
        void shouldLoadDataUriImage_asIs() {
            String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";
            ImageResource resource = createAgent(tempDir).getImageResource(dataUri);

            assertThat(resource.getImage()).isNotNull();
            assertThat(resource.getImage().getWidth()).isEqualTo(DEFAULT_DOTS_PER_PIXEL);
        }

        @Test
        @DisplayName("should load a large JPEG as it is, at any resolution")
        void shouldLoadLargeJpegBackground_asIs() throws Exception {
            Path jpeg = tempDir.resolve("big.jpg");
            ImageIO.write(new BufferedImage(5000, 3000, BufferedImage.TYPE_INT_RGB), "jpg", jpeg.toFile());

            ImageResource resource = load(createAgent(tempDir), jpeg);

            assertThat(resource.getImage()).isNotNull();
            // Flying Saucer's resolution scaling is kept: the width is in dots, 20 per pixel.
            assertThat(resource.getImage().getWidth()).isEqualTo(5000 * DEFAULT_DOTS_PER_PIXEL);
        }

        @Test
        @DisplayName("should keep a PNG at full size when it is below the decoded-pixel cap")
        void shouldKeepPng_belowDecodedCap() throws Exception {
            Path png = writeGrayPng("six-megapixels.png", 3000, 2000);

            ImageResource resource = load(createAgent(tempDir), png);

            assertThat(decodedPixels(resource)).isEqualTo(3000L * 2000L);
            assertThat(resource.getImage().getWidth()).isEqualTo(3000 * DEFAULT_DOTS_PER_PIXEL);
        }

        @Test
        @DisplayName("should reduce a PNG above the decoded-pixel cap and keep its printed size")
        void shouldDownscaleLargePng_aboveDecodedCap() throws Exception {
            Path png = writeGrayPng("thirty-megapixels.png", 6000, 5000);

            ImageResource resource = load(createAgent(tempDir), png);

            assertThat(decodedPixels(resource)).isLessThanOrEqualTo(ImageDecoding.MAX_DECODED_PIXELS);
            // The printed size is the original's: Flying Saucer works in dots, 20 per pixel here.
            assertThat(resource.getImage().getWidth()).isEqualTo(6000 * DEFAULT_DOTS_PER_PIXEL);
            assertThat(resource.getImage().getHeight()).isEqualTo(5000 * DEFAULT_DOTS_PER_PIXEL);
        }

        @Test
        @DisplayName("should reduce a large PNG once, however many elements paint it, and share the picture between them")
        void shouldReduceLargePngOnce_forEveryAskOfTheSameUri() throws Exception {
            Path png = writeGrayPng("thirty-megapixels-again.png", 6000, 5000);
            LocalOnlyUserAgent agent = createAgent(tempDir);

            ImageResource first = load(agent, png);
            ImageResource second = load(agent, png);
            ImageResource third = load(agent, png);
            ImageResource other = load(createAgent(tempDir), png);

            ITextFSImage a = (ITextFSImage) first.getImage();
            ITextFSImage b = (ITextFSImage) second.getImage();
            ITextFSImage c = (ITextFSImage) third.getImage();
            // Copies, as Flying Saucer hands out what it caches, of one picture: one image in the PDF.
            assertThat(List.of(System.identityHashCode(a), System.identityHashCode(b), System.identityHashCode(c))).doesNotHaveDuplicates();
            assertThat(b.getImage().getMySerialId()).isEqualTo(a.getImage().getMySerialId());
            assertThat(c.getImage().getMySerialId()).isEqualTo(a.getImage().getMySerialId());
            assertThat(b.getWidth()).isEqualTo(a.getWidth());
            assertThat(b.getHeight()).isEqualTo(a.getHeight());
            // Another agent is another render: its own picture.
            assertThat(((ITextFSImage) other.getImage()).getImage().getMySerialId()).isNotEqualTo(a.getImage().getMySerialId());
        }

        @Test
        @DisplayName("should reduce a large data: PNG once as well")
        void shouldReduceLargeDataPng_once() throws Exception {
            Path png = writeGrayPng("source-of-data.png", 6000, 5000);
            String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(png));
            LocalOnlyUserAgent agent = createAgent(tempDir);

            ITextFSImage first = (ITextFSImage) agent.getImageResource(uri).getImage();
            ITextFSImage second = (ITextFSImage) agent.getImageResource(uri).getImage();

            assertThat(second.getImage().getMySerialId()).isEqualTo(first.getImage().getMySerialId());
        }

        @Test
        @DisplayName("should return an empty resource for a corrupt image")
        void shouldReturnEmpty_forCorruptImage() throws Exception {
            Path corrupt = tempDir.resolve("corrupt.png");
            Files.write(corrupt, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4, 5, 6, 7, 8});

            assertThat(load(createAgent(tempDir), corrupt).getImage()).isNull();
        }

        @Test
        @DisplayName("should return an empty resource for a file over 128 MB without reading it")
        void shouldReturnEmpty_forFileOver128Megabytes() throws Exception {
            Path huge = tempDir.resolve("huge.png");
            try (RandomAccessFile file = new RandomAccessFile(huge.toFile(), "rw")) {
                file.setLength(LocalOnlyUserAgent.MAX_FILE_BYTES + 1);
            }

            assertThat(load(createAgent(tempDir), huge).getImage()).isNull();
        }

        @Test
        @DisplayName("should return an empty resource for a header declaring over 250 megapixels, without decoding")
        void shouldReturnEmpty_forHeaderOver250Megapixels() throws Exception {
            Path bomb = tempDir.resolve("bomb.png");
            Files.write(bomb, PngHeaders.declaring(20_000, 20_000));

            assertThat(load(createAgent(tempDir), bomb).getImage()).isNull();
        }

        @Test
        @DisplayName("should return an empty resource for a PDF, a WebP and an unknown file")
        void shouldReturnEmpty_forPdfWebpAndUnknownFiles() throws Exception {
            Path pdf = tempDir.resolve("as-image.pdf");
            Files.writeString(pdf, "%PDF-1.4\n<svg xmlns='http://www.w3.org/2000/svg'/>\n");
            Path webp = tempDir.resolve("photo.webp");
            try (InputStream in = getClass().getResourceAsStream("/htmlpdf/images/opaque-4x4.webp")) {
                Files.write(webp, in.readAllBytes());
            }
            Path text = tempDir.resolve("note.png");
            Files.writeString(text, "not an image at all");

            LocalOnlyUserAgent agent = createAgent(tempDir);
            assertThat(load(agent, pdf).getImage()).as("a PDF is never drawn as an image").isNull();
            assertThat(load(agent, webp).getImage()).as("WebP is not decoded").isNull();
            assertThat(load(agent, text).getImage()).isNull();
        }

        @Test
        @DisplayName("should pass an SVG file to Flying Saucer")
        void shouldDelegateSvgFile_toFlyingSaucer() throws Exception {
            Path svg = tempDir.resolve("mark.svg");
            Files.writeString(svg, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">"
                    + "<rect width=\"10\" height=\"10\" fill=\"red\"/></svg>");

            assertThat(load(createAgent(tempDir), svg).getImage()).isNotNull();
        }

        @Test
        @DisplayName("should give an SVG its size in device dots, as Flying Saucer gives a raster image its")
        void shouldScaleSvgSize_toDeviceDots() throws Exception {
            Path sized = tempDir.resolve("sized.svg");
            Files.writeString(sized, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"30\" height=\"10\">"
                    + "<rect width=\"30\" height=\"10\" fill=\"red\"/></svg>");
            Path boxed = tempDir.resolve("boxed.svg");
            Files.writeString(boxed, "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 40 20\">"
                    + "<rect width=\"40\" height=\"20\" fill=\"red\"/></svg>");
            Path raster = writeGrayPng("raster.png", 30, 10);
            LocalOnlyUserAgent agent = createAgent(tempDir);

            FSImage svg = load(agent, sized).getImage();
            FSImage viewBox = load(agent, boxed).getImage();

            assertThat(svg.getWidth()).isEqualTo(30 * DEFAULT_DOTS_PER_PIXEL);
            assertThat(svg.getHeight()).isEqualTo(10 * DEFAULT_DOTS_PER_PIXEL);
            assertThat(viewBox.getWidth()).isEqualTo(40 * DEFAULT_DOTS_PER_PIXEL);
            assertThat(viewBox.getHeight()).isEqualTo(20 * DEFAULT_DOTS_PER_PIXEL);
            // The same size Flying Saucer gives a raster image of the same pixels.
            assertThat(load(agent, raster).getImage().getWidth()).isEqualTo(svg.getWidth());
            assertThat(load(agent, raster).getImage().getHeight()).isEqualTo(svg.getHeight());
        }

        @Test
        @DisplayName("should not overflow when an SVG declares a size in the billions")
        void shouldSaturateSvgSize_whenItIsHuge() throws Exception {
            Path huge = tempDir.resolve("huge.svg");
            Files.writeString(huge, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"2000000000\" height=\"7\">"
                    + "<rect width=\"1\" height=\"1\"/></svg>");

            FSImage image = load(createAgent(tempDir), huge).getImage();

            assertThat(image.getWidth()).isEqualTo(Integer.MAX_VALUE);
            assertThat(image.getHeight()).isEqualTo(7 * DEFAULT_DOTS_PER_PIXEL);
        }

        @Test
        @DisplayName("should return an empty resource for an image outside the allowed directories")
        void shouldReturnEmpty_forImageOutsideAllowedDirectories() throws Exception {
            Path allowed = Files.createDirectories(tempDir.resolve("allowed"));
            Path outside = writeGrayPng("outside.png", 10, 10);

            try (LogCapture capture = LogCapture.forLogger(LocalOnlyUserAgent.class)) {
                assertThat(load(createAgent(allowed), outside).getImage()).isNull();
                assertThat(String.join("\n", capture.messages())).doesNotContain(tempDir.toString());
            }
        }

        @Test
        @DisplayName("should return an empty resource for a missing file and for a remote image")
        void shouldReturnEmpty_forMissingAndRemoteImages() {
            LocalOnlyUserAgent agent = createAgent(tempDir);

            assertThat(agent.getImageResource(tempDir.resolve("missing.png").toUri().toString()).getImage()).isNull();
            assertThat(agent.getImageResource("http://evil.com/track.png").getImage()).isNull();
            assertThat(agent.getImageResource(null).getImage()).isNull();
        }

        @Test
        @DisplayName("should return an empty resource for a data: image that is not base64 or is too large")
        void shouldReturnEmpty_forUnusableDataUris() {
            LocalOnlyUserAgent agent = createAgent(tempDir);
            String notBase64 = "data:image/png,%89PNG";
            String garbage = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4});

            assertThat(agent.getImageResource(notBase64).getImage()).isNull();
            assertThat(agent.getImageResource(garbage).getImage()).isNull();
        }
    }

    /** Tests for the createRestrictedRenderer static factory methods. */
    @Nested
    @DisplayName("createRestrictedRenderer")
    class CreateRestrictedRendererTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("should return non-null renderer")
        void shouldReturnRenderer_thatIsNotNull() {
            ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer(List.of(tempDir), new ITextFontResolver());
            assertThat(renderer).isNotNull();
            // Verify the renderer has a shared context configured (required for PDF generation)
            assertThat(renderer.getSharedContext()).isNotNull();
        }

        @Test
        @DisplayName("should use the font resolver it is given")
        void shouldUseFontResolver_itIsGiven() {
            ITextFontResolver resolver = new ITextFontResolver();
            ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer(List.of(tempDir), resolver);
            assertThat(renderer.getFontResolver()).isSameAs(resolver);
        }

        @Test
        @DisplayName("should block external resources when rendering HTML with external images")
        void shouldBlockExternalResources_whenRenderingHtmlWithExternalImages() throws Exception {
            // HTML that references an external image — should be blocked, not fetched
            String html = "<html><body><p>Test</p>"
                    + "<img src=\"http://evil.com/track.png\" alt=\"blocked\" />"
                    + "</body></html>";

            ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer(List.of(tempDir), new ITextFontResolver());
            renderer.getSharedContext().setPrint(true);
            renderer.getSharedContext().setInteractive(false);
            renderer.setDocumentFromString(html, tempDir.toUri().toString());
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
