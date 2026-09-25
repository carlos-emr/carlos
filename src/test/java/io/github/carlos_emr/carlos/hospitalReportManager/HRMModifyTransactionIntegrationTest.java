/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.hospitalReportManager;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentSubClassDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentSubClass;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToProvider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Real commits and rollback, with failures injected only after a database write has flushed. */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HRMModifyTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private HRMDocumentDao documents;
    @Autowired private HRMDocumentToProviderDao routes;
    @Autowired private HRMDocumentSubClassDao subclasses;
    @Autowired private HRMDocumentToDemographicDao demographics;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;

    private TransactionTemplate tx;
    private Integer reportId;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private HRMModifyDocument2Action action;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactions);
        reportId = tx.execute(status -> {
            HRMDocument document = new HRMDocument();
            document.setDescription("PR3832 synthetic transaction fixture");
            documents.persist(document);
            return document.getId();
        });
        request = new MockHttpServletRequest("POST", "/hospitalReportManager/Modify");
        request.addParameter("reportId", reportId.toString());
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(user), eq("_hrm"), anyString(), isNull())).thenReturn(true);
        action = new HRMModifyDocument2Action();
        ReflectionTestUtils.setField(action, "securityInfoManager", security);
    }

    @AfterEach
    void tearDown() {
        if (servlet != null) servlet.close();
        if (login != null) login.close();
        if (reportId != null) {
            tx.executeWithoutResult(status -> {
                for (String entity : new String[] {"HRMDocumentToProvider", "HRMDocumentSubClass", "HRMDocumentToDemographic"}) {
                    em.createQuery("DELETE FROM " + entity + " WHERE hrmDocumentId = :id")
                            .setParameter("id", reportId).executeUpdate();
                }
                documents.remove(reportId);
            });
        }
    }

    private void addRoutingRows() {
        tx.executeWithoutResult(status -> {
            for (int i = 0; i < 2; i++) {
                HRMDocumentToProvider row = new HRMDocumentToProvider();
                row.setHrmDocumentId(reportId);
                row.setProviderNo("999998");
                row.setSignedOff(0);
                routes.persist(row);
            }
        });
    }

    @Test
    void shouldRollBackAllRoutingRows_whenSecondWriteFails() throws Exception {
        addRoutingRows();
        HRMDocumentToProviderDao failing = mock(HRMDocumentToProviderDao.class, delegatesTo(routes));
        AtomicInteger writes = new AtomicInteger();
        doAnswer(call -> {
            routes.merge(call.getArgument(0));
            em.flush();
            if (writes.incrementAndGet() == 2) throw new IllegalStateException("injected second write failure");
            return null;
        }).when(failing).merge(any(HRMDocumentToProvider.class));
        action.hrmDocumentToProviderDao = failing;
        request.addParameter("method", "signOff");
        request.addParameter("signedOff", "1");

        action.execute();

        assertThat(writes).hasValue(2);
        assertThat(response.getContentAsString()).contains("\"success\":false", "\"clearedCount\":0");
        tx.executeWithoutResult(status -> assertThat(routes.findByHrmDocumentId(reportId))
                .hasSize(2).allSatisfy(row -> assertThat(row.getSignedOff()).isZero()));
    }

    @Test
    void shouldCountCommittedTransitions_whenSigningOffDuplicateRows() throws Exception {
        addRoutingRows();
        request.addParameter("method", "signOff");
        request.addParameter("signedOff", "1");
        action.execute();
        assertThat(response.getContentAsString()).contains("\"success\":true", "\"clearedCount\":2");
        tx.executeWithoutResult(status -> assertThat(routes.findByHrmDocumentId(reportId))
                .hasSize(2).allSatisfy(row -> assertThat(row.getSignedOff()).isEqualTo(1)));
        response = new MockHttpServletResponse();
        action.response = response;
        action.execute();
        assertThat(response.getContentAsString()).contains("\"success\":true", "\"clearedCount\":0");
    }

    private int addSubclass(boolean active) {
        return tx.execute(status -> {
            HRMDocumentSubClass row = new HRMDocumentSubClass();
            row.setHrmDocumentId(reportId);
            row.setActive(active);
            subclasses.persist(row);
            return row.getId();
        });
    }

    @Test
    void shouldPreserveActiveSubclass_whenActivationFailsAfterDeactivation() throws Exception {
        int original = addSubclass(true);
        int target = addSubclass(false);
        HRMDocumentSubClassDao failing = mock(HRMDocumentSubClassDao.class, delegatesTo(subclasses));
        doAnswer(call -> {
            subclasses.merge(call.getArgument(0));
            em.flush();
            throw new IllegalStateException("injected activation failure");
        }).when(failing).merge(any(HRMDocumentSubClass.class));
        action.hrmDocumentSubClassDao = failing;
        request.addParameter("method", "makeActiveSubClass");
        request.addParameter("subClassId", Integer.toString(target));

        action.execute();

        assertThat(response.getContentAsString()).contains("\"success\":false");
        tx.executeWithoutResult(status -> assertThat(subclasses.getActiveSubClassesByDocumentId(reportId))
                .extracting(HRMDocumentSubClass::getId).containsExactly(original));
    }

    @Test
    void shouldKeepSubclassActive_whenSelectingAlreadyActiveTarget() throws Exception {
        int target = addSubclass(true);
        addSubclass(false);
        request.addParameter("method", "makeActiveSubClass");
        request.addParameter("subClassId", Integer.toString(target));

        action.execute();

        assertThat(response.getContentAsString()).contains("\"success\":true");
        tx.executeWithoutResult(status -> assertThat(subclasses.getActiveSubClassesByDocumentId(reportId))
                .extracting(HRMDocumentSubClass::getId).containsExactly(target));
    }

    @Test
    void shouldRestorePatientLink_whenReplacementWriteFails() throws Exception {
        tx.executeWithoutResult(status -> {
            HRMDocumentToDemographic row = new HRMDocumentToDemographic();
            row.setHrmDocumentId(reportId);
            row.setDemographicNo(1);
            demographics.persist(row);
        });
        HRMDocumentToDemographicDao failing = mock(HRMDocumentToDemographicDao.class, delegatesTo(demographics));
        doAnswer(call -> {
            em.flush();
            throw new IllegalStateException("injected patient replacement failure");
        }).when(failing).merge(any(HRMDocumentToDemographic.class));
        action.hrmDocumentToDemographicDao = failing;
        request.addParameter("method", "assignDemographic");
        request.addParameter("demographicNo", "2");

        action.execute();

        assertThat(response.getContentAsString()).contains("\"success\":false");
        tx.executeWithoutResult(status -> assertThat(demographics.findByHrmDocumentId(reportId))
                .extracting(HRMDocumentToDemographic::getDemographicNo).containsExactly(1));
    }
}
