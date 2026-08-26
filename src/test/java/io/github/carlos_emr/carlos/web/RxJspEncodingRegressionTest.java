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
package io.github.carlos_emr.carlos.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("regression")
@DisplayName("Rx JSP encoding regression tests")
class RxJspEncodingRegressionTest {

    @Test
    @DisplayName("Reported Rx JSP sinks should use CARLOS encoding helpers")
    void shouldUseContextAppropriateEncoding_inReportedRxJsps() throws IOException {
        String listDrugs = read("src/main/webapp/WEB-INF/jsp/rx/ListDrugs.jsp");
        String chooseDrug = read("src/main/webapp/WEB-INF/jsp/rx/ChooseDrug.jsp");
        String selectPharmacy = read("src/main/webapp/WEB-INF/jsp/rx/SelectPharmacy2.jsp");
        String sideLinks = read("src/main/webapp/WEB-INF/jsp/rx/SideLinksEditFavorites2.jsp");

        assertThat(listDrugs)
                .contains("<carlos:encode value='<%= RxPrescriptionData.getFullOutLine(prescriptDrug.getSpecial()).replaceAll(\";\", \" \") %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= prescriptDrug.getArchivedReason() %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= prescriptDrug.getOutsideProviderName() %>' context=\"html\"/>")
                .contains("title ? SafeEncode.forHtmlAttribute(codeDescr) : SafeEncode.forHtml(codeDescr)")
                .contains("title ? SafeEncode.forHtmlAttribute(drugReason.getCode()) : SafeEncode.forHtml(drugReason.getCode())");

        assertThat(chooseDrug)
                .contains("out.write(SafeEncode.forHtmlContent(drugSearch.errorMessage));")
                .contains("title=\"<carlos:encode value='<%= t.name %>' context=\"htmlAttribute\"/>\"")
                .contains("<carlos:encode value='<%= getMaxVal(t.name) %>' context=\"html\"/>")
                .contains("title=\"<carlos:encode value='<%= brandName %>' context=\"htmlAttribute\"/>\"")
                .contains("<carlos:encode value='<%= brandName %>' context=\"html\"/>");

        assertThat(selectPharmacy)
                .contains("var rx_enhance = <%=Boolean.parseBoolean(CarlosProperties.getInstance().getProperty(\"rx_enhance\"))%>;")
                .contains("<carlos:encode value='<%= surname %>' context=\"html\"/>, <carlos:encode value='<%= firstName %>' context=\"html\"/>");

        assertThat(sideLinks)
                .contains("title=\"<carlos:encode value='<%= allergies[j].getDescription() %>' context=\"htmlAttribute\"/> - <carlos:encode value='<%= allergies[j].getReaction() %>' context=\"htmlAttribute\"/>\"")
                .contains("<carlos:encode value='<%= allergies[j].getShortDesc(13, 8, \"...\") %>' context=\"html\"/>");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
