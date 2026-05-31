package io.github.carlos_emr.carlos.eform.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test pinning that GraphicalCanvasToImage.convertToImage(String, ...) throws an
 * unchecked SecurityException when the backing image file is missing (via
 * PathValidationUtils.validateConfiguredFile). PdfRecordPrinter relies on catching this
 * (alongside IOException) so a missing eForm diagram skips a single image rather than
 * aborting the whole record PDF.
 */
@DisplayName("GraphicalCanvasToImage missing-file handling")
@Tag("unit")
@Tag("fast")
class GraphicalCanvasToImageUnitTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should throw SecurityException when the image file is missing")
    void shouldThrowSecurityException_whenImageFileMissing() {
        Path missing = tempDir.resolve("missing-diagram.png");
        GraphicalCanvasToImage converter = new GraphicalCanvasToImage();

        assertThatThrownBy(() ->
                converter.convertToImage(missing.toString(), "", "PNG", new ByteArrayOutputStream()))
                .isInstanceOf(SecurityException.class);
    }
}
