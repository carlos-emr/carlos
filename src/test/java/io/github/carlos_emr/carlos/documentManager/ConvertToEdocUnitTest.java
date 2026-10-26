package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConvertToEdoc unit tests")
@Tag("unit")
@Tag("fast")
class ConvertToEdocUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void setUp() {
        registerMock(NioFileManager.class, Mockito.mock(NioFileManager.class));
    }

    @Test
    @DisplayName("should normalize invalid HTML comments for Flying Saucer")
    void shouldNormalizeInvalidHtmlComments_whenPreparingForFlyingSaucer() {
        Document document = ConvertToEdoc.prepareDocumentForFlyingSaucer("<html><body><!-- bad -- comment--><div>ok</div></body></html>");

        assertThat(document.outerHtml()).contains("bad - - comment--");
        assertThat(document.outerHtml()).doesNotContain("bad -- comment--");
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

        // SafeEncode.forCssString encodes '/' in the absolute path — match the encoded form.
        assertThat(tidied).contains("url('" + SafeEncode.forCssString(image.toAbsolutePath().toString()) + "')");
    }

    @Test
    @DisplayName("should strip unresolved absolute file paths during tidy")
    void shouldStripUnresolvedAbsoluteFilePaths_whenTidyingDocument(@TempDir Path tempDir) {
        String html = "<html><body><img src=\"/etc/passwd\"><div background=\"/etc/passwd\">x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied)
                .doesNotContain("/etc/passwd")
                .doesNotContain("background=\"/etc/passwd\"");
    }

    @Test
    @DisplayName("should translate inline background asset paths during tidy")
    void shouldTranslateInlineBackgroundAssetPaths_whenTidyingDocument(@TempDir Path tempDir) throws Exception {
        Path image = Files.createFile(tempDir.resolve("stamp(1).png"));

        String html = "<html><body style=\"background-image:url( 'stamp(1).png' )\"><div background=\"stamp(1).png\">x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied)
                .contains("background=\"" + image.toAbsolutePath() + "\"")
                .contains("background-image:url('" + SafeEncode.forCssString(image.toAbsolutePath().toString()) + "')")
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

        assertThat(document.select("link[href], img[src], script[src]")).isEmpty();
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
