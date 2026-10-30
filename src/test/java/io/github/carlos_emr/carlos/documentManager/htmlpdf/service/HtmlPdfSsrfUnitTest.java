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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestImages;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestPdfs;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Hostile HTML must make no request and read no file it was not given, on every rung of the ladder: the
 * document names a local listening socket in every way a document can, and the test counts what arrives.
 * Together with the unit tests of the user agent, the SVG cleaner and the image planner this is the end-to-end
 * check of the module's SSRF and file-disclosure rules (spec: Resource loading and security).
 *
 * <p>The listener is on the loopback interface and refuses nothing: every connection it gets is counted and
 * closed at once, so a renderer that does connect fails the test quickly instead of waiting for an answer.</p>
 */
@Tag("unit")
@Tag("htmlpdf")
@Tag("security")
@DisplayName("HTML-to-PDF SSRF and file disclosure")
class HtmlPdfSsrfUnitTest {

    @TempDir
    Path directory;

    private final PrintLayoutService layout = Mockito.spy(new PrintLayoutService());

    /** An SVG that draws the image outside the web root, as the document that a {@code data:} URI holds. */
    private static String nestedSvgUri(String secretImageUri) {
        String nested = "<svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink' width='30' height='30'>"
                + "<g id='a'><image xlink:href='" + secretImageUri + "' width='30' height='30'/></g></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(nested.getBytes(StandardCharsets.UTF_8));
    }

    /** Every way a document can name a resource, all pointing at the listener, and at files outside the web root. */
    private String hostileHtml(int port, Path secret, Path secretImage) {
        String remote = "http://127.0.0.1:" + port;
        String secretUri = secret.toUri().toString();
        String secretImageUri = secretImage.toUri().toString();
        String nestedUri = nestedSvgUri(secretImageUri);
        return "<!DOCTYPE html><html><head><title>FAKE-Hostile</title>"
                + "<base href=\"" + remote + "/base/\">"
                + "<link rel=\"stylesheet\" href=\"" + remote + "/a.css\">"
                + "<link rel=\"stylesheet\" href=\"relative.css\">"
                + "<link rel=\"stylesheet\" href=\"" + secretUri + "\">"
                + "<link rel=\"stylesheet\" href=\"file:///etc/passwd\">"
                + "<link rel=\"icon\" href=\"" + remote + "/icon.ico\"><link rel=\"prefetch\" href=\"" + remote + "/prefetch\">"
                + "<script src=\"" + remote + "/s.js\"></script>"
                + "<style>@import \"" + remote + "/b.css\"; @import url(" + remote + "/c.css); "
                + "@font-face { font-family: Evil; src: url(" + remote + "/e.ttf) } p.evil { font-family: Evil }"
                + " .bg { background: url(" + remote + "/d.png) } li { list-style-image: url(" + remote + "/li.png) }"
                + " .c:before { content: url(" + remote + "/content.png) } .cur { cursor: url(" + remote + "/cursor.cur), auto }"
                + " .jar { background: url(jar:" + remote + "/g.jar!/x.png) } .rel { background: url(//127.0.0.1:" + port + "/rel.png) }"
                + " .passwd { background: url(file:///etc/passwd) } .secret { background: url(" + secretImageUri + ") }</style>"
                + "</head><body background=\"" + remote + "/body.png\">"
                + "<p class=\"evil bg c cur jar rel passwd secret\" style=\"background: url(" + remote + "/inline.png)\">FAKE-visible text</p>"
                + "<ul><li>FAKE-item</li></ul>"
                + "<table background=\"" + remote + "/table.png\"><tr><td background=\"" + remote + "/td.png\">FAKE-cell</td></tr></table>"
                + "<img src=\"" + remote + "/f.png\"><img src=\"jar:" + remote + "/g.jar!/x.png\"><img src=\"//127.0.0.1:" + port + "/h.png\">"
                + "<img src=\"file:///etc/passwd\"><img src=\"" + secretImageUri + "\"><img src=\"../secret/secret.png\">"
                + "<img srcset=\"" + remote + "/i.png 2x\" src=\"j.png\"><img src=\"relative.png\">"
                + "<input type=\"image\" src=\"" + remote + "/button.png\">"
                + "<iframe src=\"" + remote + "/k.html\"></iframe><object data=\"" + remote + "/l.png\"></object><embed src=\"" + remote + "/m.png\">"
                + "<video src=\"" + remote + "/v.mp4\" poster=\"" + remote + "/poster.png\"></video><audio src=\"" + remote + "/a.mp3\"></audio>"
                + "<svg width=\"50\" height=\"50\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"><image xlink:href=\"" + remote + "/n.png\" width=\"10\" height=\"10\"/>"
                + "<use xlink:href=\"" + remote + "/o.svg#x\"/><style>@import url(" + remote + "/svg.css); rect { fill: url(" + remote + "/p.svg#g) }</style>"
                + "<rect width=\"20\" height=\"20\"/></svg>"
                + "<img src=\"outside.svg\" width=\"40\" height=\"20\">"
                // An SVG inside an SVG, inside a data: URI: whatever it names is outside the allowed directories too.
                + "<svg width=\"40\" height=\"40\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"><image xlink:href=\"" + nestedUri
                + "\" width=\"30\" height=\"30\"/></svg>"
                + "<svg width=\"40\" height=\"40\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"><use xlink:href=\"" + nestedUri
                + "#a\"/></svg>"
                + "<img src=\"nested.svg\" width=\"40\" height=\"40\">"
                + "<img src=\"data:image/svg+xml;utf8,&lt;svg xmlns='http://www.w3.org/2000/svg'&gt;&lt;image href='" + remote + "/q.png'/&gt;&lt;/svg&gt;\">"
                + "<a href=\"" + remote + "/link\">FAKE-link text</a>"
                + "<form action=\"" + remote + "/submit\"><input type=\"text\" value=\"FAKE-field\"></form>"
                + "</body></html>";
    }

