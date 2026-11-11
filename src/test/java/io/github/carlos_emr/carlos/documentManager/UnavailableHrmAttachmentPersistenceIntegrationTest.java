/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("integration")
@Isolated("Verifies attachment transactions with strict-audit fault injection")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UnavailableHrmAttachmentPersistenceIntegrationTest extends CarlosTestBase {
    @Autowired ConsultationRequestDao consultations;
    @Autowired ConsultDocsDao attachments;
    @Autowired HRMDocumentDao reports;
    @Autowired EReferAttachmentDaoImpl packets;
    @Autowired EReferAttachmentDataDaoImpl packetData;
    @Autowired PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") EntityManager em;
    private UnavailableHrmAttachmentService service;
    private LoggedInInfo actor;
    private Integer parent;
    private Integer packet;
    private final List<Integer> protectedPackets = new ArrayList<>();
    private MockedStatic<LogAction> audit;

    @BeforeEach void setup() {
        protectedPackets.clear();
        audit = mockStatic(LogAction.class);
        actor = mock(LoggedInInfo.class); when(actor.getLoggedInProviderNo()).thenReturn("880007");
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(actor, "_con", "w", "880005")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(actor, 880005)).thenReturn(true);
        service = new UnavailableHrmAttachmentService(consultations, attachments, reports, security, transactions, packetData);
        transaction().executeWithoutResult(status -> {
            assertThat(reports.find(899991)).isNull(); assertThat(reports.find(899992)).isNull();
            var owner = new ConsultationRequest(); owner.setDemographicId(880005); owner.setProviderNo("880007");
            owner.setReferralDate(new Date()); owner.setReasonForReferral("Synthetic unavailable attachments");
            owner.setLastUpdateDate(new Date()); consultations.persist(owner); parent = owner.getId();
            attachments.persist(new ConsultDocs(parent, 899991, "H", "880007"));
            attachments.persist(new ConsultDocs(parent, 899992, "H", "880007"));
            var outgoing = new EReferAttachment(880005); packets.persist(outgoing); packet = outgoing.getId();
            packetData.persist(new EReferAttachmentData(outgoing, 899991, "H"));
            packetData.persist(new EReferAttachmentData(outgoing, 899992, "H"));
            for (int boundary = 0; boundary < 3; boundary++) {
                var retained = new EReferAttachment(boundary == 0 ? 880006 : 880005);
                retained.setArchived(boundary == 1);
                if (boundary == 2) retained.setCreated(new Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000L));
                packets.persist(retained); protectedPackets.add(retained.getId());
                packetData.persist(new EReferAttachmentData(retained, 899991, "H"));
            }
        });
    }
    @AfterEach void cleanup() {
        try {
            transaction().executeWithoutResult(status -> {
                if (parent != null) {
                    em.createQuery("DELETE FROM ConsultDocs d WHERE d.requestId=:id").setParameter("id", parent).executeUpdate();
                    consultations.remove(parent);
                }
                if (packet != null) {
                    em.createQuery("DELETE FROM EReferAttachmentData d WHERE d.eReferAttachment.id=:id").setParameter("id", packet).executeUpdate();
                    if (packets.find(packet) != null) packets.remove(packet);
                }
                for (int retained : protectedPackets) {
                    em.createQuery("DELETE FROM EReferAttachmentData d WHERE d.eReferAttachment.id=:id").setParameter("id", retained).executeUpdate();
                    packets.remove(retained);
                }
            });
        } finally {
            audit.close();
        }
    }
    private TransactionTemplate transaction() { return new TransactionTemplate(transactions); }

    @Test void shouldRemoveMissingRelationshipsAndPendingEntries_inOneCommittedTransaction() {
        var session = new MockHttpSession(); String token = service.prepare(actor, parent, session);
        service.remove(actor, token, session);
        transaction().executeWithoutResult(status -> {
            assertThat(attachments.findByRequestIdDocType(parent, "H")).isEmpty();
            assertThat(em.createQuery("SELECT d FROM EReferAttachmentData d WHERE d.eReferAttachment.id=:id")
                    .setParameter("id", packet).getResultList()).isEmpty();
            assertThat(em.createQuery("SELECT d FROM EReferAttachmentData d WHERE d.eReferAttachment.id IN :ids")
                    .setParameter("ids", protectedPackets).getResultList()).hasSize(3);
        });
    }
    @Test void shouldRestoreRelationshipsAndPacket_whenALaterStrictAuditFails() {
        var calls = new AtomicInteger();
        audit.when(() -> LogAction.addLogStrict(eq(actor), eq("DETACHED"), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> { if (calls.incrementAndGet() == 2) throw new IllegalStateException("synthetic audit failure"); return null; });
        var session = new MockHttpSession(); String token = service.prepare(actor, parent, session);
        assertThatThrownBy(() -> service.remove(actor, token, session)).isInstanceOf(IllegalStateException.class);
        transaction().executeWithoutResult(status -> {
            assertThat(attachments.findByRequestIdDocType(parent, "H")).hasSize(2);
            assertThat(packets.find(packet)).isNotNull();
            assertThat(em.createQuery("SELECT d FROM EReferAttachmentData d WHERE d.eReferAttachment.id=:id")
                    .setParameter("id", packet).getResultList()).hasSize(2);
        });
    }
}
