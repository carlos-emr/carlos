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
package io.github.carlos_emr.carlos.waitinglist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * #6141: every writer of a waiting-list note refuses one longer than {@code waitingList.note}
 * holds, before any row is read or changed. Under the packaged empty {@code sql_mode} the column
 * cut a longer note with only a warning, and the waiting-list page reported success.
 *
 * @since 2026-11-11
 */
@Tag("unit")
@Tag("waitinglist")
@DisplayName("Waiting-list note length guard")
class WLWaitingListUtilNoteLengthUnitTest extends CarlosUnitTestBase {

    private static final String TOO_LONG = "N".repeat(WaitingList.NOTE_MAX_LENGTH + 1);
    private static final String REFUSAL = "Waiting list note exceeds maximum length of "
            + WaitingList.NOTE_MAX_LENGTH + " characters.";

    private WaitingListDao dao;

    @BeforeEach
    void registerDao() {
        dao = createAndRegisterMock(WaitingListDao.class);
    }

    @Test
    void shouldRefuseALongNote_beforeAnyWriterTouchesARow() {
        assertThatThrownBy(() -> WLWaitingListUtil.add2WaitingList("7", TOO_LONG, "42", "2026-11-11"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(REFUSAL);
        assertThatThrownBy(() -> WLWaitingListUtil.updateWaitingListRecord("7", TOO_LONG, "42", "2026-11-11"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(REFUSAL);
        assertThatThrownBy(() -> WLWaitingListUtil.updateWaitingList("3", "7", TOO_LONG, "42", "2026-11-11"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(REFUSAL);

        verifyNoInteractions(dao);
    }

    @Test
    void shouldCountEachSubmittedLineBreakAsTwoCharacters_whenANoteComesFromATextarea() {
        // A textarea submits each line break as CR LF, and the column stores both characters.
        String note = "N".repeat(WaitingList.NOTE_MAX_LENGTH - 1) + "\r\n";

        assertThat(WLWaitingListUtil.noteLengthErrors(note)).containsExactly(REFUSAL);
    }

    @Test
    void shouldSaveTheNoteWhole_whenItFillsTheColumnWithCharactersOutsideTheBasicPlane() {
        // 255 characters, each 2 UTF-16 units: the column counts characters.
        String longest = "𝐀".repeat(WaitingList.NOTE_MAX_LENGTH);
        assertThat(WLWaitingListUtil.noteLengthErrors(longest)).isEmpty();

        WLWaitingListUtil.add2WaitingList("7", longest, "42", "2026-11-11");

        ArgumentCaptor<WaitingList> saved = ArgumentCaptor.forClass(WaitingList.class);
        verify(dao).persist(saved.capture());
        assertThat(saved.getValue().getNote()).isEqualTo(longest);
    }

    @Test
    void shouldAcceptAnEmptyOrMissingNote_forEveryWriter() {
        assertThat(WLWaitingListUtil.noteLengthErrors(null)).isEmpty();
        assertThat(WLWaitingListUtil.noteLengthErrors("")).isEmpty();
        assertThat(WLWaitingListUtil.noteLengthErrors("N".repeat(WaitingList.NOTE_MAX_LENGTH))).isEmpty();
        assertThat(WLWaitingListUtil.noteLengthErrors(TOO_LONG)).isEqualTo(List.of(REFUSAL));
    }
}
