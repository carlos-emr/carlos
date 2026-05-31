package io.github.carlos_emr.carlos.integration.mcedt.mailbox;

import io.github.carlos_emr.CarlosProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Regression tests for {@link Action2Utils} ONEDT_OUTBOX handling after blank-config guards
 * were added around PathValidationUtils.resolveConfiguredDirectory (which throws on a blank
 * path). A blank ONEDT_OUTBOX must degrade gracefully (empty list / no-op) instead of
 * throwing, while a configured outbox still lists its files (also covering the prior
 * listFiles()==null NPE path).
 */
@DisplayName("Action2Utils ONEDT_OUTBOX handling")
@Tag("unit")
@Tag("fast")
class Action2UtilsOutboxUnitTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should return an empty upload list when ONEDT_OUTBOX is blank")
    void shouldReturnEmptyUploadList_whenOnedtOutboxIsBlank() {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("ONEDT_OUTBOX", "")).thenReturn("");

            List<File> result = Action2Utils.getUploadList();

            assertThat(result).isEmpty();
        }
    }

    @Test
    @DisplayName("should not throw when creating the outbox directory and ONEDT_OUTBOX is blank")
    void shouldSkipOutboxDirectoryCreation_whenOnedtOutboxIsBlank() {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("ONEDT_OUTBOX", "")).thenReturn("");

            assertThatCode(Action2Utils::createOnEDTOutboxDir).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("should list outbox files when ONEDT_OUTBOX is configured")
    void shouldReturnUploadList_whenOnedtOutboxConfigured() throws Exception {
        Files.createFile(tempDir.resolve("claim.txt"));
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("ONEDT_OUTBOX", "")).thenReturn(tempDir.toString());

            List<File> result = Action2Utils.getUploadList();

            assertThat(result).extracting(File::getName).contains("claim.txt");
        }
    }
}
