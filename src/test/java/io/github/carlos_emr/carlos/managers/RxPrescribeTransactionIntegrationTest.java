/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import java.util.List;
import java.util.UUID;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the real annotation-driven transaction boundary and committed JDBC state. */
@Tag("integration")
@Tag("manager")
class RxPrescribeTransactionIntegrationTest {
    @Test
    void shouldRollbackHeaderAndEarlierDrugs_whenLaterWriteReturnsNull() {
        exercise(false);
    }

    @Test
    void shouldCommitWholePrescription_whenAllWritesSucceed() {
        exercise(true);
    }

    private void exercise(boolean success) {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        try {
            jdbc.execute("CREATE TABLE write_receipt (kind VARCHAR(20))");
            RxManagerImpl target = new RxManagerImpl() {
                @Override protected void writeCheck(LoggedInInfo info, int id) { }
                @Override protected Boolean canPrescribe(Drug drug) { return true; }
                @Override public Drug addDrug(LoggedInInfo info, Drug drug) {
                    if (!success && "late".equals(drug.getGenericName())) return null;
                    jdbc.update("INSERT INTO write_receipt VALUES ('drug')");
                    return drug;
                }
                @Override public Drug updateDrug(LoggedInInfo info, Drug drug) {
                    throw new AssertionError("New medications must use the add path");
                }
            };
            target.prescriptionManager = mock(PrescriptionManager.class);
            when(target.prescriptionManager.createNewPrescription(any(), anyList(), anyInt())).thenAnswer(call -> {
                jdbc.update("INSERT INTO write_receipt VALUES ('header')");
                Prescription prescription = mock(Prescription.class);
                when(prescription.getId()).thenReturn(1);
                return prescription;
            });
            var transactions = new TransactionInterceptor(new DataSourceTransactionManager(source),
                    new AnnotationTransactionAttributeSource());
            var factory = new ProxyFactory(target);
            factory.addAdvice(transactions);
            RxManager proxy = (RxManager) factory.getProxy();
            Drug first = new Drug(), last = new Drug();
            first.setDemographicId(1);
            last.setDemographicId(1);
            first.setGenericName("first");
            last.setGenericName("late");
            if (success) {
                assertThat(proxy.prescribe(new LoggedInInfo(), List.of(first, last), 1)).isNotNull();
            } else {
                assertThatThrownBy(() -> proxy.prescribe(new LoggedInInfo(), List.of(first, last), 1))
                        .isInstanceOf(PrescriptionWriteException.class);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM write_receipt", Integer.class))
                    .isEqualTo(success ? 3 : 0);
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }
}