    /** An SVG file in the web root with an external DTD, external entities and references. */
    private byte[] hostileSvg(int port, Path secret) {
        return ("<?xml version=\"1.0\"?>\n<!DOCTYPE svg SYSTEM \"http://127.0.0.1:" + port + "/evil.dtd\" [\n"
                + "<!ENTITY remote SYSTEM \"http://127.0.0.1:" + port + "/entity\">\n<!ENTITY local SYSTEM \"" + secret.toUri() + "\">\n"
                + "<!ENTITY passwd SYSTEM \"file:///etc/passwd\">\n<!ENTITY % param SYSTEM \"http://127.0.0.1:" + port + "/param.dtd\"> %param;\n]>\n"
                + "<?xml-stylesheet href=\"http://127.0.0.1:" + port + "/svg-style.css\" type=\"text/css\"?>\n"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"40\" height=\"20\">"
                + "<style>@import url(http://127.0.0.1:" + port + "/svg-import.css);</style>"
                + "<text>&remote;&local;&passwd;</text><image xlink:href=\"http://127.0.0.1:" + port + "/svg-image.png\"/>"
                + "<rect width=\"40\" height=\"20\" fill=\"#cc0000\"/></svg>").getBytes(StandardCharsets.UTF_8);
    }

    private HtmlPdfRenderService service(int textOnlyHtmlChars) {
        return new HtmlPdfRenderService(new PrintDocumentComposer(), new PrintImageService(directory), layout, new RenderedPdfValidator(),
                HtmlPdfTestServices.sharedFontFiles(), Duration.ofSeconds(10), 256L << 20, textOnlyHtmlChars, directory);
    }

    /** Runs the render with a listener on the loopback interface and returns how many connections it got. */
    private int connectionsDuringRender(HtmlPdfRenderService service, boolean forceSafe, boolean forceTextOnly, Result out) throws Exception {
        Path webRoot = Files.createDirectories(directory.resolve("web"));
        Path secretDirectory = Files.createDirectories(directory.resolve("secret"));
        Path secret = Files.writeString(secretDirectory.resolve("secret.txt"), "FAKE-SECRET-CONTENT root:x:0:0");
        Path secretImage = TestImages.write(secretDirectory, "secret.png", TestImages.png(20, 20));
        AtomicInteger connections = new AtomicInteger();
        try (ServerSocket probe = new ServerSocket(0)) {
            Thread listener = new Thread(() -> {
                try {
                    while (true) {
                        probe.accept().close();
                        connections.incrementAndGet();
                    }
                } catch (IOException closed) {
                    // The test is over.
                }
            }, "ssrf-probe");
            listener.setDaemon(true);
            listener.start();
            int port = probe.getLocalPort();
            Files.write(webRoot.resolve("outside.svg"), hostileSvg(port, secret));
            Files.writeString(webRoot.resolve("nested.svg"), "<svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'"
                    + " width='40' height='40'><image xlink:href='" + nestedSvgUri(secretImage.toUri().toString())
                    + "' width='30' height='30'/></svg>", StandardCharsets.UTF_8);
            TestImages.write(webRoot, "j.png", TestImages.png(10, 10));
            if (forceSafe || forceTextOnly) {
                doAnswer(invocation -> {
                    PrintMode mode = invocation.getArgument(0);
                    if (mode == PrintMode.NORMAL || (forceTextOnly && mode == PrintMode.SAFE)) {
                        throw new IllegalStateException("forced");
                    }
                    return invocation.callRealMethod();
                }).when(layout).render(any(), any(), any(), any(), any(), any());
            }
            HtmlPdfRenderRequest request = new HtmlPdfRenderRequest(hostileHtml(port, secret, secretImage), webRoot, List.of(webRoot), HtmlPdfRenderSource.FORM);

            out.result = service.render(request);
        }
        return connections.get();
    }

    private static final class Result {
        HtmlPdfRenderResult result;
    }

