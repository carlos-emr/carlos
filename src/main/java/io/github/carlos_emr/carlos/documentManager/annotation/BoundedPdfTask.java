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
package io.github.carlos_emr.carlos.documentManager.annotation;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs one piece of PDF work on a daemon thread with a hard deadline.
 *
 * <p>Every PDF this package touches is untrusted: an inbound fax is whatever the sender chose
 * to transmit. PDFBox has no internal time budget, so a document crafted to drive the parser
 * into pathological work would otherwise hold a servlet thread for as long as it liked, and a
 * handful of such requests would exhaust the pool. Each entry point that parses a document
 * routes through here so the ceiling is enforced in one place rather than declared in three
 * and applied in none.
 *
 * <p>A timeout cancels the future and surfaces as {@link IOException}, releasing the request
 * thread. The abandoned worker keeps running: {@code cancel(true)} only sets the interrupt flag,
 * and a PDFBox parse is CPU-bound and never checks it. Java cannot kill a thread, so that is
 * unavoidable; the worker is a daemon precisely so a wedged parse cannot keep the JVM alive.
 *
 * <p>The executor is shut down with {@code shutdownNow()} in a finally block, NOT with
 * try-with-resources. {@link ExecutorService#close()} is {@code shutdown()} followed by an
 * unbounded {@code awaitTermination}, so closing here parks the caller until the abandoned parse
 * finishes — measured at 6.0s for a 6s task under a 1s deadline, which makes the deadline inert
 * for exactly the case this class exists to handle.
 *
 * @since 2026-09
 */
public final class BoundedPdfTask {

    private BoundedPdfTask() {
    }

    /**
     * @param seconds    hard deadline; the caller's thread is released when it expires
     * @param threadName names the worker so a wedged parse is identifiable in a thread dump
     * @param task       the parse to run
     * @return the task's result
     * @throws IOException on timeout, interruption, or a checked failure inside the task.
     *         A {@link RuntimeException} thrown by the task propagates unchanged, because a
     *         programming error should not be disguised as an I/O problem.
     */
    public static <T> T runWithin(int seconds, String threadName, Callable<T> task) throws IOException {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread worker = new Thread(runnable, threadName);
            worker.setDaemon(true);
            return worker;
        });
        try {
            Future<T> future = executor.submit(task);
            try {
                return future.get(seconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new IOException("Reading the document took too long.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Reading the document was interrupted.");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException io) {
                    throw io;
                }
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new IOException("The document could not be read.");
            }
        } finally {
            // Returns immediately; close() would wait for the abandoned worker instead.
            executor.shutdownNow();
        }
    }
}
