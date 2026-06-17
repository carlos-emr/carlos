package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
    void shouldTranslateInlineBackgroundAssetPaths_whenTidyingDocument() throws Exception {
        Path tempDir = Files.createTempDirectory("convert-edoc");
        Path image = Files.createFile(tempDir.resolve("stamp.png"));

        String html = "<html><body style=\"background-image:url('stamp.png')\"><div background=\"stamp.png\">x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied).contains(image.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("should not translate inline background asset paths outside the allowed real path root")
    void shouldNotTranslateInlineBackgroundAssetPaths_whenOutsideAllowedRealPathRoot() throws Exception {
        Path tempDir = Files.createTempDirectory("convert-edoc-root");
        Path externalDir = Files.createTempDirectory("convert-edoc-external");
        Path externalImage = Files.createFile(externalDir.resolve("outside-stamp.png"));

        String html = "<html><body style=\"background-image:url('" + externalImage.toAbsolutePath() + "')\"><div>x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied).doesNotContain(externalImage.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("should translate cache-busted local asset paths during tidy")
    void shouldTranslateCacheBustedLocalAssetPaths_whenTidyingDocument() throws Exception {
        Path tempDir = Files.createTempDirectory("convert-edoc-cache");
        Path image = Files.createFile(tempDir.resolve("stamp.png"));

        String html = "<html><body style=\"background-image:url('stamp.png?v=1')\"><div>x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied).contains(image.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("should strip unresolved traversal shaped background asset paths during tidy")
    void shouldStripUnresolvedTraversalShapedBackgroundAssetPaths_whenTidyingDocument() throws Exception {
        Path tempDir = Files.createTempDirectory("convert-edoc-traversal");
        String traversalPath = "../../../../etc/passwd.png";

        String html = "<html><body style=\"background-image:url('" + traversalPath + "')\"><div background=\"" + traversalPath + "\">x</div></body></html>";

        String tidied = ConvertToEdoc.tidyDocument(html, tempDir.toString());

        assertThat(tidied).doesNotContain(traversalPath);
        assertThat(tidied).contains("background-image:url('')");
        assertThat(tidied).doesNotContain("background=\"");
    }

    @Test
    @DisplayName("should remove unresolved traversal shaped resource elements when parsing document")
    void shouldRemoveUnresolvedTraversalShapedResourceElements_whenParsingDocument() throws Exception {
        String html = "<html><head><link rel=\"stylesheet\" href=\"../../../../etc/passwd.css\"></head>"
                + "<body><img src=\"../../../../etc/passwd.png\"><script src=\"../../../../etc/passwd.js\"></script></body></html>";

        Document document = ConvertToEdoc.getDocument(html, Files.createTempDirectory("convert-edoc-validate").toString());

        assertThat(document.select("link[href], img[src], script[src]")).isEmpty();
    }
}
