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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The service/diagnostic association codes come from tables an administrator edits; the association list and the BC
 * bill form's association script write them out in the context each place needs.
 *
 * @since 2026-10-18
 */
@DisplayName("BC service/diagnostic association codes on the list and the bill form")
@Tag("unit")
@Tag("billing")
class ServiceCodeAssocOutputPageUnitTest {

    private static final Path LIST = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/manageSVCDXAssoc.jsp");
    private static final Path BILL_FORM = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingBC.jsp");

    private static String read(Path page) throws IOException {
        return Files.readString(page, StandardCharsets.UTF_8);
    }

    @Test
    void shouldWriteTheServiceCode_inEachContextOfTheList() throws IOException {
        assertThat(read(LIST))
                .contains("<td><carlos:encode value='<%= assoc.getServiceCode() %>'/>")
                .contains("svcCode=<carlos:encode value='<%= assoc.getServiceCode() %>' context=\"uriComponent\"/>")
                .contains("name=\"svcCode\" value=\"<carlos:encode value='<%= assoc.getServiceCode() %>' context=\"htmlAttribute\"/>\"")
                .doesNotContain("<%=assoc.getServiceCode()%>");
    }

    @Test
    void shouldWriteEachDiagnosisAsText_onTheList() throws IOException {
        assertThat(read(LIST)).contains("out.print(SafeEncode.forHtmlContent(s) + \"<br>\");").doesNotContain("out.print(s + \"<br>\");");
    }

    @Test
    void shouldWriteTheCodesAsScriptStrings_inTheBillFormsAssociationArrays() throws IOException {
        assertThat(read(BILL_FORM))
                .contains("[0]='\" + SafeEncode.forJavaScript(key) + \"'; \"")
                .contains("[1]='\" + SafeEncode.forJavaScript(value) + \"';\\n\"")
                .doesNotContain("[0]='\" + key + \"'; \"");
    }
}
