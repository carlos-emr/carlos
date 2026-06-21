/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UploadedFileUtils}.
 *
 * <p>Verifies backing-file extraction from Struts {@link UploadedFile} objects,
 * including null safety and the throwing vs. null-returning variants.</p>
 *
 * @see UploadedFileUtils
 * @since 2026
 */
@Tag("unit")
@Tag("fast")
@DisplayName("UploadedFileUtils")
class UploadedFileUtilsUnitTest {

    @Nested
    @DisplayName("getUploadedFile(UploadedFile)")
    class GetUploadedFile {

        @Test
        @DisplayName("should return backing file when upload has file-backed content")
        void shouldReturnBackingFile_whenUploadHasFileBacking() {
            File expected = new File("/tmp/test.pdf");
            UploadedFile upload = mock(UploadedFile.class);
            when(upload.getContent()).thenReturn(expected);

            assertThat(UploadedFileUtils.getUploadedFile(upload)).isSameAs(expected);
        }

        @Test
        @DisplayName("should throw IllegalStateException when upload is null")
        void shouldThrowIllegalStateException_whenUploadIsNull() {
            assertThatThrownBy(() -> UploadedFileUtils.getUploadedFile(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null");
        }

        @Test
        @DisplayName("should throw IllegalStateException when content is not file-backed")
        void shouldThrowIllegalStateException_whenContentIsNotFileBacked() {
            UploadedFile upload = mock(UploadedFile.class);
            when(upload.getContent()).thenReturn("not-a-file");

            assertThatThrownBy(() -> UploadedFileUtils.getUploadedFile(upload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no backing file");
        }

        @Test
        @DisplayName("should throw IllegalStateException when content is null")
        void shouldThrowIllegalStateException_whenContentIsNull() {
            UploadedFile upload = mock(UploadedFile.class);
            when(upload.getContent()).thenReturn(null);

            assertThatThrownBy(() -> UploadedFileUtils.getUploadedFile(upload))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("getUploadedFileOrNull(UploadedFile)")
    class GetUploadedFileOrNull {

        @Test
        @DisplayName("should return backing file when upload has file-backed content")
        void shouldReturnBackingFile_whenUploadHasFileBacking() {
            File expected = new File("/tmp/test.pdf");
            UploadedFile upload = mock(UploadedFile.class);
            when(upload.getContent()).thenReturn(expected);

            assertThat(UploadedFileUtils.getUploadedFileOrNull(upload)).isSameAs(expected);
        }

        @Test
        @DisplayName("should return null when upload is null")
        void shouldReturnNull_whenUploadIsNull() {
            assertThat(UploadedFileUtils.getUploadedFileOrNull(null)).isNull();
        }

        @Test
        @DisplayName("should return null when content is not file-backed")
        void shouldReturnNull_whenContentIsNotFileBacked() {
            UploadedFile upload = mock(UploadedFile.class);
            when(upload.getContent()).thenReturn("not-a-file");

            assertThat(UploadedFileUtils.getUploadedFileOrNull(upload)).isNull();
        }

        @Test
        @DisplayName("should return null when content is null")
        void shouldReturnNull_whenContentIsNull() {
            UploadedFile upload = mock(UploadedFile.class);
            when(upload.getContent()).thenReturn(null);

            assertThat(UploadedFileUtils.getUploadedFileOrNull(upload)).isNull();
        }
    }
}
