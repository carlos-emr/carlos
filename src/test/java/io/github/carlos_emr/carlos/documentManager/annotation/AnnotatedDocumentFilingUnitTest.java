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
package io.github.carlos_emr.carlos.documentManager.annotation;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("Annotated document transactional filing")
class AnnotatedDocumentFilingUnitTest extends CarlosUnitTestBase {
    private static class Transactions extends AbstractPlatformTransactionManager {
        boolean committed;
        boolean rolledBack;
        boolean failCommit;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) { }
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new IllegalStateException("Lost commit acknowledgement");
            committed = true;
        }
        protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
    }

    private AnnotatedDocumentService service(Transactions tx, OscarLogDao audit) {
        registerMock(PlatformTransactionManager.class, tx);
        registerMock(OscarLogDao.class, audit);
        return new AnnotatedDocumentService(mock(SecurityInfoManager.class), new AnnotatedDocumentComposer(), null);
    }

    @Test
    @DisplayName("should commit document linkage and audit as one transaction")
    void shouldCommitDocumentAndAudit_together(@TempDir Path dir) throws Exception {
        Transactions tx = new Transactions();
        OscarLogDao audit = mock(OscarLogDao.class);
        Path target = Files.writeString(dir.resolve("copy.pdf"), "synthetic");
        EDoc copy = new EDoc();
        try (var documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.addDocumentSQL(copy)).thenReturn("42");
            assertThat(service(tx, audit).fileCopy(copy, target, 10, new LoggedInInfo())).isEqualTo(42);
            assertThat(tx.committed).isTrue();
            verify(audit).persist(argThat(log -> log.getDemographicId() == 10 && "42".equals(log.getContentId())));
            assertThat(target).exists();
        }
    }

    @Test
    @DisplayName("should roll back and remove the copy when audit persistence fails")
    void shouldRollBack_whenAuditFails(@TempDir Path dir) throws Exception {
        Transactions tx = new Transactions();
        OscarLogDao audit = mock(OscarLogDao.class);
        doThrow(new IllegalStateException("synthetic audit failure")).when(audit).persist(any());
        Path target = Files.writeString(dir.resolve("copy.pdf"), "synthetic");
        EDoc copy = new EDoc();
        try (var documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.addDocumentSQL(copy)).thenReturn("42");
            assertThatThrownBy(() -> service(tx, audit).fileCopy(copy, target, 10, new LoggedInInfo()))
                    .isInstanceOf(AnnotatedDocumentService.FilingException.class);
            assertThat(tx.rolledBack).isTrue();
            assertThat(tx.committed).isFalse();
            assertThat(target).doesNotExist();
        }
    }

    @Test
    @DisplayName("should retain bytes when the commit outcome is uncertain")
    void shouldRetainBytes_whenCommitIsUncertain(@TempDir Path dir) throws Exception {
        Transactions tx = new Transactions();
        tx.failCommit = true;
        Path target = Files.writeString(dir.resolve("copy.pdf"), "synthetic");
        EDoc copy = new EDoc();
        try (var documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.addDocumentSQL(copy)).thenReturn("42");
            assertThatThrownBy(() -> service(tx, mock(OscarLogDao.class)).fileCopy(copy, target, 10, new LoggedInInfo()))
                    .isInstanceOf(AnnotatedDocumentService.FilingException.class);
            assertThat(target).exists();
        }
    }
}
