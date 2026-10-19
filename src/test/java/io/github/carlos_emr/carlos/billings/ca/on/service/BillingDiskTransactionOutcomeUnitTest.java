/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class BillingDiskTransactionOutcomeUnitTest {
    @Test
    void shouldCommitWholeGroup_whenFinalizationSucceeds() { exercise(false, false); }
    @Test
    void shouldRollBackWholeGroup_whenLaterWriterFails() { exercise(true, false); }
    @Test
    void shouldReportUncertainty_whenCommitAcknowledgementIsLost() { exercise(false, true); }

    private void exercise(boolean failWriter, boolean loseAcknowledgement) {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:billing-outcome-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE claims(id INT PRIMARY KEY, status VARCHAR(1))");
        jdbc.update("INSERT INTO claims VALUES(1,'O'),(2,'O')");
        var manager = new DataSourceTransactionManager(source) {
            @Override protected void doCommit(DefaultTransactionStatus status) {
                super.doCommit(status);
                if (loseAcknowledgement) throw new TransactionSystemException("lost commit acknowledgement");
            }
        };
        ProxyFactory factory = new ProxyFactory(new BillingOnDiskTransactionService());
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var service = (BillingOnDiskTransactionService) factory.getProxy();
        var first = mock(OhipClaimFileService.class);
        var second = mock(OhipClaimFileService.class);
        doAnswer(call -> { jdbc.update("UPDATE claims SET status='B' WHERE id=1"); return null; })
                .when(first).finalizeGeneratedDisk();
        doAnswer(call -> {
            jdbc.update("UPDATE claims SET status='B' WHERE id=2");
            if (failWriter) throw new IllegalStateException("later writer failed");
            return null;
        }).when(second).finalizeGeneratedDisk();
        var outcome = new BillingOnDiskTransactionService.Outcome();
        if (failWriter || loseAcknowledgement) {
            assertThatThrownBy(() -> service.finalizeGeneratedDisks(List.of(first, second), 1, outcome))
                    .isInstanceOf(RuntimeException.class);
        } else service.finalizeGeneratedDisks(List.of(first, second), 1, outcome);
        assertThat(outcome.mayHaveCommitted()).isEqualTo(!failWriter);
        assertThat(jdbc.queryForList("SELECT status FROM claims ORDER BY id", String.class))
                .containsExactly(failWriter ? "O" : "B", failWriter ? "O" : "B");
        jdbc.execute("SHUTDOWN");
    }
}
