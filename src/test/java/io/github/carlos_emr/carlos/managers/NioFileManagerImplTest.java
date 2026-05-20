package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NioFileManagerImpl")
@Tag("unit")
@Tag("security")
@Isolated("Mutates catalina.base and PathValidationUtils temp-directory cache")
class NioFileManagerImplTest {
    private String originalCatalinaBase;

    @BeforeEach
    void setUp() throws Exception {
        originalCatalinaBase = System.getProperty("catalina.base");
        resetAllowedTempDirectories();
    }

    @AfterEach
    void tearDown() throws Exception {
        restoreProperty("catalina.base", originalCatalinaBase);
        resetAllowedTempDirectories();
    }

    @Test
    @DisplayName("saveTempFile rejects names that sanitize to empty")
    void saveTempFileRejectsNamesThatSanitizeToEmpty() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(1);

        assertThatThrownBy(() -> new NioFileManagerImpl().saveTempFile("$~..", bytes))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid filename");
    }

    @Test
    @DisplayName("deleteTempFile accepts files in Tomcat work temp directory")
    void deleteTempFileAcceptsFilesInTomcatWorkDirectory() throws Exception {
        Path catalinaBase = Paths.get("target", "nio-catalina-base-" + UUID.randomUUID()).toAbsolutePath();
        Path workDir = catalinaBase.resolve("work");
        Path tempFile = workDir.resolve("upload.tmp");

        try {
            Files.createDirectories(workDir);
            Files.write(tempFile, new byte[]{1});
            System.setProperty("catalina.base", catalinaBase.toString());
            resetAllowedTempDirectories();

            boolean deleted = new NioFileManagerImpl().deleteTempFile(tempFile.toString());

            assertThat(deleted).isTrue();
            assertThat(tempFile).doesNotExist();
        } finally {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(workDir);
            Files.deleteIfExists(catalinaBase);
        }
    }

    @Test
    @DisplayName("deleteTempFile returns false for missing allowed temp files")
    void deleteTempFileReturnsFalseWhenTempFileIsMissing() throws Exception {
        Path catalinaBase = Paths.get("target", "nio-catalina-base-" + UUID.randomUUID()).toAbsolutePath();
        Path workDir = catalinaBase.resolve("work");
        Path missingFile = workDir.resolve("missing.tmp");

        try {
            Files.createDirectories(workDir);
            System.setProperty("catalina.base", catalinaBase.toString());
            resetAllowedTempDirectories();

            boolean deleted = new NioFileManagerImpl().deleteTempFile(missingFile.toString());

            assertThat(deleted).isFalse();
        } finally {
            Files.deleteIfExists(missingFile);
            Files.deleteIfExists(workDir);
            Files.deleteIfExists(catalinaBase);
        }
    }

    @Test
    @DisplayName("deleteTempFile rejects paths that are outside allowed temp directories")
    void deleteTempFileRejectsPathsOutsideAllowedTempDirectory() throws Exception {
        Path outsideFile = Paths.get("target", "nio-temp-outside-delete-" + UUID.randomUUID() + ".tmp").toAbsolutePath();
        Files.writeString(outsideFile, "outside");

        try {
            assertThatThrownBy(() -> new NioFileManagerImpl().deleteTempFile(outsideFile.toString()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Path traversal attempt detected");
        } finally {
            Files.deleteIfExists(outsideFile);
        }
    }

    @Test
    @DisplayName("deleteTempFile ignores blank inputs")
    void deleteTempFileReturnsFalseForBlankInput() {
        assertThat(new NioFileManagerImpl().deleteTempFile(null)).isFalse();
        assertThat(new NioFileManagerImpl().deleteTempFile("")).isFalse();
        assertThat(new NioFileManagerImpl().deleteTempFile("   ")).isFalse();
    }

    @Test
    @DisplayName("deleteTempFile rejects directories in allowed temp directories")
    void deleteTempFileRejectsDirectoriesInAllowedTempDirectories() throws Exception {
        Path catalinaBase = Paths.get("target", "nio-catalina-base-" + UUID.randomUUID()).toAbsolutePath();
        Path workDir = catalinaBase.resolve("work");

        try {
            Files.createDirectories(workDir);
            System.setProperty("catalina.base", catalinaBase.toString());
            resetAllowedTempDirectories();
            NioFileManagerImpl manager = new NioFileManagerImpl();
            String workDirName = workDir.toString();

            assertThatThrownBy(() -> manager.deleteTempFile(workDirName))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("regular file");
        } finally {
            Files.deleteIfExists(workDir);
            Files.deleteIfExists(catalinaBase);
        }
    }

    private static void resetAllowedTempDirectories() throws Exception {
        Field field = PathValidationUtils.class.getDeclaredField("allowedTempDirectories");
        field.setAccessible(true);
        field.set(null, null);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
