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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that a lab upload's saved file never outlives an upload that stored nothing (#4086), and is
 * never removed while a stored lab may reference it.
 *
 * @since 2026-09-30
 */
@Tag("unit")
@Tag("lab")
class FileUploadCheckSavedFileUnitTest extends CarlosUnitTestBase {
    @TempDir Path documentDir;
    private Path saved;
    private FileUploadCheckDao dao;
    private RecordingTransactionManager transactions;

    @BeforeEach
    void setUpUpload() throws IOException {
        saved = Files.writeString(documentDir.resolve("LabUpload.synthetic.hl7.1"), "MSH|synthetic saved lab");
        dao = mock(FileUploadCheckDao.class);
        registerMock(FileUploadCheckDao.class, dao);
        transactions = new RecordingTransactionManager();
        registerMock(PlatformTransactionManager.class, transactions);
        doAnswer(invocation -> {
            invocation.<io.github.carlos_emr.carlos.commn.model.FileUploadCheck>getArgument(0).setId(7);
            return null;
        }).when(dao).persist(any());
    }

    @Test
    void shouldKeepSavedFile_whenUploadIsStored() throws Exception {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of());

        assertThat(store(checksumId -> true)).isEqualTo(FileUploadCheck.StoreOutcome.STORED);

        assertThat(saved).exists();
        assertThat(transactions.commits).isEqualTo(1);
    }

    @Test
    void shouldRemoveSavedFile_whenContentWasAlreadyRecorded() throws Exception {
        when(dao.findByMd5Sum(anyString()))
                .thenReturn(List.of(new io.github.carlos_emr.carlos.commn.model.FileUploadCheck()));

        assertThat(store(checksumId -> {
            throw new AssertionError("a duplicate must not be stored");
        })).isEqualTo(FileUploadCheck.StoreOutcome.ALREADY_RECORDED);

        assertThat(saved).doesNotExist();
        verify(dao, never()).persist(any());
    }

    @Test
    void shouldRemoveSavedFileAndReportLookupFailure_whenDuplicateCheckFails() {
        when(dao.findByMd5Sum(anyString())).thenThrow(new IllegalStateException("database unavailable"));

        // Thrown, never answered as ALREADY_RECORDED: a sender told "uploaded previously" stops retrying.
        assertThatThrownBy(() -> store(checksumId -> true))
                .isInstanceOf(FileUploadCheck.LookupFailedException.class);

        assertThat(saved).doesNotExist();
        verify(dao, never()).persist(any());
    }

    @Test
    void shouldRemoveSavedFile_whenTransactionCannotStart() {
        transactions.failBegin = true;

        assertThatThrownBy(() -> store(checksumId -> true)).isInstanceOf(CannotCreateTransactionException.class);

        assertThat(saved).doesNotExist();
    }

    @Test
    void shouldRemoveSavedFileAndChecksum_whenStoreStepRejects() throws Exception {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of());

        assertThat(store(checksumId -> false)).isEqualTo(FileUploadCheck.StoreOutcome.REJECTED);

        assertThat(saved).doesNotExist();
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
    }

    @Test
    void shouldRemoveSavedFileAndRethrow_whenReadingTheLabFails() {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of());
        IOException unreadable = new IOException("document folder briefly unavailable");

        // The checksum was recorded before the lab was read; it must roll back so the retry is accepted.
        assertThatThrownBy(() -> store(checksumId -> {
            throw unreadable;
        })).isSameAs(unreadable);

        assertThat(saved).doesNotExist();
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
    }

    @Test
    void shouldRemoveSavedFile_whenCommitFailsWithConfirmedRollback() {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of());
        transactions.rollBackOnCommit = true;

        assertThatThrownBy(() -> store(checksumId -> true)).isInstanceOf(IllegalStateException.class);

        assertThat(saved).doesNotExist();
    }

    @Test
    void shouldKeepSavedFile_whenCommitOutcomeIsUnknown() {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of());
        transactions.failCommit = true;

        assertThatThrownBy(() -> store(checksumId -> true)).isInstanceOf(TransactionSystemException.class);

        // The lab may have committed and reference this file.
        assertThat(saved).exists();
    }

    @Test
    void shouldNotRemoveFile_whenItLiesOutsideTheSavedDirectory(@TempDir Path elsewhere) throws Exception {
        Path outside = Files.writeString(elsewhere.resolve("LabUpload.other.hl7.2"), "MSH|someone else's file");

        assertThat(FileUploadCheck.discardUnreferenced(outside.toFile(), documentDir.toFile())).isFalse();

        assertThat(outside).exists();
    }

    @Test
    void shouldReportFailureWithoutThrowing_whenSavedFileCannotBeDeleted() throws Exception {
        // A non-empty directory in the file's place makes the delete fail even for a privileged user.
        Path stuck = Files.createDirectory(documentDir.resolve("LabUpload.stuck.hl7.3"));
        Files.writeString(stuck.resolve("keep"), "x");

        assertThat(FileUploadCheck.discardUnreferenced(stuck.toFile(), documentDir.toFile())).isFalse();

        assertThat(stuck).exists();
    }

    @Test
    void shouldTreatMissingFileAsRemoved_forNullOrAbsentFile() {
        assertThat(FileUploadCheck.discardUnreferenced(null, documentDir.toFile())).isTrue();
        assertThat(FileUploadCheck.discardUnreferenced(documentDir.resolve("absent").toFile(), documentDir.toFile()))
                .isTrue();
    }

    private FileUploadCheck.StoreOutcome store(FileUploadCheck.ContentStore step) throws Exception {
        return FileUploadCheck.storeSavedFileIfNew(saved.toFile(), documentDir.toFile(), saved.getFileName().toString(),
                "999998", step);
    }
}
