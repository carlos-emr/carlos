/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@Tag("dao")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TicklerAssigneePreferenceIntegrationTest extends CarlosTestBase {
    @Autowired private UserPropertyDAO properties;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager entityManager;

    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }

    private String owner() {
        String owner = "T" + UUID.randomUUID().toString().substring(0, 5);
        transaction().executeWithoutResult(status -> {
            Provider provider = new Provider();
            provider.setProviderNo(owner);
            provider.setFirstName("Preference");
            provider.setLastName("Fixture");
            provider.setProviderType("doctor");
            provider.setSex("U");
            provider.setSpecialty("");
            provider.setStatus("1");
            entityManager.persist(provider);
        });
        return owner;
    }

    private void add(String owner, String name, String value) {
        UserProperty property = new UserProperty();
        property.setProviderNo(owner);
        property.setName(name);
        property.setValue(value);
        entityManager.persist(property);
    }

    private List<String> values(String owner, String name) {
        return entityManager.createQuery("select p.value from UserProperty p where p.providerNo=:owner and p.name=:name", String.class)
                .setParameter("owner", owner).setParameter("name", name).getResultList();
    }

    private void cleanup(String owner) {
        transaction().executeWithoutResult(status -> {
            entityManager.createQuery("delete from UserProperty where providerNo=:owner").setParameter("owner", owner).executeUpdate();
            entityManager.createQuery("delete from Provider where providerNo=:owner").setParameter("owner", owner).executeUpdate();
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"mrp", "999998"})
    void shouldReplaceEveryOldCopy_withoutTouchingOtherPreferences(String value) {
        String owner = owner();
        try {
            transaction().executeWithoutResult(status -> {
                add(owner, UserProperty.TICKLER_TASK_ASSIGNEE, "old-one");
                add(owner, UserProperty.TICKLER_TASK_ASSIGNEE, "old-two");
                add(owner, "unrelated", "keep");
            });
            properties.replaceTicklerTaskAssignee(owner, value);
            transaction().executeWithoutResult(status -> {
                assertThat(values(owner, UserProperty.TICKLER_TASK_ASSIGNEE)).containsExactlyElementsOf(value == null ? List.of() : List.of(value));
                assertThat(values(owner, "unrelated")).containsExactly("keep");
            });
        } finally { cleanup(owner); }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"999998"})
    void shouldSerializeFirstCreationAndAnotherSave_whenTransactionsOverlap(String secondValue) throws Exception {
        String owner = owner();
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> transaction().executeWithoutResult(status -> {
                properties.replaceTicklerTaskAssignee(owner, "mrp");
                firstSaved.countDown();
                await(releaseFirst);
            }));
            assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> transaction().executeWithoutResult(status -> {
                secondStarted.countDown();
                properties.replaceTicklerTaskAssignee(owner, secondValue);
            }));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            transaction().executeWithoutResult(status -> assertThat(values(owner, UserProperty.TICKLER_TASK_ASSIGNEE))
                    .containsExactlyElementsOf(secondValue == null ? List.of() : List.of(secondValue)));
        } finally {
            releaseFirst.countDown();
            workers.shutdown();
            if (!workers.awaitTermination(15, TimeUnit.SECONDS)) workers.shutdownNow();
            cleanup(owner);
        }
    }

    @Test
    void shouldRollBackReplacement_whenTheTransactionFails() {
        String owner = owner();
        try {
            properties.replaceTicklerTaskAssignee(owner, "original");
            assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
                properties.replaceTicklerTaskAssignee(owner, "changed");
                throw new IllegalStateException("injected failure");
            })).isInstanceOf(IllegalStateException.class);
            transaction().executeWithoutResult(status -> assertThat(values(owner, UserProperty.TICKLER_TASK_ASSIGNEE)).containsExactly("original"));
        } finally { cleanup(owner); }
    }

    @Test
    void shouldRefuseAnUnknownOwner_withoutCreatingAnOrphan() {
        String absent = "T" + UUID.randomUUID().toString().substring(0, 5);
        assertThatThrownBy(() -> properties.replaceTicklerTaskAssignee(absent, "mrp"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Unknown tickler preference owner");
        transaction().executeWithoutResult(status -> assertThat(values(absent, UserProperty.TICKLER_TASK_ASSIGNEE)).isEmpty());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test transaction");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
