/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.on;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.QueueDocumentLink;
import io.github.carlos_emr.carlos.documentManager.IncomingDocumentCapacityResponse;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real JDBC commit/rollback boundaries around production batch and DOC filing logic. */
@Tag("unit")
@Tag("lab")
class CommonLabResultDataFilingUnitTest extends CarlosUnitTestBase {
    final SecurityInfoManager security = mock(SecurityInfoManager.class);
    final QueueDocumentLinkDao queues = mock(QueueDocumentLinkDao.class);
    final DocumentDao documents = mock(DocumentDao.class);
    final LoggedInInfo info = mock(LoggedInInfo.class);
    final Map<String, Object> originals = new LinkedHashMap<>();
    JdbcTemplate jdbc;
    JdbcDataSource source;
    MockedStatic<CommonLabResultData> common;
    MockedStatic<IncomingDocumentCapacityResponse> access;
    Runnable afterProviderWrite = () -> { };

    @BeforeEach void fixture() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, queues);
        registerMock(DocumentDao.class, documents);
        for (String field : new String[]{"securityInfoManager", "queueDocumentLinkDao"}) {
            originals.put(field, ReflectionTestUtils.getField(CommonLabResultData.class, field));
        }
        ReflectionTestUtils.setField(CommonLabResultData.class, "securityInfoManager", security);
        ReflectionTestUtils.setField(CommonLabResultData.class, "queueDocumentLinkDao", queues);
        when(info.getLoggedInProviderNo()).thenReturn("991001");
        when(security.hasPrivilege(eq(info), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:doc-filing-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        registerMock(PlatformTransactionManager.class, new DataSourceTransactionManager(source));
        jdbc.execute("CREATE TABLE owned_document(id INT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE owned_routing(id INT, kind VARCHAR(20), status CHAR(1), PRIMARY KEY(id,kind))");
        jdbc.execute("CREATE TABLE owned_queue(id INT PRIMARY KEY, doc INT, queue_no INT, status CHAR(1))");
        jdbc.execute("INSERT INTO owned_document VALUES(42),(43)");
        jdbc.execute("INSERT INTO owned_routing VALUES(42,'DOC','N'),(42,'HL7','N'),(42,'HRM','N'),(43,'DOC','N'),(42,'Spire','N'),(42,'ALPHA','N'),(42,'TRUENORTH','N')");
        jdbc.execute("INSERT INTO owned_queue VALUES(1,42,1,'A'),(2,42,2,'A'),(3,42,9,'I'),(4,43,1,'A')");
        when(documents.findForPageMutation(anyInt())).thenAnswer(call -> {
            int id = call.getArgument(0);
            jdbc.queryForObject("SELECT id FROM owned_document WHERE id=? FOR UPDATE", Integer.class, id);
            return new Document(id);
        });
        when(queues.getQueueFromDocument(anyInt())).thenAnswer(call -> jdbc.query(
                "SELECT * FROM owned_queue WHERE doc=? ORDER BY id", (row, index) -> {
                    QueueDocumentLink link = new QueueDocumentLink(); link.setId(row.getInt("id"));
                    link.setDocId(row.getInt("doc")); link.setQueueId(row.getInt("queue_no"));
                    link.setStatus(row.getString("status")); return link;
                }, (Integer) call.getArgument(0)));
        doAnswer(call -> { QueueDocumentLink link = call.getArgument(0);
            jdbc.update("UPDATE owned_queue SET status=? WHERE id=?", link.getStatus(), link.getId()); return null;
        }).when(queues).merge(any(QueueDocumentLink.class));
        access = mockStatic(IncomingDocumentCapacityResponse.class);
        access.when(() -> IncomingDocumentCapacityResponse.positiveId(anyString())).thenCallRealMethod();
        access.when(() -> IncomingDocumentCapacityResponse.requireQueueAccess(any(), any(), anyString())).thenCallRealMethod();
        common = mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
        common.when(() -> CommonLabResultData.updateReportStatus(anyInt(), anyString(), eq('F'), eq(""), anyString()))
                .thenAnswer(call -> {
                    boolean changed = jdbc.update("UPDATE owned_routing SET status='F' WHERE id=? AND kind=?",
                            (Integer) call.getArgument(0), (String) call.getArgument(4)) == 1;
                    afterProviderWrite.run();
                    return changed;
                });
    }

    @AfterEach void release() {
        if (common != null) common.close();
        if (access != null) access.close();
        originals.forEach((field, value) -> ReflectionTestUtils.setField(CommonLabResultData.class, field, value));
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    private ArrayList<String[]> selections(String... ids) {
        ArrayList<String[]> result = new ArrayList<>();
        for (String id : ids) result.add(new String[]{id, "DOC"});
        return result;
    }
    private String routing(int id, String kind) {
        return jdbc.queryForObject("SELECT status FROM owned_routing WHERE id=? AND kind=?", String.class, id, kind);
    }
    private String queue(int id) { return jdbc.queryForObject("SELECT status FROM owned_queue WHERE id=?", String.class, id); }

    @Test void docFilingCommitsProviderAndAllAccessibleQueuesWhileKeepingHistory() {
        assertThat(CommonLabResultData.fileLabs(selections("42"), info)).isTrue();
        assertThat(routing(42, "DOC")).isEqualTo("F");
        assertThat(routing(42, "HL7")).isEqualTo("N");
        assertThat(queue(1)).isEqualTo("I"); assertThat(queue(2)).isEqualTo("I"); assertThat(queue(3)).isEqualTo("I");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM owned_queue", Integer.class)).isEqualTo(4);
        verify(queues, never()).remove(anyInt());
    }

    @Test void laterQueueFailureRollsBackRealProviderWriteAndEarlierQueueWrite() {
        doAnswer(call -> { QueueDocumentLink link = call.getArgument(0);
            if (link.getId() == 2) throw new IllegalStateException("Injected later queue failure");
            jdbc.update("UPDATE owned_queue SET status=? WHERE id=?", link.getStatus(), link.getId()); return null;
        }).when(queues).merge(any(QueueDocumentLink.class));
        assertThatThrownBy(() -> CommonLabResultData.fileLabs(selections("42"), info))
                .isInstanceOfSatisfying(CommonLabResultData.FilingFailure.class, failure -> assertThat(failure.accepted()).isFalse());
        assertThat(routing(42, "DOC")).isEqualTo("N");
        assertThat(queue(1)).isEqualTo("A"); assertThat(queue(2)).isEqualTo("A");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"HL7", "HRM", "Spire", "ALPHA", "TRUENORTH"})
    void labWithSameNumericIdCannotDeactivateOrDeleteDocumentQueueLinks(String type) {
        ArrayList<String[]> selected = new ArrayList<>(); selected.add(new String[]{"42", type});
        try (var construction = mockConstruction(CommonLabResultData.class,
                (bean, context) -> when(bean.getMatchingLabs("42", type)).thenReturn("42"))) {
            assertThat(CommonLabResultData.fileLabs(selected, "991001")).isTrue();
        }
        assertThat(routing(42, type)).isEqualTo("F"); assertThat(routing(42, "DOC")).isEqualTo("N");
        assertThat(queue(1)).isEqualTo("A"); assertThat(queue(2)).isEqualTo("A");
        verifyNoInteractions(queues, documents);
    }

    @Test void providerOnlyDocBatchRefusesBeforeAnyStatusWrite() {
        assertThatThrownBy(() -> CommonLabResultData.fileLabs(selections("42"), "991001")).isInstanceOf(SecurityException.class);
        assertThat(routing(42, "DOC")).isEqualTo("N"); verifyNoInteractions(queues, documents);
    }

    @Test void inaccessibleSecondQueueRejectsBeforeAnyProviderOrQueueMutation() {
        when(security.hasPrivilege(info, "_queue.2", "r", (String) null)).thenReturn(false);
        assertThatThrownBy(() -> CommonLabResultData.fileLabs(selections("42"), info)).isInstanceOf(SecurityException.class);
        assertThat(routing(42, "DOC")).isEqualTo("N"); assertThat(queue(1)).isEqualTo("A");
        verify(documents, never()).findForPageMutation(anyInt());
    }

    @Test void changedPatientAccessAfterWaitingRollsBackBeforeFiling() {
        AtomicInteger checks = new AtomicInteger();
        access.when(() -> IncomingDocumentCapacityResponse.requireStoredDocumentWriteAccess(security, info, "42"))
                .thenAnswer(call -> { if (checks.incrementAndGet() > 1) throw new SecurityException("Access changed while waiting"); return null; });
        assertThatThrownBy(() -> CommonLabResultData.fileLabs(selections("42"), info))
                .isInstanceOfSatisfying(CommonLabResultData.FilingFailure.class, failure -> assertThat(failure.accepted()).isFalse());
        assertThat(checks).hasValue(2); assertThat(routing(42, "DOC")).isEqualTo("N"); assertThat(queue(1)).isEqualTo("A");
    }

    @Test void laterBatchRollbackReportsEarlierCommittedDocumentAsAccepted() {
        doAnswer(call -> { QueueDocumentLink link = call.getArgument(0);
            if (link.getDocId() == 43) throw new IllegalStateException("Second document failed");
            jdbc.update("UPDATE owned_queue SET status=? WHERE id=?", link.getStatus(), link.getId()); return null;
        }).when(queues).merge(any(QueueDocumentLink.class));
        assertThatThrownBy(() -> CommonLabResultData.fileLabs(selections("42", "43"), info))
                .isInstanceOfSatisfying(CommonLabResultData.FilingFailure.class, failure -> assertThat(failure.accepted()).isTrue());
        assertThat(routing(42, "DOC")).isEqualTo("F"); assertThat(queue(1)).isEqualTo("I");
        assertThat(routing(43, "DOC")).isEqualTo("N"); assertThat(queue(4)).isEqualTo("A");
    }

    @Test void malformedLaterSelectionDoesNotCommitEarlierDocument() {
        ArrayList<String[]> selected = selections("42"); selected.add(new String[]{"43", "doc "});
        assertThatThrownBy(() -> CommonLabResultData.fileLabs(selected, info)).isInstanceOf(IllegalArgumentException.class);
        assertThat(routing(42, "DOC")).isEqualTo("N"); verify(documents, never()).findForPageMutation(anyInt());
    }

    @Test void aQueueAddedAfterAdmissionIsRecheckedBeforeItsStatusCanChange() {
        when(security.hasPrivilege(info, "_queue.9", "r", (String) null)).thenReturn(false);
        // Re-stubbing a CALLS_REAL_METHODS static mock invokes its existing Answer during
        // setup. Inject this hook instead, so the new queue appears inside the transaction.
        afterProviderWrite = () -> jdbc.update("UPDATE owned_queue SET status='A' WHERE id=3");
        assertThatThrownBy(() -> CommonLabResultData.fileLabs(selections("42"), info))
                .isInstanceOfSatisfying(CommonLabResultData.FilingFailure.class, failure -> assertThat(failure.accepted()).isFalse());
        assertThat(routing(42, "DOC")).isEqualTo("N"); assertThat(queue(1)).isEqualTo("A"); assertThat(queue(3)).isEqualTo("I");
        verify(queues, never()).merge(any(QueueDocumentLink.class));
    }

    @Test void uncertainCommitRetainsAcceptedOutcomeEvenIfWritesWereCommitted() {
        registerMock(PlatformTransactionManager.class, new DataSourceTransactionManager(source) {
            @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
                super.doCommit(status);
                throw new org.springframework.transaction.TransactionSystemException("Commit acknowledgement lost");
            }
        });
        assertThatThrownBy(() -> CommonLabResultData.fileLabs(selections("42"), info))
                .isInstanceOfSatisfying(CommonLabResultData.FilingFailure.class, failure -> assertThat(failure.accepted()).isTrue());
        assertThat(routing(42, "DOC")).isEqualTo("F"); assertThat(queue(1)).isEqualTo("I");
    }
}
