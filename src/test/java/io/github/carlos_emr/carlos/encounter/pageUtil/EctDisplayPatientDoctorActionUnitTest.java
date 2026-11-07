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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Family Doctor and Referring Doctor left-navigation panels (OSCAR 19's fDoc/rDoc
 * widgets) and the Documents panel's folder naming.
 */
@Tag("unit")
@Tag("fast")
@Tag("encounter")
@DisplayName("eChart doctor panels and document folders")
class EctDisplayPatientDoctorActionUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should show the doctor's name as one row that opens the Master Record")
    void shouldShowName_asRowOpeningMasterRecord() {
        NavBarDisplayDAO dao = new NavBarDisplayDAO();

        EctDisplayPatientDoctorAction.fillPanel(dao, "Family Doctor", "FAKE-Fam, FAKE-Fiona", "/carlos", "42", "fDoc");

        assertThat(dao.getLeftHeading()).isEqualTo("Family Doctor");
        assertThat(dao.getLeftPopup().url()).isEqualTo("/carlos/demographic/DemographicEdit?demographic_no=42");
        assertThat(dao.getRightPopup().url()).isEqualTo(dao.getLeftPopup().url());
        assertThat(dao.getRightHeadingID()).isEqualTo("fDoc");
        assertThat(dao.numItems()).isEqualTo(1);
        NavBarDisplayDAO.Item item = dao.getItem(0);
        assertThat(item.getTitle()).isEqualTo("FAKE-Fam, FAKE-Fiona");
        // JavaScript-string encoded, so the slashes arrive escaped.
        assertThat(item.getURL()).startsWith("popupPage(700,1024,'Master42','")
                .contains("\\/carlos\\/demographic\\/DemographicEdit?demographic_no=42");
        assertThat(item.isURLJavaScript()).isTrue();
        assertThat(item.getDate()).isNull();
    }

    @Test
    @DisplayName("should HTML-encode the name, which the renderer writes as markup")
    void shouldEncodeName_forMarkupRenderer() {
        NavBarDisplayDAO dao = new NavBarDisplayDAO();

        EctDisplayPatientDoctorAction.fillPanel(dao, "Referring Doctor", "<img src=x onerror=alert(1)>", "/carlos", "7", "rDoc");

        assertThat(dao.getItem(0).getTitle()).doesNotContain("<img").contains("&lt;img");
        assertThat(dao.getItem(0).getLinkTitle()).isEqualTo("<img src=x onerror=alert(1)>");
    }

    @Test
    @DisplayName("should keep the heading but add no row when no doctor is recorded")
    void shouldAddNoRow_whenNoDoctorRecorded() {
        NavBarDisplayDAO dao = new NavBarDisplayDAO();

        EctDisplayPatientDoctorAction.fillPanel(dao, "Referring Doctor", " ", "/carlos", "7", "rDoc");

        assertThat(dao.getLeftHeading()).isEqualTo("Referring Doctor");
        assertThat(dao.numItems()).isZero();
    }

    @Test
    @DisplayName("should give each panel its own OSCAR 19 command name")
    void shouldUseOscarCommandNames_forEachPanel() {
        assertThat(EctDisplayFamilyDoctor2Action.CMD).isEqualTo("fDoc");
        assertThat(EctDisplayReferringDoctor2Action.CMD).isEqualTo("rDoc");
    }

    @Test
    @DisplayName("should file a document under its type, or the shared folder when it has none")
    void shouldFileDocument_underTypeOrSharedFolder() {
        assertThat(EctDisplayDocs2Action.folderFor(" consult ", "Other")).isEqualTo("consult");
        assertThat(EctDisplayDocs2Action.folderFor("", "Other")).isEqualTo("Other");
        assertThat(EctDisplayDocs2Action.folderFor(null, "Other")).isEqualTo("Other");
    }

    @Test
    @DisplayName("should default a navigation row to no folder and to an unfiled panel")
    void shouldDefaultToNoFolder_andUnfiledPanel() {
        NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
        item.setFolder(null);

        assertThat(item.getFolder()).isEmpty();
        assertThat(new NavBarDisplayDAO().isFiledInFolders()).isFalse();
    }
}
