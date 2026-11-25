/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Two document previews can resolve the same queue preference on independent connections. */
@Tag("integration")
@Tag("dao")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IncomingDocumentQueuePreferenceIntegrationTest extends CarlosTestBase {
    private static final String QUEUE = UserProperty.INCOMING_DOCUMENT_DEFAULT_QUEUE;
    private static final String OTHER = UserProperty.INCOMING_DOCUMENT_ENTRY_MODE;

    @Autowired private UserPropertyDAO properties;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager database;
    private TransactionTemplate transactions;
    private String owner;
    private String otherOwner;
    private Integer otherId;

    @BeforeEach
    void preparePreferences() {
        transactions = new TransactionTemplate(transactionManager);
        String token = UUID.randomUUID().toString().substring(0, 5);
        owner = "Q" + token;
        otherOwner = "R" + token;
        transactions.executeWithoutResult(status -> {
            for (String provider : List.of(owner, otherOwner)) {
                database.persist(new Provider(provider, "Synthetic", "doctor", "F", "", "Queue"));
            }
        });
        otherId = store(OTHER, "manual");
    }

    @AfterEach
    void removeOwnedProperties() {
        transactions.executeWithoutResult(status -> {
            List<String> owners = List.of(owner, otherOwner);
            for (String key : List.of(QUEUE, OTHER)) {
                properties.getAllProperties(key, owners).forEach(properties::delete);
            }
            owners.forEach(provider -> database.remove(database.find(Provider.class, provider)));
        });
    }

    private Integer store(String key, String value) {
        return transactions.execute(status -> {
            UserProperty property = new UserProperty();
            property.setProviderNo(owner);
            property.setName(key);
            property.setValue(value);
            properties.saveProp(property);
            return property.getId();
        });
    }

    private List<UserProperty> rows(String key) {
        return transactions.execute(status -> properties.getAllProperties(key, List.of(owner)));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private void concurrentPreviewsReturn(String expected) throws Exception {
        CountDownLatch bothReading = new CountDownLatch(2);
        Callable<String> preview = () -> transactions.execute(status -> {
            // Establish both database read views before either request resolves the default.
            properties.findIncomingDocumentQueue(owner);
            bothReading.countDown();
            await(bothReading);
            return IncomingDocUtil.getAndSetIncomingDocQueue(owner, null);
        });
        try (var requests = Executors.newFixedThreadPool(2)) {
            var first = requests.submit(preview);
            var second = requests.submit(preview);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(expected);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(expected);
        }
    }

    @Test
    void shouldReadDefaultWithoutCreatingRows_whenConcurrentPreviewsFindNoPreference() throws Exception {
        concurrentPreviewsReturn("1");
        assertThat(rows(QUEUE)).isEmpty();
    }

    @Test
    void shouldPreserveNullPreference_whenConcurrentPreviewsReadTheDefault() throws Exception {
        Integer id = store(QUEUE, null);
        concurrentPreviewsReturn("1");
        assertThat(rows(QUEUE)).extracting(UserProperty::getId, UserProperty::getValue)
                .containsExactly(tuple(id, null));
    }

    @Test
    void shouldUseStoredSelection_whenConcurrentPreviewsReadThePreference() throws Exception {
        Integer id = store(QUEUE, "7");
        concurrentPreviewsReturn("7");
        assertThat(rows(QUEUE)).extracting(UserProperty::getId, UserProperty::getValue)
                .containsExactly(tuple(id, "7"));
    }

    @Test
    void shouldPersistExplicitSelections_andKeepOtherPreferencesIntact() {
        // An explicit selection of the fallback still means the user chose it.
        assertThat(IncomingDocUtil.getAndSetIncomingDocQueue(owner, "1")).isEqualTo("1");
        var saved = rows(QUEUE);
        assertThat(saved).extracting(UserProperty::getValue).containsExactly("1");
        Integer id = saved.getFirst().getId();
        assertThat(IncomingDocUtil.getAndSetIncomingDocQueue(owner, "7")).isEqualTo("7");
        assertThat(IncomingDocUtil.getAndSetIncomingDocQueue(owner, "7")).isEqualTo("7");
        assertThat(rows(QUEUE)).extracting(UserProperty::getId, UserProperty::getValue)
                .containsExactly(tuple(id, "7"));
        assertThat(rows(OTHER)).extracting(UserProperty::getId, UserProperty::getValue)
                .containsExactly(tuple(otherId, "manual"));
    }

    @Test
    void shouldUpdateExistingNullPreference_whenAQueueIsExplicitlySelected() {
        Integer id = store(QUEUE, null);
        assertThat(IncomingDocUtil.getAndSetIncomingDocQueue(owner, "3")).isEqualTo("3");
        assertThat(rows(QUEUE)).extracting(UserProperty::getId, UserProperty::getValue)
                .containsExactly(tuple(id, "3"));
    }

    @Test
    void shouldKeepHistoricalDuplicateIdentities_andUseTheOldestRow() {
        Integer oldest = store(QUEUE, null);
        Integer duplicate = store(QUEUE, "8");
        assertThat(IncomingDocUtil.getAndSetIncomingDocQueue(owner, null)).isEqualTo("1");
        assertThat(IncomingDocUtil.getAndSetIncomingDocQueue(owner, "3")).isEqualTo("3");
        assertThat(IncomingDocUtil.getAndSetIncomingDocQueue(owner, null)).isEqualTo("3");
        assertThat(rows(QUEUE)).extracting(UserProperty::getId, UserProperty::getValue)
                .containsExactlyInAnyOrder(tuple(oldest, "3"), tuple(duplicate, "8"));
    }

    @Test
    void shouldKeepOneRowForOverlappingFirstSelections_withoutBlockingOtherProvidersOrPreviews() throws Exception {
        var started = new CountDownLatch(2);
        try (var requests = Executors.newFixedThreadPool(2)) {
            List<Future<String>> selections = transactions.execute(status -> {
                assertThat(properties.lockProviderPreferences(owner)).isTrue();
                var submissions = List.of("3", "7").stream().map(queue -> requests.submit(() -> {
                    started.countDown();
                    return IncomingDocUtil.getAndSetIncomingDocQueue(owner, queue);
                })).toList();
                await(started);
                for (var pending : submissions) {
                    assertThatThrownBy(() -> pending.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                }
                assertThat(IncomingDocUtil.getAndSetIncomingDocQueue(owner, null)).isEqualTo("1");
                assertThat(IncomingDocUtil.getAndSetIncomingDocQueue(otherOwner, "9")).isEqualTo("9");
                return submissions;
            });
            assertThat(selections.get(0).get(20, TimeUnit.SECONDS)).isEqualTo("3");
            assertThat(selections.get(1).get(20, TimeUnit.SECONDS)).isEqualTo("7");
        }
        String visible = IncomingDocUtil.getAndSetIncomingDocQueue(owner, null);
        assertThat(visible).isIn("3", "7");
        assertThat(rows(QUEUE)).extracting(UserProperty::getValue).containsExactly(visible);
        assertThat(IncomingDocUtil.getAndSetIncomingDocQueue(otherOwner, null)).isEqualTo("9");
    }

    @Test
    void shouldPreserveTheLastSelection_whenBothRequestsPreviouslySawNoPreference() throws Exception {
        var bothRead = new CountDownLatch(2);
        var firstSaved = new CountDownLatch(1);
        try (var requests = Executors.newFixedThreadPool(2)) {
            var first = requests.submit(() -> transactions.execute(status -> {
                assertThat(properties.findIncomingDocumentQueue(owner)).isNull();
                bothRead.countDown();
                await(bothRead);
                String selected = IncomingDocUtil.getAndSetIncomingDocQueue(owner, "3");
                firstSaved.countDown();
                return selected;
            }));
            var second = requests.submit(() -> transactions.execute(status -> {
                assertThat(properties.findIncomingDocumentQueue(owner)).isNull();
                bothRead.countDown();
                await(firstSaved);
                return IncomingDocUtil.getAndSetIncomingDocQueue(owner, "7");
            }));
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo("3");
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo("7");
        }
        assertThat(IncomingDocUtil.getAndSetIncomingDocQueue(owner, null)).isEqualTo("7");
        assertThat(rows(QUEUE)).extracting(UserProperty::getValue).containsExactly("7");
    }

    @Test
    void shouldRejectExplicitSelectionsWithoutAnOwner() {
        for (String missing : new String[]{null, "M" + owner.substring(1)}) {
            assertThatThrownBy(() -> IncomingDocUtil.getAndSetIncomingDocQueue(missing, "3"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
