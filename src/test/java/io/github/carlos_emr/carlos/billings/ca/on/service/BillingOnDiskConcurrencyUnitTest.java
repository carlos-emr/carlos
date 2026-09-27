/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class BillingOnDiskConcurrencyUnitTest {
    @TempDir Path directory;
    private Object oldHome;

    @BeforeEach void configure() {
        oldHome = CarlosProperties.getInstance().put("HOME_DIR", directory.toString());
    }
    @AfterEach void restore() {
        if (oldHome == null) CarlosProperties.getInstance().remove("HOME_DIR");
        else CarlosProperties.getInstance().put("HOME_DIR", oldHome);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectOverlappingOperation_beforeReadingOrChangingBillingState(boolean regenerate) throws Exception {
        var providers = mock(ProviderDao.class);
        var prep = mock(BillingDiskCreationService.class);
        var loader = mock(BillingOnDiskLoader.class);
        when(loader.getDiskCreateDate("1")).thenReturn("2026-09-26");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(providers.getProvider("999998")).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        });
        var transaction = mock(BillingOnDiskTransactionService.class);
        var first = new BillingOnDiskService(providers, prep, loader, () -> null, transaction);
        var second = new BillingOnDiskService(providers, prep, loader, () -> null, transaction);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> first.generateNewDisk(request()));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> {
                    if (regenerate) second.regenerateDisk(request());
                    else second.generateNewDisk(request());
                }).isInstanceOf(BillingFileWriteException.class).hasMessageContaining("already in progress");
                verifyNoInteractions(prep, transaction);
                verify(loader, never()).getDiskCreateDate("1");
            } finally {
                release.countDown();
                running.get(10, TimeUnit.SECONDS);
            }
        }
        // The lease must also be released after a normal completion.
        second.generateNewDisk(request());
        assertThat(calls.get()).isEqualTo(2);
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setParameter("providers", "999998");
        request.setParameter("diskId", "1");
        request.setParameter("curDate", "2026-09-26");
        return request;
    }
}
