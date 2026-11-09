/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.PrescriptionManager;
import io.github.carlos_emr.carlos.managers.RxManagerImpl;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RxSaveReceiptCommitIntegrationTest.Config.class)
@Tag("integration") @Tag("mariadb-native") @Tag("prescription")
class RxRestPrescriptionRollbackIntegrationTest extends CarlosUnitTestBase {
    @Autowired private DrugDao drugs;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager em;
    static class IsolatedRxManager extends RxManagerImpl {
        IsolatedRxManager(DrugDao drugs, PrescriptionManager headers) { this.drugDao=drugs; this.prescriptionManager=headers; }
        @Override protected void writeCheck(LoggedInInfo info, int patient) { }
    }
    @Test void shouldRollbackHeaderAndEarlierDrugs_whenSecondRestDrugIsRefused() {
        var headers=mock(PrescriptionManager.class);
        when(headers.createNewPrescription(any(),anyList(),eq(1007))).thenAnswer(invocation->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            var header=new Prescription(); header.setProviderNo("999998"); header.setDemographicId(1007); em.persist(header); return header;
        });
        var observed=mock(DrugDao.class,org.mockito.AdditionalAnswers.delegatesTo(drugs));
        var count=new AtomicInteger();
        when(observed.addNewDrug(any())).thenAnswer(invocation->{
            if(count.incrementAndGet()==2) {
                assertThat(em.createQuery("select count(d) from Drug d where d.demographicId=1007",Long.class).getSingleResult()).isEqualTo(1L);
                return false;
            }
            return drugs.addNewDrug(invocation.getArgument(0));
        });
        var factory=new ProxyFactory(new IsolatedRxManager(observed,headers)); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        var manager=(RxManagerImpl)factory.getProxy();
        var first=drug("Synthetic REST one"); var second=drug("Synthetic REST two");
        var info=mock(LoggedInInfo.class); when(info.getLoggedInProviderNo()).thenReturn("999998");
        assertThatThrownBy(()->manager.prescribe(info,List.of(first,second),1007)).isInstanceOf(IllegalStateException.class);
        assertThat(count.get()).isEqualTo(2);
        var tx=new TransactionTemplate(transactions);
        Long remainingDrugs=tx.execute(status->em.createQuery("select count(d) from Drug d where d.demographicId=1007",Long.class).getSingleResult());
        Long remainingHeaders=tx.execute(status->em.createQuery("select count(p) from Prescription p where p.demographicId=1007",Long.class).getSingleResult());
        assertThat(remainingDrugs).isZero(); assertThat(remainingHeaders).isZero();
    }
    private static Drug drug(String name) {
        var drug=new Drug(); drug.setProviderNo("999998"); drug.setDemographicId(1007); drug.setBrandName(name);
        drug.setSpecial("1 tab PO OD"); drug.setRxDate(java.sql.Date.valueOf("2026-01-01")); drug.setEndDate(java.sql.Date.valueOf("2026-02-01"));
        return drug;
    }
}
