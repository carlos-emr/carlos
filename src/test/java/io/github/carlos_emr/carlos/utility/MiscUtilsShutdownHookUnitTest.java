/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Isolated
class MiscUtilsShutdownHookUnitTest {
    @Test
    void registersOnlyOneHookAndRemovesItOnRepeatedShutdown() {
        Object previous = ReflectionTestUtils.getField(MiscUtils.class, "shutdownHookThread");
        ReflectionTestUtils.setField(MiscUtils.class, "shutdownHookThread", null);
        try {
            MiscUtils.registerShutdownHook();
            Object registered = ReflectionTestUtils.getField(MiscUtils.class, "shutdownHookThread");
            assertThat(registered).isInstanceOf(Thread.class);
            MiscUtils.registerShutdownHook();
            assertThat(ReflectionTestUtils.getField(MiscUtils.class, "shutdownHookThread")).isSameAs(registered);
            MiscUtils.deregisterShutdownHook();
            MiscUtils.deregisterShutdownHook();
            assertThat(ReflectionTestUtils.getField(MiscUtils.class, "shutdownHookThread")).isNull();
        } finally {
            MiscUtils.deregisterShutdownHook();
            ReflectionTestUtils.setField(MiscUtils.class, "shutdownHookThread", previous);
        }
    }
}
