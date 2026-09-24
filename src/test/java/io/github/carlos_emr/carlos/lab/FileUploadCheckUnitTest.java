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
package io.github.carlos_emr.carlos.lab;

import io.github.carlos_emr.carlos.commn.dao.FileUploadCheckDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.unit.RecordingTransactionManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pins the duplicate lookup and the store-once transaction that, unlike addFile, do not swallow failures.
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("lab")
class FileUploadCheckUnitTest extends CarlosUnitTestBase {
    private static final byte[] CONTENT = "MSH|recorded lab content".getBytes(StandardCharsets.UTF_8);

    private FileUploadCheckDao dao;
    private RecordingTransactionManager transactions;

    @BeforeEach
    void setUpDao() {
        dao = mock(FileUploadCheckDao.class);
        registerMock(FileUploadCheckDao.class, dao);
        transactions = new RecordingTransactionManager();
        registerMock(PlatformTransactionManager.class, transactions);
        // IDENTITY ids are assigned on insert; the mock stands in for that.
        doAnswer(invocation -> {
            invocation.<io.github.carlos_emr.carlos.commn.model.FileUploadCheck>getArgument(0).setId(41);
            return null;
        }).when(dao).persist(any());
    }

    @Test
    void shouldReportRecorded_whenChecksumRowExists() throws Exception {
        when(dao.findByMd5Sum(DigestUtils.md5Hex(CONTENT)))
                .thenReturn(List.of(new io.github.carlos_emr.carlos.commn.model.FileUploadCheck()));

        assertThat(FileUploadCheck.isFileRecorded(new ByteArrayInputStream(CONTENT))).isTrue();
    }

    @Test
    void shouldReportNotRecorded_whenNoChecksumRowExists() throws Exception {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of());

        assertThat(FileUploadCheck.isFileRecorded(new ByteArrayInputStream(CONTENT))).isFalse();
    }

    @Test
    void shouldPersistChecksumRow_whenRecordingFile() throws Exception {
        assertThat(FileUploadCheck.recordFile("lab.hl7", new ByteArrayInputStream(CONTENT), "999998")).isEqualTo(41);

        var row = ArgumentCaptor.forClass(io.github.carlos_emr.carlos.commn.model.FileUploadCheck.class);
        verify(dao).persist(row.capture());
        assertThat(row.getValue().getMd5sum()).isEqualTo(DigestUtils.md5Hex(CONTENT));
        assertThat(row.getValue().getFilename()).isEqualTo("lab.hl7");
        assertThat(row.getValue().getProviderNo()).isEqualTo("999998");
        assertThat(row.getValue().getDateTime()).isNotNull();
    }

    @Test
    void shouldPropagatePersistFailure_whenRecordingFile() {
        doThrow(new IllegalStateException("database unavailable")).when(dao).persist(any());

        // Unlike addFile, the failure reaches the caller's transaction so it rolls back.
        assertThatThrownBy(() -> FileUploadCheck.recordFile("lab.hl7", new ByteArrayInputStream(CONTENT), "999998"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldPropagateDatabaseFailure_whenLookupThrows() {
        when(dao.findByMd5Sum(anyString())).thenThrow(new IllegalStateException("database unavailable"));

        // addFile would turn this into UNSUCCESSFUL_SAVE; this lookup must not look like "not recorded".
        assertThatThrownBy(() -> FileUploadCheck.isFileRecorded(new ByteArrayInputStream(CONTENT)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldCommitChecksumWithStoredContent_whenContentIsNew() throws Exception {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of());

        FileUploadCheck.StoreOutcome outcome = FileUploadCheck.storeIfNew("lab.hl7",
                () -> new ByteArrayInputStream(CONTENT), "999998", checksumId -> {
                    // The store step runs under the checksum lock, inside the transaction that holds
                    // the uncommitted checksum row, and is told that row's id.
                    assertThat(Thread.holdsLock(FileUploadCheck.class)).isTrue();
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(checksumId).isEqualTo(41);
                    verify(dao).persist(any());
                    return true;
                });

        assertThat(outcome).isEqualTo(FileUploadCheck.StoreOutcome.STORED);
        assertThat(transactions.commits).isEqualTo(1);
        assertThat(transactions.rollbacks).isZero();
        // ProviderLabRouting.routeMagic joins this transaction and needs READ_COMMITTED.
        assertThat(transactions.lastIsolationLevel).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Test
    void shouldStoreNothing_whenChecksumAlreadyRecorded() throws Exception {
        when(dao.findByMd5Sum(DigestUtils.md5Hex(CONTENT)))
                .thenReturn(List.of(new io.github.carlos_emr.carlos.commn.model.FileUploadCheck()));

        FileUploadCheck.StoreOutcome outcome = FileUploadCheck.storeIfNew("lab.hl7",
                () -> new ByteArrayInputStream(CONTENT), "999998", checksumId -> {
                    throw new AssertionError("a recorded duplicate must not be stored again");
                });

        assertThat(outcome).isEqualTo(FileUploadCheck.StoreOutcome.ALREADY_RECORDED);
        assertThat(transactions.begun).isZero();
        verify(dao, never()).persist(any());
    }

    @Test
    void shouldRollBackChecksum_whenStoreStepRejectsContent() throws Exception {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of());

        FileUploadCheck.StoreOutcome outcome = FileUploadCheck.storeIfNew("lab.hl7",
                () -> new ByteArrayInputStream(CONTENT), "999998", checksumId -> false);

        // The checksum was written in the transaction that rolled back, so a retry is not refused.
        assertThat(outcome).isEqualTo(FileUploadCheck.StoreOutcome.REJECTED);
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
    }

    @Test
    void shouldRollBackAndRethrowCheckedFailure_whenStoreStepThrows() {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of());
        java.io.IOException failure = new java.io.IOException("unparseable lab");

        assertThatThrownBy(() -> FileUploadCheck.storeIfNew("lab.hl7",
                () -> new ByteArrayInputStream(CONTENT), "999998", checksumId -> {
                    throw failure;
                })).isSameAs(failure);
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
    }

    @Test
    void shouldReportLookupFailure_whenDuplicateCheckThrows() {
        when(dao.findByMd5Sum(anyString())).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> FileUploadCheck.storeIfNew("lab.hl7",
                () -> new ByteArrayInputStream(CONTENT), "999998", checksumId -> true))
                .isInstanceOf(FileUploadCheck.LookupFailedException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(transactions.begun).isZero();
        verify(dao, never()).persist(any());
    }

    @Test
    void shouldPropagateFailure_whenTransactionCannotStart() {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of());
        transactions.failBegin = true;

        assertThatThrownBy(() -> FileUploadCheck.storeIfNew("lab.hl7",
                () -> new ByteArrayInputStream(CONTENT), "999998", checksumId -> true))
                .isInstanceOf(org.springframework.transaction.CannotCreateTransactionException.class);
        verify(dao, never()).persist(any());
    }
}
