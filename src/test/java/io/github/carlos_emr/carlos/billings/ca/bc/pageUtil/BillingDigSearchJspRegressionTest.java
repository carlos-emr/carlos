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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Source checks for the BC diagnostic code search picker ({@code billingDigSearch.jsp}).
 *
 * <p>Each result row hands its code and description to {@code CodeAttach}, which fills the
 * billing form in the opener. 84 of the BC seed diagnostic codes have an apostrophe in their
 * description (for example {@code PARATYPHOID FEVER 'A'}); written raw into a
 * {@code javascript:} link, the apostrophe ended the string and the click did nothing.
 *
 * @since 2026-10-14
 */
@DisplayName("BC diagnostic code search JSP")
@Tag("unit")
@Tag("billing")
class BillingDigSearchJspRegressionTest {

    private static final Path JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "billing", "CA", "BC", "billingDigSearch.jsp");
    private static final String ENCODE = "io.github.carlos_emr.carlos.utility.SafeEncode.";

    @Test
    @DisplayName("should pass every picked code to CodeAttach from an onclick handler with attribute encoding")
    void shouldPassPickedCode_fromEncodedOnclickHandler() throws IOException {
        String jsp = Files.readString(JSP, StandardCharsets.UTF_8);

        assertThat(jsp).doesNotContain("javascript:CodeAttach(");
        assertThat(jsp).contains(
                "type=\"button\" class=\"link-button\" onclick=\"CodeAttach('<%=" + ENCODE + "forJavaScriptAttribute(Dcode)%>|<%="
                        + ENCODE + "forJavaScriptAttribute(DcodeDesc)%>');\">",
                "type=\"button\" class=\"link-button\" onclick=\"CodeAttach('<%=" + ENCODE + "forJavaScriptAttribute(Dcode2)%>|<%="
                        + ENCODE + "forJavaScriptAttribute(DcodeDesc2)%>');\">");
    }

    @Test
    @DisplayName("should encode the single-result auto-pick inside its script block")
    void shouldEncodeSingleResultAutoPick_forScriptBlock() throws IOException {
        String jsp = Files.readString(JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains(
                "CodeAttach('<%=" + ENCODE + "forJavaScript(Dcode)%>|<%=" + ENCODE + "forJavaScript(DcodeDesc)%>');");
    }

    @Test
    @DisplayName("should write no code or description into the page without encoding")
    void shouldWriteNoRawCodeOrDescription_inResultRows() throws IOException {
        String jsp = Files.readString(JSP, StandardCharsets.UTF_8);

        for (String name : new String[]{"Dcode", "DcodeDesc", "Dcode2", "DcodeDesc2"}) {
            assertThat(jsp).as(name).doesNotContain("<%=" + name + "%>");
        }
    }

    @Test
    @DisplayName("should close each code link with an end tag")
    void shouldCloseEachCodeLink_withEndTag() throws IOException {
        String jsp = Files.readString(JSP, StandardCharsets.UTF_8);

        // The rows used "<a>" where "</a>" was meant, opening a stray empty link after every code.
        assertThat(jsp).doesNotContain("<a></font>");
    }

    @Test
    @DisplayName("should label each row's description field without showing the label")
    void shouldLabelEachDescriptionField_withHiddenLabel() throws IOException {
        String jsp = Files.readString(JSP, StandardCharsets.UTF_8);

        // One id prefix per row block (a search by code or text; the text and code rows of a search by both),
        // since Sonar reads the source, where one shared id would appear three times. intCount goes up once
        // per row before the row is written, so each id is also unique on the rendered page.
        String[][] blocks = {{"dxDescResult", "Dcode"}, {"dxDescText", "Dcode"}, {"dxDescCode", "Dcode2"}};
        for (String[] block : blocks) {
            String id = block[0] + "<%=intCount%>";
            assertThat(jsp).contains("<label for=\"" + id + "\" class=\"visually-hidden\">Description for <%="
                    + ENCODE + "forHtml(" + block[1] + ")%></label><input type=\"text\" id=\"" + id + "\"");
            assertThat(jsp.split("id=\"" + id + "\"", -1)).as(id).hasSize(2);
        }
        assertThat(jsp).contains(".visually-hidden { position: absolute;");
    }
}
