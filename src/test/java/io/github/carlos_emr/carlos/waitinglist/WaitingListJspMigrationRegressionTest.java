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
package io.github.carlos_emr.carlos.waitinglist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the migrated JSP callers onto the secured Struts endpoints so future
 * refactors do not accidentally restore the old direct-JSP mutation paths.
 *
 * @since 2026-04-14
 */
@DisplayName("Waiting-list JSP migration regressions")
@Tag("unit")
@Tag("waitinglist")
@Tag("security")
class WaitingListJspMigrationRegressionTest {

    private static final Path DEMOGRAPHIC_UPDATE_JSP =
        Path.of("src/main/webapp/WEB-INF/jsp/demographic/demographicupdatearecord.jsp");
    private static final Path DISPLAY_WAITING_LIST_JSP =
        Path.of("src/main/webapp/WEB-INF/jsp/waitinglist/DisplayWaitingList.jsp");
    private static final Path DISPLAY_PATIENT_WAITING_LIST_JSP =
        Path.of("src/main/webapp/WEB-INF/jsp/waitinglist/DisplayPatientWaitingList.jsp");

    @Test
    @DisplayName("demographic update JSP should POST to Add2WaitingList without leaking note or date in the URL")
    void demographicUpdateJspShouldPostToAdd2WaitingList_doWithoutLeakingNoteOrDate() throws IOException {
        String jsp = Files.readString(DEMOGRAPHIC_UPDATE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("/waitinglist/Add2WaitingList");
        assertThat(jsp).contains("method=\"post\"");
        assertThat(jsp).contains("name=\"waitingListNote\"");
        assertThat(jsp).contains("name=\"onListSince\"");
        assertThat(jsp).doesNotContain("Add2WaitingList.jsp");
        assertThat(jsp).doesNotContain("waitingListNote=");
        assertThat(jsp).doesNotContain("onListSince=");
    }

    @Test
    @DisplayName("display waiting-list JSP should target RemoveFromWaitingList and copy the CSRF token")
    void displayWaitingListJspShouldTargetRemoveFromWaitingList_doAndCopyTheCsrfToken() throws IOException {
        String jsp = Files.readString(DISPLAY_WAITING_LIST_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("/waitinglist/RemoveFromWaitingList");
        assertThat(jsp).contains("input[name=\"CSRF-TOKEN\"]");
        assertThat(jsp).contains("csrfInput.name = 'CSRF-TOKEN'");
        assertThat(jsp).doesNotContain("RemoveFromWaitingList.jsp");
    }

    @Test
    @DisplayName("display patient waiting-list JSP should target RemoveFromWaitingList and seed CSRF injection")
    void displayPatientWaitingListJspShouldTargetRemoveFromWaitingList_doAndSeedCsrfInjection() throws IOException {
        String jsp = Files.readString(DISPLAY_PATIENT_WAITING_LIST_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("/waitinglist/RemoveFromWaitingList");
        assertThat(jsp).contains("id=\"csrfForm\"");
        assertThat(jsp).contains("method=\"post\" style=\"display:none;\"");
        assertThat(jsp).contains("input[name=\"CSRF-TOKEN\"]");
        assertThat(jsp).contains("csrfInput.name = 'CSRF-TOKEN'");
        assertThat(jsp).doesNotContain("RemoveFromWaitingList.jsp");
    }
}
