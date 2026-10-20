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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.utility.SafeEncode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Master Record read view (demographic/edit-view.jsp) lists a patient's consents, and pointing at a consent's name
 * opens a popup with the consent type's description (#4984). The popup's call carries the description as a quoted,
 * encoded string. Unit tests have no EL implementation, so this reads the page.
 *
 * @since 2026-10-18
 */
@DisplayName("Master Record consent popup")
@Tag("unit")
@Tag("demographic")
class MasterRecordConsentPopupJspUnitTest {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/demographic/edit-view.jsp");

    /** The first argument of every nhpup.popup(...) call in the page, JSP comments left out. */
    private static List<String> popupArguments() throws IOException {
        String page = Files.readString(PAGE, StandardCharsets.UTF_8).replaceAll("(?s)<%--.*?--%>", "");
        Matcher call = Pattern.compile("nhpup\\.popup\\(\\s*('(?:[^'\\\\]|\\\\.)*'|\\$\\{[^}]*\\}|[A-Za-z_][A-Za-z0-9_]*)").matcher(page);
        List<String> arguments = new ArrayList<>();
        while (call.find()) {
            arguments.add(call.group(1));
        }
        return arguments;
    }

    @Test
    void shouldPassTheConsentDescriptionAsAnEncodedString_toThePopup() throws IOException {
        assertThat(popupArguments())
                .contains("'${carlos:forJavaScriptAttribute(carlos:forHtml(patientConsent.consentType.description))}'");
    }

    @Test
    void shouldWriteNoValueIntoAPopupCall_withoutQuotesAndEncoding() throws IOException {
        List<String> arguments = popupArguments();
        assertThat(arguments).as("the page's popup calls").isNotEmpty();
        // A popup takes a script variable built on the page, or a quoted string whose value is encoded for that place.
        assertThat(arguments).allSatisfy(argument -> assertThat(argument).matches(
                "[A-Za-z_][A-Za-z0-9_]*|'\\$\\{carlos:forJavaScriptAttribute\\(carlos:forHtml\\([^}]*\\)\\)\\}'"));
    }

    @Test
    void shouldKeepTheDescriptionInsideOneQuotedString_forProseWithQuotes() {
        String description = "This patient's consent (email, text) covers \"all\" <b>contact</b> & more";
        String encoded = SafeEncode.forJavaScriptAttribute(SafeEncode.forHtml(description));
        // Nothing that would end the attribute or the string is left in it.
        assertThat(encoded).doesNotContain("'", "\"", "<", ">").contains("\\x26lt;b\\x26gt;");
    }
}
