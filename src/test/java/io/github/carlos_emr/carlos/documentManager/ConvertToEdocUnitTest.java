package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConvertToEdoc unit tests")
@Tag("unit")
@Tag("fast")
@Isolated
class ConvertToEdocUnitTest extends CarlosUnitTestBase {
    private Object originalRealPath;

    @Test
    void shouldKeepEachConcurrentConversionInItsOwnResourceRoot_whenResourceResolutionOverlaps(@TempDir Path tempDir) throws Exception {
        Path firstRoot = Files.createDirectory(tempDir.resolve("first"));
        Path secondRoot = Files.createDirectory(tempDir.resolve("second"));
        for (Path root : java.util.List.of(firstRoot, secondRoot)) {
            Files.createFile(root.resolve("stamp.png"));
        }
        String html = "<html><body><img src='stamp.png'></body></html>";
        CountDownLatch searching = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
                    AtomicBoolean paused = new AtomicBoolean();
                    files.when(() -> Files.walk(firstRoot)).thenAnswer(call -> {
                        if (paused.compareAndSet(false, true)) {
                            searching.countDown();
                            if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("Conversion was not resumed");
                        }
                        return call.callRealMethod();
                    });
                    return ConvertToEdoc.tidyDocument(html, firstRoot.toString());
                }
            });
            assertThat(searching.await(5, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> {
                secondThread.set(Thread.currentThread()); secondStarted.countDown();
                return ConvertToEdoc.tidyDocument(html, secondRoot.toString());
            });
            try {
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!second.isDone() && secondThread.get().getState() != Thread.State.BLOCKED
                        && System.nanoTime() < deadline) Thread.yield();
            } finally { resume.countDown(); }
            assertThat(first.get(5, TimeUnit.SECONDS)).contains(firstRoot.resolve("stamp.png").toString())
                    .doesNotContain(secondRoot.resolve("stamp.png").toString());
            assertThat(second.get(5, TimeUnit.SECONDS)).contains(secondRoot.resolve("stamp.png").toString())
                    .doesNotContain(firstRoot.resolve("stamp.png").toString());
        } finally { resume.countDown(); }
    }

    @BeforeEach
    void setUp() {
        originalRealPath = org.springframework.test.util.ReflectionTestUtils.getField(ConvertToEdoc.class, "realPath");
        registerMock(NioFileManager.class, Mockito.mock(NioFileManager.class));
    }

    @AfterEach
    void restoreResourceRoot() {
        synchronized (ConvertToEdoc.class) {
            org.springframework.test.util.ReflectionTestUtils.setField(ConvertToEdoc.class, "realPath", originalRealPath);
        }
    }

    @Test
    @DisplayName("should remove unresolved external background and css urls during tidy")
    void shouldRemoveUnresolvedExternalBackgroundAndCssUrls_whenTidyingDocument() {
        String html = "<html><body background=\"https://evil.example/tracker.png\" style=\"background-image:url('https://evil.example/tracker.png')\">x</body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html);

        assertThat(tidied)
                .doesNotContain("https://evil.example/tracker.png")
                .doesNotContain("background=\"https://evil.example/tracker.png\"")
                .contains("background-image:url('')");
    }

    @Test
    @DisplayName("should preserve embedded data resource urls during tidy")
    void shouldPreserveEmbeddedDataResourceUrls_whenTidyingDocument() {
        String html = "<html><body background=\"data:image/png;base64,abc\" style=\"background-image:url('data:image/png;base64,abc')\">x</body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html);

        // data: URIs pass through translateSingleResourcePath unchanged, then SafeEncode.forCssString
        // encodes '/' as '\2f' in the CSS url() context (FlyingSaucer's CSS parser decodes it back).
        // The background= attribute is not CSS-encoded, so the raw URI is preserved there.
        assertThat(tidied)
                .contains("data:image/png;base64,abc")
                .contains("background-image:url('" + SafeEncode.forCssString("data:image/png;base64,abc") + "')")
                .contains("background=\"data:image/png;base64,abc\"");
    }

    @Test
    @DisplayName("should translate cache-busted local asset paths during tidy")
    void shouldTranslateCacheBustedLocalAssetPaths_whenTidyingDocument(@TempDir Path tempDir) throws Exception {
        Path image = Files.createFile(tempDir.resolve("stamp.png"));

        String html = "<html><body style=\"background-image:url('stamp.png?v=1')\"><div>x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        // A translated path is an encoded file: URI; SafeEncode.forCssString encodes '/' in it — match the encoded form.
        assertThat(tidied).contains("url('" + SafeEncode.forCssString(image.toAbsolutePath().toUri().toString()) + "')");
    }

    @Test
    @DisplayName("should strip unresolved absolute file paths during tidy, and keep the image as an unavailable marker")
    void shouldStripUnresolvedAbsoluteFilePaths_whenTidyingDocument(@TempDir Path tempDir) {
        String html = "<html><body><img src=\"/etc/passwd\"><div background=\"/etc/passwd\">x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied)
                .doesNotContain("/etc/passwd")
                .doesNotContain("background=\"/etc/passwd\"");
        // The image is not removed: the printed page shows a box where it was.
        assertThat(Jsoup.parse(tidied).select("img[data-carlos-image-unavailable]:not([src])")).hasSize(1);
    }

    @Test
    @DisplayName("should translate inline background asset paths during tidy")
    void shouldTranslateInlineBackgroundAssetPaths_whenTidyingDocument(@TempDir Path tempDir) throws Exception {
        Path image = Files.createFile(tempDir.resolve("stamp(1).png"));

        String html = "<html><body style=\"background-image:url( 'stamp(1).png' )\"><div background=\"stamp(1).png\">x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied)
                .contains("background=\"" + image.toAbsolutePath().toUri() + "\"")
                .contains("background-image:url('" + SafeEncode.forCssString(image.toAbsolutePath().toUri().toString()) + "')")
                .doesNotContain("background-image:url( 'stamp(1).png' )");
    }

    @Test
    @DisplayName("should preserve data uri resource elements when parsing document")
    void shouldPreserveDataUriResourceElements_whenParsingDocument(@TempDir Path tempDir) {
        String dataUri = "data:image/png;base64,ZmFrZQ==";
        String html = "<html><head><link rel=\"icon\" href=\"" + dataUri + "\"></head>"
                + "<body><img alt=\"inline\" src=\"" + dataUri + "\"><script src=\"data:text/javascript,console.log(1)\"></script></body></html>";

        Document document = ConvertToEdoc.getDocument(html, tempDir.toString());

        assertThat(document.select("link[href], img[src], script[src]")).hasSize(3);
    }

    @Test
    @DisplayName("should remove unresolved traversal shaped resource elements when parsing document")
    void shouldRemoveUnresolvedTraversalShapedResourceElements_whenParsingDocument(@TempDir Path tempDir) {
        String html = "<html><head><link rel=\"stylesheet\" href=\"../../../../etc/passwd.css\"></head>"
                + "<body><img src=\"../../../../etc/passwd.png\"><script src=\"../../../../etc/passwd.js\"></script></body></html>";

        Document document = ConvertToEdoc.getDocument(html, tempDir.toString());

        // The link and the script go; the image stays as an unavailable marker without a source or a path.
        assertThat(document.select("link[href], img[src], script[src]")).isEmpty();
        assertThat(document.select("link, script")).isEmpty();
        assertThat(document.select("img[data-carlos-image-unavailable]")).hasSize(1);
        assertThat(document.outerHtml()).doesNotContain("passwd");
    }
    @Test
    @DisplayName("should translate every image when the same src is used several times")
    void shouldTranslateEveryImage_whenSrcRepeats(@TempDir Path tempDir) throws Exception {
        Path stamp = Files.createFile(tempDir.resolve("stamp.png"));
        String html = "<html><body><img id=\"a\" src=\"stamp.png\"><img id=\"b\" src=\"stamp.png\"><img id=\"c\" src=\"stamp.png\"></body></html>";

        Document tidied = Jsoup.parse(ConvertToEdoc.tidyDocument(html, tempDir.toString()));

        assertThat(tidied.select("img")).hasSize(3);
        for (String id : java.util.List.of("a", "b", "c")) {
            assertThat(tidied.getElementById(id).attr("src")).as(id).isEqualTo(stamp.toAbsolutePath().toUri().toString());
        }
    }

    @Test
    @DisplayName("should emit encoded file URIs for translated paths, so a space or an accent in a folder name loads")
    void shouldEmitEncodedFileUris_forTranslatedPaths(@TempDir Path tempDir) throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("FAKE dir " + (char) 0xE9));
        Files.createFile(root.resolve("stamp.png"));
        Files.createFile(root.resolve("print.css"));
        String html = "<html><head><link rel=\"stylesheet\" href=\"print.css\"></head><body><img src=\"stamp.png\"></body></html>";

        Document tidied = Jsoup.parse(ConvertToEdoc.tidyDocument(html, root.toString()));

        String src = tidied.selectFirst("img").attr("src");
        assertThat(src).startsWith("file:").contains("%20").contains("%C3%A9").doesNotContain(" ");
        assertThat(Path.of(java.net.URI.create(src))).isEqualTo(root.resolve("stamp.png").toRealPath());
        assertThat(tidied.selectFirst("link").attr("href")).startsWith("file:").contains("%20").contains("%C3%A9");
    }

    @Test
    @DisplayName("should keep an image it cannot resolve, or whose type is not allowed, as an unavailable marker without a source")
    void shouldMarkUnresolvedImage_insteadOfRemoving(@TempDir Path tempDir) throws Exception {
        // WebP and HEIC are not decoded by the render service, so a path to one stays out of the allowed types.
        Files.createFile(tempDir.resolve("scan.webp"));
        Files.createFile(tempDir.resolve("photo.heic"));
        String html = "<html><body><img id=\"missing\" src=\"missing.png\" width=\"90\" height=\"40\">"
                + "<img id=\"webp\" src=\"scan.webp\" srcset=\"scan.webp 2x\"><img id=\"heic\" src=\"photo.heic\"></body></html>";

        Document tidied = Jsoup.parse(ConvertToEdoc.tidyDocument(html, tempDir.toString()));

        assertThat(tidied.select("img")).hasSize(3);
        for (String id : java.util.List.of("missing", "webp", "heic")) {
            assertThat(tidied.getElementById(id).hasAttr("data-carlos-image-unavailable")).as(id).isTrue();
            assertThat(tidied.getElementById(id).hasAttr("src")).as(id).isFalse();
            assertThat(tidied.getElementById(id).hasAttr("srcset")).as(id).isFalse();
        }
        // The size the author gave is kept, so the box takes the picture's place.
        assertThat(tidied.getElementById("missing").attr("width")).isEqualTo("90");
        assertThat(tidied.outerHtml()).doesNotContain("missing.png").doesNotContain("scan.webp").doesNotContain("photo.heic");
    }

    @Test
    @DisplayName("should translate the path of every picture type the render service draws, whatever the case of its extension")
    void shouldTranslateImagePaths_ofTheTypesTheRenderServiceDraws(@TempDir Path tempDir) throws Exception {
        java.util.List<String> names = java.util.List.of("stamp.png", "photo.jpg", "photo.jpeg", "spinner.gif", "scan.bmp",
                "pixel.wbmp", "fax.tif", "fax.tiff", "logo.svg", "SCAN.BMP");
        StringBuilder html = new StringBuilder("<html><body>");
        for (int i = 0; i < names.size(); i++) {
            Files.createFile(tempDir.resolve(names.get(i)));
            html.append("<img id=\"i").append(i).append("\" src=\"").append(names.get(i)).append("\">");
        }
        html.append("</body></html>");

        Document tidied = Jsoup.parse(ConvertToEdoc.tidyDocument(html.toString(), tempDir.toString()));

        assertThat(tidied.select("img")).hasSize(names.size());
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            assertThat(tidied.getElementById("i" + i).hasAttr("data-carlos-image-unavailable")).as(name).isFalse();
            String src = tidied.getElementById("i" + i).attr("src");
            assertThat(src).as(name).startsWith("file:");
            assertThat(Files.isSameFile(Path.of(java.net.URI.create(src)), tempDir.resolve(name))).as(name).isTrue();
        }
    }

    @Test
    @DisplayName("should keep a remote, protocol-relative or file: image as an unavailable marker, without its address")
    void shouldMarkRemoteImage_insteadOfRemoving(@TempDir Path tempDir) {
        String html = "<html><body><img src=\"http://evil.example/t.png\"><img src=\"https://evil.example/t.png\">"
                + "<img src=\"//evil.example/t.png\"><img src=\"file:///etc/passwd\"></body></html>";

        Document tidied = Jsoup.parse(ConvertToEdoc.tidyDocument(html, tempDir.toString()));

        assertThat(tidied.select("img[data-carlos-image-unavailable]:not([src])")).hasSize(4);
        assertThat(tidied.outerHtml()).doesNotContain("evil.example").doesNotContain("passwd");
    }

    @Test
    @DisplayName("should keep removing links and scripts it cannot resolve while tidying")
    void shouldKeepRemovingUnresolvedLinksAndScripts_whenTidying(@TempDir Path tempDir) {
        String html = "<html><head><link rel=\"stylesheet\" href=\"missing.css\"><link rel=\"stylesheet\" href=\"http://evil.example/x.css\">"
                + "<script src=\"missing.js\"></script><script src=\"https://evil.example/x.js\"></script></head><body><p>text</p></body></html>";

        Document tidied = Jsoup.parse(ConvertToEdoc.tidyDocument(html, tempDir.toString()));

        assertThat(tidied.select("link, script")).isEmpty();
        assertThat(tidied.text()).isEqualTo("text");
    }

    @Test
    @DisplayName("should point an image at the first candidate that is a readable file, not at the first candidate")
    void shouldUseFirstReadableCandidate_whenAnImageSrcNamesSeveralParameters(@TempDir Path tempDir) throws Exception {
        String originalEformImagesDir = CarlosProperties.getInstance().getProperty("EFORM_IMAGES_DIR");
        Path imageDirectory = Files.createDirectories(tempDir.resolve("eform-images"));
        CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", imageDirectory.toString());
        Path logo = Files.createFile(imageDirectory.resolve("logo.png"));
        try {
            // Two parameters name a file in the image directory; only the second one exists.
            String html = "<html><body><img src=\"eform/displayImage?junk=missing.png&imagefile=logo.png\"></body></html>";

            Document tidied = Jsoup.parse(ConvertToEdoc.tidyDocument(html, tempDir.toString()));

            assertThat(tidied.selectFirst("img").attr("src")).isEqualTo(logo.toAbsolutePath().toUri().toString());
        } finally {
            if (originalEformImagesDir == null) {
                CarlosProperties.getInstance().remove("EFORM_IMAGES_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", originalEformImagesDir);
            }
        }
    }

    @Test
    @DisplayName("should link the custom stylesheet named by the hidden input as an encoded file URI")
    void shouldLinkCustomStylesheet_asFileUri(@TempDir Path tempDir) throws Exception {
        String originalEformImagesDir = CarlosProperties.getInstance().getProperty("EFORM_IMAGES_DIR");
        Path imageDirectory = Files.createDirectories(tempDir.resolve("eform images"));
        CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", imageDirectory.toString());
        Path stylesheet = Files.writeString(imageDirectory.resolve("letter print.css"), "p { color: #123456 }");
        try {
            String html = "<html><head></head><body><input type=\"hidden\" id=\"" + ConvertToEdoc.CUSTOM_STYLESHEET_ID
                    + "\" name=\"s\" value=\"letter print.css\"><p>x</p></body></html>";

            Document tidied = Jsoup.parse(ConvertToEdoc.tidyDocument(html, tempDir.toString()));

            String href = tidied.selectFirst("head link[rel=stylesheet]").attr("href");
            assertThat(href).startsWith("file:").contains("letter%20print.css").contains("eform%20images");
            assertThat(Path.of(java.net.URI.create(href))).isEqualTo(stylesheet.toRealPath());
        } finally {
            if (originalEformImagesDir == null) {
                CarlosProperties.getInstance().remove("EFORM_IMAGES_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", originalEformImagesDir);
            }
        }
    }

    @Test
    @DisplayName("should preserve oscar image path resources when backing files exist")
    void shouldPreserveOscarImagePathResources_whenBackingFilesExist(@TempDir Path tempDir) throws Exception {
        // Point the eform image root at this test's @TempDir so the ${oscar_image_path} backing
        // files are created and resolved in an isolated, auto-cleaned location rather than the real
        // configured image store. ConvertToEdoc resolves the directory live, so this override takes
        // effect for the getDocument() call below; restored in the finally block.
        String originalEformImagesDir = CarlosProperties.getInstance().getProperty("EFORM_IMAGES_DIR");
        Path imageDirectory = tempDir.resolve("eform-images");
        Files.createDirectories(imageDirectory);
        CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", imageDirectory.toString());
        Path image = imageDirectory.resolve("convert-to-edoc-oscar-image-path-test.png");
        Path script = imageDirectory.resolve("convert-to-edoc-oscar-image-path-test.js");
        Files.writeString(image, "png-placeholder");
        Files.writeString(script, "console.log('ok');");

        try {
            String html = "<html><head><script src=\"${oscar_image_path}convert-to-edoc-oscar-image-path-test.js\"></script></head>"
                    + "<body><img src=\"${oscar_image_path}convert-to-edoc-oscar-image-path-test.png\"></body></html>";

            Document document = ConvertToEdoc.getDocument(html, tempDir.toString());

            assertThat(document.select("img[src], script[src]")).hasSize(2);
            assertThat(document.outerHtml())
                    .contains("${oscar_image_path}convert-to-edoc-oscar-image-path-test.png")
                    .contains("${oscar_image_path}convert-to-edoc-oscar-image-path-test.js");
        } finally {
            // Properties.setProperty rejects null, so a previously-unset value is cleared from the map.
            if (originalEformImagesDir == null) {
                CarlosProperties.getInstance().remove("EFORM_IMAGES_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", originalEformImagesDir);
            }
        }
    }

    @Test
    @DisplayName("should preserve percent-encoded oscar image path resources when backing files exist")
    void shouldPreserveEncodedOscarImagePathResources_whenBackingFilesExist(@TempDir Path tempDir) throws Exception {
        String originalEformImagesDir = CarlosProperties.getInstance().getProperty("EFORM_IMAGES_DIR");
        Path imageDirectory = tempDir.resolve("eform-images");
        Files.createDirectories(imageDirectory);
        CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", imageDirectory.toString());
        Path image = imageDirectory.resolve("convert-to-edoc-encoded-token-test.png");
        Files.writeString(image, "png-placeholder");

        try {
            // The token (and filename) arrive percent-encoded in stored markup; the resolver must
            // still find the backing file and preserve the resource instead of dropping it.
            String html = "<html><body>"
                    + "<img src=\"%24%7Boscar_image_path%7Dconvert-to-edoc-encoded-token-test.png\">"
                    + "</body></html>";

            Document document = ConvertToEdoc.getDocument(html, tempDir.toString());

            assertThat(document.select("img[src]")).hasSize(1);
            assertThat(document.outerHtml()).contains("%24%7Boscar_image_path%7Dconvert-to-edoc-encoded-token-test.png");
        } finally {
            if (originalEformImagesDir == null) {
                CarlosProperties.getInstance().remove("EFORM_IMAGES_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", originalEformImagesDir);
            }
        }
    }

}
