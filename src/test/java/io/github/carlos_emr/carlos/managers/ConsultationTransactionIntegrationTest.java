/* Copyright (c) 2026 CARLOS Contributors. Published under the GPL GNU General Public License. */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real DAO transactions verify rollback across request, extras and archive writes. */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConsultationTransactionIntegrationTest extends CarlosTestBase {
    private static final int DEMOGRAPHIC = 999886;
    @PersistenceContext private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ConsultRequestDao requestDao;
    @Autowired private ConsultationRequestExtDao extDao;
    @Autowired private ConsultationRequestArchiveDao archiveDao;
    @Autowired private ConsultationRequestExtArchiveDao archiveExtDao;
    @Autowired private ProfessionalSpecialistDao specialistDao;
    private ConsultationManagerImpl target;
    private ConsultationManager service;
    private TransactionTemplate transactions;
    private LoggedInInfo login;

    @BeforeEach
    void configureManager() {
        transactions = new TransactionTemplate(transactionManager);
        target = new ConsultationManagerImpl();
        target.consultationRequestDao = requestDao;
        target.consultationRequestExtDao = extDao;
        target.consultationRequestArchiveDao = archiveDao;
        target.consultationRequestExtArchiveDao = archiveExtDao;
        target.professionalSpecialistDao = specialistDao;
        target.securityInfoManager = mock(SecurityInfoManager.class);
        when(target.securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        login = mock(LoggedInInfo.class);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(interceptor);
        service = (ConsultationManager) proxy.getProxy();
    }

    @AfterEach
    void removeSyntheticRows() {
        transactions.executeWithoutResult(status -> {
            entityManager.createQuery("delete from ConsultationRequestExtArchive where requestId in "
                    + "(select id from ConsultationRequest where demographicId = :demo)")
                    .setParameter("demo", DEMOGRAPHIC).executeUpdate();
            entityManager.createQuery("delete from ConsultationRequestArchive where demographicId = :demo")
                    .setParameter("demo", DEMOGRAPHIC).executeUpdate();
            entityManager.createQuery("delete from ConsultationRequestExt where requestId in "
                    + "(select id from ConsultationRequest where demographicId = :demo)")
                    .setParameter("demo", DEMOGRAPHIC).executeUpdate();
            entityManager.createQuery("delete from ConsultationRequest where demographicId = :demo")
                    .setParameter("demo", DEMOGRAPHIC).executeUpdate();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 26})
    void shouldRollbackNewRequestAndExtras_whenSaveFailsAfterBatch(int count) {
        ConsultationRequest request = requestWithExtras(count);
        try (var logs = mockStatic(LogAction.class)) {
            logs.when(() -> LogAction.addLogSynchronous(any(), eq("ConsultationManager.saveConsultationRequest"), anyString()))
                    .thenThrow(new IllegalStateException("synthetic failure after batch"));
            assertThatThrownBy(() -> service.saveConsultationRequest(login, request))
                    .isInstanceOf(IllegalStateException.class);
        }
        transactions.executeWithoutResult(status -> {
            assertThat(requestDao.find(request.getId())).isNull();
            assertThat(extDao.getConsultationRequestExts(request.getId())).isEmpty();
        });
    }

    @Test
    void shouldCommitRequestAndExtrasTogether_whenSaveSucceeds() {
        ConsultationRequest request = requestWithExtras(26);
        try (var logs = mockStatic(LogAction.class)) {
            service.saveConsultationRequest(login, request);
        }
        transactions.executeWithoutResult(status -> {
            assertThat(requestDao.find(request.getId()).getReasonForReferral()).isEqualTo("Synthetic referral");
            assertThat(extDao.getConsultationRequestExts(request.getId())).hasSize(26);
        });
    }

    @Test
    void shouldRollbackArchiveAndExtras_whenArchiveChildWriteFails() {
        ConsultationRequest request = requestWithExtras(1);
        try (var logs = mockStatic(LogAction.class)) {
            service.saveConsultationRequest(login, request);
        }
        var failingArchiveExtDao = mock(ConsultationRequestExtArchiveDao.class);
        doAnswer(invocation -> {
            archiveExtDao.persist(invocation.getArgument(0));
            throw new IllegalStateException("synthetic failure after archive child write");
        }).when(failingArchiveExtDao).persist(any());
        target.consultationRequestExtArchiveDao = failingArchiveExtDao;
        Integer requestId = request.getId();
        assertThatThrownBy(() -> service.archiveConsultationRequest(requestId))
                .isInstanceOf(IllegalStateException.class);
        transactions.executeWithoutResult(status -> {
            assertThat(entityManager.createQuery("select count(a) from ConsultationRequestArchive a "
                    + "where a.demographicId = :demo", Long.class).setParameter("demo", DEMOGRAPHIC).getSingleResult()).isZero();
            assertThat(entityManager.createQuery("select count(a) from ConsultationRequestExtArchive a "
                    + "where a.requestId = :id", Long.class).setParameter("id", request.getId()).getSingleResult()).isZero();
            assertThat(requestDao.find(request.getId())).isNotNull();
        });
    }

    @Test
    void shouldRollbackExistingRequestAndMixedExtras_whenUpdateFails() {
        ConsultationRequest request = requestWithExtras(1);
        try (var logs = mockStatic(LogAction.class)) {
            service.saveConsultationRequest(login, request);
        }
        request.setReasonForReferral("Changed referral");
        request.getExtras().get(0).setValue("changed");
        var extra = new ConsultationRequestExt();
        extra.setKey("new-key");
        extra.setValue("new-value");
        var extras = new ArrayList<>(request.getExtras());
        extras.add(extra);
        request.setExtras(extras);
        try (var logs = mockStatic(LogAction.class)) {
            logs.when(() -> LogAction.addLogSynchronous(any(), eq("ConsultationManager.saveConsultationRequest"), anyString()))
                    .thenThrow(new IllegalStateException("synthetic update failure"));
            assertThatThrownBy(() -> service.saveConsultationRequest(login, request))
                    .isInstanceOf(IllegalStateException.class);
        }
        transactions.executeWithoutResult(status -> {
            assertThat(requestDao.find(request.getId()).getReasonForReferral()).isEqualTo("Synthetic referral");
            assertThat(extDao.getConsultationRequestExts(request.getId())).singleElement()
                    .satisfies(saved -> assertThat(saved.getValue()).isEqualTo("value"));
        });
    }

    private ConsultationRequest requestWithExtras(int count) {
        ConsultationRequest request = new ConsultationRequest();
        request.setDemographicId(DEMOGRAPHIC);
        request.setReferralDate(new Date());
        request.setReasonForReferral("Synthetic referral");
        List<ConsultationRequestExt> extras = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ConsultationRequestExt extra = new ConsultationRequestExt();
            extra.setKey("synthetic-" + i);
            extra.setValue("value");
            extras.add(extra);
        }
        request.setExtras(extras);
        return request;
    }
}
