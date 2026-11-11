// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExt;
import io.github.carlos_emr.carlos.commn.model.EReferAttachment;
import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses committed setup and fresh transactions so an audit failure must really roll back. */
@Tag("integration") @Tag("dao")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConsultationHrmAttachmentPersisterIntegrationTest extends CarlosTestBase {
    @Autowired private ConsultationRequestDao requests;
    @Autowired private ConsultDocsDao attachments;
    @Autowired private ConsultationRequestExtDao extensions;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    private TransactionTemplate tx;
    private ConsultationHrmAttachmentPersister service;
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private int requestId;
    private int selectedId;
    private int duplicateId;
    private int otherId;
    private int patient;
    private int queueId;
    private int foreignQueueId;
    private Object originalQueueDataDao;
    private Object originalQueueDao;

    @BeforeEach
    void seedCommittedRelationships() {
        tx = new TransactionTemplate(transactions);
        // The legacy utility caches its context once. Other suites install static mocks;
        // bind this test's real transactional DAOs and restore the previous context afterwards.
        Class<?> ocean = io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil.OceanEReferralAttachmentUtil.class;
        originalQueueDataDao = org.springframework.test.util.ReflectionTestUtils.getField(ocean, "eReferAttachmentDataDao");
        originalQueueDao = org.springframework.test.util.ReflectionTestUtils.getField(ocean, "eReferAttachmentDao");
        org.springframework.test.util.ReflectionTestUtils.setField(ocean, "eReferAttachmentDataDao",
                applicationContext.getBean(io.github.carlos_emr.carlos.commn.dao.EReferAttachmentDataDaoImpl.class));
        org.springframework.test.util.ReflectionTestUtils.setField(ocean, "eReferAttachmentDao",
                applicationContext.getBean(io.github.carlos_emr.carlos.commn.dao.EReferAttachmentDaoImpl.class));
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(info), eq("_con"), eq("w"), anyInt())).thenReturn(true);
        when(security.hasPrivilege(info, "_hrm", "r", (String) null)).thenReturn(true);
        var target = new ConsultationHrmAttachmentPersister(requests, attachments, extensions, security);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (ConsultationHrmAttachmentPersister) proxy.getProxy();
        tx.executeWithoutResult(status -> {
            var request = new ConsultationRequest(); request.setDemographicId(9705756);
            requests.persist(request); requestId = request.getId(); patient = 9705756 + requestId;
            request.setDemographicId(patient); requests.merge(request);
            selectedId = attachment(128); duplicateId = attachment(128); otherId = attachment(129);
            var ext = new ConsultationRequestExt(); ext.setRequestId(requestId);
            ext.setKey("ereferral_ref"); ext.setValue("synthetic"); ext.setDateCreated(new java.util.Date()); extensions.persist(ext);
            queueId = queue(patient); foreignQueueId = queue(patient + 1);
            em.flush();
        });
    }

    private int attachment(int document) {
        var row = new ConsultDocs(requestId, document, ConsultDocs.DOCTYPE_HRM, "999998");
        attachments.persist(row); return row.getId();
    }

    private int queue(int demographic) {
        var queue = new EReferAttachment(demographic);
        queue.setAttachments(List.of(new EReferAttachmentData(queue, 128, ConsultDocs.DOCTYPE_HRM),
                new EReferAttachmentData(queue, 129, ConsultDocs.DOCTYPE_HRM)));
        em.persist(queue); return queue.getId();
    }

    @AfterEach
    void removeCommittedFixtures() {
        Class<?> ocean = io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil.OceanEReferralAttachmentUtil.class;
        org.springframework.test.util.ReflectionTestUtils.setField(ocean, "eReferAttachmentDataDao", originalQueueDataDao);
        org.springframework.test.util.ReflectionTestUtils.setField(ocean, "eReferAttachmentDao", originalQueueDao);
        tx.executeWithoutResult(status -> {
            em.createQuery("delete from EReferAttachmentData d where d.eReferAttachment.id in :ids")
                    .setParameter("ids", List.of(queueId, foreignQueueId)).executeUpdate();
            em.createQuery("delete from EReferAttachment q where q.id in :ids")
                    .setParameter("ids", List.of(queueId, foreignQueueId)).executeUpdate();
            extensions.clear(requestId);
            attachments.findByRequestId(requestId).forEach(row -> attachments.remove(row.getId()));
            requests.remove(requestId);
        });
    }

    private List<String> deletedFlags() {
        return tx.execute(status -> java.util.stream.Stream.of(selectedId, duplicateId, otherId)
                .map(id -> java.util.Objects.toString(attachments.find(id).getDeleted(), "active")).toList());
    }

    private List<Integer> queuedDocuments(int id) {
        return tx.execute(status -> em.createQuery(
                "select d.labId from EReferAttachmentData d where d.eReferAttachment.id = :id order by d.labId", Integer.class)
                .setParameter("id", id).getResultList());
    }

    @Test
    void shouldRollbackRelationshipsAndPendingQueue_whenStrictAuditFails() {
        try (var audit = mockStatic(LogAction.class)) {
            audit.when(() -> LogAction.addLogStrict(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new IllegalStateException("synthetic audit failure"));
            assertThatThrownBy(() -> service.detach(info, requestId, selectedId)).isInstanceOf(IllegalStateException.class);
            assertThat(deletedFlags()).containsExactly("active", "active", "active");
            assertThat(queuedDocuments(queueId)).containsExactly(128, 129);
            assertThat(queuedDocuments(foreignQueueId)).containsExactly(128, 129);
        }
    }

    @Test
    void shouldCommitDuplicatesAndPatientScopedQueue_whenExplicitDetachSucceeds() {
        boolean recentQueueExists = tx.execute(status -> applicationContext.getBean(
                io.github.carlos_emr.carlos.commn.dao.EReferAttachmentDataDaoImpl.class)
                .getRecentByDocumentId(128, ConsultDocs.DOCTYPE_HRM, patient,
                        new java.util.Date(System.currentTimeMillis() - 3600000)) != null);
        assertThat(recentQueueExists).isTrue();
        boolean oceanContextExists = tx.execute(status -> extensions.getConsultationRequestExts(requestId).stream()
                .anyMatch(ext -> "ereferral_ref".equals(ext.getKey())));
        assertThat(oceanContextExists).isTrue();
        try (var audit = mockStatic(LogAction.class)) {
            service.detach(info, requestId, selectedId);
            assertThat(deletedFlags()).containsExactly("Y", "Y", "active");
            assertThat(queuedDocuments(queueId)).containsExactly(129);
            assertThat(queuedDocuments(foreignQueueId)).containsExactly(128, 129);
            service.detach(info, requestId, selectedId);
            audit.verify(() -> LogAction.addLogStrict(info, "delete", "consultation HRM attachment",
                    Integer.toString(selectedId), Integer.toString(patient), "explicit detach"), times(1));
            service.detach(info, requestId, otherId);
            long remaining = tx.execute(status -> em.createQuery(
                    "SELECT COUNT(q) FROM EReferAttachment q WHERE q.id = :id", Long.class)
                    .setParameter("id", queueId).getSingleResult());
            assertThat(remaining).isZero();
            assertThat(queuedDocuments(foreignQueueId)).containsExactly(128, 129);
        }
    }
}
