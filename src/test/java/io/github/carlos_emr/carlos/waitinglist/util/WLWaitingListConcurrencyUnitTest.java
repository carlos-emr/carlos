/*
 * Copyright (c) 2026 CARLOS Contributors.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details. You should have received a copy of the GNU General
 * Public License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * CARLOS EMR Project, https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.waitinglist.util;

import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
class WLWaitingListConcurrencyUnitTest {
    @Test
    void shouldRepositionActiveEntries_whenRemovingAPatient() {
        WaitingListDao dao = mock(WaitingListDao.class);
        var transactions = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        WaitingList removed = new WaitingList();
        WaitingList remaining = new WaitingList();
        removed.setDemographicNo(101);
        removed.setHistory(false);
        remaining.setDemographicNo(102);
        remaining.setHistory(false);
        remaining.setPosition(2);
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(dao.findByWaitingListIdForUpdate(7)).thenReturn(List.of(removed, remaining));

        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(WaitingListDao.class)).thenReturn(dao);
            spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(transactions);
            WLWaitingListUtil.removeFromWaitingList("7", "101");
        }

        assertThat(removed.getIsHistory()).isEqualTo(WaitingList.IS_HISTORY_YES);
        assertThat(remaining.getPosition()).isOne();
        var operations = inOrder(transactions, dao);
        operations.verify(transactions).getTransaction(any());
        operations.verify(dao).findByWaitingListIdForUpdate(7);
        operations.verify(dao).merge(same(removed));
        operations.verify(dao).merge(same(remaining));
        operations.verify(transactions).commit(transaction);
    }

    @Test
    void shouldRollBackAndChangeNothing_whenTheListCannotBeLocked() {
        WaitingListDao dao = mock(WaitingListDao.class);
        var transactions = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        var refused = new CannotAcquireLockException("FAKE lock refusal");
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(dao.findByWaitingListIdForUpdate(7)).thenThrow(refused);

        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(WaitingListDao.class)).thenReturn(dao);
            spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(transactions);
            assertThatThrownBy(() -> WLWaitingListUtil.removeFromWaitingList("7", "101")).isSameAs(refused);
        }

        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
        verify(dao, never()).merge(any(WaitingList.class));
    }

    @Test
    void shouldReposition_whileAnotherThreadHoldsThePublicClassMonitor() throws Exception {
        WaitingListDao dao = mock(WaitingListDao.class);
        try (var worker = Executors.newSingleThreadExecutor()) {
            synchronized (WLWaitingListUtil.class) {
                var reposition = worker.submit(() -> {
                    try (var spring = mockStatic(SpringUtils.class)) {
                        spring.when(() -> SpringUtils.getBean(WaitingListDao.class)).thenReturn(dao);
                        WLWaitingListUtil.rePositionWaitingList("7");
                    }
                });
                reposition.get(5, TimeUnit.SECONDS);
            }
        }
        verify(dao).findByWaitingListId(7);
    }

    @Test
    void shouldSerializePublicReposition_withConcurrentWaitingListInsertion() throws Exception {
        WaitingListDao dao = mock(WaitingListDao.class);
        WaitingList existing = new WaitingList(); existing.setPosition(10);
        var rows = new CopyOnWriteArrayList<WaitingList>(); rows.add(existing);
        CountDownLatch choosing = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        AtomicBoolean inserting = new AtomicBoolean();
        AtomicBoolean interleaved = new AtomicBoolean();
        when(dao.getMaxPosition(7)).thenAnswer(call -> {
            inserting.set(true); choosing.countDown();
            if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("Insert was not resumed");
            return 10;
        });
        doAnswer(call -> { rows.add(call.getArgument(0)); inserting.set(false); return null; }).when(dao).persist(any(WaitingList.class));
        when(dao.findByWaitingListId(7)).thenAnswer(call -> {
            if (inserting.get()) interleaved.set(true);
            return java.util.List.copyOf(rows);
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                try (var spring = mockStatic(SpringUtils.class)) {
                    spring.when(() -> SpringUtils.getBean(WaitingListDao.class)).thenReturn(dao);
                    WLWaitingListUtil.add2WaitingList("7", "FAKE new entry", "101", null);
                }
            });
            assertThat(choosing.await(5, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> {
                try (var spring = mockStatic(SpringUtils.class)) {
                    spring.when(() -> SpringUtils.getBean(WaitingListDao.class)).thenReturn(dao);
                    secondThread.set(Thread.currentThread()); secondStarted.countDown();
                    WLWaitingListUtil.rePositionWaitingList("7");
                }
            });
            try {
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!second.isDone() && secondThread.get().getState() != Thread.State.BLOCKED
                        && System.nanoTime() < deadline) Thread.yield();
            } finally { resume.countDown(); }
            first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
        } finally { resume.countDown(); }
        assertThat(interleaved).as("DAO reorder must not enter the position allocation/insertion critical section").isFalse();
        assertThat(rows.stream().map(WaitingList::getPosition).toList()).containsExactly(1L, 2L);
    }
}
