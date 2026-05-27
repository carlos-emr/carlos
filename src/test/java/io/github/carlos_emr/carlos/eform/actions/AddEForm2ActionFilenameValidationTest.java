package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.utility.FileValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AddEForm2Action filename validation")
@Tag("unit")
@Tag("security")
class AddEForm2ActionFilenameValidationTest {

    @Test
    @DisplayName("should normalize template filename")
    void shouldNormalizeTemplateFilename_whenFilenameIsValid() {
        String result = AddEForm2Action.validateTemplateFileName("my form.html");

        assertThat(result).isEqualTo("my_form.html");
    }

    @Test
    @DisplayName("should reject hidden template filename")
    void shouldRejectHiddenTemplateFilename_whenFilenameStartsWithDot() {
        assertThatThrownBy(() -> AddEForm2Action.validateTemplateFileName(".hidden.html"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("hidden files not allowed");
    }

    @Test
    @DisplayName("should leave missing template filename unchanged")
    void shouldLeaveMissingTemplateFilename_whenFilenameIsMissing() {
        assertThat(AddEForm2Action.validateTemplateFileName(null)).isNull();
        assertThat(AddEForm2Action.validateTemplateFileName("")).isEmpty();
    }
}
