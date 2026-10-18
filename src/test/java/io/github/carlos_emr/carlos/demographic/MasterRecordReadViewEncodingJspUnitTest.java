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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Master Record read view prints its patient, contact and provider values through {@code carlos:encode} or
 * {@code SafeEncode}, with the same lines develop has.
 *
 * @since 2026-10-18
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("Master Record read view output")
class MasterRecordReadViewEncodingJspUnitTest {

    private static String editView() throws IOException, URISyntaxException {
        Path current = Path.of(MasterRecordReadViewEncodingJspUnitTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        while (current != null && !Files.isDirectory(current.resolve("src/main/webapp"))) {
            current = current.getParent();
        }
        assertThat(current).as("project root with src/main/webapp").isNotNull();
        return Files.readString(current.resolve("src/main/webapp/WEB-INF/jsp/demographic/edit-view.jsp"), StandardCharsets.UTF_8);
    }

    private static int occurrences(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "StringUtils.trimToEmpty(demographic.getTitle())",
            "StringUtils.trimToEmpty(demographic.getOfficialLanguage())",
            "countryCode.getCountryName()",
            "sp_lang",
            "sin",
            "StringUtils.trimToEmpty((String) relHash.get(\"relation\"))",
            "StringUtils.trimToEmpty((String) relHash.get(\"lastName\"))",
            "StringUtils.trimToEmpty((String) relHash.get(\"firstName\"))",
            "relHash.get(\"phone\") == null ? \"\" : relHash.get(\"phone\").toString()",
            "formattedWorkPhone",
            "formattedCellPhone",
            "dContact.getRole()",
            "dContact.getContactName()",
            "demographic.getPatientStatus()",
            "StringUtils.trimToEmpty(demographic.getChartNo())",
            "OtherIdManager.getDemoOtherId(demographic_no, \"meditech_id\")",
            "StringUtils.trimToEmpty(demoExt.get(\"cytolNum\"))",
            "alert",
            "archivedDate",
            "archivedProgram",
            "privacyConsent",
            "informedConsent",
            "usSigned",
            "StringUtils.trimToEmpty(demographic.getPhone())",
            "StringUtils.trimToEmpty(demoExt.get(\"hPhoneExt\"))",
            "StringUtils.trimToEmpty(demographic.getPhone2())",
            "StringUtils.trimToEmpty(demoExt.get(\"wPhoneExt\"))",
            "StringUtils.trimToEmpty(demoExt.get(\"demo_cell\"))",
            "StringUtils.trimToEmpty(ISO36612.getInstance().translateCodeToHumanReadableString(demographic.getProvince()))",
            "StringUtils.trimToEmpty(demographic.getPostal())",
            "StringUtils.trimToEmpty(ISO36612.getInstance().translateCodeToHumanReadableString(demographic.getResidentialProvince()))",
            "StringUtils.trimToEmpty(demographic.getResidentialPostal())",
            "demographic.getEmail() != null ? demographic.getEmail() : \"\"",
            "demographic.getNewsletter() != null ? demographic.getNewsletter() : \"Unknown\"",
            "StringUtils.trimToEmpty(demographic.getHin())",
            "StringUtils.trimToEmpty(demographic.getVer())",
            "demographic.getHcType() == null ? \"\" : demographic.getHcType()",
            "rd",
            "rdohip"})
    void shouldPrintTheValueThroughTheEncodeTag_forEachValue(String value) throws Exception {
        String jsp = editView();

        assertThat(jsp).contains("<carlos:encode value='<%= " + value + " %>' context=\"html\"/>");
        assertThat(jsp).doesNotContain("<%=" + value + "%>");
        assertThat(occurrences(jsp, "<%= " + value + " %>")).as("printed outside the encode tag")
                .isEqualTo(occurrences(jsp, "value='<%= " + value + " %>'"));
    }

    static Stream<Arguments> safeEncodedValues() {
        return Stream.of(
                Arguments.of("forHtmlAttribute", "key"),
                Arguments.of("forHtmlContent", "enrolledTo"),
                Arguments.of("forHtmlContent", "hasPrimaryCarePhysician"),
                Arguments.of("forHtmlContent", "employmentStatus"),
                Arguments.of("forHtmlContent", "providerBean.getProperty(demographic.getProviderNo(), \"\")"),
                Arguments.of("forHtmlContent", "providerBean.getProperty(resident, \"\")"),
                Arguments.of("forHtmlContent", "providerBean.getProperty(midwife, \"\")"),
                Arguments.of("forHtmlContent", "providerBean.getProperty(nurse, \"\")"),
                Arguments.of("forHtmlContent", "providerBean.getProperty(nurse == null ? \"\" : nurse, \"\")"),
                Arguments.of("forHtmlContent", "providerBean.getProperty(midwife == null ? \"\" : midwife, \"\")"),
                Arguments.of("forHtmlContent", "providerBean.getProperty(resident == null ? \"\" : resident, \"\")"),
                Arguments.of("forHtmlContent", "adm.getProgramName()"));
    }

    @ParameterizedTest
    @MethodSource("safeEncodedValues")
    void shouldPrintTheValueThroughSafeEncode_forEachValue(String method, String value) throws Exception {
        String jsp = editView();

        assertThat(jsp).contains("<%=SafeEncode." + method + "(" + value + ")%>");
        assertThat(jsp).doesNotContain("<%=" + value + "%>").doesNotContain("<%= " + value + " %>");
    }
}
