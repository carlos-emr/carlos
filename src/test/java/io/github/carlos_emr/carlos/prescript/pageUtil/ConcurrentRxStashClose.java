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

import java.util.concurrent.TimeUnit;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Arranges a second window's close exactly between a key lookup and use of its index. */
final class ConcurrentRxStashClose extends RxSessionBean implements AutoCloseable {
    private final int keyToClose;
    private Thread closer;
    private boolean closeAfterSizeCheck;

    ConcurrentRxStashClose(int keyToClose) {
        this.keyToClose = keyToClose;
    }

    @Override
    public int getIndexFromRx(int randomId) {
        int index = super.getIndexFromRx(randomId);
        startClose();
        return index;
    }

    @Override
    public RxPrescriptionData.Prescription getCurrentStashItem() {
        RxPrescriptionData.Prescription item = super.getCurrentStashItem();
        startClose();
        return item;
    }

    void armSizeCheck() {
        closeAfterSizeCheck = true;
    }

    @Override
    public int getStashSize() {
        int size = super.getStashSize();
        if (closeAfterSizeCheck) {
            closeAfterSizeCheck = false;
            startClose();
        }
        return size;
    }

    private void startClose() {
        if (closer == null) {
            closer = new Thread(() -> {
                synchronized (this) {
                    int closeIndex = super.getIndexFromRx(keyToClose);
                    removeStashItem(closeIndex);
                }
            }, "concurrent-rx-close");
            closer.start();
            // With atomic request operations the close blocks until the operation finishes.
            // Without them it finishes here, moving the index onto a different medication.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (closer.getState() != Thread.State.BLOCKED && closer.isAlive()
                    && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            assertThat(closer.getState()).isIn(Thread.State.BLOCKED, Thread.State.TERMINATED);
        }
    }

    void awaitCompletion() throws InterruptedException {
        if (closer != null) {
            closer.join(TimeUnit.SECONDS.toMillis(5));
            assertThat(closer.isAlive()).as("the other window's close completed").isFalse();
        }
    }

    @Override
    public void close() throws InterruptedException {
        awaitCompletion();
    }
}
