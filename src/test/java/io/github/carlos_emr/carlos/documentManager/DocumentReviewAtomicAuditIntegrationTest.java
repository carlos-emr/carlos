/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.log.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("integration")
@Tag("document")
@Isolated("Uses the legacy EDoc and audit DAO bindings while verifying real transaction atomicity")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DocumentReviewAtomicAuditIntegrationTest extends CarlosTestBase {
    @Autowired DocumentDao documents;
    @Autowired DocumentExtraReviewerDao extras;
    @Autowired CtlDocumentDao links;
    @Autowired OscarLogDao logs;
    @Autowired PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") EntityManager em;
    private Object previousDocuments;
    private Object previousLogs;
    private MockedStatic<IncomingDocumentCapacityResponse> access;
    private DocumentReviewService service;
    private LoggedInInfo actor;
    private Integer id;

    @BeforeEach void setup() {
        previousDocuments = ReflectionTestUtils.getField(EDocUtil.class, "documentDao");
        previousLogs = ReflectionTestUtils.getField(LogAction.class, "oscarLogDao");
        ReflectionTestUtils.setField(EDocUtil.class, "documentDao", documents);
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", logs);
        access = mockStatic(IncomingDocumentCapacityResponse.class);
        actor = new LoggedInInfo(); Provider provider = new Provider(); provider.setProviderNo("880007"); actor.setLoggedInProvider(provider);
        service = new DocumentReviewService(documents, extras, links, mock(SecurityInfoManager.class), transactions);
        id = transaction().execute(status -> {
            Provider legacyReviewer = new Provider(); legacyReviewer.setProviderNo("880003");
            legacyReviewer.setFirstName("Synthetic"); legacyReviewer.setLastName("Reviewer");
            legacyReviewer.setProviderType("doctor"); legacyReviewer.setSpecialty(""); legacyReviewer.setSex("M");
            legacyReviewer.setLastUpdateDate(new Date()); em.persist(legacyReviewer);
            Document document = new Document(); document.setStatus('H'); document.setDocxml("old content");
            document.setDocfilename("synthetic-inline.html"); document.setDocdesc("Synthetic atomic review"); document.setDoctype("Lab");
            document.setDoccreator("880001"); document.setResponsible("880001"); document.setContenttype("text/html");
            document.setReviewer("880001"); document.setReviewdatetime(new Date(1700000000000L));
            document.setNumberofpages(0); document.setRestrictToProgram(false); documents.persist(document);
            DocumentExtraReviewer additional = new DocumentExtraReviewer(); additional.setDocumentNo(document.getId());
            additional.setReviewerProviderNo("880002"); additional.setReviewDateTime(new Date(1700000001000L)); extras.persist(additional);
            em.persist(new DocumentReview(document.getId(), "880003", new Date(1700000002000L)));
            return document.getId();
        });
    }
    @AfterEach void cleanup() {
        try {
            ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", logs);
            if (id != null) transaction().executeWithoutResult(status -> {
                em.createQuery("DELETE FROM OscarLog l WHERE l.contentId=:id AND l.content=:content")
                        .setParameter("id", id.toString()).setParameter("content", LogConst.CON_DOCUMENT).executeUpdate();
                for (var review : extras.findByDocumentNo(id)) extras.remove(review.getId());
                documents.retireCurrentReviewRows(id);
                documents.remove(id);
                em.flush();
                em.remove(em.find(Provider.class, "880003"));
            });
        } finally {
            access.close();
            ReflectionTestUtils.setField(EDocUtil.class, "documentDao", previousDocuments);
            ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", previousLogs);
        }
    }
    private TransactionTemplate transaction() { return new TransactionTemplate(transactions); }
    private EDoc replacement() {
        EDoc edit = new EDoc("Synthetic atomic review", "Lab", "", "replacement", "880001", "880001", "", 'H', "", "forged", "2000/01/01 00:00:00", "provider", "880007");
        edit.setDocId(id.toString()); return edit;
    }
    private void replace() {
        service.edit(actor, replacement(), true, false, true, DocumentEditRevision.digest("old content"), "");
    }
    @Test void shouldCommitNewContentAndReview_withAnArchiveOfEveryPreviousAttestation() {
        replace();
        transaction().executeWithoutResult(status -> {
            Document current = documents.find(id);
            assertThat(current.getDocxml()).isEqualTo("replacement");
            assertThat(current.getReviewer()).isEqualTo("880007");
            assertThat(extras.findByDocumentNo(id)).isEmpty();
            assertThat(documents.findCurrentReviewRows(id)).isEmpty();
            var events = em.createQuery("SELECT l FROM OscarLog l WHERE l.contentId=:id AND l.content=:content", OscarLog.class)
                    .setParameter("id", id.toString()).setParameter("content", LogConst.CON_DOCUMENT).getResultList();
            assertThat(events.stream().filter(e -> "REVIEW_INVALIDATED".equals(e.getAction())).toList()).hasSize(3)
                    .allSatisfy(e -> assertThat(e.getData()).contains("contentRevision=" + DocumentEditRevision.digest("old content")));
            assertThat(events.stream().filter(e -> LogConst.REVIEWED.equals(e.getAction())).toList()).singleElement()
                    .satisfies(e -> assertThat(e.getProviderNo()).isEqualTo("880007"));
        });
    }
    @Test void shouldRollbackContentReviewsAndArchivalEvents_whenTheFinalReviewAuditFails() {
        var failing = mock(OscarLogDao.class);
        doAnswer(call -> {
            OscarLog event = call.getArgument(0);
            if (LogConst.REVIEWED.equals(event.getAction())) throw new PersistenceException("synthetic final audit failure");
            logs.persist(event); return null;
        }).when(failing).persist(any());
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", failing);
        assertThatThrownBy(this::replace).isInstanceOf(DocumentReviewService.SaveFailure.class)
                .satisfies(f -> assertThat(((DocumentReviewService.SaveFailure) f).rollbackConfirmed()).isTrue());
        transaction().executeWithoutResult(status -> {
            Document current = documents.find(id);
            assertThat(current.getDocxml()).isEqualTo("old content"); assertThat(current.getReviewer()).isEqualTo("880001");
            assertThat(extras.findByDocumentNo(id)).hasSize(1); assertThat(documents.findCurrentReviewRows(id)).hasSize(1);
            assertThat(em.createQuery("SELECT COUNT(l) FROM OscarLog l WHERE l.contentId=:id AND l.content=:content", Long.class)
                    .setParameter("id", id.toString()).setParameter("content", LogConst.CON_DOCUMENT).getSingleResult()).isZero();
        });
    }
}
