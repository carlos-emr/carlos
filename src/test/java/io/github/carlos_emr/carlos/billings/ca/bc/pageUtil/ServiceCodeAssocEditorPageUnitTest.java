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
import java.text.MessageFormat;
import java.util.Properties;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BC service/diagnostic association editor lost its Struts form tags in a migration: Edit opened it with every
 * box empty and a service code box that could not be typed in, so its Save stored the diagnosis with no service code
 * (issue #4941).
 *
 * @since 2026-10-18
 */
@DisplayName("BC service/diagnostic association editor page")
@Tag("unit")
@Tag("billing")
class ServiceCodeAssocEditorPageUnitTest {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/dxcode_svccode_assoc.jsp");
    private static final Path STRUTS = Path.of("src/main/webapp/WEB-INF/classes/struts-billing.xml");

    private static String page() throws IOException {
        return Files.readString(PAGE, StandardCharsets.UTF_8);
    }

    /** Each box shows the association the action loaded (or the one a refused Save posted). */
    @ParameterizedTest
    @CsvSource({
            "mode, mode",
            "xml_other1, serviceCode",
            "xml_diagnostic_detail1, dx[0]",
            "xml_diagnostic_detail2, dx[1]",
            "xml_diagnostic_detail3, dx[2]",
    })
    void shouldShowTheAssociationsValue_inEachBox(String name, String value) throws IOException {
        assertThat(page()).containsPattern("name=\"" + Pattern.quote(name) + "\"[^>]*value=\"<carlos:encode value='<%= "
                + Pattern.quote(value) + " %>' context=\"htmlAttribute\"/>\"");
    }

    @Test
    void shouldLabelTheServiceCodeAndDiagnosisBoxes_byTheirIds() throws IOException {
        assertThat(page())
                .contains("<label for=\"xml_other1\">").contains("name=\"xml_other1\" id=\"xml_other1\"")
                .contains("<label for=\"xml_diagnostic_detail1\">").contains("name=\"xml_diagnostic_detail1\" id=\"xml_diagnostic_detail1\"");
    }

    @Test
    void shouldNameBothSearchIcons_withTheTranslatedSearchLabel() throws IOException {
        assertThat(page().split("alt=\"<fmt:message key='global.btnSearch'/>\"", -1)).hasSize(3);
    }

    @Test
    void shouldReadTheAssociation_fromTheRequest() throws IOException {
        assertThat(page()).contains("(ServiceCodeAssociation) request.getAttribute(\"serviceCodeAssociation\")");
    }

    @Test
    void shouldMakeTheServiceCodeReadOnly_onlyWhileEditing() throws IOException {
        // readonly="false" still makes a box read-only; the attribute must be left out instead.
        assertThat(page()).contains("<%= state ? \"readonly\" : \"\" %>").doesNotContain("readonly=\"<%=state%>\"");
    }

    /** The refused editor shows these messages; Struts formats them, so a lone apostrophe would be dropped. */
    @ParameterizedTest
    @CsvSource({
            "oscar.billing.CA.BC.billingBC.error.invaliddxcode, Diagnostic code: Z0000 doesn't exist in database<br/>",
            "oscar.billing.CA.BC.billingBC.error.invalidsvccode, Service code: Z0000 doesn't exist in database<br/>",
    })
    void shouldKeepTheApostrophe_inTheEnglishMessages(String key, String expected) throws IOException {
        Properties bundle = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/oscarResources_en.properties"), StandardCharsets.UTF_8)) {
            bundle.load(reader);
        }
        assertThat(MessageFormat.format(bundle.getProperty(key), "Z0000")).isEqualTo(expected);
    }

    /** In every locale, formatting one of the editor's messages changes nothing but the code it names. */
    @ParameterizedTest
    @CsvSource({
            "en, invaliddxcode", "en, invalidsvccode", "en, assocexists",
            "fr, invaliddxcode", "fr, invalidsvccode", "fr, assocexists",
            "es, invaliddxcode", "es, invalidsvccode", "es, assocexists",
            "pl, invaliddxcode", "pl, invalidsvccode", "pl, assocexists",
            "pt_BR, invaliddxcode", "pt_BR, invalidsvccode", "pt_BR, assocexists",
    })
    void shouldShowEachLocalesMessageAsWritten_whenFormatted(String locale, String key) throws IOException {
        Properties bundle = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/oscarResources_" + locale + ".properties"),
                StandardCharsets.UTF_8)) {
            bundle.load(reader);
        }
        String text = bundle.getProperty("oscar.billing.CA.BC.billingBC.error." + key);
        assertThat(text).as(locale + " " + key).isNotNull().contains("{0}");
        assertThat(MessageFormat.format(text, "Z0000")).as(locale + " " + key)
                .isEqualTo(text.replace("''", "'").replace("{0}", "Z0000"));
    }

    @Test
    void shouldSayTheSaveFailed_inThePolishDuplicateMessage() throws IOException {
        Properties bundle = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/oscarResources_pl.properties"), StandardCharsets.UTF_8)) {
            bundle.load(reader);
        }
        // "Operacja nie powiodła się" (the operation failed), as the English says; it read "the operation succeeded".
        assertThat(bundle.getProperty("oscar.billing.CA.BC.billingBC.error.assocexists")).startsWith("Operacja nie powiod&#X142;a si&#X119;");
    }

    @Test
    void shouldShowTheEditorAgain_forARefusedSave() throws IOException {
        assertThat(Files.readString(STRUTS, StandardCharsets.UTF_8))
                .contains("<result name=\"input\">/WEB-INF/jsp/billing/CA/BC/dxcode_svccode_assoc.jsp</result>");
    }
}
