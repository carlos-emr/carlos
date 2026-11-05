/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020Dao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020DataDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020TextDao;
import io.github.carlos_emr.carlos.form.model.FormBCAR2020;
import io.github.carlos_emr.carlos.form.model.FormBCAR2020Data;
import io.github.carlos_emr.carlos.form.model.FormBCAR2020Text;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Spring/JPA commits and failures across both 50-row batch flush/clear boundaries. */
@Tag("integration")
@Tag("form")
@ContextConfiguration(locations = "classpath:test-context-bcar2020.xml")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BCAR2020FormSaveTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private BCAR2020FormSaveService service;
    @Autowired private FormBCAR2020Dao forms;
    @Autowired private FormBCAR2020DataDao data;
    @Autowired private FormBCAR2020TextDao text;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    private TransactionTemplate tx;
    private int source;
    private LoggedInInfo actor;
    private BCAR2020FormSaveService target;
    private final Date originalEdit = new Date(1700000000000L);

    @BeforeEach
    void prepare() throws Exception {
        tx = new TransactionTemplate(transactions);
        target = AopTestUtils.getTargetObject(service);
        actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        source = tx.execute(status -> {
            FormBCAR2020 header = new FormBCAR2020();
            header.setDemographicNo(5299);
            header.setProviderNo("999997");
            forms.persist(header);
            // Different pages survive a page-one save and cross the batch clear boundary.
            for (int i = 0; i < 55; i++) {
                FormBCAR2020Data ordinary = new FormBCAR2020Data(header.getId(), "999997", 2, "t_fixture" + i, "ordinary" + i);
                ordinary.setFieldEdited(originalEdit);
                data.persist(ordinary);
                FormBCAR2020Text prose = new FormBCAR2020Text(header.getId(), "999997", 2, "mt_fixture" + i, "prose" + i);
                prose.setFieldEdited(originalEdit);
                text.persist(prose);
            }
            data.persist(new FormBCAR2020Data(header.getId(), "999997", 1, "c_removed", "X"));
            data.persist(new FormBCAR2020Data(header.getId(), "999997", 1, "t_changed", "before"));
            text.persist(new FormBCAR2020Text(header.getId(), "999997", 1, "mt_changed", "before"));
            return header.getId();
        });
    }

    @AfterEach
    void removeVersions() {
        ReflectionTestUtils.setField(target, "bcar2020DataDao", data);
        ReflectionTestUtils.setField(target, "bcar2020TextDao", text);
        tx.executeWithoutResult(status -> {
            List<Integer> ids = em.createQuery("SELECT f.formId FROM FormBCAR2020 f WHERE f.demographicNo = 5299", Integer.class).getResultList();
            for (Integer id : ids) {
                em.createQuery("DELETE FROM FormBCAR2020Data WHERE formId = :id").setParameter("id", id).executeUpdate();
                em.createQuery("DELETE FROM FormBCAR2020Text WHERE formId = :id").setParameter("id", id).executeUpdate();
                forms.remove(id);
            }
        });
    }

    private Map<String, String> fields() {
        return Map.of("t_changed", "after", "mt_changed", "after prose", "c_added", "checked", "t_patientSurname", "Synthetic");
    }

    private void assertPreviousVersion() {
        tx.executeWithoutResult(status -> {
            assertThat(forms.find(source).getProviderNo()).isEqualTo("999997");
            assertThat(data.findFields(source)).hasSize(57);
            assertThat(text.findFields(source)).hasSize(56);
            assertThat(data.findFieldForPage(source, 1, "t_changed").getValue()).isEqualTo("before");
            assertThat(text.findFields(source)).filteredOn(row -> row.getField().equals("mt_changed"))
                    .singleElement().satisfies(row -> assertThat(row.getValue()).isEqualTo("before"));
            assertThat(data.findFieldForPage(source, 2, "t_fixture0").getFieldEdited().getTime()).isEqualTo(originalEdit.getTime());
        });
    }

    @Test
    void shouldCommitBothFieldTablesAndKeepTheSource_whenSavingANewVersion() {
        FormBCAR2020 saved = service.saveVersion(actor, source, 5299, 1, fields());
        assertThat(saved.getId()).isNotEqualTo(source);
        tx.executeWithoutResult(status -> {
            assertThat(forms.find(saved.getId()).getProviderNo()).isEqualTo("999998");
            assertThat(data.findFields(saved.getId())).hasSize(58);
            assertThat(text.findFields(saved.getId())).hasSize(56);
            assertThat(data.findFieldForPage(saved.getId(), 1, "t_changed").getValue()).isEqualTo("after");
            assertThat(data.findFieldForPage(saved.getId(), 1, "c_removed")).isNull();
            assertThat(data.findFieldForPage(saved.getId(), 1, "c_added").getValue()).isEqualTo("X");
            assertThat(data.findFieldForPage(saved.getId(), 1, "t_patientSurname").getPageNo()).isZero();
            assertThat(data.findFieldForPage(saved.getId(), 2, "t_fixture0").getFieldEdited().getTime()).isEqualTo(originalEdit.getTime());
            assertThat(text.findFields(saved.getId())).filteredOn(row -> row.getField().equals("mt_changed"))
                    .singleElement().satisfies(row -> assertThat(row.getValue()).isEqualTo("after prose"));
        });
        assertPreviousVersion();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 49, 50, 51, 101})
    void shouldPreserveEveryFieldInBothTables_acrossBatchBoundaries(int size) {
        Map<String, String> submitted = new java.util.LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            submitted.put("t_batch" + i, "Écho 測定 " + i);
            submitted.put("mt_batch" + i, "Synthetic Unicode prose Écho 測定 " + i + "\n" + "z".repeat(4000));
        }
        FormBCAR2020 saved = service.saveVersion(actor, 0, 5299, 1, submitted);
        tx.executeWithoutResult(status -> {
            em.clear();
            assertThat(data.findFields(saved.getId())).hasSize(size).allSatisfy(row -> {
                assertThat(row.getValue()).isEqualTo(submitted.get(row.getField()));
                assertThat(row.getProviderNo()).isEqualTo("999998");
                assertThat(row.getPageNo()).isEqualTo(1);
            });
            assertThat(text.findFields(saved.getId())).hasSize(size).allSatisfy(row -> {
                assertThat(row.getValue()).isEqualTo(submitted.get(row.getField()));
                assertThat(row.getProviderNo()).isEqualTo("999998");
                assertThat(row.getPageNo()).isEqualTo(1);
            });
        });
        assertPreviousVersion();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldLeaveAllVersionsUntouched_whenSourceIsMissingOrForeign(boolean foreign) {
        assertThatThrownBy(() -> service.saveVersion(actor, foreign ? source : Integer.MAX_VALUE,
                foreign ? 5300 : 5299, 1, fields())).isInstanceOf(SecurityException.class);
        tx.executeWithoutResult(status -> {
            em.clear();
            assertThat(em.createQuery("SELECT COUNT(f) FROM FormBCAR2020 f WHERE f.demographicNo IN (5299, 5300)", Long.class)
                    .getSingleResult()).isEqualTo(1);
            assertThat(data.findFields(source)).hasSize(57);
            assertThat(text.findFields(source)).hasSize(56);
        });
        assertPreviousVersion();
    }

    @ParameterizedTest
    @ValueSource(strings = {"header", "ordinary", "afterOrdinary", "text", "commit"})
    void shouldRollBackTheEntireVersion_whenFailureOccursAtEachStage(String stage) throws Exception {
        FormBCAR2020DataDao failingData = mock(FormBCAR2020DataDao.class, delegatesTo(data));
        doAnswer(call -> {
            if (stage.equals("header")) throw new IllegalStateException("injected after header");
            if (stage.equals("ordinary")) {
                List<FormBCAR2020Data> rows = call.getArgument(0);
                data.batchPersistAtomically(rows.subList(0, 51), 50);
                throw new IllegalStateException("injected during ordinary persistence");
            }
            data.batchPersistAtomically(call.getArgument(0), 50);
            return null;
        }).when(failingData).batchPersistAtomically(anyList(), eq(50));
        FormBCAR2020TextDao failingText = mock(FormBCAR2020TextDao.class, delegatesTo(text));
        doAnswer(call -> {
            if (stage.equals("commit")) {
                text.batchPersistAtomically(call.getArgument(0), 50);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        throw new IllegalStateException("injected before commit");
                    }
                });
                return null;
            }
            if (stage.equals("text")) {
                List<FormBCAR2020Text> rows = call.getArgument(0);
                text.batchPersistAtomically(rows.subList(0, 51), 50);
            }
            throw new IllegalStateException("injected during text persistence");
        }).when(failingText).batchPersistAtomically(anyList(), eq(50));
        ReflectionTestUtils.setField(target, "bcar2020DataDao", failingData);
        ReflectionTestUtils.setField(target, "bcar2020TextDao", failingText);

        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class); var login = mockStatic(LoggedInInfo.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(actor);
            FrmBCAR20202Action action = new FrmBCAR20202Action();
            assertThatThrownBy(action::save).isInstanceOf(IllegalStateException.class);
            assertThat(response.isCommitted()).isFalse();
            assertThat(response.getRedirectedUrl()).isNull();
            assertThatThrownBy(action::saveAndExit).isInstanceOf(IllegalStateException.class);
        }
        tx.executeWithoutResult(status -> {
            assertThat(em.createQuery("SELECT COUNT(f) FROM FormBCAR2020 f WHERE f.demographicNo = 5299", Long.class).getSingleResult()).isEqualTo(1);
            assertThat(em.createQuery("SELECT COUNT(f) FROM FormBCAR2020Data f WHERE f.formId > :id", Long.class).setParameter("id", source).getSingleResult()).isZero();
            assertThat(em.createQuery("SELECT COUNT(f) FROM FormBCAR2020Text f WHERE f.formId > :id", Long.class).setParameter("id", source).getSingleResult()).isZero();
        });
        assertPreviousVersion();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/form/BCAR2020");
        request.addParameter("formId", Integer.toString(source));
        request.addParameter("demographicNo", "5299");
        request.addParameter("pageNo", "1");
        request.addParameter("forwardTo", "6");
        fields().forEach(request::addParameter);
        return request;
    }

    @Test
    void shouldNavigateOnlyAfterCommit_whenSavingOrClosing() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] committed = {false};
        FormBCAR2020TextDao observing = mock(FormBCAR2020TextDao.class, delegatesTo(text));
        doAnswer(call -> {
            text.batchPersistAtomically(call.getArgument(0), 50);
            assertThat(response.isCommitted()).isFalse();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committed[0] = true; }
            });
            return null;
        }).when(observing).batchPersistAtomically(anyList(), eq(50));
        ReflectionTestUtils.setField(target, "bcar2020TextDao", observing);
        try (var servlet = mockStatic(ServletActionContext.class); var login = mockStatic(LoggedInInfo.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(actor);
            FrmBCAR20202Action action = new FrmBCAR20202Action();
            assertThat(action.saveAndExit()).isEqualTo("exit");
            assertThat(committed[0]).isTrue();
            committed[0] = false;
            assertThat(action.save()).isEqualTo("none");
            assertThat(committed[0]).isTrue();
            assertThat(response.getRedirectedUrl()).startsWith("/form/formBCAR2020Attachments?demographic_no=5299&formId=");
        }
    }
}
