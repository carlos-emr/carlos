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
package io.github.carlos_emr.carlos.commn.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Site;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Satellite-sites Admin list writes each site's stored values as text, and gives the colour column's
 * {@code style} attribute only a plain colour.
 */
@Tag("unit")
@Tag("web")
@DisplayName("Satellite-sites Admin list values")
class SitesAdminListRegressionTest {

    private static final Path LIST_JSP = Path.of("src/main/webapp/WEB-INF/jsp/admin/sitesAdmin.jsp");

    private static String read() throws IOException {
        return Files.readString(LIST_JSP, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should write every site value through the HTML encoder, with no property= columns")
    void shouldEncodeEveryColumn_inSiteList() throws IOException {
        String page = read();

        assertThat(page).doesNotContain("property=\"");
        for (String field : List.of("name", "shortName", "bgColor", "phone", "fax", "address", "city", "province",
                "postal", "providerIdFrom", "providerIdTo")) {
            assertThat(page).contains("${carlos:forHtml(site." + field + ")}");
        }
    }

    @Test
    @DisplayName("should give the colour column's style only the checked colour")
    void shouldStyleColourColumn_withCheckedColour() throws IOException {
        String page = read();

        assertThat(page).contains("style=\"background-color:${site.cssBgColor}\"");
        assertThat(page).doesNotContain("background-color:${site.bgColor}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"#C0FFEE", "#abc", "#11223344", "red", "LightSteelBlue", " #C0FFEE "})
    @DisplayName("should keep a #hex colour or a colour name")
    void shouldKeepColour_whenPlainCssColour(String colour) {
        Site site = new Site();
        site.setBgColor(colour);

        assertThat(site.getCssBgColor()).isEqualTo(colour.trim());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "red;position:fixed", "#C0FFEE\" onmouseover=\"x", "url(x)", "#12", "#C0FFEEZ",
            "expression(1)", "rgb(1,2,3)"})
    @DisplayName("should give an empty colour for anything that is not a plain colour")
    void shouldGiveEmptyColour_whenNotPlainCssColour(String colour) {
        Site site = new Site();
        site.setBgColor(colour);

        assertThat(site.getCssBgColor()).isEmpty();
    }
}
