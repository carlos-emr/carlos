package io.github.carlos_emr.carlos.managers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NioFileManagerImpl")
@Tag("unit")
@Tag("security")
class NioFileManagerImplTest {

    @Test
    @DisplayName("saveTempFile rejects names that sanitize to empty")
    void saveTempFileRejectsNamesThatSanitizeToEmpty() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(1);

        assertThatThrownBy(() -> new NioFileManagerImpl().saveTempFile("$~..", bytes))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid filename");
    }
}
