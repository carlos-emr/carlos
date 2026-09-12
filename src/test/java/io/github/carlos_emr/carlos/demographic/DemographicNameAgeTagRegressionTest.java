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
package io.github.carlos_emr.carlos.demographic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for secure demographic name/age tag rendering.
 *
 * @since 2026-05-29
 */
@DisplayName("Demographic name age tag regression tests")
@Tag("unit")
@Tag("demographic")
@Tag("security")
class DemographicNameAgeTagRegressionTest {

    private static final Path DEMOGRAPHIC_NAME_AGE_TAG = Path.of(
            "src/main/java/io/github/carlos_emr/carlos/demographic/tld/DemographicNameAgeTag.java");

    @Test
    @DisplayName("should encode nameage output in HTML body context")
    void shouldEncodeNameAgeOutput_inHtmlBodyContext() throws IOException {
        String tagSource = Files.readString(DEMOGRAPHIC_NAME_AGE_TAG, StandardCharsets.UTF_8);

        assertThat(tagSource)
                .contains("import io.github.carlos_emr.carlos.utility.SafeEncode;")
                .contains("SafeEncode.forHtmlContent(out, nameage);")
                .doesNotContain("out.print(nameage);");
    }
}
