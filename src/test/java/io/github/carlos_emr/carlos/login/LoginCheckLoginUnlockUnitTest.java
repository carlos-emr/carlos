/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.login;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for {@link LoginCheckLogin#unlock(String)}: it must report success for exactly one
 * caller per tracked entry, because {@code UnLock2Action} writes one audit row per success.
 * Mutates the application-scoped {@link LoginList}, hence {@code @Isolated}.
 */
@Isolated
@Tag("unit")
@Tag("login")
@Tag("security")
@DisplayName("LoginCheckLogin unlock")
class LoginCheckLoginUnlockUnitTest {

    private static final String KEY = "203.0.113.9";

    @BeforeEach
    @AfterEach
    void clearEntry() {
        LoginList.getLoginListInstance().remove(KEY);
    }

    @Test
    void shouldReportSuccessOnce_whenTheSameEntryIsUnlockedConcurrently() throws Exception {
        int callers = 16;
        for (int round = 0; round < 50; round++) {
            LoginList.getLoginListInstance().put(KEY, new LoginInfoBean(new java.util.GregorianCalendar(), 3, 10));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(callers);
            try {
                List<Future<Boolean>> results = new ArrayList<>();
                for (int i = 0; i < callers; i++) {
                    results.add(pool.submit(() -> {
                        LoginCheckLogin check = new LoginCheckLogin();
                        start.await();
                        return check.unlock(KEY);
                    }));
                }
                start.countDown();
                int successes = 0;
                for (Future<Boolean> result : results) {
                    if (result.get()) {
                        successes++;
                    }
                }
                assertThat(successes).isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void shouldReportNothingRemoved_whenTheEntryIsNotTracked() {
        assertThat(new LoginCheckLogin().unlock(KEY)).isFalse();
    }
}
