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
                .doesNotContain("value=\"${demoExt[\"statusNum\"]}\"")
                .doesNotContain("value=\"${firstNationCommunity.value}\"")
                .doesNotContain("value=\"${ demoExt[\"fNationFamilyNumber\"] }\"")
                .doesNotContain("value=\"${demoExt[\"fNationFamilyNumber\"]}\"")
                .doesNotContain("value=\"${ demoExt[\"fNationFamilyPosition\"] }\"")
                .doesNotContain("value=\"${demoExt[\"fNationFamilyPosition\"]}\"")
                .doesNotContain("value=\"${ demoExt[\"ethnicity\"] }\"")
                .doesNotContain("value=\"${demoExt[\"ethnicity\"]}\"");
    }

    /**
     * The selected-option comparisons must never read the raw stored value.
     *
     * <p>EL coerces both sides of {@code eq 12} to {@code Long}, so a
     * demographicExt ethnicity that is not a number threw {@code ELException}
     * and returned 500 for the whole master record — reachable through the same
     * untrusted field the encoding above protects. The page normalizes the value
     * once into {@code ethnicityCode} instead.
     */
    @Test
    @DisplayName("should compare the normalized ethnicity code rather than the raw stored value")
    void shouldCompareNormalizedEthnicityCode_whenSelectingFirstNationStatus() throws IOException {
        String jsp = Files.readString(MANAGE_FIRST_NATIONS_MODULE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("pageContext.setAttribute(\"ethnicityCode\", ethnicityCode);")
                .contains("${ ethnicityCode eq 12 ? 'selected' : '' }")
                .doesNotContain("${ demoExt['ethnicity'] eq")
                .doesNotContain("${demoExt['ethnicity'] eq");
    }

    /**
     * The community control is optional markup, so its script must treat it as such.
     *
     * <p>{@code #fNationCom} is rendered only when {@code showBandNumberOnly} is
     * off. An unguarded {@code addEventListener} on the resulting null threw a
     * TypeError out of the page's DOMContentLoaded handler in exactly the
     * configuration the element is absent in, taking every listener registered
     * after it down with it.
     */
    @Test
    @DisplayName("should guard the optional community control before using it")
    void shouldGuardOptionalCommunityControl_whenShowBandNumberOnlyHidesIt() throws IOException {
        String jsp = Files.readString(MANAGE_FIRST_NATIONS_MODULE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("if (communitySelect) {")
                .contains("if (communityField && !communityField.value) {")
                .doesNotContain("document.getElementById('fNationCom').addEventListener(")
                .doesNotContain("if (!document.getElementById('fNationCom').value) {");
    }
}
