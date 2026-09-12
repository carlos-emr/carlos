/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the spoken-languages bundle's encoding.
 *
 * @since 2026-08-20
 */
@DisplayName("SpokenLangProperties")
@Tag("unit")
@Tag("i18n")
class SpokenLangPropertiesUnitTest {

    private static final Path BUNDLE = Path.of("src", "main", "resources", "spoken_languages_codes.properties");

    @Test
    @DisplayName("should decode an accented language name rather than mojibake")
    void shouldDecodeAccentedLanguageName_ratherThanMojibake() {
        // The bug this pins: the file held a raw UTF-8 'a-ring' while SpokenLangProperties loads
        // it through Properties.load(InputStream), which decodes ISO-8859-1. The language picker
        // rendered "BokmÃ¥l". Asserting the decoded value rather than the file bytes, because it
        // is the decode that was wrong, not the character.
        assertThat(SpokenLangProperties.getInstance().getLangByCode("NOB"))
                .isEqualTo("Bokmål, Norwegian");
    }

    @Test
    @DisplayName("should keep the bundle source ASCII-only so Properties.load decodes consistently")
    void shouldKeepBundleSourceAsciiOnly_soPropertiesLoadDecodesConsistently() throws IOException {
        // Guards the whole file, not just the one entry above: any future accented language name
        // added as raw UTF-8 would mojibake the same way, and would otherwise only be noticed by
        // a user reading the dropdown.
        byte[] raw = Files.readAllBytes(BUNDLE);
        List<String> nonAscii = new ArrayList<>();
        int line = 1;
        for (byte b : raw) {
            if (b == '\n') {
                line++;
            } else if ((b & 0xFF) > 127) {
                nonAscii.add("line " + line + " byte 0x" + Integer.toHexString(b & 0xFF).toUpperCase());
            }
        }

        assertThat(nonAscii)
                .as("%s must use \\uXXXX escapes so Properties.load(InputStream) decodes consistently", BUNDLE)
                .isEmpty();
    }
}
