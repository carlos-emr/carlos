/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CdsExportAttemptDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportEventDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportHeldPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportRunDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportStatementWaitReadSupport;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * No metadata transaction of the export's ledger, and no batch of its chart audit, has a time
 * limit of its own any more (they had 15 s, which failed the export on a slow database): each is
 * opened without a timeout and runs as a write unit, which waits for the database and whose
 * observer (a renewal's lease) may bound even its first sending.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportLedgerTransactionUnitTest {
    private static final String RUN = "22222222-2222-4222-8222-222222222222";
    private final List<TransactionDefinition> opened = new ArrayList<>();
    private final List<String> asked = new ArrayList<>();
    private Runnable restore = () -> {};

    /** Records each transaction it is asked to open; every one ends at once. */
    private final PlatformTransactionManager manager =
            new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definition) {
                    opened.add(definition);
                    return new SimpleTransactionStatus();
                }

                @Override
                public void commit(TransactionStatus status) {
                    // Nothing to commit.
                }

                @Override
                public void rollback(TransactionStatus status) {
                    // Nothing to roll back.
                }
            };

    /** An observer as a renewal binds it: every question it is asked is recorded. */
    @BeforeEach
    void observeTheAttempt() {
        restore =
                CdsExportStatementWaitReadSupport.observe(
                        new CdsExportStatementWaitReadSupport.Observer() {
                            @Override
                            public long waiting(long waitedMillis) {
                                asked.add("waited " + waitedMillis);
                                return 0;
                            }

                            @Override
                            public long before() {
                                asked.add("before");
                                return 0;
                            }
                        });
    }

    @AfterEach
    void restoreThread() {
        restore.run();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    private static CdsExportLeaseDto lease() {
        var command =
                new CdsExportCommand(
                        "test",
                        "100",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        EnumSet.allOf(CdsCategory.class),
                        "recipient",
                        "audit");
        var document =
                new CdsExportJobDocumentDto(
                        1,
                        new CdsExportActorDto("operator", "100", null, null),
                        command,
                        "a".repeat(64),
                        "instance",
                        "b".repeat(64),
                        "c".repeat(64));
        return new CdsExportLeaseDto(RUN, 2, "33333333-3333-4333-8333-333333333333", document);
    }

    @Test
    void shouldOpenEveryLedgerTransactionWithoutATimeLimit_andRunItAsAWriteUnit() {
        var runs = mock(CdsExportRunDao.class);
        var ledger =
                new CdsExportLedgerService(
                        runs,
                        mock(CdsExportEventDao.class),
                        mock(CdsExportAttemptDao.class),
                        mock(CdsExportHeldPatientDao.class),
                        manager);

        // A read, a write, the diagnostic snapshot and the capture's own checkpoint: the job is
        // not found in any of them, so each opens its transaction and ends.
        assertThat(ledger.pending(5)).isEmpty();
        assertThatThrownBy(() -> ledger.rejectUnreadableIntent(RUN))
                .hasMessage("CDS_EXPORT_RUN_NOT_FOUND");
        assertThatThrownBy(() -> ledger.diagnostic(RUN)).hasMessage("CDS_EXPORT_RUN_NOT_FOUND");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThatThrownBy(() -> ledger.captureCheckpoint(lease()))
                .hasMessage("CDS_EXPORT_RUN_NOT_FOUND");

        assertThat(opened).hasSize(4);
        assertThat(opened)
                .allSatisfy(
                        definition ->
                                assertThat(definition.getTimeout())
                                        .isEqualTo(TransactionDefinition.TIMEOUT_DEFAULT));
        // The capture's checkpoint is a transaction of its own beside the capture's snapshot.
        assertThat(opened.get(3).getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Each was a write unit: its observer was asked before its first sending.
        assertThat(asked).containsExactly("before", "before", "before", "before");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWriteEachAuditBatchInItsOwnTransactionWithoutATimeLimit_asAWriteUnit() {
        var logs = mock(OscarLogDao.class);
        var managers = (ObjectProvider<PlatformTransactionManager>) mock(ObjectProvider.class);
        when(managers.getObject()).thenReturn(manager);
        var audit = new CdsExportAuditPersister(logs, managers);
        var lease = lease();

        audit.prepared(lease, List.of(27, 28));
        audit.adopted(lease, List.of(27), 1);
        audit.read(lease, List.of(29), CdsExportAuditPersister.ReadOutcome.HELD);

        assertThat(opened).hasSize(3);
        assertThat(opened)
                .allSatisfy(
                        definition -> {
                            assertThat(definition.getTimeout())
                                    .isEqualTo(TransactionDefinition.TIMEOUT_DEFAULT);
                            assertThat(definition.getPropagationBehavior())
                                    .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                        });
        assertThat(asked).containsExactly("before", "before", "before");
        verify(logs, times(4)).persist(any());
        verify(logs, times(3)).flush();
    }
}
