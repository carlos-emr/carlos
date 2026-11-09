/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ScheduleSyncSelectionUnitTest {
    @Test void shouldRetainValueSemantics_andDefendTheContextArray() {
        byte[] context = {1, 2, 3};
        var value = new ScheduleSyncHeaders.Selection(true, 7, 8, 9, context, "FAKE-cursor");
        var same = new ScheduleSyncHeaders.Selection(true, 7, 8, 9, new byte[]{1, 2, 3}, "FAKE-cursor");
        context[0] = 4; value.context()[1] = 5;
        assertThat(value).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(value.toString()).isEqualTo(same.toString()).contains("[1, 2, 3]");
        assertThat(value).isNotEqualTo(new ScheduleSyncHeaders.Selection(true, 7, 8, 9, new byte[]{4, 2, 3}, "FAKE-cursor"));
    }
}
