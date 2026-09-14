/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.eform.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit tests for {@link EFormAssetContentType#forFilename(String)}: the allowlisted
 * extension resolution the two eForm asset-streaming routes rely on, and the dotless-filename
 * guard that stops a bare extension keyword (e.g. {@code "png"}) from resolving as if it were a
 * matching extension.
 */
@DisplayName("EFormAssetContentType unit tests")
@Tag("unit") @Tag("fast") @Tag("eform")
class EFormAssetContentTypeUnitTest {

    @ParameterizedTest
    @CsvSource({"bg.png,image/png", "bg.PNG,image/png", "photo.JPeG,image/jpeg", "scan.jfif,image/jpeg", "widget.js,text/javascript", "form.rtl,text/html"})
    @DisplayName("should resolve allowlisted extensions case-insensitively")
    void shouldResolveAllowlistedType_forKnownExtensions(String fileName, String expected) {
        assertThat(EFormAssetContentType.forFilename(fileName)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "html", "js", "noextension", "archive.zip", "double..", "."})
    @DisplayName("should return empty for dotless or unknown filenames")
    void shouldReturnEmpty_forDotlessOrUnknownFilenames(String fileName) {
        assertThat(EFormAssetContentType.forFilename(fileName)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {".png", ".PNG", ".js", ".jfif", "bg.png.", "widget.js.", "name."})
    @DisplayName("should return empty for leading-dot dotfiles and trailing-dot names")
    void shouldReturnEmpty_forLeadingOrTrailingDotFilenames(String fileName) {
        // A leading-dot dotfile (".png") has no name part and must not be typed off its
        // "extension"; a trailing dot ("name.", "bg.png.") yields an empty extension. Both are
        // treated as "no extension" so a dotfile cannot be served with an inferred content type.
        assertThat(EFormAssetContentType.forFilename(fileName)).isEmpty();
    }

    @Test
    @DisplayName("should return empty for null filename")
    void shouldReturnEmpty_forNullFilename() {
        assertThat(EFormAssetContentType.forFilename(null)).isEmpty();
    }
}
