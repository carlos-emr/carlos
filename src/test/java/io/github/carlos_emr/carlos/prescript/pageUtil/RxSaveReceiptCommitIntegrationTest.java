/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real JPA + JDBC COMMIT with a lost acknowledgement, including durable receipt/audit recovery. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RxSaveReceiptCommitIntegrationTest.Config.class)
@Tag("integration") @Tag("mariadb-native") @Tag("prescription")
class RxSaveReceiptCommitIntegrationTest extends CarlosUnitTestBase {
    @Autowired private DataSource source;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private RxSubmissionReceiptService receipts;
    @Autowired private DrugDao drugs;
    @Autowired private PartialDateDao partialDates;
    @Autowired private OscarLogDao audit;
    @Autowired private CommitFault fault;
    @PersistenceContext private EntityManager em;

    @BeforeEach void prepare() throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of(
                "database/mysql/migration/common/V1.0.59.38__rx_submission_receipts.sql"));
        try (var connection = source.getConnection()) {
            if (connection.getMetaData().getDatabaseProductName().contains("H2")) {
                sql = sql.replace(" CHARACTER SET ascii COLLATE ascii_bin", "")
                        .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci", "");
            }
        }
        new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(source);
        new JdbcTemplate(source).update("DELETE FROM rx_submission_receipt");
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            em.createQuery("delete from OscarLog").executeUpdate();
            em.createQuery("delete from PartialDate").executeUpdate();
            em.createQuery("delete from Drug").executeUpdate();
            em.createQuery("delete from Prescription").executeUpdate();
        });
        logActionMock.close(); logActionMock = null;
        setAudit(audit);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(RxManager.class, mock(RxManager.class));
        registerMock(DrugDao.class, drugs);
        registerMock(PartialDateDao.class, partialDates);
        registerMock(PlatformTransactionManager.class, transactions);
        registerMock(RxSubmissionReceiptService.class, receipts);
    }
    @AfterEach void resetFault() { fault.remaining.set(0); fault.commitBeforeThrow = true; setAudit(null); }

    private static void setAudit(OscarLogDao dao) {
        try {
            var setter = LogAction.class.getDeclaredMethod("setOscarLogDaoForTesting", OscarLogDao.class);
            setter.setAccessible(true); setter.invoke(null, dao);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"lost-ack", "audit-failure", "failed-commit", "intent-rollback", "intent-lost-ack", "begin-failure", "rollback-only"})
    void shouldRecoverReceiptAndAudit_whenAcknowledgementOrAuditFails(String scenario) throws Exception {
        boolean intentFailure = scenario.startsWith("intent-");
        boolean beginFailure = "begin-failure".equals(scenario);
        boolean auditFailure = "audit-failure".equals(scenario);
        boolean rollbackOnly = "rollback-only".equals(scenario);
        boolean failedCommit = "failed-commit".equals(scenario);
        var bean = new RxSessionBean(); bean.setProviderNo("999998"); bean.setDemographicNo(1001);
        var card = new RxPrescriptionData.Prescription(0, "999998", 1001);
        card.setRandomId(123); card.setGCN_SEQNO("0"); card.setBrandName("Synthetic receipt medication");
        card.setSpecial("1 tab PO OD"); card.setRxDate(java.sql.Date.valueOf("2026-01-01"));
        bean.getStashList().add(card);
        String originalRevision = card.getDraftRevision();
        String token = RxSubmissionReceiptService.fingerprint(java.util.Map.of("123", originalRevision));
        var request = new MockHttpServletRequest("POST", "/rx/WriteScript");
        request.addParameter("demographicNo", "1001"); request.addParameter("draftRevision_123", originalRevision);
        var response = new MockHttpServletResponse();
        var info = mock(LoggedInInfo.class); when(info.getLoggedInProviderNo()).thenReturn("999998");
        var security = mock(SecurityInfoManager.class); registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(eq(info), eq("_rx"), anyString(), isNull())).thenReturn(true);
        when(security.hasPrivilege(eq(info), eq("_rx"), anyString(), eq(1001))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(info, 1001)).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class);
             var session = mockStatic(LoggedInInfo.class);
             var headers = mockConstruction(RxPrescriptionData.class, (data, context) ->
                     when(data.saveScript(any(), any())).thenAnswer(invocation -> {
                         var header = new Prescription(); header.setProviderNo("999998"); header.setDemographicId(1001);
                         em.persist(header); return header.getId().toString();
                     }))) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            var action = new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class));
            if (beginFailure) {
                var beginFault = mock(PlatformTransactionManager.class);
                when(beginFault.getTransaction(any())).thenThrow(new org.springframework.transaction.CannotCreateTransactionException("Synthetic begin failure"));
                registerMock(PlatformTransactionManager.class, beginFault);
            } else if (auditFailure || rollbackOnly) {
                var failingAudit = mock(OscarLogDao.class, org.mockito.AdditionalAnswers.delegatesTo(audit));
                doAnswer(invocation -> {
                    OscarLog row = invocation.getArgument(0);
                    if ("add".equals(row.getAction())) {
                        if (auditFailure) throw new IllegalStateException("Synthetic authoritative audit failure");
                        new TransactionTemplate(transactions).executeWithoutResult(status -> status.setRollbackOnly());
                    }
                    audit.persist(row); return null;
                }).when(failingAudit).persist(any(OscarLog.class));
                setAudit(failingAudit);
            } else {
                fault.commitBeforeThrow = !failedCommit && !"intent-rollback".equals(scenario);
                fault.remaining.set(intentFailure ? 1 : 2); // Intent commits normally; clinical COMMIT then loses its acknowledgement.
            }
            assertThatThrownBy(() -> action.persistStash(info, bean)).isInstanceOf(RxWriteScript2Action.PrescriptionSaveException.class);
            if (intentFailure || beginFailure) {
                assertThat(request.getAttribute("RX_SAVE_OUTCOME_UNKNOWN")).isNull();
                assertThat(card.getPendingSubmission()).isNull();
                assertThat(card.getDraftRevision()).isEqualTo(originalRevision);
                assertThat(receipts.find("999998", 1001, token)).isNull();
                assertThat(new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM prescription", Long.class)).isZero();
                registerMock(PlatformTransactionManager.class, transactions);
                action.persistStash(info, bean);
                assertThat(receipts.find("999998", 1001, token).outcome()).isEqualTo("SAVED");
                return;
            }
            if (auditFailure || rollbackOnly) {
                assertThat(request.getAttribute("RX_SAVE_OUTCOME_UNKNOWN")).isNull();
                assertThat(card.getDraftRevision()).isEqualTo(originalRevision);
                assertThat(receipts.find("999998", 1001, token)).isNull();
                var failedJdbc = new JdbcTemplate(source);
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM prescription", Long.class)).isZero();
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM drugs", Long.class)).isZero();
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM log WHERE action='add'", Long.class)).isZero();
                // The content evidence joins the clinical transaction: none survives its rollback.
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM rx_submission_receipt WHERE outcome='SAVED'", Long.class)).isZero();
                setAudit(audit);
                action.persistStash(info, bean); // Only a confirmed rollback releases the exact draft for retry.
            } else {
                assertThat(request.getAttribute("RX_SAVE_OUTCOME_UNKNOWN")).isEqualTo(true);
            }
            if (failedCommit) {
                assertThat(card.getPendingSubmission()).isEqualTo(token);
                bean.removePersistedStashItems();
                assertThat(bean.getStashSize()).isEqualTo(1); // Reopening must retain the editable, locked draft.
                assertThat(receipts.find("999998", 1001, token).outcome()).isEqualTo("PENDING");
                request.setParameter("draftRevision_123", card.getDraftRevision()); // New rendered revision after reopening.
                try (var resolver = mockStatic(RxSessionBeanResolver.class)) {
                    resolver.when(() -> RxSessionBeanResolver.requestedDemographicNo(request)).thenReturn(1001);
                    resolver.when(() -> RxSessionBeanResolver.resolve(request)).thenReturn(bean);
                    resolver.when(() -> RxSessionBeanResolver.isRequestForBeanPatient(request, bean)).thenReturn(true);
                    assertThat(action.updateSaveAllDrugs()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
                }
                assertThat(response.getStatus()).isEqualTo(409);
                assertThat(response.getContentAsString()).contains("RX_SAVE_OUTCOME_UNKNOWN");
                var failedJdbc = new JdbcTemplate(source);
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM prescription", Long.class)).isZero();
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM drugs", Long.class)).isZero();
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM log WHERE action='add'", Long.class)).isZero();
                return;
            }
            assertThat(card.getDraftRevision()).isNotEqualTo(originalRevision);
            var receipt = receipts.find("999998", 1001, token);
            assertThat(receipt.outcome()).isEqualTo("SAVED");
            var jdbc = new JdbcTemplate(source);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prescription", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM drugs", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM log WHERE action='add' AND content='prescription'", Long.class)).isEqualTo(1L);
            // The draft receipt and the evidence of what it stored, both naming the one script.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rx_submission_receipt WHERE outcome='SAVED' AND prescription_id="
                    + receipt.scriptId(), Long.class)).isEqualTo(2L);
            assertThat(receipts.find("999997", 1001, token)).isNull();
            if (!auditFailure && !rollbackOnly) {
                final var replayResponse = new org.springframework.mock.web.MockHttpServletResponse();
                servlet.when(ServletActionContext::getResponse).thenReturn(replayResponse);
                // Action retains its response field, so construct a new request-scoped action.
                var replayAction = new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class));
                request.setParameter("draftRevision_123", card.getDraftRevision());
                var newer = new RxPrescriptionData.Prescription(0, "999998", 1001);
                newer.setRandomId(456); newer.setSpecial("Unsaved newer draft");
                bean.getStashList().add(newer);
                request.setParameter("draftRevision_456", newer.getDraftRevision());
                request.setParameter("drugName_123", "Earlier saved medication");
                request.setParameter("drugName_456", "Newer unsaved medication");
                try (var resolver = mockStatic(RxSessionBeanResolver.class)) {
                    resolver.when(() -> RxSessionBeanResolver.requestedDemographicNo(request)).thenReturn(1001);
                    resolver.when(() -> RxSessionBeanResolver.resolve(request)).thenReturn(bean);
                    resolver.when(() -> RxSessionBeanResolver.isRequestForBeanPatient(request, bean)).thenReturn(true);
                    assertThat(replayAction.updateSaveAllDrugs()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
                    assertThat(bean.getStash()).containsExactly(newer);
                    assertThat(newer.getDrugId()).isZero();
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prescription", Long.class)).isEqualTo(1L);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM drugs", Long.class)).isEqualTo(1L);
                    assertThat(replayResponse.getContentAsString()).contains("RX_DRAFT_REVIEW_REQUIRED");
                }
                request.removeParameter("draftRevision_456");
                request.removeParameter("drugName_456");
                // The exact replay below posts what the saved request posted, nothing more.
                request.removeParameter("drugName_123");
                request.setParameter("draftRevision_123", originalRevision);
            }
            // No Rx bean in this new request/session: replay is resolved from durable, ownership-scoped evidence.
            assertThat(action.updateSaveAllDrugs()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(response.getContentAsString()).contains("RX_ALREADY_SAVED");
            var legacyResponse = new MockHttpServletResponse();
            servlet.when(ServletActionContext::getResponse).thenReturn(legacyResponse);
            // Even a malformed container context cannot escape the encoded recovery href.
            request.setContextPath("/carlos\" onclick=\"alert(1)");
            var legacy = new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class));
            legacy.setAction("updateAndPrint");
            assertThat(legacy.execute()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
            assertThat(legacyResponse.getStatus()).isEqualTo(409);
            assertThat(legacyResponse.getContentType()).startsWith("text/html");
            assertThat(legacyResponse.getContentAsString()).contains("already saved").doesNotContain("RX_ALREADY_SAVED");
            var recoveryPage = org.jsoup.Jsoup.parse(legacyResponse.getContentAsString());
            assertThat(recoveryPage.select("script,[onclick]")).isEmpty();
            assertThat(recoveryPage.select("a")).hasSize(1);
            assertThat(recoveryPage.select("a").attr("href")).isEqualTo(
                    request.getContextPath() + "/rx/choosePatient?demographicNo=1001");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM drugs", Long.class)).isEqualTo(1L);
        }
    }

    /**
     * One staged card saved through the real clinical transaction by a request that carried a
     * known set of fields, with the statics a replay needs kept open. The receipt alone used to
     * decide a replay; these tests pin that the submitted fields decide it as well.
     */
    private final class SavedDraft implements AutoCloseable {
        final RxSessionBean bean = new RxSessionBean();
        final RxPrescriptionData.Prescription card = new RxPrescriptionData.Prescription(0, "999998", 1001);
        final LoggedInInfo info = mock(LoggedInInfo.class);
        final String renderedRevision = card.getDraftRevision();
        final int scriptId;
        private final org.mockito.MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
        private final org.mockito.MockedStatic<LoggedInInfo> session = mockStatic(LoggedInInfo.class);
        private final org.mockito.MockedConstruction<RxPrescriptionData> headers = mockConstruction(RxPrescriptionData.class,
                (data, context) -> when(data.saveScript(any(), any())).thenAnswer(invocation -> {
                    var header = new Prescription(); header.setProviderNo("999998"); header.setDemographicId(1001);
                    em.persist(header); return header.getId().toString();
                }));

        SavedDraft() {
            try {
                scriptId = save();
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private int save() {
            bean.setProviderNo("999998"); bean.setDemographicNo(1001);
            card.setRandomId(123); card.setGCN_SEQNO("0"); card.setBrandName("Synthetic receipt medication");
            card.setSpecial("1 tab PO OD"); card.setRxDate(java.sql.Date.valueOf("2026-01-01"));
            bean.getStashList().add(card);
            when(info.getLoggedInProviderNo()).thenReturn("999998");
            var security = mock(SecurityInfoManager.class); registerMock(SecurityInfoManager.class, security);
            when(security.hasPrivilege(eq(info), eq("_rx"), anyString(), isNull())).thenReturn(true);
            when(security.hasPrivilege(eq(info), eq("_rx"), anyString(), eq(1001))).thenReturn(true);
            when(security.isAllowedAccessToPatientRecord(info, 1001)).thenReturn(true);
            session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(jakarta.servlet.http.HttpServletRequest.class))).thenReturn(info);
            var request = submission("1 tab PO OD");
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            return Integer.parseInt(new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class)).persistStash(info, bean));
        }

        /** The rendered form as the browser posts it; only the directions vary between posts. */
        MockHttpServletRequest submission(String instructions) {
            var request = new MockHttpServletRequest("POST", "/rx/WriteScript");
            request.addParameter("demographicNo", "1001");
            request.addParameter("draftRevision_123", renderedRevision);
            request.addParameter("drugName_123", "Synthetic receipt medication");
            request.addParameter("instructions_123", instructions);
            request.addParameter("rxPharmacyId", "");
            return request;
        }

        /** Posts {@code request} to the AJAX save in a fresh request-scoped action. */
        com.fasterxml.jackson.databind.JsonNode replay(MockHttpServletRequest request, RxSessionBean staged) throws Exception {
            var response = new MockHttpServletResponse();
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action = new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class));
            if (staged == null) {
                // No Rx bean: the answer comes from durable, ownership-scoped evidence alone.
                assertThat(action.updateSaveAllDrugs()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
            } else {
                try (var resolver = mockStatic(RxSessionBeanResolver.class)) {
                    resolver.when(() -> RxSessionBeanResolver.requestedDemographicNo(request)).thenReturn(1001);
                    resolver.when(() -> RxSessionBeanResolver.resolve(request)).thenReturn(staged);
                    resolver.when(() -> RxSessionBeanResolver.isRequestForBeanPatient(request, staged)).thenReturn(true);
                    assertThat(action.updateSaveAllDrugs()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
                }
            }
            assertThat(response.getStatus()).isEqualTo(409);
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        }

        /** Posts {@code request} to the legacy Save & Print form action. */
        MockHttpServletResponse legacyReplay(MockHttpServletRequest request) throws Exception {
            var response = new MockHttpServletResponse();
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var legacy = new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class));
            legacy.setAction("updateAndPrint");
            assertThat(legacy.execute()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
            return response;
        }

        /** Posts {@code request} to the programmatic save entry point with the workspace resolved. */
        com.fasterxml.jackson.databind.JsonNode saveDrug(MockHttpServletRequest request) throws Exception {
            var response = new MockHttpServletResponse();
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action = new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class));
            try (var resolver = mockStatic(RxSessionBeanResolver.class)) {
                resolver.when(() -> RxSessionBeanResolver.resolve(request)).thenReturn(bean);
                resolver.when(() -> RxSessionBeanResolver.isRequestForBeanPatient(request, bean)).thenReturn(true);
                action.saveDrug(request);
            }
            assertThat(response.getStatus()).isEqualTo(409);
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        }

        /** Everything a second save would add to. */
        String storedRows() {
            var jdbc = new JdbcTemplate(source);
            return jdbc.queryForObject("SELECT COUNT(*) FROM prescription", Long.class) + " prescription, "
                    + jdbc.queryForObject("SELECT COUNT(*) FROM drugs", Long.class) + " drugs, "
                    + jdbc.queryForObject("SELECT COUNT(*) FROM log WHERE action='add'", Long.class) + " audits, "
                    + jdbc.queryForList("SELECT submission_hash FROM rx_submission_receipt ORDER BY submission_hash", String.class);
        }

        @Override public void close() { headers.close(); session.close(); servlet.close(); }
    }

    @Test void shouldAnswerAlreadySaved_whenTheSavedDraftIsReplayedWithTheSameFields() throws Exception {
        try (var saved = new SavedDraft()) {
            String before = saved.storedRows();
            var retry = saved.submission("1 tab PO OD");
            // What legitimately differs between two posts of an unchanged form.
            retry.addParameter("rand", "4711"); retry.addParameter("CSRF-TOKEN", "rotated-token");
            retry.addParameter("clearSaved", "true"); retry.addParameter("searchString", "another search");
            retry.addParameter("method", "brandName"); retry.addParameter("wildcard", "true");

            var answer = saved.replay(retry, null);

            assertThat(answer.path("error").asText()).isEqualTo("RX_ALREADY_SAVED");
            assertThat(answer.path("scriptId").asInt()).isEqualTo(saved.scriptId);
            assertThat(saved.storedRows()).isEqualTo(before);
        }
    }

    @Test void shouldRefuseAsEarlierVersion_whenTheSavedDraftIsResubmittedWithEditedFields() throws Exception {
        try (var saved = new SavedDraft()) {
            String before = saved.storedRows();

            var answer = saved.replay(saved.submission("2 tabs PO BID"), null);

            // "Already saved; do not enter it again" would drop the correction without a word.
            assertThat(answer.path("error").asText()).isEqualTo("RX_SAVED_EARLIER_VERSION");
            assertThat(answer.path("scriptId").asInt()).isEqualTo(saved.scriptId);
            assertThat(saved.storedRows()).isEqualTo(before);
            assertThat(new JdbcTemplate(source).queryForObject("SELECT special FROM drugs", String.class)).contains("1 tab PO OD");
        }
    }

    @Test void shouldRefuseAsEarlierVersion_whenAFieldIsAddedOrRemovedFromTheSavedDraft() throws Exception {
        try (var saved = new SavedDraft()) {
            var added = saved.submission("1 tab PO OD"); added.addParameter("repeats_123", "3");
            var removed = saved.submission("1 tab PO OD"); removed.removeParameter("instructions_123");
            var otherPharmacy = saved.submission("1 tab PO OD"); otherPharmacy.setParameter("rxPharmacyId", "7");

            for (var edited : java.util.List.of(added, removed, otherPharmacy)) {
                assertThat(saved.replay(edited, null).path("error").asText()).isEqualTo("RX_SAVED_EARLIER_VERSION");
            }
        }
    }

    @Test void shouldRefuseAsEarlierVersion_whenTheReceiptCarriesNoContentEvidence() throws Exception {
        try (var saved = new SavedDraft()) {
            // A receipt written before content was bound to it cannot vouch for what was stored.
            String draft = RxSubmissionReceiptService.fingerprint(java.util.Map.of("123", saved.renderedRevision));
            new JdbcTemplate(source).update("DELETE FROM rx_submission_receipt WHERE submission_hash<>?", draft);

            var answer = saved.replay(saved.submission("1 tab PO OD"), null);

            assertThat(answer.path("error").asText()).isEqualTo("RX_SAVED_EARLIER_VERSION");
            assertThat(new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM prescription", Long.class)).isEqualTo(1L);
        }
    }

    @Test void shouldRenderEarlierVersionRecovery_whenTheLegacyFormResubmitsEditedFields() throws Exception {
        try (var saved = new SavedDraft()) {
            String before = saved.storedRows();
            var messages = java.util.ResourceBundle.getBundle("oscarResources", java.util.Locale.ENGLISH);

            var unchanged = saved.legacyReplay(saved.submission("1 tab PO OD"));
            var edited = saved.legacyReplay(saved.submission("2 tabs PO BID"));

            assertThat(unchanged.getStatus()).isEqualTo(409);
            assertThat(org.jsoup.Jsoup.parse(unchanged.getContentAsString()).select("p[role=alert]").text())
                    .isEqualTo(messages.getString("oscarRx.saveAlreadySaved"));
            assertThat(edited.getStatus()).isEqualTo(409);
            var page = org.jsoup.Jsoup.parse(edited.getContentAsString());
            assertThat(page.select("p[role=alert]").text())
                    .isEqualTo(messages.getString("oscarRx.saveEarlierVersionSaved"))
                    .contains("NOT saved").doesNotContain("do not enter it again");
            assertThat(page.select("a").attr("href")).isEqualTo("/rx/choosePatient?demographicNo=1001");
            assertThat(saved.storedRows()).isEqualTo(before);
        }
    }

    @Test void shouldTreatTheDrugMethodAsContent_whenTheLegacyFormIsResubmitted() throws Exception {
        try (var saved = new SavedDraft()) {
            // On the legacy form "method" is the drug's method (Take, Apply, ...), not SearchDrug3's
            // search filter, so there a change to it is a change to the prescription.
            var otherMethod = saved.submission("1 tab PO OD"); otherMethod.addParameter("method", "Apply");
            var messages = java.util.ResourceBundle.getBundle("oscarResources", java.util.Locale.ENGLISH);

            var page = org.jsoup.Jsoup.parse(saved.legacyReplay(otherMethod).getContentAsString());

            assertThat(page.select("p[role=alert]").text()).isEqualTo(messages.getString("oscarRx.saveEarlierVersionSaved"));
            // The same field on the SearchDrug3 save is its search filter and changes nothing.
            assertThat(saved.replay(otherMethod, null).path("error").asText()).isEqualTo("RX_ALREADY_SAVED");
        }
    }

    @Test void shouldTellReplayFromEdit_whenTheProgrammaticSaveSeesASavedDraft() throws Exception {
        try (var saved = new SavedDraft()) {
            String before = saved.storedRows();

            var unchanged = saved.saveDrug(saved.submission("1 tab PO OD"));
            var edited = saved.saveDrug(saved.submission("2 tabs PO BID"));

            assertThat(unchanged.path("error").asText()).isEqualTo("RX_ALREADY_SAVED");
            assertThat(edited.path("error").asText()).isEqualTo("RX_SAVED_EARLIER_VERSION");
            assertThat(edited.path("scriptId").asInt()).isEqualTo(saved.scriptId);
            assertThat(saved.storedRows()).isEqualTo(before);
        }
    }

    @Test void shouldReturnAcknowledgedRevisions_whenTheReplayedCardsAreStillStaged() throws Exception {
        try (var saved = new SavedDraft()) {
            // Save & Print keeps its saved cards staged under new revisions the lost reply carried.
            String acknowledged = saved.card.getDraftRevision();
            assertThat(acknowledged).isNotEqualTo(saved.renderedRevision);

            var answer = saved.replay(saved.submission("1 tab PO OD"), saved.bean);

            assertThat(answer.path("error").asText()).isEqualTo("RX_ALREADY_SAVED");
            assertThat(answer.path("draftRevisions").path("123").asText()).isEqualTo(acknowledged);
            assertThat(saved.bean.getStash()).containsExactly(saved.card);
            assertThat(saved.card.getDraftRevision()).isEqualTo(acknowledged);
        }
    }

    @Test void shouldClearSavedCards_whenTheSaveOnlyReplayFindsThemStillStaged() throws Exception {
        try (var saved = new SavedDraft()) {
            var retry = saved.submission("1 tab PO OD"); retry.addParameter("clearSaved", "true");

            var answer = saved.replay(retry, saved.bean);

            // As the lost Save reply would have left it: nothing staged, nothing to acknowledge.
            assertThat(answer.path("error").asText()).isEqualTo("RX_ALREADY_SAVED");
            assertThat(answer.has("draftRevisions")).isFalse();
            assertThat(saved.bean.getStashSize()).isZero();
        }
    }

    @Test void shouldKeepAnotherWindowsDraft_whenTheSaveOnlyReplayIsAcknowledged() throws Exception {
        try (var saved = new SavedDraft()) {
            var newer = new RxPrescriptionData.Prescription(0, "999998", 1001);
            newer.setRandomId(456); newer.setSpecial("Unsaved newer draft");
            saved.bean.getStashList().add(newer);
            var retry = saved.submission("1 tab PO OD"); retry.addParameter("clearSaved", "true");

            var answer = saved.replay(retry, saved.bean);

            assertThat(answer.path("error").asText()).isEqualTo("RX_ALREADY_SAVED");
            assertThat(answer.has("draftRevisions")).isFalse();
            assertThat(saved.bean.getStash()).contains(newer);
        }
    }

    @Test void shouldLeaveStagedCardsUntouched_whenEditedFieldsAreRefused() throws Exception {
        try (var saved = new SavedDraft()) {
            String acknowledged = saved.card.getDraftRevision();
            int drugId = saved.card.getDrugId();
            var edited = saved.submission("2 tabs PO BID"); edited.addParameter("clearSaved", "true");

            var answer = saved.replay(edited, saved.bean);

            assertThat(answer.path("error").asText()).isEqualTo("RX_SAVED_EARLIER_VERSION");
            assertThat(answer.has("draftRevisions")).isFalse();
            assertThat(saved.bean.getStash()).containsExactly(saved.card);
            assertThat(saved.card.getDraftRevision()).isEqualTo(acknowledged);
            assertThat(saved.card.getDrugId()).isEqualTo(drugId);
            assertThat(saved.card.getSpecial()).contains("1 tab PO OD");
        }
    }

    @Test void shouldKeepPendingIntentAcrossNewManager_whenNoClinicalCommitWasConfirmed() {
        String token = RxSubmissionReceiptService.fingerprint(java.util.Map.of("1", java.util.UUID.randomUUID().toString()));
        assertThat(receipts.start("999998", 1001, token, "00000000-0000-0000-0000-000000000001", "127.0.0.1")).isTrue();
        assertThat(receipts.start("999998", 1001, token, "00000000-0000-0000-0000-000000000001", "127.0.0.1")).isFalse();
        var reopened = new RxSubmissionReceiptService(source);
        assertThat(reopened.find("999998", 1001, token).outcome()).isEqualTo("PENDING");
        receipts.failed("999998", 1001, token, "00000000-0000-0000-0000-000000000002");
        assertThat(receipts.find("999998", 1001, token).outcome()).isEqualTo("PENDING");
        receipts.failed("999998", 1001, token, "00000000-0000-0000-0000-000000000001");
        assertThat(receipts.find("999998", 1001, token)).isNull();
        assertThat(receipts.start("999998", 1001, token, "00000000-0000-0000-0000-000000000001", "127.0.0.1")).isTrue();
    }

    static class CommitFault {
        final AtomicInteger remaining = new AtomicInteger();
        volatile boolean commitBeforeThrow = true;
        Connection wrap(Connection delegate) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        try {
                            if (method.getName().equals("commit") && remaining.get() > 0 && remaining.decrementAndGet() == 0) {
                                if (commitBeforeThrow) delegate.commit();
                                else delegate.rollback(); // Simulate disconnect before COMMIT reached the server.
                                throw new SQLException("Synthetic lost COMMIT acknowledgement", "08006");
                            }
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
        }
    }
    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(destroyMethod = "close") IsolatedJpaTestDatabase database() throws Exception { return new IsolatedJpaTestDatabase("rx_commit_receipt"); }
        @Bean CommitFault fault() { return new CommitFault(); }
        @Bean DataSource dataSource(IsolatedJpaTestDatabase db, CommitFault fault) {
            return new DelegatingDataSource(db.dataSource()) {
                @Override public Connection getConnection() throws SQLException { return fault.wrap(super.getConnection()); }
                @Override public Connection getConnection(String user, String password) throws SQLException { return fault.wrap(super.getConnection(user, password)); }
            };
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source, IsolatedJpaTestDatabase db) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(source);
            factory.setManagedTypes(PersistenceManagedTypes.of(Drug.class.getName(), Prescription.class.getName(), PartialDate.class.getName(), OscarLog.class.getName(), Favorites.class.getName(), FavoritesPrivilege.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter()); factory.setJpaPropertyMap(db.jpaProperties()); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean DrugDao drugs() { return new DrugDaoImpl(); }
        @Bean PartialDateDao dates() { return new PartialDateDaoImpl(); }
        @Bean OscarLogDao audit() { return new OscarLogDaoImpl(); }
        @Bean FavoritesDao favorites() { return new FavoritesDaoImpl(); }
        @Bean FavoritesPrivilegeDao favoritePrivileges() { return new FavoritesPrivilegeDaoImpl(); }
        @Bean RxFavoriteCopyIntegrationTest.CopyFault copyFault() { return new RxFavoriteCopyIntegrationTest.CopyFault(); }
        @Bean RxFavoriteCopyService favoriteCopies(FavoritesDao favorites, FavoritesPrivilegeDao privileges,
                RxFavoriteCopyIntegrationTest.CopyFault fault) {
            var observed = mock(FavoritesDao.class, org.mockito.AdditionalAnswers.delegatesTo(favorites));
            doAnswer(invocation -> {
                if (fault.remaining.get() > 0 && fault.remaining.decrementAndGet() == 0) {
                    throw new IllegalStateException("Synthetic second favourite insert failure");
                }
                favorites.persist(invocation.getArgument(0)); return null;
            }).when(observed).persist(any(Favorites.class));
            return new RxFavoriteCopyService(observed, privileges);
        }
        @Bean RxSourceVersionValidator concurrency(DataSource source) { return new RxSourceVersionValidator(source); }
        @Bean RxSubmissionReceiptService receipts(DataSource source) { return new RxSubmissionReceiptService(source); }
    }
}
