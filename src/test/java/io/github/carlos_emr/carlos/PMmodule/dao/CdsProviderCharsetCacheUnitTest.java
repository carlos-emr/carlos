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
package io.github.carlos_emr.carlos.PMmodule.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Cache publication is shared across threads; failed schema metadata must remain retryable. */
@Tag("unit")
class CdsProviderCharsetCacheUnitTest {
    @Test void shouldPublishOneImmutableCharsetSnapshot_whenConcurrentReadersArrive() throws Exception {
        var manager = mock(EntityManager.class); var query = mock(Query.class);
        when(manager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setMaxResults(3)).thenReturn(query);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(query.getResultList()).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test metadata release timed out");
            return metadata("latin1");
        });
        var dao = new ProviderDaoImpl(); ReflectionTestUtils.setField(dao, "entityManager", manager);
        var workers = Executors.newFixedThreadPool(8);
        try {
            var futures = java.util.stream.IntStream.range(0, 8).mapToObj(ignored -> workers.submit(
                    () -> ReflectionTestUtils.invokeMethod(dao, "cdsNameCharsets"))).toList();
            assertTrue(entered.await(5, TimeUnit.SECONDS)); release.countDown();
            Object first = futures.getFirst().get(5, TimeUnit.SECONDS);
            assertNotNull(first);
            for (var future : futures) assertSame(first, future.get(5, TimeUnit.SECONDS));
            verify(query, times(1)).getResultList();
        } finally { release.countDown(); workers.shutdownNow(); }
    }

    @Test void shouldRetryAfterInvalidMetadata_withoutPublishingAnUnsafeCharset() {
        var manager = mock(EntityManager.class); var query = mock(Query.class);
        when(manager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setMaxResults(3)).thenReturn(query);
        when(query.getResultList()).thenReturn(metadata("latin1;invalid"), metadata("latin1"));
        var dao = new ProviderDaoImpl(); ReflectionTestUtils.setField(dao, "entityManager", manager);
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(dao, "cdsNameCharsets"));
        Object valid = ReflectionTestUtils.invokeMethod(dao, "cdsNameCharsets");
        assertNotNull(valid); assertSame(valid, ReflectionTestUtils.invokeMethod(dao, "cdsNameCharsets"));
        verify(query, times(2)).getResultList();
    }

    private static List<Object[]> metadata(String first) {
        return List.of(new Object[]{"first_name", first}, new Object[]{"last_name", "latin1"});
    }
}
