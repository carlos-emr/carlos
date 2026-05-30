package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("zip utility tests")
@Tag("unit")
@Tag("fast")
class ZipUtilTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("unzipXML should handle short entry names")
    void unzipXMLShouldHandleShortEntryNames() throws Exception {
        Path zipPath = tempDir.resolve("short.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zipOutputStream.putNextEntry(new ZipEntry("a"));
            zipOutputStream.write("data".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }

        boolean result = zip.unzipXML(tempDir.toString(), "short.zip");

        assertThat(result).isTrue();
        assertThat(Files.readString(tempDir.resolve("a.xml"))).isEqualTo("data");
    }
}
