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
package io.github.carlos_emr.carlos.login;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes the concurrent-session admission step for one user (issue #3980).
 *
 * <p>A login under a non-default policy counts the user's other sessions, decides, registers
 * the new session and then signs others out. Without serialization, two logins for the same
 * account can both count the same sessions before either registers: two first logins under
 * {@code single} would both see zero and both stay signed in, and a configured maximum could be
 * exceeded. Holding a per-user lock across count, decision, registration and settlement makes
 * each login see the previous one's session.</p>
 *
 * <p>Locks are striped by security number so the memory cost is fixed and unrelated users rarely
 * contend. The lock is reentrant because signing a session out runs {@code OscarSessionListener}
 * on the same thread. It is per JVM, like the session registry it protects.</p>
 *
 * @since 2026-09-26
 */
final class ConcurrentSessionAdmission {

    private static final int STRIPES = 64;
    private static final ReentrantLock[] LOCKS = new ReentrantLock[STRIPES];

    static {
        for (int i = 0; i < STRIPES; i++) {
            LOCKS[i] = new ReentrantLock();
        }
    }

    private ConcurrentSessionAdmission() {
        // Static lock holder.
    }

    /** A login step that may write to the response. */
    @FunctionalInterface
    interface Step<T> {
        T run() throws IOException;
    }

    /**
     * Runs {@code step} while holding the admission lock for {@code securityNo}.
     *
     * @param securityNo security row of the user signing in; {@code null} runs unlocked
     * @param step the count/decide/register/settle sequence
     * @return the step's result
     * @throws IOException when the step does
     */
    static <T> T serialize(Integer securityNo, Step<T> step) throws IOException {
        if (securityNo == null) {
            return step.run();
        }
        ReentrantLock lock = lockFor(securityNo);
        lock.lock();
        try {
            return step.run();
        } finally {
            lock.unlock();
        }
    }

    static ReentrantLock lockFor(Integer securityNo) {
        return LOCKS[Math.floorMod(securityNo.hashCode(), STRIPES)];
    }
}
