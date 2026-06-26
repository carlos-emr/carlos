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

        assertThat(tidied).contains("data:image/png;base64,abc");
    }

    @Test
    @DisplayName("should strip unresolved absolute file paths during tidy")
    void shouldStripUnresolvedAbsoluteFilePaths_whenTidyingDocument(@TempDir Path tempDir) throws Exception {
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
                .contains("background-image:url('" + image.toAbsolutePath() + "')")
                .doesNotContain("background-image:url( 'stamp(1).png' )");
    }
}
