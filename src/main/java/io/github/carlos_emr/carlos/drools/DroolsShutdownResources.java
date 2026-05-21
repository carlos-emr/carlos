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
package io.github.carlos_emr.carlos.drools;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.drools.compiler.builder.impl.KnowledgeBuilderImpl;
import org.kie.internal.concurrent.ExecutorProviderFactory;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/** Releases Drools compiler resources that retain the webapp class loader. */
public final class DroolsShutdownResources {

    private static final Logger logger = MiscUtils.getLogger();
    private static final int COMPILER_POOL_SHUTDOWN_TIMEOUT_SECONDS = 5;

    private DroolsShutdownResources() {
    }

    public static int shutdownExecutors() {
        RuleBaseFactory.flushAllCached();
        int dropped = shutdownCompilerPool();
        dropped += shutdownKieExecutor();
        return dropped;
    }

    private static int shutdownCompilerPool() {
        ForkJoinPool compilerPool = KnowledgeBuilderImpl.ForkJoinPoolHolder.COMPILER_POOL;
        compilerPool.shutdown();
        try {
            if (!compilerPool.awaitTermination(COMPILER_POOL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return logDroppedTasks("Drools compiler pool", compilerPool.shutdownNow());
            }
        } catch (InterruptedException e) {
            int dropped = logDroppedTasks("Drools compiler pool", compilerPool.shutdownNow());
            Thread.currentThread().interrupt();
            return dropped;
        }
        return 0;
    }

    private static int shutdownKieExecutor() {
        ExecutorService executor = ExecutorProviderFactory.getExecutorProvider().getExecutor();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(COMPILER_POOL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return logDroppedTasks("Drools KIE executor", executor.shutdownNow());
            }
        } catch (InterruptedException e) {
            int dropped = logDroppedTasks("Drools KIE executor", executor.shutdownNow());
            Thread.currentThread().interrupt();
            return dropped;
        }
        return 0;
    }

    private static int logDroppedTasks(String executorName, List<Runnable> droppedTasks) {
        if (droppedTasks.isEmpty()) {
            return 0;
        }
        logger.warn("{} shutdown dropped {} queued task(s)", executorName, droppedTasks.size());
        return droppedTasks.size();
    }
}
