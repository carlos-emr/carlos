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
package io.github.carlos_emr.carlos.casemgmt.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The chart's CDS source links line ("CDS source fields — Additional clinical categories").
 *
 * <p>Neither chart page declares a page encoding, so the JSP compiler reads each file as
 * ISO-8859-1. A literal UTF-8 dash in the file was then shown as "â" on every chart, in every
 * language. The pages keep to ASCII and write the dash as {@code &#8212;}, which shows the same
 * whatever the page's encoding.</p>
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("eChart CDS source links: encoding-safe text")
class CdsSourceLinksJspEncodingRegressionTest {

    private static final Path CASEMGMT_JSPS = Path.of("src/main/webapp/WEB-INF/jsp/casemgmt");

    @ParameterizedTest
    @ValueSource(strings = {"newCaseManagementView.jsp", "CaseManagementView.jsp"})
    @DisplayName("should keep a chart page without a page encoding to ASCII text")
    void shouldKeepChartPageAscii_withoutPageEncoding(String page) throws IOException {
        byte[] bytes = Files.readAllBytes(CASEMGMT_JSPS.resolve(page));
        String head = new String(bytes, 0, Math.min(bytes.length, 4_000), StandardCharsets.ISO_8859_1);
        assertThat(head).as("%s declares no page encoding (else this guard is not needed)", page)
                .doesNotContainPattern("pageEncoding\\s*=")
                .doesNotContainPattern("charset\\s*=\\s*UTF-8");
        for (int i = 0; i < bytes.length; i++) {
            assertThat(bytes[i] & 0xFF).as("%s byte %d is not ASCII", page, i).isLessThan(0x80);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"newCaseManagementView.jsp", "CaseManagementView.jsp"})
    @DisplayName("should write the CDS source links dash as an entity")
    void shouldWriteCdsLinksDash_asEntity(String page) throws IOException {
        String jsp = Files.readString(CASEMGMT_JSPS.resolve(page), StandardCharsets.ISO_8859_1);
        assertThat(jsp).contains("&#8212; <fmt:message key=\"sourceAdditionalCategories\"");
    }
}
