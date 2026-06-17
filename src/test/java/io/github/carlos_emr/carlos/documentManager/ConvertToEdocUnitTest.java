package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
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
    @DisplayName("should translate inline background asset paths during tidy")
    void shouldTranslateInlineBackgroundAssetPaths_whenTidyingDocument(@TempDir Path tempDir) throws Exception {
        Path image = Files.createFile(tempDir.resolve("stamp.png"));

        String html = "<html><body style=\"background-image:url('stamp.png')\"><div background=\"stamp.png\">x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied).contains(image.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("should not translate inline background asset paths outside the allowed real path root")
    void shouldNotTranslateInlineBackgroundAssetPaths_whenOutsideAllowedRealPathRoot(@TempDir Path tempDir) throws Exception {
        Path externalDir = Files.createDirectory(tempDir.resolve("external"));
        Path externalImage = Files.createFile(externalDir.resolve("outside-stamp.png"));

        String html = "<html><body style=\"background-image:url('" + externalImage.toAbsolutePath() + "')\"><div>x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied).doesNotContain(externalImage.toAbsolutePath().toString());
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
    @DisplayName("should preserve data uri backgrounds during tidy")
    void shouldPreserveDataUriBackgrounds_whenTidyingDocument(@TempDir Path tempDir) {
        String dataUri = "data:image/png;base64,ZmFrZQ==";
        String html = "<html><body style=\"background-image:url('" + dataUri + "')\"><div background=\"" + dataUri + "\">x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied)
                .contains("background-image:url('" + dataUri + "')")
                .contains("background=\"" + dataUri + "\"");
    }

    @Test
    @DisplayName("should strip unresolved traversal shaped background asset paths during tidy")
    void shouldStripUnresolvedTraversalShapedBackgroundAssetPaths_whenTidyingDocument(@TempDir Path tempDir) {
        String traversalPath = "../../../../etc/passwd.png";
        String html = "<html><body style=\"background-image:url('" + traversalPath + "')\"><div background=\"" + traversalPath + "\">x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied)
                .doesNotContain(traversalPath)
                .contains("background-image:url('')")
                .doesNotContain("background=\"");
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
