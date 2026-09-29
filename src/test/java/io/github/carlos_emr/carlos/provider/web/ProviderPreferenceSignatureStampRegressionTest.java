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
package io.github.carlos_emr.carlos.provider.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the provider-preferences signature-stamp preview against rendering a
 * broken image when the backing consult signature file is missing.
 */
@DisplayName("Provider preference signature stamp regressions")
@Tag("unit")
@Tag("provider")
class ProviderPreferenceSignatureStampRegressionTest {

    private static final Path PROVIDER_PREFERENCE_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "providerpreference.jsp");

    @Test
    @DisplayName("should base signature preview visibility on backing file existence")
    void shouldBaseSignaturePreviewVisibility_onBackingFileExistence() throws IOException {
        String jsp = Files.readString(PROVIDER_PREFERENCE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("boolean hasConsultSignature = false;")
                .contains("consultSigFile.isFile()")
                .contains("if (providerNo != null && !providerNo.trim().isEmpty())")
                .contains("id=\"sigPlaceholder\"")
                .doesNotContain("consultSigValue");
    }
}
