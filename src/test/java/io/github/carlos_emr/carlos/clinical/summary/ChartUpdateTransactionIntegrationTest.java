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
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.support.IntegrationTestSeedService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated H2 verification of the actual JPA record/link/receipt transaction, not an EMR database. */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChartUpdateTransactionIntegrationTest extends CarlosTestBase {
    private static final int PATIENT = 999887;
    @PersistenceContext private EntityManager em;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TicklerDao ticklerDao;
    @Autowired private CaseManagementManager caseManagementManager;
    private TransactionTemplate transactions;
    private ChartUpdateReceiptStore receipts;
    private ReviewedChartUpdateService service;
    private LoggedInInfo user;
    private ChartUpdateReview review;
    private ChartUpdateProposals.Proposal proposal;
    private CarlosProperties properties;
    private ChartUpdateContext.Snapshot snapshot;
    private ChartUpdateContext context;
    private final java.util.List<Issue> createdIssues = new java.util.ArrayList<>();

    @BeforeEach void configure() {
        transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(status -> {
            IntegrationTestSeedService.ensureProviderExists(em, "999998");
            IntegrationTestSeedService.ensureDemographicExists(em, PATIENT);
            em.createNativeQuery("INSERT INTO program (id, name) SELECT 10016, 'Synthetic program' WHERE NOT EXISTS (SELECT 1 FROM program WHERE id=10016)").executeUpdate();
            for (String section : ChartUpdateSections.CODES) {
                if (em.createQuery("from Issue where code = :code").setParameter("code", section).getResultList().isEmpty()) {
                    var issue = new Issue();
                    issue.setCode(section); issue.setDescription(section);
                    issue.setRole("doctor"); issue.setType("system");
                    em.persist(issue);
                    createdIssues.add(issue);
                }
            }
        });
        receipts = spy(new ChartUpdateReceiptStore());
        ReflectionTestUtils.setField(receipts, "entityManager", em);
        // H2 has transactional tables, but not MySQL's information_schema ENGINE column.
        // Production's fail-closed InnoDB guard is covered separately by unit tests.
        doNothing().when(receipts).requireTransactionalTables(anyBoolean(), anyBoolean());
        context = mock(ChartUpdateContext.class);
        snapshot = new ChartUpdateContext.Snapshot(42, PATIENT, "Synthetic patient", "Synthetic", "2026-09-28", "Review symptoms.",
                "source", "fresh", "10016", "1", List.of());
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        when(context.load(user, 42)).thenReturn(snapshot);
        var providers = mock(ProviderDao.class);
        var provider = new Provider();
        provider.setProviderNo("999998");
        provider.setFirstName("Synthetic");
        provider.setLastName("Provider");
        when(user.getLoggedInProvider()).thenReturn(provider);
        when(providers.getActiveProviders()).thenReturn(List.of(provider));
        var nativeTicklers = new TicklerManagerImpl();
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        ReflectionTestUtils.setField(nativeTicklers, "securityInfoManager", security);
        ReflectionTestUtils.setField(nativeTicklers, "ticklerDao", ticklerDao);
        var target = new ReviewedChartUpdateService(context, receipts, nativeTicklers, caseManagementManager, providers);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(interceptor);
        service = (ReviewedChartUpdateService) proxy.getProxy();
        proposal = new ChartUpdateProposals.Proposal("tickler", snapshot.source());
        review = new ChartUpdateReview("999998", snapshot, List.of(proposal));
        properties = mock(CarlosProperties.class);
        when(properties.getProperty(anyString(), eq("false"))).thenReturn("true");
    }

    @AfterEach void cleanSyntheticRows() {
        transactions.executeWithoutResult(status -> {
            em.createQuery("delete from TicklerLink where ticklerNo in (select id from Tickler where demographicNo = :patient)")
                    .setParameter("patient", PATIENT).executeUpdate();
            em.createQuery("delete from Tickler where demographicNo = :patient").setParameter("patient", PATIENT).executeUpdate();
            em.createQuery("delete from ChartUpdateReceipt where patient = :patient").setParameter("patient", PATIENT).executeUpdate();
            for (var note : em.createQuery("from CaseManagementNote where demographic_no = :patient", CaseManagementNote.class)
                    .setParameter("patient", String.valueOf(PATIENT)).getResultList()) {
                em.createQuery("delete from CaseManagementNoteLink where noteId = :id").setParameter("id", note.getId()).executeUpdate();
                em.createQuery("delete from HashAudit where type = :type and id2 = :id")
                        .setParameter("type", HashAudit.NOTE).setParameter("id", note.getId().toString()).executeUpdate();
                em.remove(note);
            }
            em.flush();
            em.createQuery("delete from CaseManagementIssue where demographic_no = :patient").setParameter("patient", PATIENT).executeUpdate();
            em.createQuery("delete from CasemgmtNoteLock where demographicNo = :patient").setParameter("patient", PATIENT).executeUpdate();
            // Bulk deletion leaves stale issue references in the persistence context.
            em.flush();
            em.clear();
            for (var issue : createdIssues) {
                var managed = em.find(Issue.class, issue.getId());
                if (managed != null) em.remove(managed);
            }
            createdIssues.clear();
        });
    }

    private ReviewedChartUpdateService.Result apply() { return apply("MedHistory"); }

    private ReviewedChartUpdateService.Result apply(String destination) {
        try (var settings = mockStatic(CarlosProperties.class); var logs = mockStatic(LogAction.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            return service.apply(user, review, review.getToken(), proposal.key(),
                    new ReviewedChartUpdateService.Approval("Review symptoms.", "2026-10-12", "999998", destination, true, "fresh"));
        }
    }

    @Test void shouldCommitTicklerLinkAndReceipt_withoutReplayDuplicates() {
        var result = apply();
        assertThat(result.replay()).isFalse();
        assertThat(apply()).isEqualTo(new ReviewedChartUpdateService.Result("tickler", result.target(), true));
        transactions.executeWithoutResult(status -> {
            assertThat(ticklerDao.findActiveByDemographicNo(PATIENT)).hasSize(1);
            assertThat(em.createQuery("select count(l) from TicklerLink l where l.ticklerNo = :id", Long.class)
                    .setParameter("id", (int) result.target()).getSingleResult()).isEqualTo(1);
            assertThat(receipts.find(review.receiptKey(proposal.key())).getTarget()).isEqualTo(result.target());
        });
    }

    @Test void shouldRejectSameReminderFromLaterSource_usingCommittedReceipt() {
        var first = apply();
        String recorded = transactions.execute(status -> {
            assertThat(receipts.hasProvenance(PATIENT, "tickler", first.target(), 42, proposal.evidence())).isTrue();
            assertThat(receipts.hasProvenance(PATIENT + 1, "tickler", first.target(), 42, proposal.evidence())).isFalse();
            assertThat(receipts.hasProvenance(PATIENT, "history", first.target(), 42, proposal.evidence())).isFalse();
            assertThat(receipts.hasProvenance(PATIENT, "tickler", first.target() + 1, 42, proposal.evidence())).isFalse();
            assertThat(receipts.hasProvenance(PATIENT, "tickler", first.target(), 43, proposal.evidence())).isFalse();
            assertThat(receipts.hasProvenance(PATIENT, "tickler", first.target(), 42, "Changed evidence")).isFalse();
            return em.find(Tickler.class, (int) first.target()).getMessage();
        });
        var later = new ChartUpdateContext.Snapshot(43, PATIENT, "Synthetic patient", "Later document", "2026-09-29",
                "Later source: review symptoms.", "different-source", "fresh", "10016", "1", List.of(
                        new ChartUpdateContext.Entry("tickler-" + first.target(), "tickler", recorded, recorded, "2026-10-12", "999998")));
        when(context.load(user, 43)).thenReturn(later);
        proposal = new ChartUpdateProposals.Proposal("tickler", later.source());
        review = new ChartUpdateReview("999998", later, List.of(proposal));
        assertThatThrownBy(this::apply).hasMessageContaining("already recorded");
        transactions.executeWithoutResult(status -> {
            assertThat(ticklerDao.findActiveByDemographicNo(PATIENT)).hasSize(1);
            assertThat(receipts.find(review.receiptKey(proposal.key()))).isNull();
        });
    }

    @Test void shouldRollBackRecordAndLink_whenReceiptFlushFails() {
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("Synthetic failure after receipt flush");
        }).when(receipts).save(any());
        assertThatThrownBy(this::apply).hasMessageContaining("Synthetic failure");
        transactions.executeWithoutResult(status -> {
            assertThat(ticklerDao.findActiveByDemographicNo(PATIENT)).isEmpty();
            assertThat(receipts.find(review.receiptKey(proposal.key()))).isNull();
            assertThat(em.createQuery("select count(l) from TicklerLink l where l.tableName = 'DOC' and l.tableId = 42", Long.class)
                    .getSingleResult()).isZero();
        });
    }

    private void prepareHistory() {
        proposal = new ChartUpdateProposals.Proposal("history", snapshot.source());
        review = new ChartUpdateReview("999998", snapshot, List.of(proposal));
        var session = new MockHttpSession();
        when(user.getSession()).thenReturn(session);
        transactions.executeWithoutResult(status -> {
            var lock = new CasemgmtNoteLock();
            lock.setDemographicNo(PATIENT);
            lock.setProviderNo("999998");
            lock.setSessionId(session.getId());
            em.persist(lock);
            em.flush();
            session.setAttribute("casemgmtNoteLock" + PATIENT, lock);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MedHistory", "Concerns", "SocHistory", "FamHistory", "RiskFactors", "OMeds", "Reminders"})
    void shouldCommitSignedHistory_withIssueLinkHashAndReceipt(String destination) {
        prepareHistory();
        var result = apply(destination);
        transactions.executeWithoutResult(status -> {
            var note = em.find(CaseManagementNote.class, result.target());
            assertThat(note.isSigned()).isTrue();
            assertThat(note.getIssues()).hasSize(1);
            assertThat(note.getIssues().iterator().next().getIssue().getCode()).isEqualTo(destination);
            assertThat(note.getNote()).contains("Reviewed source passage:");
            assertThat(em.createQuery("select count(l) from CaseManagementNoteLink l where l.noteId = :id", Long.class)
                    .setParameter("id", result.target()).getSingleResult()).isEqualTo(1);
            assertThat(em.createQuery("select count(h) from HashAudit h where h.type = :type and h.id2 = :id", Long.class)
                    .setParameter("type", HashAudit.NOTE).setParameter("id", String.valueOf(result.target())).getSingleResult()).isEqualTo(1);
            assertThat(receipts.find(review.receiptKey(proposal.key())).getTarget()).isEqualTo(result.target());
        });
    }

    @Test void shouldRollBackHistoryIssueLinkHashAndReceipt_onFailure() {
        prepareHistory();
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("Synthetic history failure after receipt flush");
        }).when(receipts).save(any());
        assertThatThrownBy(this::apply).hasMessageContaining("Synthetic history failure");
        transactions.executeWithoutResult(status -> {
            assertThat(em.createQuery("from CaseManagementNote where demographic_no = :patient")
                    .setParameter("patient", String.valueOf(PATIENT)).getResultList()).isEmpty();
            assertThat(em.createQuery("from CaseManagementIssue where demographic_no = :patient")
                    .setParameter("patient", PATIENT).getResultList()).isEmpty();
            assertThat(em.createQuery("from CaseManagementNoteLink where tableId = 42 and tableName = :type")
                    .setParameter("type", CaseManagementNoteLink.DOCUMENT).getResultList()).isEmpty();
            assertThat(receipts.find(review.receiptKey(proposal.key()))).isNull();
        });
    }
}
