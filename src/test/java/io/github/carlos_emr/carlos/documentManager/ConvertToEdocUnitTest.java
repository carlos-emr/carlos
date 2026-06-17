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

}
