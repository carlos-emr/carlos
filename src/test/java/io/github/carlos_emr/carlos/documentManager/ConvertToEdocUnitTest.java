package io.github.carlos_emr.carlos.documentManager;

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

        // data: URIs pass through translateSingleResourcePath unchanged, so they
        // are emitted as url('data:...') — SafeEncode.forCssString is a no-op on base64 content.
        assertThat(tidied)
                .contains("data:image/png;base64,abc")
                .contains("background-image:url('data:image/png;base64,abc')")
                .contains("background=\"data:image/png;base64,abc\"");
    }

    @Test
    @DisplayName("should translate cache-busted local asset paths during tidy")
    void shouldTranslateCacheBustedLocalAssetPaths_whenTidyingDocument(@TempDir Path tempDir) throws Exception {
        Path image = Files.createFile(tempDir.resolve("stamp.png"));

        String html = "<html><body style=\"background-image:url('stamp.png?v=1')\"><div>x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied).contains(image.toAbsolutePath().toString());
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
}
