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
package io.github.carlos_emr.carlos.lab.ca.all.upload;

import io.github.carlos_emr.carlos.commn.dao.FileUploadCheckDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.logging.HibernateSessionAssertions;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MessageUploader#clean(int)} against real Hibernate sessions and real Spring transactions (#4436).
 *
 * <p>Lab handlers call {@code clean} from their {@code catch} blocks, inside the transaction that
 * {@code FileUploadCheck.storeIfNew} opened. When the failure was a rejected insert, Hibernate has
 * already marked that transaction rollback-only and left the failed entity half-registered in the
 * session, so the first query {@code clean} ran threw {@code AssertionFailure} (HHH000099). That
 * exception left the handler before it logged the original cause, so the ERROR log named a Hibernate
 * assertion instead of the database error.</p>
 *
 * @since 2026-10-08
 */
@Tag("integration")
@Tag("lab")
@Tag("upload")
@Isolated("MessageUploader caches Spring beans in static fields")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MessageUploaderCleanIntegrationTest extends CarlosTestBase {
    private static final int CHECKSUM = 9843600;
    private static final String SYNTHETIC_MESSAGE = "U1lOVEhFVElD";
    // clean() copies each removed row into the recycle bin; these find this test's copies by content.
    private static final String SYNTHETIC_LAST_NAME = "Synth4436";

    @Autowired private PlatformTransactionManager transactions;
    @Autowired private Hl7TextMessageDao messages;
    @Autowired private Hl7TextInfoDao infos;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    private final Map<Field, Object> originalBeans = new LinkedHashMap<>();
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() throws Exception {
        // A unit test may have initialized this legacy class first with mocked Spring beans.
        for (Field field : MessageUploader.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
                    && (field.getName().endsWith("Dao") || field.getName().endsWith("Manager"))) {
                field.setAccessible(true);
                originalBeans.put(field, field.get(null));
                field.set(null, applicationContext.getBean(field.getType()));
            }
        }
        // Same shape as FileUploadCheck.storeIfNew: an independent READ_COMMITTED transaction.
        tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        purge();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (var entry : originalBeans.entrySet()) entry.getKey().set(null, entry.getValue());
        purge();
    }

    private void purge() {
        tx.executeWithoutResult(status -> {
            em.createQuery("delete from Hl7TextInfo i where i.lastName = :name")
                    .setParameter("name", SYNTHETIC_LAST_NAME).executeUpdate();
            em.createQuery("delete from Hl7TextMessage m where m.fileUploadCheckId = :checksum")
                    .setParameter("checksum", CHECKSUM).executeUpdate();
            em.createQuery("delete from RecycleBin r where r.providerNo = '0' and "
                    + "((r.tableName = 'hl7TextMessage' and r.tableContent like :message) "
                    + "or (r.tableName = 'hl7TextInfo' and r.tableContent like :info))")
                    .setParameter("message", "%" + SYNTHETIC_MESSAGE + "%")
                    .setParameter("info", "%" + SYNTHETIC_LAST_NAME + "%").executeUpdate();
        });
    }

    private Hl7TextMessage persistMessage() {
        Hl7TextMessage message = new Hl7TextMessage();
        message.setFileUploadCheckId(CHECKSUM);
        message.setType("CML");
        message.setServiceName("synthetic");
        message.setBase64EncodedeMessage(SYNTHETIC_MESSAGE);
        messages.persist(message);
        return message;
    }

    private Hl7TextInfo infoFor(Hl7TextMessage message, String sex) {
        Hl7TextInfo info = new Hl7TextInfo();
        info.setLabNumber(message.getId());
        info.setLastName(SYNTHETIC_LAST_NAME);
        info.setSex(sex);
        return info;
    }

    private long countMessages() {
        return tx.execute(status -> em.createQuery(
                "select count(m) from Hl7TextMessage m where m.fileUploadCheckId = :checksum", Long.class)
                .setParameter("checksum", CHECKSUM).getSingleResult());
    }

    // By the synthetic last name, not through the message: clean() deletes the message, and a count that
    // joins through it would read zero even when clean left the info row behind.
    private long countInfos() {
        return tx.execute(status -> em.createQuery(
                "select count(i) from Hl7TextInfo i where i.lastName = :name", Long.class)
                .setParameter("name", SYNTHETIC_LAST_NAME).getSingleResult());
    }

    private long countRecycled(String table, String marker) {
        return tx.execute(status -> em.createQuery(
                "select count(r) from RecycleBin r where r.tableName = :table and r.tableContent like :marker",
                Long.class).setParameter("table", table).setParameter("marker", "%" + marker + "%").getSingleResult());
    }

    private long countRecycledMessages() {
        return countRecycled("hl7TextMessage", SYNTHETIC_MESSAGE);
    }

    private long countRecycledInfos() {
        return countRecycled("hl7TextInfo", SYNTHETIC_LAST_NAME);
    }

    @Test
    void shouldReturnNormally_whenTransactionWasDoomedByRejectedInsert() {
        try (LogCapture hibernateCore = HibernateSessionAssertions.capture()) {
            tx.executeWithoutResult(status -> {
                Hl7TextMessage message = persistMessage();
                // A value longer than the column is rejected by the database like the trigger in the
                // lab-upload-rollback probe: IDENTITY ids make persist insert immediately, so it throws here.
                Hl7TextInfo oversized = infoFor(message, "x".repeat(400));
                assertThatThrownBy(() -> infos.persist(oversized))
                        .as("the insert the database rejects")
                        .isInstanceOf(RuntimeException.class);

                // What every handler's catch block does next.
                assertThatCode(() -> MessageUploader.clean(CHECKSUM))
                        .as("clean must not throw out of a handler's catch block").doesNotThrowAnyException();
                assertThat(HibernateSessionAssertions.in(hibernateCore))
                        .as("clean must not run a query that makes Hibernate log HHH000099 at ERROR").isEmpty();

                // Control: the session really is unusable, and this capture does see Hibernate's assertion,
                // so the check above cannot pass because the capture listens on the wrong logger.
                TypedQuery<Long> count = em.createQuery("select count(m) from Hl7TextMessage m", Long.class);
                assertThatThrownBy(count::getSingleResult)
                        .as("a query in the failed session").isInstanceOf(RuntimeException.class);
                assertThat(HibernateSessionAssertions.in(hibernateCore))
                        .as("the capture records HHH000099 when a query does run in the failed session").isNotEmpty();

                status.setRollbackOnly();
            });
        }

        assertThat(countMessages()).as("the doomed transaction rolled back").isZero();
        assertThat(countRecycledMessages() + countRecycledInfos()).as("nothing was recycled by a skipped clean").isZero();
    }

    @Test
    void shouldSkipCleanup_whenManagerFailsEarlyOnRollbackOnlyTransaction() {
        // A transaction manager set to fail early completes a participant in a rollback-only transaction by
        // throwing UnexpectedRollbackException. clean joins the transaction as a participant to read its
        // state, so that exception must still read as "doomed" rather than "unknown, try the cleanup".
        AbstractPlatformTransactionManager manager =
                (AbstractPlatformTransactionManager) SpringUtils.getBean(PlatformTransactionManager.class);
        boolean failEarly = manager.isFailEarlyOnGlobalRollbackOnly();
        manager.setFailEarlyOnGlobalRollbackOnly(true);
        try (LogCapture hibernateCore = HibernateSessionAssertions.capture()) {
            tx.executeWithoutResult(status -> {
                Hl7TextMessage message = persistMessage();
                Hl7TextInfo oversized = infoFor(message, "x".repeat(400));
                assertThatThrownBy(() -> infos.persist(oversized))
                        .as("the insert the database rejects")
                        .isInstanceOf(RuntimeException.class);

                assertThatCode(() -> MessageUploader.clean(CHECKSUM))
                        .as("clean must not throw out of a handler's catch block").doesNotThrowAnyException();
                assertThat(HibernateSessionAssertions.in(hibernateCore))
                        .as("clean must not query the failed session when the manager fails early").isEmpty();

                status.setRollbackOnly();
            });
        } finally {
            manager.setFailEarlyOnGlobalRollbackOnly(failEarly);
        }

        assertThat(countMessages()).as("the doomed transaction rolled back").isZero();
    }

    @Test
    void shouldMoveRowsToRecycleBin_whenTransactionIsStillHealthy() {
        // IHAPOIHandler reports per-message failures in its result string, returns it (non-null) and
        // relies on clean to undo what it stored before the surrounding transaction commits.
        tx.executeWithoutResult(status -> {
            Hl7TextMessage message = persistMessage();
            infos.persist(infoFor(message, "F"));

            MessageUploader.clean(CHECKSUM);
        });

        assertThat(countMessages()).isZero();
        assertThat(countInfos()).isZero();
        assertThat(countRecycledMessages()).isEqualTo(1);
        assertThat(countRecycledInfos()).isEqualTo(1);
    }

    @Test
    void shouldRecycleCommittedRows_whenNoTransactionIsActive() {
        tx.executeWithoutResult(status -> infos.persist(infoFor(persistMessage(), "F")));
        assertThat(countMessages()).as("the rows are committed before clean runs").isEqualTo(1);

        MessageUploader.clean(CHECKSUM);

        assertThat(countMessages()).isZero();
        assertThat(countInfos()).isZero();
        assertThat(countRecycledMessages()).isEqualTo(1);
        assertThat(countRecycledInfos()).isEqualTo(1);
    }

    @Test
    void shouldRollBackPartialCleanup_whenNoTransactionIsActiveAndCleanupFails() throws Exception {
        tx.executeWithoutResult(status -> infos.persist(infoFor(persistMessage(), "F")));
        assertThat(countMessages()).as("the rows are committed before clean runs").isEqualTo(1);

        // The last step of the cleanup fails, after the message and info rows have been recycled and removed.
        Field dao = MessageUploader.class.getDeclaredField("fileUploadCheckDao");
        dao.setAccessible(true);
        FileUploadCheckDao failing = mock(FileUploadCheckDao.class);
        when(failing.find(anyInt())).thenThrow(new IllegalStateException("synthetic late cleanup failure"));
        dao.set(null, failing);

        assertThatCode(() -> MessageUploader.clean(CHECKSUM))
                .as("with no transaction to doom, the failure is logged and not thrown").doesNotThrowAnyException();

        // Without a caller transaction every DAO call commits on its own, so a cleanup that is not made
        // atomic would leave the rows removed and recycled even though it reported failure.
        assertThat(countMessages()).as("the message is still stored").isEqualTo(1);
        assertThat(countInfos()).as("the info row is still stored").isEqualTo(1);
        assertThat(countRecycledMessages() + countRecycledInfos()).as("nothing was recycled").isZero();
    }

    @Test
    void shouldDoomTransactionAndReturn_whenCleanupItselfFails() throws Exception {
        Field dao = MessageUploader.class.getDeclaredField("hl7TextMessageDao");
        dao.setAccessible(true);
        Hl7TextMessageDao failing = mock(Hl7TextMessageDao.class);
        when(failing.findByFileUploadCheckId(anyInt())).thenThrow(new IllegalStateException("synthetic cleanup failure"));
        dao.set(null, failing);

        try (LogCapture uploaderLog = LogCapture.forLogger(MessageUploader.class)) {
            // IHAPOIHandler returns its per-message failure string after clean, and the caller commits
            // whatever the transaction holds. A failed cleanup must therefore doom the transaction, or
            // the half-removed rows and the checksum would be committed.
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                infos.persist(infoFor(persistMessage(), "F"));

                assertThatCode(() -> MessageUploader.clean(CHECKSUM))
                        .as("clean must not replace the failure that sent the handler here").doesNotThrowAnyException();

                assertThat(status.isRollbackOnly()).as("a failed cleanup dooms the transaction").isTrue();
            })).as("the doomed transaction cannot commit").isInstanceOf(UnexpectedRollbackException.class);

            assertThat(uploaderLog.events())
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("the failed cleanup is reported once, by type and location only").hasSize(1)
                    .allSatisfy(event -> {
                        assertThat(event.getMessage().getFormattedMessage())
                                .contains(Integer.toString(CHECKSUM), "java.lang.IllegalStateException")
                                .doesNotContain("synthetic cleanup failure");
                        assertThat(event.getThrown()).as("no throwable that could carry row content").isNull();
                    });
        }

        assertThat(countMessages()).as("nothing the upload stored was committed").isZero();
        assertThat(countInfos()).isZero();
    }
}
