/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.waitinglist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * What {@link WLWaitingListUtil#updateWaitingListRecord} and its stricter sibling
 * {@link WLWaitingListUtil#updateCurrentWaitingListRecord} tell their caller (issue #5298).
 *
 * <p>The helper is a no-op when no list is named or the patient has no entry on the named list.
 * The patient edit form relies on that; the REST demographic update must instead refuse, because it
 * was asked to save the note. The REST-only helper reports whether it wrote the note.</p>
 *
 * <p>The helper finds its DAO through {@code SpringUtils}, which {@link CarlosUnitTestBase} mocks:
 * a bean that was not registered is refused, so a case that registers no DAO also proves that
 * none was looked up.</p>
 *
 * @since 2026-11-19
 */
@Tag("unit")
@DisplayName("WLWaitingListUtil.updateWaitingListRecord result")
class WLWaitingListUpdateRecordUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldKeepTheVoidReturnType_forPreviouslyCompiledCallers() throws NoSuchMethodException {
        assertThat(WLWaitingListUtil.class.getMethod("updateWaitingListRecord",
                String.class, String.class, String.class, String.class).getReturnType()).isEqualTo(void.class);
    }

    @ParameterizedTest(name = "list {0}, patient {1}")
    @CsvSource({"0, 101", "7, 0"})
    @DisplayName("should write nothing when no list or no patient is named")
    void shouldWriteNothing_whenNoListOrPatientIsNamed(String listId, String demographicNo) {
        // No WaitingListDao is registered: looking one up would fail this case.
        assertThatCode(() -> WLWaitingListUtil.updateWaitingListRecord(listId, "FAKE note", demographicNo, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should write nothing when the patient has no entry on the list")
    void shouldWriteNothing_whenThePatientHasNoEntryOnTheList() {
        WaitingListDao dao = createAndRegisterMock(WaitingListDao.class);
        when(dao.findByWaitingListIdAndDemographicId(7, 101)).thenReturn(List.of());

        WLWaitingListUtil.updateWaitingListRecord("7", "FAKE note", "101", null);

        verify(dao, never()).saveEntity(any(WaitingList.class));
        verify(dao, never()).merge(any(WaitingList.class));
    }

    @Test
    @DisplayName("should record the note at the entry's position when the patient is on the list")
    void shouldRecordTheNote_whenThePatientIsOnTheList() {
        WaitingListDao dao = createAndRegisterMock(WaitingListDao.class);
        WaitingList current = new WaitingList();
        current.setListId(7);
        current.setDemographicNo(101);
        current.setPosition(3);
        current.setHistory(false);
        when(dao.findByWaitingListIdAndDemographicId(7, 101)).thenReturn(List.of(current));

        WLWaitingListUtil.updateWaitingListRecord("7", "FAKE note", "101", null);

        assertThat(current.getIsHistory()).as("the replaced entry is kept as history").isEqualTo("Y");
        ArgumentCaptor<WaitingList> saved = ArgumentCaptor.forClass(WaitingList.class);
        verify(dao).saveEntity(saved.capture());
        assertThat(saved.getValue().getNote()).isEqualTo("FAKE note");
        assertThat(saved.getValue().getPosition()).isEqualTo(3L);
        assertThat(saved.getValue().getIsHistory()).isEqualTo("N");
    }

    @Test
    @DisplayName("should put the patient back on the list when only history remains, as the patient edit form expects")
    void shouldRecordACurrentEntry_whenOnlyHistoryRemainsForTheEditForm() {
        WaitingListDao dao = createAndRegisterMock(WaitingListDao.class);
        when(dao.findByWaitingListIdAndDemographicId(7, 101)).thenReturn(List.of(entry(4, true)));

        WLWaitingListUtil.updateWaitingListRecord("7", "FAKE note", "101", null);

        ArgumentCaptor<WaitingList> saved = ArgumentCaptor.forClass(WaitingList.class);
        verify(dao).saveEntity(saved.capture());
        assertThat(saved.getValue().getIsHistory()).isEqualTo("N");
    }

    /** {@code updateCurrentWaitingListRecord}: the caller must not put a removed patient back. */
    @Nested
    @DisplayName("when a current entry is required")
    class WhenACurrentEntryIsRequired {

        @ParameterizedTest(name = "list {0}, patient {1}")
        @CsvSource({"0, 101", "7, 0"})
        @DisplayName("should report that nothing was written when no list or no patient is named")
        void shouldReportNothingWritten_whenNoListOrPatientIsNamed(String listId, String demographicNo) {
            // No WaitingListDao is registered: looking one up would fail this case.
            assertThat(WLWaitingListUtil.updateCurrentWaitingListRecord(listId, "FAKE note", demographicNo, null)).isFalse();
        }

        @Test
        @DisplayName("should write nothing and report it when the patient was taken off the list")
        void shouldWriteNothingAndReportIt_whenOnlyHistoryRemains() {
            WaitingListDao dao = createAndRegisterMock(WaitingListDao.class);
            when(dao.findByWaitingListIdForUpdate(7)).thenReturn(List.of());

            assertThat(WLWaitingListUtil.updateCurrentWaitingListRecord("7", "FAKE note", "101", null)).isFalse();

            verify(dao, never()).saveEntity(any(WaitingList.class));
            verify(dao, never()).merge(any(WaitingList.class));
            verify(dao, never()).findByWaitingListId(any());
        }

        @Test
        @DisplayName("should write nothing and report it when the patient has no entry on the list")
        void shouldWriteNothingAndReportIt_whenThePatientHasNoEntryOnTheList() {
            WaitingListDao dao = createAndRegisterMock(WaitingListDao.class);
            WaitingList anotherPatient = entry(4, false);
            anotherPatient.setDemographicNo(102);
            when(dao.findByWaitingListIdForUpdate(7)).thenReturn(List.of(anotherPatient));

            assertThat(WLWaitingListUtil.updateCurrentWaitingListRecord("7", "FAKE note", "101", null)).isFalse();

            verify(dao, never()).saveEntity(any(WaitingList.class));
        }

        @Test
        @DisplayName("should record the note and report it when the patient is on the list, whatever history precedes")
        void shouldRecordTheNoteAndReportIt_whenACurrentEntryFollowsHistory() {
            WaitingListDao dao = createAndRegisterMock(WaitingListDao.class);
            WaitingList current = entry(3, false);
            when(dao.findByWaitingListIdForUpdate(7)).thenReturn(List.of(current), List.of());

            assertThat(WLWaitingListUtil.updateCurrentWaitingListRecord("7", "FAKE note", "101", null)).isTrue();

            assertThat(current.getIsHistory()).as("the replaced entry is kept as history").isEqualTo("Y");
            ArgumentCaptor<WaitingList> saved = ArgumentCaptor.forClass(WaitingList.class);
            verify(dao).saveEntity(saved.capture());
            assertThat(saved.getValue().getNote()).isEqualTo("FAKE note");
            assertThat(saved.getValue().getPosition()).isEqualTo(3L);
            assertThat(saved.getValue().getIsHistory()).isEqualTo("N");
        }
    }

    private static WaitingList entry(long position, boolean history) {
        WaitingList entry = new WaitingList();
        entry.setListId(7);
        entry.setDemographicNo(101);
        entry.setPosition(position);
        entry.setHistory(history);
        return entry;
    }
}
