/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The read-only lock check a note editor opened by GET uses: it reports another provider's lock on
 * a saved note, as {@code isNoteEdited} would, and takes or changes no lock
 * ({@link CaseManagementEntryNoteOpenUnitTest}). Every patient here is FAKE.
 */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementEntryNoteLockCheckUnitTest extends CarlosUnitTestBase {
    private static final String PROVIDER = "999998";
    private static final String OTHER_PROVIDER = "999997";

    private CasemgmtNoteLockDao locks;

    @BeforeEach
    void setUp() {
        locks = createAndRegisterMock(CasemgmtNoteLockDao.class);
    }

    @Test
    @DisplayName("should report only another provider's lock on a saved note, and read without writing")
    void shouldReportAnotherProvidersLock_forSavedNotesOnly() {
        when(locks.findByNoteDemo(101, 5001L)).thenReturn(lock(5001L, OTHER_PROVIDER));
        when(locks.findByNoteDemo(101, 5002L)).thenReturn(lock(5002L, PROVIDER));
        when(locks.findByNoteDemo(101, 0L)).thenReturn(lock(0L, OTHER_PROVIDER));

        assertThat(CaseManagementEntry2Action.isLockedByAnotherProvider(5001L, 101, PROVIDER)).isTrue();
        assertThat(CaseManagementEntry2Action.isLockedByAnotherProvider(5002L, 101, PROVIDER)).isFalse();
        assertThat(CaseManagementEntry2Action.isLockedByAnotherProvider(5004L, 101, PROVIDER)).isFalse();
        assertThat(CaseManagementEntry2Action.isLockedByAnotherProvider(0L, 101, PROVIDER)).isFalse();
        assertThat(CaseManagementEntry2Action.isLockedByAnotherProvider(null, 101, PROVIDER)).isFalse();
        verify(locks, never()).persist(any());
        verify(locks, never()).merge(any());
    }

    private static CasemgmtNoteLock lock(long noteId, String providerNo) {
        CasemgmtNoteLock lock = new CasemgmtNoteLock();
        lock.setNoteId(noteId);
        lock.setDemographicNo(101);
        lock.setProviderNo(providerNo);
        lock.setSessionId("FAKE-session");
        return lock;
    }
}
