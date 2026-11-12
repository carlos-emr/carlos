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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The professional contact search popup (demographic/procontactSearch.jsp) is opened by the Contacts editor's External
 * row and by the Health Care Team panel. Its "Add/Edit Professional Contact" link has to create the kind of contact
 * the opener searches for: an external professional contact for the External row, whose search lists only those, and a
 * professional specialist for the panel (#4987). Unit tests have no JSP engine, so this reads the pages.
 *
 * @since 2026-10-18
 */
@DisplayName("Professional contact search popup")
@Tag("unit")
@Tag("demographic")
class ProfessionalContactSearchJspUnitTest {

    private static final Path SEARCH = Path.of("src/main/webapp/WEB-INF/jsp/demographic/procontactSearch.jsp");
    private static final Path CONTACTS_EDITOR = Path.of("src/main/webapp/WEB-INF/jsp/demographic/ManageContacts.jsp");

    private static String read(Path page) throws IOException {
        return Files.readString(page, StandardCharsets.UTF_8).replaceAll("(?s)<%--.*?--%>", "");
    }

    @Test
    void shouldNotHardCodeTheContactTypeOfTheAddLink() throws IOException {
        assertThat(read(SEARCH)).doesNotContain("contactType=3\"");
    }

    @Test
    void shouldAskForTheTypeTheOpenerSent_andOnlyForExternalOrSpecialist() throws IOException {
        String page = read(SEARCH);
        assertThat(page).contains(
                "String.valueOf(DemographicContact.TYPE_CONTACT).equals(request.getParameter(\"contactType\"))",
                ": String.valueOf(DemographicContact.TYPE_PROFESSIONALSPECIALIST)");
        assertThat(page).contains("&contactType=<carlos:encode value='<%= contactType %>' context=\"uriComponent\"/>");
    }

    @Test
    void shouldKeepTheContactTypeAcrossSearchesAndPages() throws IOException {
        String page = read(SEARCH);
        assertThat(page).contains("<input type='hidden' name='contactType' value=\"<carlos:encode value='<%= contactType %>'");
        assertThat(page.split("&contactType=<carlos:encode value='<%= contactType %>' context=\"javaScript\"/>&limit1="))
                .as("the previous and next page URLs").hasSize(3);
    }

    @Test
    void shouldTellTheFormWhichRowToFill() throws IOException {
        assertThat(read(SEARCH)).contains(
                "&parentForm=<carlos:encode value='<%= form %>' context=\"uriComponent\"/>",
                "&parentElementName=<carlos:encode value='<%= elementName %>' context=\"uriComponent\"/>",
                "&parentElementId=<carlos:encode value='<%= elementId %>' context=\"uriComponent\"/>");
    }

    @Test
    void shouldOpenTheSearchFromAnExternalContactsRow_forExternalProfessionalContacts() throws IOException {
        assertThat(read(CONTACTS_EDITOR)).contains("+ '&contactType=<%=DemographicContact.TYPE_CONTACT%>';");
    }
}
