/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.dashboard.admin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TicklerSubmissionUnitTest {
    private Map<String, String[]> parameters() {
        return new HashMap<>(Map.of("demographics", new String[]{"101,102"},
                "messageAppend", new String[]{"Follow up"}));
    }

    @Test
    void shouldReuseResult_whenResponseIsLostOrFormIsCopied() {
        var session = new MockHttpSession();
        String token = TicklerSubmission.issue(session, "101,102");
        var calls = new AtomicInteger();
        assertEquals(true, TicklerSubmission.execute(session, token, parameters(), () -> calls.incrementAndGet() == 1));
        assertEquals(true, TicklerSubmission.execute(session, token, parameters(), () -> calls.incrementAndGet() == 1));
        assertEquals(1, calls.get());
    }

    @Test
    void shouldSerializeConcurrentRetries_ofOneOperation() throws Exception {
        var session = new MockHttpSession();
        String token = TicklerSubmission.issue(session, "101,102");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var retryStarted = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> TicklerSubmission.execute(session, token, parameters(), () -> {
                calls.incrementAndGet();
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return true;
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var retry = executor.submit(() -> {
                retryStarted.countDown();
                return TicklerSubmission.execute(session, token, parameters(), () -> { calls.incrementAndGet(); return true; });
            });
            assertTrue(retryStarted.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(true, first.get(5, TimeUnit.SECONDS));
            assertEquals(true, retry.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally { release.countDown(); }
    }

    @Test
    void shouldRefuseUnknownOrOtherSessionKeys_withoutSaving() {
        var session = new MockHttpSession();
        String token = TicklerSubmission.issue(session, "101,102");
        assertNull(TicklerSubmission.execute(new MockHttpSession(), token, parameters(), () -> fail("saved")));
        assertNull(TicklerSubmission.execute(session, "unknown", parameters(), () -> fail("saved")));
        assertNull(TicklerSubmission.execute(null, token, parameters(), () -> fail("saved")));
    }

    @Test
    void shouldRefuseChangedPatientsAndPayload_withoutConsumingAnUnusedKey() {
        var session = new MockHttpSession();
        String token = TicklerSubmission.issue(session, "101,102");
        var altered = parameters();
        altered.put("demographics", new String[]{"103"});
        assertNull(TicklerSubmission.execute(session, token, altered, () -> fail("saved")));
        assertEquals(true, TicklerSubmission.execute(session, token, parameters(), () -> true));
        altered = parameters();
        altered.put("messageAppend", new String[]{"Different operation"});
        assertNull(TicklerSubmission.execute(session, token, altered, () -> fail("saved")));
    }

    @Test
    void shouldNeverRepeatPartialOrUncertainWrites() {
        var session = new MockHttpSession();
        String failed = TicklerSubmission.issue(session, "101,102");
        assertEquals(false, TicklerSubmission.execute(session, failed, parameters(), () -> false));
        assertEquals(false, TicklerSubmission.execute(session, failed, parameters(), () -> fail("retried partial save")));
        String uncertain = TicklerSubmission.issue(session, "101,102");
        assertThrows(IllegalStateException.class, () -> TicklerSubmission.execute(session, uncertain,
                parameters(), () -> { throw new IllegalStateException("after first write"); }));
        assertEquals(false, TicklerSubmission.execute(session, uncertain, parameters(), () -> fail("retried uncertain save")));
    }

    @Test
    void shouldAllowDistinctOperations_andRefuseEvictedKeys() {
        var session = new MockHttpSession();
        String first = TicklerSubmission.issue(session, "101,102");
        String second = TicklerSubmission.issue(session, "101,102");
        assertNotEquals(first, second);
        assertEquals(true, TicklerSubmission.execute(session, first, parameters(), () -> true));
        assertEquals(true, TicklerSubmission.execute(session, second, parameters(), () -> true));
        for (int i = 0; i < 64; i++) TicklerSubmission.issue(session, "101,102");
        assertNull(TicklerSubmission.execute(session, first, parameters(), () -> fail("saved expired form")));
    }
}
