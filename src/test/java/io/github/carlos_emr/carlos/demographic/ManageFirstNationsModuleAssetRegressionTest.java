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

@DisplayName("First Nations demographic JSP regression tests")
@Tag("unit")
@Tag("demographic")
class ManageFirstNationsModuleAssetRegressionTest {

    private static final Path MANAGE_FIRST_NATIONS_MODULE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/demographic/manageFirstNationsModule.jsp");

    @Test
    @DisplayName("should HTML-attribute encode stored demographic extension values")
    void shouldEncodeStoredValuesForHtmlAttributes_whenRenderingManageFirstNationsModule() throws IOException {
        String jsp = Files.readString(MANAGE_FIRST_NATIONS_MODULE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("value=\"${carlos:forHtmlAttribute(demoExt['statusNum'])}\"")
                .contains("value=\"${carlos:forHtmlAttribute(firstNationCommunity.value)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(demoExt['fNationFamilyNumber'])}\"")
                .contains("value=\"${carlos:forHtmlAttribute(demoExt['fNationFamilyPosition'])}\"")
                .contains("value=\"${carlos:forHtmlAttribute(demoExt['ethnicity'])}\"")
                .doesNotContain("value=\"${ demoExt[\"statusNum\"] }\"")
                .doesNotContain("value=\"${firstNationCommunity.value}\"")
                .doesNotContain("value=\"${ demoExt[\"fNationFamilyNumber\"] }\"")
                .doesNotContain("value=\"${ demoExt[\"fNationFamilyPosition\"] }\"")
                .doesNotContain("value=\"${ demoExt[\"ethnicity\"] }\"");
    }
}