    /** A size limit no test document reaches. */
    private static final class PrintModeLimits {
        static final int NO_LIMIT = 100_000_000;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"normal", "safe", "text-only"})
    @DisplayName("should make no request and print no file's content for hostile HTML, in every ladder mode")
    void shouldMakeNoRequest_forHostileHtmlInEveryMode(String mode) throws Exception {
        Result out = new Result();
        HtmlPdfRenderService service = service(PrintModeLimits.NO_LIMIT);

        int connections = connectionsDuringRender(service, mode.equals("safe"), mode.equals("text-only"), out);

        assertThat(connections).as("connections to the local listener").isZero();
        String text = String.join("\n", TestPdfs.pageTexts(out.result.pdf()));
        assertThat(text).contains("FAKE-visible text", "FAKE-cell", "FAKE-link text", "FAKE-item");
        assertThat(text).doesNotContain("FAKE-SECRET-CONTENT", "root:x:0:0", "root:");
        // The one image that is drawn is the harmless j.png of the srcset test; the PNG outside the web root, which
        // the document names as an image, a background and a relative path, is never read.
        assertThat(TestPdfs.imageDraws(out.result.pdf())).isEqualTo(mode.equals("text-only") ? 0 : 1);
        switch (mode) {
            case "normal" -> assertThat(out.result.degradations()).contains(HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE)
                    .doesNotContain(HtmlPdfRenderDegradation.SAFE_MODE, HtmlPdfRenderDegradation.TEXT_ONLY);
            case "safe" -> assertThat(out.result.degradations()).contains(HtmlPdfRenderDegradation.SAFE_MODE, HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
            // Text-only mode has no images at all, so there is nothing to box.
            default -> assertThat(out.result.degradations()).contains(HtmlPdfRenderDegradation.TEXT_ONLY);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"inline-use", "inline-image", "svg-file", "svg-data-img"})
    @DisplayName("should draw no file from outside the allowed directories through an SVG held in a data: URI inside an SVG")
    void shouldNotDrawSecretImage_throughNestedSvgInDataUri(String vector) throws Exception {
        // Batik loads a data: URI as a document of its own and, for the use of one of its elements, draws what
        // that element names (here an image outside the web root) without asking the user agent.
        Path webRoot = Files.createDirectories(directory.resolve("web"));
        Path secretImage = TestImages.write(Files.createDirectories(directory.resolve("secret")), "secret.png", TestImages.png(20, 20));
        String nestedUri = nestedSvgUri(secretImage.toUri().toString());
        String xlink = "xmlns:xlink=\"http://www.w3.org/1999/xlink\"";
        String useOfNested = "<svg width=\"40\" height=\"40\" " + xlink + "><use xlink:href=\"" + nestedUri + "#a\"/></svg>";
        String body = switch (vector) {
            case "inline-use" -> useOfNested;
            case "inline-image" -> "<svg width=\"40\" height=\"40\" " + xlink + "><image xlink:href=\"" + nestedUri + "\" width=\"30\" height=\"30\"/></svg>";
            case "svg-file" -> {
                Files.writeString(webRoot.resolve("outer.svg"), useOfNested.replace("<svg ", "<svg xmlns=\"http://www.w3.org/2000/svg\" "),
                        StandardCharsets.UTF_8);
                yield "<img src=\"outer.svg\" width=\"40\" height=\"40\">";
            }
            default -> "<img width=\"40\" height=\"40\" src=\"data:image/svg+xml;base64,"
                    + Base64.getEncoder().encodeToString(useOfNested.replace("<svg ", "<svg xmlns=\"http://www.w3.org/2000/svg\" ")
                            .getBytes(StandardCharsets.UTF_8)) + "\">";
        };
        HtmlPdfRenderRequest request = new HtmlPdfRenderRequest("<html><body><p>FAKE-visible text</p>" + body + "</body></html>", webRoot,
                List.of(webRoot), HtmlPdfRenderSource.FORM);

        HtmlPdfRenderResult result = service(PrintModeLimits.NO_LIMIT).render(request);

        assertThat(TestPdfs.imageDraws(result.pdf())).as("images drawn").isZero();
        // The drawing is cleaned, not given up on: no step down the ladder, and the rest of the page prints.
        assertThat(result.degradations()).doesNotContain(HtmlPdfRenderDegradation.SAFE_MODE, HtmlPdfRenderDegradation.TEXT_ONLY);
        assertThat(String.join("\n", TestPdfs.pageTexts(result.pdf()))).contains("FAKE-visible text");
    }

    @Test
    @DisplayName("should make no request either when the size limit sends the hostile document straight to text-only mode")
    void shouldMakeNoRequest_whenSizeLimitForcesTextOnly() throws Exception {
        Result out = new Result();

        int connections = connectionsDuringRender(service(200), false, false, out);

        assertThat(connections).isZero();
        assertThat(out.result.degradations()).contains(HtmlPdfRenderDegradation.TEXT_ONLY);
        assertThat(String.join("\n", TestPdfs.pageTexts(out.result.pdf()))).doesNotContain("FAKE-SECRET-CONTENT", "root:");
    }
}
