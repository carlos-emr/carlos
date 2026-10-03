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
package io.github.carlos_emr.carlos.casemgmt.util;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Capacity, snapshot isolation and concurrent registration contracts. */
@Tag("unit")
class ExtPrintRegistryTest {
    @BeforeEach
    @AfterEach
    void clearRegistry() throws Exception {
        Field field = ExtPrintRegistry.class.getDeclaredField("entries");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(null)).clear();
    }

    @Test
    void shouldRejectOverflow_withoutEvictingExistingRegistrations() {
        for (int i = 0; i < ExtPrintRegistry.MAX_ENTRIES; i++) ExtPrintRegistry.addEntry("printer" + i, "bean" + i);
        assertThatIllegalStateException().isThrownBy(() -> ExtPrintRegistry.addEntry("overflow", "bean"));
        ExtPrintRegistry.addEntry("printer0", "replacement");
        assertThat(ExtPrintRegistry.getEntry("printer0")).isEqualTo("replacement");
        assertThat(ExtPrintRegistry.getEntry("printer127")).isEqualTo("bean127");
        assertThat(ExtPrintRegistry.getEntries()).hasSize(ExtPrintRegistry.MAX_ENTRIES);
    }

    @Test
    void shouldProtectSnapshot_fromWritesAndLaterRegistrations() {
        ExtPrintRegistry.addEntry("first", "bean");
        Map<String, String> snapshot = ExtPrintRegistry.getEntries();
        assertThatThrownBy(() -> snapshot.put("bypass", "bean")).isInstanceOf(UnsupportedOperationException.class);
        ExtPrintRegistry.addEntry("second", "bean2");
        assertThat(snapshot).containsOnlyKeys("first");
    }

    @Test
    void shouldRejectInvalidNames_beforeChangingRegistry() {
        for (String invalid : new String[]{null, "", "  ", "x".repeat(257)}) {
            assertThatIllegalArgumentException().isThrownBy(() -> ExtPrintRegistry.addEntry(invalid, "bean"));
            assertThatIllegalArgumentException().isThrownBy(() -> ExtPrintRegistry.addEntry("name", invalid));
        }
        assertThat(ExtPrintRegistry.getEntries()).isEmpty();
    }

    @Test
    void shouldEnforceCapacity_withConcurrentRegistrations() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 256; i++) {
                final String name = "printer" + i;
                results.add(executor.submit(() -> {
                    try {
                        ExtPrintRegistry.addEntry(name, "bean");
                        return true;
                    } catch (IllegalStateException full) {
                        return false;
                    }
                }));
            }
            int accepted = 0;
            for (Future<Boolean> result : results) if (result.get()) accepted++;
            assertThat(accepted).isEqualTo(ExtPrintRegistry.MAX_ENTRIES);
            assertThat(ExtPrintRegistry.getEntries()).hasSize(accepted);
        }
    }
}
