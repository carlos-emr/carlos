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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stash cursor ({@link RxSessionBean#getStashIndex()}) stays on a staged item or at -1, and
 * follows its item when other items are removed, so a bad or stale index from a request can never
 * select another item or make a later read fail (#3908).
 *
 * @since 2026-09-24
 */
@DisplayName("RxSessionBean stash cursor")
@Tag("unit")
@Tag("prescript")
class RxSessionBeanCursorUnitTest extends CarlosUnitTestBase {

    private RxSessionBean bean;
    private RxPrescriptionData.Prescription first;
    private RxPrescriptionData.Prescription second;
    private RxPrescriptionData.Prescription third;

    @BeforeEach
    void setUp() {
        bean = new RxSessionBean();
        bean.setDemographicNo(1001);
        first = staged(1L);
        second = staged(2L);
        third = staged(3L);
        bean.getStashList().add(first);
        bean.getStashList().add(second);
        bean.getStashList().add(third);
    }

    private static RxPrescriptionData.Prescription staged(long randomId) {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, "999998", 1001);
        rx.setRandomId(randomId);
        return rx;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {-2, -100, 3, 99})
    @DisplayName("should ignore a cursor index outside the stash")
    void shouldKeepCursor_whenIndexOutOfRange(int index) {
        bean.setStashIndex(1);

        bean.setStashIndex(index);

        assertThat(bean.getStashIndex()).isEqualTo(1);
        assertThat(bean.getCurrentStashItem()).isSameAs(second);
    }

    @Test
    @DisplayName("should allow clearing the selection with -1")
    void shouldClearSelection_withMinusOne() {
        bean.setStashIndex(1);

        bean.setStashIndex(-1);

        assertThat(bean.getStashIndex()).isEqualTo(-1);
        assertThat(bean.getCurrentStashItem()).isNull();
    }

    @Test
    @DisplayName("should keep the cursor on its item when an earlier item is removed")
    void shouldFollowSelectedItem_whenEarlierItemRemoved() {
        bean.setStashIndex(2);

        bean.removeStashItem(0);

        assertThat(bean.getCurrentStashItem()).isSameAs(third);
    }

    @Test
    @DisplayName("should keep the cursor within the stash when the selected last item is removed")
    void shouldMoveToNewLastItem_whenSelectedLastItemRemoved() {
        bean.setStashIndex(2);

        bean.removeStashItem(2);

        assertThat(bean.getStashIndex()).isEqualTo(1);
        assertThat(bean.getCurrentStashItem()).isSameAs(second);
    }

    @Test
    @DisplayName("should keep the cursor unchanged when a later item is removed")
    void shouldKeepCursor_whenLaterItemRemoved() {
        bean.setStashIndex(0);

        bean.removeStashItem(2);

        assertThat(bean.getCurrentStashItem()).isSameAs(first);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {-1, 3, 50})
    @DisplayName("should ignore a removal index outside the stash")
    void shouldIgnoreRemoval_whenIndexOutOfRange(int index) {
        bean.setStashIndex(1);

        bean.removeStashItem(index);

        assertThat(bean.getStashList()).containsExactly(first, second, third);
        assertThat(bean.getCurrentStashItem()).isSameAs(second);
    }

    @Test
    @DisplayName("should reset the cursor when the stash is cleared")
    void shouldResetCursor_whenStashCleared() {
        bean.setStashIndex(2);

        bean.clearStash();

        assertThat(bean.getStashIndex()).isEqualTo(-1);
        assertThat(bean.getCurrentStashItem()).isNull();
    }

    @Test
    @DisplayName("should defer a legacy ReRx clear until the atomic bean operation completes")
    void shouldDeferReRxClear_whenAtomicBeanOperationIsInProgress() throws InterruptedException {
        bean.addReRxDrugIdList("55");
        CountDownLatch attempted = new CountDownLatch(1);
        Thread clear = new Thread(() -> {
            attempted.countDown();
            bean.clearReRxDrugIdList();
        }, "legacy-rerx-clear");

        try {
            synchronized (bean) {
                clear.start();
                assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (clear.isAlive() && clear.getState() != Thread.State.BLOCKED
                        && System.nanoTime() < deadline) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                }
                assertThat(clear.getState()).isEqualTo(Thread.State.BLOCKED);
                assertThat(bean.getReRxDrugIdList()).containsExactly("55");
            }
        } finally {
            clear.join(TimeUnit.SECONDS.toMillis(5));
        }

        assertThat(clear.isAlive()).isFalse();
        assertThat(bean.getReRxDrugIdList()).isEmpty();
        assertThat(bean.getStashList()).containsExactly(first, second, third);
    }

    @Test
    @DisplayName("should pull a cursor left past the end back to the last item")
    void shouldClampCursor_whenItemsRemovedOutsideTheBean() {
        bean.setStashIndex(2);
        // removeFromReRxDrugIdList removes through the list's iterator, bypassing removeStashItem.
        Iterator<RxPrescriptionData.Prescription> iterator = bean.getStashList().iterator();
        iterator.next();
        iterator.remove();
        iterator.next();
        iterator.remove();

        assertThat(bean.getStashIndex()).isZero();
        assertThat(bean.getCurrentStashItem()).isSameAs(third);
    }

    @Test
    @DisplayName("should keep each patient's cursor on its own bean")
    void shouldKeepCursorPerPatient_withTwoPatientsOpen() {
        RxSessionBean other = new RxSessionBean();
        other.setDemographicNo(2002);
        other.getStashList().add(staged(9L));
        bean.setStashIndex(2);

        other.setStashIndex(0);
        other.removeStashItem(0);

        assertThat(bean.getCurrentStashItem()).isSameAs(third);
        assertThat(other.getStashIndex()).isEqualTo(-1);
    }
}
