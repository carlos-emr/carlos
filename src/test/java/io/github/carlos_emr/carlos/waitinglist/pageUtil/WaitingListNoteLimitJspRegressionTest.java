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
package io.github.carlos_emr.carlos.waitinglist.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * #6141: the waiting-list page keeps its note boxes to the column's limit, counted as the server
 * counts it, checks the selected note before its script submits the form (a script submit skips the
 * browser's own check), and shows a refusal the server gives.
 *
 * @since 2026-11-11
 */
@Tag("unit")
@Tag("waitinglist")
@DisplayName("The waiting-list page's note limit")
class WaitingListNoteLimitJspRegressionTest {

    @Test
    void shouldLimitAndCheckTheNoteBoxes_andShowTheServersRefusal() throws IOException {
        String page = Files.readString(projectRoot().resolve(
                "src/main/webapp/WEB-INF/jsp/waitinglist/DisplayWaitingList.jsp"), StandardCharsets.UTF_8);

        // The limit comes from the model, in code points, with each line break as the submitted CR LF.
        assertThat(page).contains("io.github.carlos_emr.carlos.commn.model.WaitingList.NOTE_MAX_LENGTH",
                "replace(/\\r?\\n/g, '\\r\\n')", "Array.from(submitted).length <= WAITING_LIST_NOTE_MAX",
                "key=\"admin.securityupdate.msgMaximumCharacters\"");
        // The script submit checks the selected note first.
        String update = page.substring(page.indexOf("function updateWaitingList("));
        assertThat(update.indexOf("waitingListNoteFits(selectedNote)"))
                .isGreaterThan(0)
                .isLessThan(update.indexOf("document.forms[0].submit()"));
        // A server refusal is shown, encoded.
        assertThat(page).contains("data-carlos-waiting-list-note-error=\"true\"",
                "items=\"${waitingListNoteErrors}\"", "<carlos:encode value=\"${noteError}\"/>");
        assertThat(WLSetupDisplayWaitingList2Action.NOTE_ERRORS).isEqualTo("waitingListNoteErrors");
    }

    private static Path projectRoot() {
        try {
            Path location = Path.of(WaitingListNoteLimitJspRegressionTest.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            for (Path current = Files.isRegularFile(location) ? location.getParent() : location;
                 current != null; current = current.getParent()) {
                if (Files.isDirectory(current.resolve("src/main/webapp"))) {
                    return current;
                }
            }
        } catch (URISyntaxException invalid) {
            throw new IllegalStateException("Cannot locate the project root", invalid);
        }
        throw new IllegalStateException("Cannot locate the project root");
    }
}
