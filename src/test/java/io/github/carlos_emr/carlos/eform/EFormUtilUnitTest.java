/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@DisplayName("eForm utilities")
class EFormUtilUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldPreserveTheExistingFileList_whenDirectoryListingFails() {
        File directory = mock(File.class);
        when(directory.isDirectory()).thenReturn(true);
        when(directory.list()).thenReturn(null);
        var existing = new ArrayList<>(List.of("previous.rtl"));

        assertThat(EFormUtil.getFiles(directory, ".*rtl$", existing)).isSameAs(existing)
                .containsExactly("previous.rtl");
        assertThat(EFormUtil.getRichTextLetterTemplates(directory)).isEmpty();
    }

    @Test
    void shouldListMatchingTemplates_whenTheDirectoryCanBeRead(@TempDir Path directory) throws IOException {
        Files.createFile(directory.resolve("Letter.RTL"));
        Path otherFile = Files.createFile(directory.resolve("other.pdf"));

        assertThat(EFormUtil.getRichTextLetterTemplates(directory.toFile())).containsExactly("Letter.RTL");
        assertThat(EFormUtil.getRichTextLetterTemplates(otherFile.toFile())).isEmpty();
    }

    @Test
    @DisplayName("should allow closing braces outside unresolved template markers")
    void shouldAllow_closingBracesOutsideUnresolvedTemplateMarkers() {
        assertThatCode(() -> EFormSqlSafety.validateLegacySqlSafety(
                "select JSON_OBJECT('status', '}') as payload from demographic"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject unresolved template markers")
    void shouldReject_unresolvedTemplateMarkers() {
        assertThatThrownBy(() -> EFormSqlSafety.validateLegacySqlSafety(
                "select * from demographic where provider_no = ${providerNo}"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsafe dynamic SQL template");
    }

    @Test
    @DisplayName("should reject stacked statements")
    void shouldReject_stackedStatements() {
        assertThatThrownBy(() -> EFormSqlSafety.validateLegacySqlSafety(
                "select * from demographic; drop table provider"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsafe SQL detected");
    }

    @Test
    @DisplayName("should reject SQL comments outside literals")
    void shouldReject_sqlCommentsOutsideLiterals() {
        assertThatThrownBy(() -> EFormSqlSafety.validateLegacySqlSafety(
                "select * from demographic -- comment"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsafe SQL detected");
    }
}
