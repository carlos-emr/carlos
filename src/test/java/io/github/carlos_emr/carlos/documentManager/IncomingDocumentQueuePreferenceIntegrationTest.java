/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private TransactionTemplate transactions;
    private String owner;
    private Integer otherId;

    @BeforeEach
    void preparePreferences() {
        transactions = new TransactionTemplate(transactionManager);
        owner = "Q" + UUID.randomUUID().toString().substring(0, 5);
        otherId = store(OTHER, "manual");
    }

    @AfterEach
    void removeOwnedProperties() {
        transactions.executeWithoutResult(status -> {
            for (String key : List.of(QUEUE, OTHER)) {
                properties.getAllProperties(key, List.of(owner)).forEach(properties::delete);
            }
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

    private void concurrentPreviewsReturn(String expected) throws Exception {
        CountDownLatch bothReading = new CountDownLatch(2);
        Callable<String> preview = () -> transactions.execute(status -> {
            // Establish both database read views before either request resolves the default.
            properties.getProp(owner, QUEUE);
            bothReading.countDown();
            try {
                assertThat(bothReading.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
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
}
