/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.waitinglist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * What {@link WLWaitingListUtil#updateWaitingListRecord} tells its caller (issue #5298).
 *
 * <p>The helper is a no-op when no list is named or the patient has no entry on the named list.
 * The patient edit form relies on that; the REST demographic update must instead refuse, because it
 * was asked to save the note. Both read the result these cases pin.</p>
 *
 * @since 2026-11-19
 */
@Tag("unit")
@DisplayName("WLWaitingListUtil.updateWaitingListRecord result")
class WLWaitingListUpdateRecordUnitTest {

    @ParameterizedTest(name = "list {0}, patient {1}")
    @CsvSource({"0, 101", "7, 0"})
    @DisplayName("should report that nothing was written when no list or no patient is named")
    void shouldReportNothingWritten_whenNoListOrPatientIsNamed(String listId, String demographicNo) {
        try (var spring = mockStatic(SpringUtils.class)) {
            assertThat(WLWaitingListUtil.updateWaitingListRecord(listId, "FAKE note", demographicNo, null)).isFalse();

            spring.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("should report that nothing was written when the patient has no entry on the list")
    void shouldReportNothingWritten_whenThePatientHasNoEntryOnTheList() {
        WaitingListDao dao = mock(WaitingListDao.class);
        when(dao.findByWaitingListIdAndDemographicId(7, 101)).thenReturn(List.of());
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(WaitingListDao.class)).thenReturn(dao);

            assertThat(WLWaitingListUtil.updateWaitingListRecord("7", "FAKE note", "101", null)).isFalse();
        }
        verify(dao, never()).saveEntity(any(WaitingList.class));
        verify(dao, never()).merge(any(WaitingList.class));
    }

    @Test
    @DisplayName("should record the note at the entry's position and report it when the patient is on the list")
    void shouldRecordTheNoteAndReportIt_whenThePatientIsOnTheList() {
        WaitingListDao dao = mock(WaitingListDao.class);
        WaitingList current = new WaitingList();
        current.setListId(7);
        current.setDemographicNo(101);
        current.setPosition(3);
        current.setHistory(false);
        when(dao.findByWaitingListIdAndDemographicId(7, 101)).thenReturn(List.of(current));
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(WaitingListDao.class)).thenReturn(dao);

            assertThat(WLWaitingListUtil.updateWaitingListRecord("7", "FAKE note", "101", null)).isTrue();
        }
        assertThat(current.getIsHistory()).as("the replaced entry is kept as history").isEqualTo("Y");
        ArgumentCaptor<WaitingList> saved = ArgumentCaptor.forClass(WaitingList.class);
        verify(dao).saveEntity(saved.capture());
        assertThat(saved.getValue().getNote()).isEqualTo("FAKE note");
        assertThat(saved.getValue().getPosition()).isEqualTo(3L);
        assertThat(saved.getValue().getIsHistory()).isEqualTo("N");
    }
}
